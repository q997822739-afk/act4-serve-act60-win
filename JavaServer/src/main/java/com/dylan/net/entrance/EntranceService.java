package com.dylan.net.entrance;

import com.dylan.net.ServerConfig;
import com.dylan.pvf.PvfArchive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 频道网关的装配与生命周期 —— **不阻塞**，供两种用法共用：
 *
 * <ul>
 *   <li>{@link com.dylan.net.GameServer} 在游戏服进程里内嵌启动（单进程双端口：10011 + 7001）；</li>
 *   <li>{@link EntranceMain} 作为独立进程启动（保留原有用法）。</li>
 * </ul>
 *
 * <h2>为什么要内嵌</h2>
 * 客户端在**频道选择界面**会无视命令行里的游戏服地址，去连一个硬编码的
 * {@code 121.14.107.176:7001}。没有东西应答那个端口，界面就永远停在
 * 「正在接收频道信息」。让游戏服自己顺带监听 7001，用户直接
 * {@code java -jar} 就能用，不必再起第二个进程。
 *
 * <h2>配置</h2>
 * 与独立入口**完全同一套**系统属性（{@code s1a5.entrance.*}）：
 *
 * <table>
 *   <tr><th>属性</th><th>缺省</th><th>说明</th></tr>
 *   <tr><td>{@code entrance.enabled}</td><td>true</td><td>内嵌启动时是否开网关（独立入口忽略此项）</td></tr>
 *   <tr><td>{@code entrance.port}</td><td>7001</td><td>监听端口</td></tr>
 *   <tr><td>{@code entrance.maxConnections}</td><td>64</td><td>连接上限，0 = 不限</td></tr>
 *   <tr><td>{@code entrance.host}</td><td>{@code s1a5.peer.host} → 127.0.0.1</td><td>写进频道表的 host</td></tr>
 *   <tr><td>{@code entrance.gamePort}</td><td>{@code s1a5.port} = 10011</td><td>写进频道表的端口</td></tr>
 *   <tr><td>{@code entrance.group}</td><td>{@code MY_GROUP} = 2</td><td>用哪个 {@code [server]} 块</td></tr>
 *   <tr><td>{@code entrance.pvf}</td><td>{@code s1a5.pvf} → 猜几个常见位置</td><td>客户端 Script.pvf</td></tr>
 *   <tr><td>{@code entrance.plain}</td><td>0</td><td>1 = 不加密不压缩（纯调试）</td></tr>
 *   <tr><td>{@code entrance.logFile}</td><td>logs/entrance.log</td><td>网关日志</td></tr>
 * </table>
 *
 * <h2>失败处理</h2>
 * {@link #start()} 在端口被占或脚本读不到时**只告警、不抛异常**，因为它是内嵌在
 * 游戏服里的：频道界面用不了，不该连带把能正常玩的游戏服也拖死。
 */
public final class EntranceService {

    /** 与独立入口保持一致的兜底搜索路径。 */
    private static final String[] PVF_FALLBACKS = {
            "../ACT5/Script.pvf",
            "../../ACT5/ACT5/Script.pvf",
            "client/Script.pvf",
            "../../../S1A5-Client/ACT5/ACT5/Script.pvf",
            "D:/deepseek harness files/S1A5-Client/ACT5/ACT5/Script.pvf",
    };

    private final ChannelList channels = new ChannelList();
    private final int port;
    private final boolean plain;
    private final Path logFile;
    private final int maxConnections;

    /**
     * 对外只开哪一个频道。默认 10。
     *
     * <p>显式写成常量而**不是**读 {@code ServerConfig.ENTRANCE_CHANNEL}：
     * 本包要能对着"还没合入本次改动的旧 jar"编译（部署时就是这种情况），
     * 那时 {@code ServerConfig} 里还没有那几个网关常量。
     * 内嵌用法由 {@code GameServer} 把值显式传进来，两条路最终都落在
     * {@code -Ds1a5.entrance.channel} 上，行为一致。
     */
    private static final int DEFAULT_CHANNEL = 10;

    private final int channel;

    private Path pvf;
    private EntranceServer server;

    public EntranceService(int port, int maxConnections) {
        this(port, maxConnections, intProperty("s1a5.entrance.channel", DEFAULT_CHANNEL));
    }

    public EntranceService(int port, int maxConnections, int channel) {
        this.port = port;
        this.maxConnections = maxConnections;
        this.channel = channel;
        this.plain = flag("entrance.plain", false);
        this.logFile = Path.of(str("entrance.logFile", "logs/entrance.log"));
        if (channel > 0) {
            System.setProperty("s1a5.entrance.channel", String.valueOf(channel));
        }
    }

    /** 只开放哪一个频道。 */
    public int channel() {
        return channel;
    }

    private static int intProperty(String key, int fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 源目录解析出来的 Script.pvf，没找到时为 null。 */
    public Path pvf() {
        return pvf;
    }

    public ChannelList channels() {
        return channels;
    }

    public int port() {
        return port;
    }

    public boolean isRunning() {
        return server != null;
    }

    private Consumer<String> logger = message -> System.out.println("[entrance] " + message);

    /**
     * 读脚本、建频道表、起监听。**不阻塞**。
     *
     * @return 是否成功启动；失败时已经打过告警，调用方无需再处理
     */
    public boolean start() {
        load();

        try {
            server = new EntranceServer(channels, port, plain, logFile);
            server.withMaxConnections(maxConnections);
            logger = server::log;

            server.log("");
            server.log("================= S1A5 频道网关（entrance，内嵌）=================");
            server.log("  监听            0.0.0.0:" + port);
            server.log("  频道脚本        " + channels.channelScript().length + " 字节（归一化）");
            if (channels.channelIds().size() == 1) {
                server.log("  开放频道        只有 ch" + channels.channelIds().get(0)
                        + "（单频道模式；多频道做法见 ChannelList.fillChannelPorts 注释）");
            } else {
                server.log("  开放频道        " + channels.channelIds().size() + " 个："
                        + channels.channelIds());
            }
            server.log("  服务器组号      " + channels.serverGroup() + "（来自 "
                    + (pvf == null ? "内置脚本" : "channel_info.etc") + "）");
            server.log("  频道指向        " + channels.host() + ":" + channels.port());
            server.log("  日志            " + logFile.toAbsolutePath());
            server.log("===============================================================");
            server.log("");
            server.start();
            return true;
        } catch (IOException e) {
            // Port busy or socket refused: the game server itself is still fine.
            logger.accept("网关启动失败（游戏服不受影响）：" + e);
            server = null;
            return false;
        }
    }

    /** 幂等；未启动时是空操作。 */
    public void stop() {
        EntranceServer s = server;
        server = null;
        if (s != null) {
            s.stop();
        }
    }

    /**
     * 读 pvf、按 pvp 规则改写、解析出频道表并填好端口。**不监听任何端口。**
     *
     * <p>自检模式用它来拿到装配结果；{@link #start()} 内部也先调它。
     * 可重复调用（每次都会重新读一遍脚本）。
     */
    public void load() {
        String host = str("entrance.host", null);
        if (host == null) {
            String peer = ServerConfig.PEER_UDP_HOST;
            host = (peer == null || peer.isBlank() || peer.equals("127.0.0.1")) ? null : peer;
        }
        if (host == null) {
            host = "127.0.0.1";
        }
        int gamePort = num("entrance.gamePort", ServerConfig.PORT);
        int group = num("entrance.group", ServerConfig.A4_GROUP);

        channels.configure(host, gamePort, str("entrance.channelName", "Channel 1"));

        pvf = resolvePvf();
        boolean loaded = false;
        byte[] rawScript = null;
        if (pvf != null) {
            logger.accept("读频道表：" + pvf);
            try (PvfArchive archive = PvfArchive.open(pvf)) {
                /*
                 * 取**原始字节**再按 ISO-8859-1 解码（一字符对一字节），而不是 readText()：
                 * pvf 的脚本按 EUC-KR 解码，而客户端是拿原始字节喂解析器的。
                 * 用原始字节可保证我们发出去的频道脚本与文件**逐字节一致**。
                 */
                byte[] raw = archive.read("etc/channel_info.etc");
                rawScript = raw;
                String text = new String(raw, StandardCharsets.ISO_8859_1);
                /*
                 * ★ 套用 A12 的 pvp_channels.etc 规则改写决斗频道行。
                 * 不改写的话归一化文本会少 10 字节，客户端建的表和后面 38 行对不上。
                 */
                String rewritten = ChannelList.applyPvpRules(text);
                if (!rewritten.equals(text)) {
                    logger.accept("套用 pvp_channels.etc 规则：" + text.length() + " → "
                            + rewritten.length() + " 字节（A12 生产日志是 3173）");
                    text = rewritten;
                }
                logger.accept("channel_info.etc 解出 " + raw.length + " 字节");
                loaded = channels.loadScript(text, group, logger);
                if (!loaded) {
                    logger.accept("频道脚本解析失败，改用内置脚本");
                }
            } catch (Exception e) {
                logger.accept("读 pvf 失败：" + e);
            }
        } else {
            logger.accept("没找到 Script.pvf，改用内置脚本（频道列表会少很多）");
        }
        if (!loaded) {
            byte[] builtin = ChannelList.builtinChannelScript();
            channels.loadScript(new String(builtin, StandardCharsets.US_ASCII), 0, logger);
        }
        channels.fillChannelPorts();

        // A4 官方成品服协议 10 发的是 channel_info.etc 原文（11 个 [server] 块全在），
        // 不是 ACT5 那份归一化单组脚本。rawScript=0 可退回 ACT5 行为。
        if (!"0".equals(System.getProperty("s1a5.entrance.rawScript", "1"))
                && rawScript != null && rawScript.length > 0) {
            channels.useRawScript(rawScript);
            logger.accept("协议 9 改发原文 " + rawScript.length + " 字节（含全部服务器组）");
        }
    }

    private static Path resolvePvf() {
        List<Path> candidates = new ArrayList<>();
        String explicit = System.getProperty("s1a5.entrance.pvf");
        if (explicit != null && !explicit.isBlank()) {
            candidates.add(Path.of(explicit.trim()));
        }
        try {
            candidates.add(ServerConfig.PVF_PATH);
        } catch (RuntimeException ignored) {
        }
        for (String fallback : PVF_FALLBACKS) {
            candidates.add(Path.of(fallback));
        }
        for (Path candidate : candidates) {
            try {
                if (candidate != null && Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().normalize();
                }
            } catch (RuntimeException ignored) {
            }
        }
        return null;
    }

    static String str(String key, String fallback) {
        String value = System.getProperty("s1a5." + key);
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    static int num(String key, int fallback) {
        String value = str(key, null);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.out.println("[entrance] 属性 s1a5." + key + " 不是数字：" + value);
            return fallback;
        }
    }

    static boolean flag(String key, boolean fallback) {
        String value = str(key, null);
        if (value == null) {
            return fallback;
        }
        return !value.equals("0") && !value.equalsIgnoreCase("false");
    }
}
