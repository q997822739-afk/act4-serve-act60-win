package com.dylan.net.entrance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 登录/网关服（entrance）—— 让客户端能进**频道选择界面**。
 *
 * <h2>为什么需要它</h2>
 * 客户端在频道界面会去连一个**硬编码**的 {@code 121.14.107.176:7001}（官方登录服），
 * 完全无视命令行里传的游戏服地址。那个地址在客户端任何文件里都找不到（是内部混淆的），
 * 所以解法是**把地址接管过来**：
 * <pre>
 *   New-NetIPAddress -InterfaceAlias '以太网' -IPAddress 121.14.107.176 -PrefixLength 32 -SkipAsSource $true
 * </pre>
 * 然后在本进程监听 7001，客户端就落到这里了。实测客户端第一发包（43 字节）：
 * <pre>
 *   00 0B 2B 00 00 00 00 00 00 00 01 | F2 F7 …（32 字节）
 * </pre>
 *
 * <h2>协议（移植自 A12 {@code proto/entrance.cpp}）</h2>
 * <table>
 *   <tr><th>请求</th><th>应答协议号</th><th>应答 payload</th></tr>
 *   <tr><td>11 密钥交换</td><td><b>12</b></td><td>{@code u32(0)} + 32 字节密钥（0x10..0x2F），**不加密**；请求体完全不解析</td></tr>
 *   <tr><td>5 握手</td><td><b>6</b></td><td>{@code u32(脚本为空?1:0)} + 16 字节令牌 {@code "dnf2008-local\0\0\0"} + 12 个 0，然后 **AES（只加密，不 deflate）**</td></tr>
 *   <tr><td>9 频道脚本</td><td><b>10</b></td><td>{@code Wrap(频道脚本)}</td></tr>
 *   <tr><td>17 组表</td><td><b>18</b></td><td>{@code Wrap(BuildGroupTable(0, 频道数))}</td></tr>
 *   <tr><td>1 服务器表</td><td><b>3</b></td><td>{@code Wrap(Encode())}</td></tr>
 * </table>
 * 应答协议号那张映射表是 A12 {@code session/server_session.cpp:226-230} 里的三元表达式，
 * **不是**"请求号加一"：其中 {@code 5 → 6} 是因为 {@code packet_ids.h} 里
 * {@code CREATE_CHARACTER = 5}（不是"握手用 6"这种直觉），最容易搞错。
 *
 * <p>网关帧本身**不做** {@code XorRotBody}（那是游戏频道帧才有的），直接明文套 11 字节头。
 */
