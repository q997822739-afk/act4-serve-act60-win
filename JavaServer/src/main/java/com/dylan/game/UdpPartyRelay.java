package com.dylan.game;

import com.dylan.net.ServerConfig;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 组队 P2P 的服务器 UDP 中转 —— <b>一个端口</b>，按 MTUPD 头里的身份转发。
 *
 * <h2>为什么是「一个端口」而不是「每对一条腿」</h2>
 * 客户端 decide 走中转时不看 NOTI 11 的队友端口，而是看 <b>登录回包</b> 里的中转地址：
 * {@code Cmd1ReplyBody} 的 {@code u32 udpIdentity / string relayHost / u32 relayUdpPort / u32 relayTcpPort}
 * （2006 JP 同一段布局，见 3_服务端尝试/45JP_.../handlers/character_manage.py 的 LOGIN 成功体）。
 * 客户端 {@code sub_A66BC0} 把 (host, +2484=tcp, +2488=udp) 存进 NUDP 模块，
 * {@code CNRDUdpModule::addPeer}（A4 00861E90）把它当第 3 个候选端点：
 * <pre>
 *   候选 1 = 对方 +0x14C 内网 ip + 固定 5063（00861F73 处 mov word [ebp-0x10], 5063）
 *   候选 2 = 对方 +0x150 外网 ip + 对方 +0x154 端口（NOTI 11 那一行）
 *   候选 3 = 中转 ip + 中转 udp 端口（登录回包 / 客户端目录 _RELAY 文件覆盖）
 * </pre>
 * 一个中转端口就够：每次发的 MTUPD 头里带着「发给谁」，见下。
 *
 * <h2>MTUPD 数据报（45JP mtupd.py，字段与 A4 实抓一致）</h2>
 * <pre>
 *   u16 type         0 = 不可靠数据（UDP）；TCP 中转信封里是 1
 *   u16 size         整包长度（= 数据报长度）
 *   u32 local  身份  发送者身份（登录回包的 udpIdentity；A4 实测 0 —— 我们转发时补成账号 id）
 *   u32 peer   身份  收件人身份（= NOTI 11 那一行的 acc）
 *   [块]  u8 tag（1 可靠 / 2 不可靠）+ 块内容；2 的 10 字节负载就是组队 CHECK 包
 * </pre>
 * 实抓（2026-10-28 01:36 外网，两家宽带）：TCP 中转上双方各只发 1 帧 31 字节 → 各自 6 秒后
 * CMD 13 退队。UDP 一个包都没到，因为登录回包里中转 udp 端口填的是网关端口 7001 上不存在的 UDP 监听。
 *
 * <h2>转发规则</h2>
 * 收包 → 用 local（或来源地址反查）认出是谁 → 记下它这次的来源端点 → 按 peer 查到对方端点 →
 * 把 local 改成发送者账号 id（收件人靠这个把自己队伍名单里的那一位对上）→ 原样从 <b>同一个 socket</b> 发出去。
 * 同一个 socket 很关键：两家都是「端口受限圆锥」（客户端 NAT 类型 4），对方只收自己发过的那个 ip:port。
 *
 * <p>只在 {@code s1a5.peer.host} 不是回环时开（本机双开仍走局域网直连）。
 * 端口 {@code s1a5.relay.udpPort}，默认跟网关同一个号（7001）—— 登录回包两个端口目前都是它。
 */
public final class UdpPartyRelay {

    /** 中转端口；0 = 没起来。 */
    public static final int PORT = Integer.getInteger("s1a5.relay.udpPort", ServerConfig.ENTRANCE_PORT);

    public static final boolean ENABLED = !isLoopback(ServerConfig.PEER_UDP_HOST)
            && Boolean.parseBoolean(System.getProperty("s1a5.relay.udp", "true"));

    private static final Map<Integer, InetSocketAddress> ENDPOINTS = new ConcurrentHashMap<>();
    private static final Map<String, Integer> BY_ADDR = new ConcurrentHashMap<>();
    /** 已经打过「首包」日志的来源，避免每包刷屏。 */
    private static final java.util.Set<String> SEEN = ConcurrentHashMap.newKeySet();

    private static volatile DatagramSocket socket;
    private static volatile Thread thread;

    private UdpPartyRelay() {
    }

    public static int port() {
        DatagramSocket s = socket;
        return s == null ? 0 : s.getLocalPort();
    }

    /** 起中转。已经起来或没开就什么都不做。 */
    public static synchronized void start() {
        if (!ENABLED || socket != null) {
            return;
        }
        try {
            DatagramSocket s = new DatagramSocket(PORT);
            socket = s;
            Thread t = new Thread(() -> pump(s), "udp-party-relay");
            t.setDaemon(true);
            t.start();
            thread = t;
            System.out.println("[UDP 中转] 监听 0.0.0.0:" + s.getLocalPort()
                    + "（MTUPD 单端口，按身份转发；登录回包 / NOTI 11 都指向这里）");
        } catch (IOException e) {
            System.out.println("[UDP 中转] 端口 " + PORT + " 起不来：" + e);
        }
    }

    public static synchronized void stop() {
        DatagramSocket s = socket;
        socket = null;
        if (s != null) {
            s.close();
        }
        Thread t = thread;
        thread = null;
        if (t != null) {
            t.interrupt();
        }
    }