public final class EntranceServer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * 协议 5 的令牌：ASCII {@code "dnf2008-local"}（13 字节）+ 3 个 0 = 16 字节。
     *
     * <p>不用字符字面量数组（{@code {'d','n',…}}）是因为 char 常量缩窄到 byte 在
     * 数组初始化器里容易出编译问题，而这份代码**没有编译器可验证** —— 用 getBytes 最稳。
     */
    private static final byte[] TOKEN_PREFIX =
            "dnf2008-local".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private static final int TOKEN_LENGTH = 16;

    private final ChannelList channels;
    private final int port;
    private final boolean plainMode;
    private final Path logFile;
    private final AtomicInteger connections = new AtomicInteger();

    /** 当前还连着的连接数（上限按这个判）。 */
    private final AtomicInteger open = new AtomicInteger();

    /**
     * 组表的 {@code groupIndex} 字段。
     *
     * <p>A12 里是硬编码 {@code BuildGroupTable(0, …)} —— 但它的内置脚本声明的是
     * {@code [server] 1}（卡恩），也就是"组 1 ↔ 索引 0"。我们的脚本声明的是
     * {@code [server] 2}（迪雷吉 = diregie），而客户端在协议 17 的请求里**点名要
     * "diregie"**。所以我们发 0 很可能对不上，客户端读不到频道。
     *
     * <p>默认取 {@code 组号 - 1}（与 A12 的"组 1 → 索引 0"一致）；
     * 可用 {@code -Ds1a5.entrance.groupIndex=N} 覆盖（{@code -1} = 用默认算法）。
     */
    private final int groupIndexOverride =
            Integer.getInteger("s1a5.entrance.groupIndex", -1);

    private int groupIndex() {
        if (groupIndexOverride >= 0) {
            return groupIndexOverride;
        }
        return Math.max(0, channels.serverGroup() - 1);
    }

    /**
     * 协议 17 是否发**空表**以精确复现 A12（见 {@code case 17} 里的注释）。
     * {@code -Ds1a5.entrance.emptyGroupTable=1} 打开。
     */
    private final boolean emptyGroupTable =
            "1".equals(System.getProperty("s1a5.entrance.emptyGroupTable", "0"));
    private final Object logLock = new Object();

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptorThread;

    /**
     * Daemon by default: when embedded in {@code GameServer} the gateway must never
     * keep the JVM alive on its own. The standalone entry point flips this to false
     * so the process stays up after {@code main} returns.
     */
    private boolean acceptorDaemon = true;

    /** 0 = unlimited. See {@code s1a5.entrance.maxConnections}. */
    private int maxConnections;

    public EntranceServer(ChannelList channels, int port, boolean plainMode, Path logFile) {
        this.channels = channels;
        this.port = port;
        this.plainMode = plainMode;
        this.logFile = logFile;
    }

    /** Keep the JVM alive on the accept thread (standalone mode). */
    public EntranceServer asStandalone() {
        this.acceptorDaemon = false;
        return this;
    }

    public EntranceServer withMaxConnections(int limit) {
        this.maxConnections = Math.max(0, limit);
        return this;
    }

    public int port() {
        return port;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));
        running = true;
        log("已监听 0.0.0.0:" + port + "（" + (plainMode ? "明文模式" : "AES+deflate 加密模式") + "）");
        acceptorThread = new Thread(this::acceptLoop, "entrance-accept");
        acceptorThread.setDaemon(acceptorDaemon);
        acceptorThread.start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        Thread t = acceptorThread;
        if (t != null && t != Thread.currentThread()) {
            try {
                // Closing the socket makes accept() throw, so this returns promptly.
                t.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                // 按「当前还连着的」数，不是 connections（那是累计编号，只增不减：
                // 开机累计 64 个连接 —— 外网扫描器也算 —— 之后就全拒，11-02 测试服就是这样「服务没了」）
                if (maxConnections > 0 && open.get() >= maxConnections) {
                    log("连接数已达上限 " + maxConnections + "，拒绝 " + socket.getRemoteSocketAddress());
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                    continue;
                }
                open.incrementAndGet();
                Thread worker = new Thread(() -> {
                    try {
                        serve(socket);
                    } finally {
                        open.decrementAndGet();
                    }
                }, "entrance-conn");
                worker.setDaemon(true);
                worker.start();
            } catch (IOException e) {
                if (running) {
                    log("accept 失败：" + e);
                }
                return;
            }
        }
    }

    /** 协议 0 = P2P 中转连接（见 {@link com.dylan.game.P2PRelay}），按连接号记。 */
    private final java.util.Map<Integer, com.dylan.game.P2PRelay.Conn> relays =
            new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.Map<Integer, String> ips = new java.util.concurrent.ConcurrentHashMap<>();

    private void serve(Socket socket) {
        int id = connections.incrementAndGet();
        String who = String.valueOf(socket.getRemoteSocketAddress());
        log("--- #" + id + " 连接来自 " + who);
        ips.put(id, socket.getInetAddress() == null ? "" : socket.getInetAddress().getHostAddress());
        try (Socket s = socket) {
            s.setTcpNoDelay(true);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(4096);
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, n);
                if (!drain(buffer, out, id)) {
                    return;
                }
            }
            log("--- #" + id + " 客户端断开（残留 " + buffer.size() + " 字节）");
        } catch (IOException e) {
            log("--- #" + id + " 连接异常：" + e);
        } finally {
            com.dylan.game.P2PRelay.close(relays.remove(id));
            ips.remove(id);
        }
    }

    /**
     * 把缓冲区里能凑齐的帧全部处理掉。返回 false 表示连接该断开。
     *
     * <p>同一个 7001 端口上有两种帧，靠 <b>byte[1]（协议号）</b> 分：
     * <pre>
     *   中转帧（协议 0）  u16 type | u16 整长 | u32 身份 | u32 对方身份 | 包体      ← 长度在 +2 是 u16
     *   网关帧（协议 5/9/11/17/1…）  u8 type(0) | u8 协议 | u32 整长 | …          ← 长度在 +2 是 u32
     * </pre>
     * 客户端的中转帧 +4 起是身份（MTUPD 头），所以 <b>不能</b> 拿网关那套「u32 长度」去读：
     * 2026-10-28 01:36 外网实抓里客户端身份是 0，两种读法碰巧都是同一个数；登录回包开始下发账号 id 后，
     * u32 里高 16 位变成身份，会把长度读成几十万，整条中转连接就卡住了（probe_relay.py 能复现）。
     */
    private boolean drain(ByteArrayOutputStream buffer, OutputStream out, int id) throws IOException {
        byte[] data = buffer.toByteArray();
        int offset = 0;
        while (data.length - offset >= EntranceFrame.MIN_HEADER) {
            boolean relay = (data[offset + 1] & 0xFF) == 0;
            int total;
            int min;
            if (relay) {
                total = (data[offset + 2] & 0xFF) | (data[offset + 3] & 0xFF) << 8;
                min = com.dylan.game.P2PRelay.HEADER;
            } else {
                total = (int) EntranceFrame.readU32(data, offset + 2);
                min = EntranceFrame.MIN_HEADER;
            }
            if (total < min || total > EntranceFrame.MAX_FRAME) {
                log("--- #" + id + " 帧长度非法（" + total + (relay ? "，中转帧" : "") + "），丢弃缓冲");
                buffer.reset();
                return true;
            }
            if (data.length - offset < total) {
                break;
            }
            byte[] frame = new byte[total];
            System.arraycopy(data, offset, frame, 0, total);
            offset += total;
            if (relay) {
                relay(frame, out, id);
            } else {
                handle(frame, out, id);
            }
        }
        byte[] rest = new byte[data.length - offset];
        System.arraycopy(data, offset, rest, 0, rest.length);
        buffer.reset();
        buffer.write(rest, 0, rest.length);
        return true;
    }

    /** 中转帧（协议 0）：见 {@link com.dylan.game.P2PRelay}。 */
    private void relay(byte[] frame, OutputStream out, int id) {
        com.dylan.game.P2PRelay.Conn c = relays.computeIfAbsent(id,
                k -> com.dylan.game.P2PRelay.open(ips.getOrDefault(k, ""), out));
        String why = com.dylan.game.P2PRelay.forward(c, frame);
        String said = why == null ? "转发中 acc " + c.acc : why;
        if (!said.equals(c.lastLog)) {
            c.lastLog = said;
            log("中转 #" + id + " " + said + " 整帧=" + EntranceFrame.hexPrefix(frame, 32));
        }
    }

    private void handle(byte[] frame, OutputStream out, int id) throws IOException {
        int type = EntranceFrame.typeOf(frame);
        int protocolId = EntranceFrame.protocolIdOf(frame);
        // 协议 0 = P2P 中转，drain() 里已经按中转帧格式分走了，走不到这里。
        byte[] body = new byte[Math.max(0, frame.length - EntranceFrame.HEADER)];
        if (body.length > 0) {
            System.arraycopy(frame, EntranceFrame.HEADER, body, 0, body.length);
        }
        log("请求 #" + id + " type=" + type + " 协议=" + protocolId + " len=" + frame.length
                + " payload=" + EntranceFrame.hexPrefix(body, 64));

        // 显式初始化：switch 语句各分支的"确定赋值"分析没有编译器可验证，
        // 先赋初值就不会有"可能未初始化"的编译错误。
        byte[] payload = new byte[0];
        int replyId = 0;
        switch (protocolId) {
            case 11 -> {
                // 请求体完全不解析（A12 也是如此）；应答就是密钥本身，明文发出。
                replyId = 12;
                payload = keyExchangePayload();
                log("  → 密钥交换：回 32 字节密钥 " + EntranceFrame.hex(EntranceCodec.KEY32));
            }
            case 5 -> {
                /*
                 * ★ 应答协议号是 6，不是 3！
                 *
                 * A12 的三元表达式里第二项是 `ProtocolId == PacketId(ClientCommand::
                 * CREATE_CHARACTER) ? 6 : …`，而 packet_ids.h 里 **CREATE_CHARACTER = 5**
                 * （LOGIN = 1、DELETE_CHARACTER = 6）。所以"协议 5 的应答号"是 6。
                 * 只看 entrance.cpp 的表格会以为是 3 —— 这个坑踩了握手就废。
                 */
                replyId = 6;
                payload = handshakePayload();
                log("  → 握手：AES（只加密不 deflate）" + payload.length + " 字节");
            }
            case 9 -> {
                replyId = 10;
                payload = wrap(channels.channelScript());
                log("  → 频道脚本：" + channels.channelScript().length + " 字节原文 → 发出 "
                        + payload.length + " 字节");
            }
            case 17 -> {
                replyId = 18;
                String layout = System.getProperty("s1a5.entrance.tableLayout", "act5");
                int rows = emptyGroupTable ? 0 : channels.channelIds().size();
                byte[] table;
                if ("act5".equals(layout)) {
                    table = channels.buildAct5Table(rows);
                } else if ("a12".equals(layout)) {
                    // Retain the A12 layout and its existing diagnostic switches.
                    boolean oldStyle = "1".equals(System.getProperty("s1a5.entrance.oldGroupTable", "0"));
                    table = oldStyle
                            ? (emptyGroupTable ? new byte[0] : channels.buildGroupTable(groupIndex(), rows,
                                Integer.getInteger("s1a5.entrance.groupTableRow", 48),
                                Integer.getInteger("s1a5.entrance.groupTablePad", 0)))
                            : channels.buildChannelInfoReply();
                } else {
                    throw new IllegalArgumentException("Unknown channel table layout: " + layout);
                }
                payload = wrap(table);
                log("  channel table layout=" + layout + " group=" + channels.groupName()
                        + " rows=" + rows + " plain=" + table.length + " wire=" + payload.length);
            }
            case 1 -> {
                replyId = 3;
                byte[] table = channels.encode();
                payload = wrap(table);
                log("  → 服务器表：" + table.length + " 字节 → 发出 " + payload.length + " 字节"
                        + ChannelList.describeServerTable(table));
            }
            default -> {
                log("  → 不认识的网关协议 " + protocolId + "，不回（A12 也是直接跳过）整帧=" + EntranceFrame.hex(frame));
                return;
            }
        }

        byte[] reply = EntranceFrame.encode(replyId, payload);
        out.write(reply);
        out.flush();
        log("应答 #" + id + " 协议=" + replyId + " len=" + reply.length
                + " 头=" + EntranceFrame.hexPrefix(reply, 11));
    }

    private byte[] keyExchangePayload() {
        byte[] payload = new byte[4 + EntranceCodec.KEY32.length];
        EntranceFrame.writeU32(payload, 0, 0);
        System.arraycopy(EntranceCodec.KEY32, 0, payload, 4, EntranceCodec.KEY32.length);
        return payload;
    }

    /**
     * 协议 5 的应答：{@code u32(脚本为空?1:0)} + 16 字节令牌 + 12 个 0，然后 AES。
     * 注意 A12 这里**只做 AES，不做 deflate**（与 {@link #wrap} 不同）。
     */
    private byte[] handshakePayload() {
        ByteArrayOutputStream payload = new ByteArrayOutputStream(32);
        long flag = channels.channelScript().length == 0 ? 1 : 0;
        payload.write((int) (flag & 0xFF));
        payload.write((int) ((flag >>> 8) & 0xFF));
        payload.write((int) ((flag >>> 16) & 0xFF));
        payload.write((int) ((flag >>> 24) & 0xFF));
        payload.write(TOKEN_PREFIX, 0, TOKEN_PREFIX.length);
        for (int i = TOKEN_PREFIX.length; i < TOKEN_LENGTH; i++) {
            payload.write(0);
        }
        for (int i = 0; i < 12; i++) {
            payload.write(0);
        }
        byte[] plain = payload.toByteArray();
        if (plainMode) {
            return plain;
        }
        return EntranceCodec.aesEncryptZeroPadded(plain);
    }

    private byte[] wrap(byte[] plain) {
        if (plainMode) {
            return plain;
        }
        return EntranceCodec.wrap(plain);
    }

    void log(String message) {
        String line = "[" + LocalTime.now().format(TIME) + "] " + message;
        System.out.println(line);
        if (logFile == null) {
            return;
        }
        synchronized (logLock) {
            try {
                Path parent = logFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(logFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.out.println("（写网关日志失败：" + e + "）");
            }
        }
    }
}