    /**
     * CMD 2 报上来的外网端点：预先登记，这样对方先发第一包时也能立刻转过去（不用等对方也发过）。
     * 之后收到实际数据报会用它真正的来源端点覆盖。
     */
    public static void note(int acc, InetSocketAddress endpoint) {
        if (!ENABLED || acc == 0 || endpoint == null || endpoint.getPort() <= 0) {
            return;
        }
        InetAddress a = endpoint.getAddress();
        if (a == null || a.isAnyLocalAddress()) {
            return;
        }
        ENDPOINTS.put(acc, endpoint);
        BY_ADDR.put(key(endpoint), acc);
    }

    /** 离线 / 退队：清掉这个账号的端点，别再往旧地址灌包。 */
    public static void forget(int acc) {
        if (acc == 0) {
            return;
        }
        InetSocketAddress old = ENDPOINTS.remove(acc);
        if (old != null) {
            BY_ADDR.remove(key(old));
        }
    }

    /** 中转对外的公网 ip（NOTI 11 队友行 + 登录回包的 relay_host）。 */
    public static byte[] relayIp() {
        try {
            return InetAddress.getByName(ServerConfig.PEER_UDP_HOST).getAddress();
        } catch (IOException e) {
            return new byte[] {127, 0, 0, 1};
        }
    }

    private static void pump(DatagramSocket s) {
        byte[] buf = new byte[4096];
        while (!s.isClosed()) {
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                s.receive(p);
            } catch (IOException e) {
                return;
            }
            try {
                handle(s, p);
            } catch (IOException e) {
                // 对方 NAT 还没开 / 临时不可达：丢这一个包，别停线程
            } catch (RuntimeException e) {
                System.out.println("[UDP 中转] 处理异常：" + e);
            }
        }
    }

    private static void handle(DatagramSocket s, DatagramPacket p) throws IOException {
        int len = p.getLength();
        byte[] b = p.getData();
        InetSocketAddress src = new InetSocketAddress(p.getAddress(), p.getPort());
        boolean first = SEEN.add(key(src));
        if (len < 12) {
            if (first) {
                System.out.println("[UDP 中转] " + len + " 字节短包（不是 MTUPD）来自 " + src
                        + " 头=" + hex(b, len));
            }
            return;
        }
        int type = u16(b, 0);
        int size = u16(b, 2);
        int local = u32(b, 4);
        int peer = u32(b, 8);
        if (first) {
            System.out.println("[UDP 中转] 首包 来自 " + src + " type=" + type + " size=" + size
                    + " 实长=" + len + " local=" + local + " peer=" + peer);
        }
        if (type != 0 && type != 1 && type != 3) {
            return;
        }
        if (size != 0 && size != len) {
            System.out.println("[UDP 中转] 长度不符（头 " + size + " / 实 " + len + "）来自 " + src);
            return;
        }
        int sender = local;
        if (sender == 0) {
            Integer known = BY_ADDR.get(key(src));
            sender = known == null ? accByIp(p.getAddress().getHostAddress()) : known;
        }
        if (sender == 0) {
            // 认不出是谁：还能按 peer 转，但收件人看不到发送者身份，记一行日志
            System.out.println("[UDP 中转] 认不出来源 " + src + "（local=" + local
                    + "，同 IP 没有唯一游戏会话），只按 peer=" + peer + " 转");
        }
        if (sender != 0) {
            ENDPOINTS.put(sender, src);
            BY_ADDR.put(key(src), sender);
        }
        if (peer == 0) {
            return;
        }
        InetSocketAddress dst = ENDPOINTS.get(peer);
        if (dst == null) {
            System.out.println("[UDP 中转] 不知道 acc " + peer + " 的端点，丢弃来自 " + src
                    + "（local=" + local + "）");
            return;
        }
        if (sender != 0 && local != sender) {
            // 收件人靠这个字段认出「是哪个队员发来的」（A4 客户端登录回包身份是 0，这里补上）
            putU32(b, 4, sender);
        }
        s.send(new DatagramPacket(b, len, dst));
        if (TRACE) {
            System.out.println("[UDP 中转] " + sender + " → " + peer + " " + len + " 字节 "
                    + src + " → " + dst + " 头=" + hex(b, Math.min(len, 16)));
        }
    }

    /** 每个包都打一行太吵，默认关；-Ds1a5.relay.trace=1 打开。 */
    private static final boolean TRACE = Boolean.parseBoolean(System.getProperty("s1a5.relay.trace", "false"));

    private static String key(InetSocketAddress a) {
        return a.getAddress().getHostAddress() + ":" + a.getPort();
    }

    /** 只按来源 IP 兜底认人；同一 IP 有多个会话（同机多开 / 同一 NAT）时返回 0，别猜。 */
    private static int accByIp(String ip) {
        int found = 0;
        for (GameSession s : SessionRegistry.all()) {
            if (ip.equals(s.clientIp()) && s.account() != null) {
                int acc = (int) s.account().mId();
                if (found != 0 && found != acc) {
                    return 0;
                }
                found = acc;
            }
        }
        return found;
    }

    private static int u16(byte[] b, int o) {
        return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8;
    }

    private static int u32(byte[] b, int o) {
        return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8 | (b[o + 2] & 0xFF) << 16 | (b[o + 3] & 0xFF) << 24;
    }

    private static void putU32(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >>> 8);
        b[o + 2] = (byte) (v >>> 16);
        b[o + 3] = (byte) (v >>> 24);
    }

    private static String hex(byte[] b, int n) {
        StringBuilder sb = new StringBuilder(n * 3);
        for (int i = 0; i < n; i++) {
            sb.append(String.format("%02X ", b[i]));
        }
        return sb.toString().trim();
    }

    private static boolean isLoopback(String host) {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (IOException e) {
            return true;
        }
    }
}
