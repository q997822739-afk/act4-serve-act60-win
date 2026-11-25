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
 * <b>决斗场 PvP 专用 UDP 中转</b>：每个有序对（收件人 → 对端）一个端口，原样转发 UDP 数据报。
 *
 * <p>为什么不跟组队共用 {@link UdpPartyRelay} 的单端口：
 * A4 组队客户端走中转时看的是<b>登录回包</b>里的 relay 端口（{@code CNRDUdpModule::addPeer} 第 3 个候选），
 * 根本不读 NOTI 11 队友行里的端口；但决斗场不是这样。A21 的 PvP 实现
 * （{@code Network/Builders/Pvp/PvpPeerInfoBuilder.BuildForRelay} + {@code PvpRelayLifecycleCoordinator}）
 * 给每对座位下发<b>独立端口</b>，客户端把 P2P UDP 直接发到「服务器公网 IP : 自己→对端」这一个端口，
 * 服务器再按反向腿转给对端。PvP 包体是原始 UDP，没有 MTUPD 的收件人字段，所以必须靠端口区分收件人。
 *
 * <p>端口从 {@code s1a5.pvp.relayBase}（默认 30000）起分配，范围 {@code s1a5.pvp.relayCount}（默认 512）。
 * 2026-10-28 实测测试服公网 UDP 30000 可达（云安全组未限制该段）。
 *
 * <p>原始包转发规则：
 * <ul>
 *   <li>只接受来源 IP 等于该腿 owner 的 TCP 远端 IP 的包，防止别人拿端口乱灌。</li>
 *   <li>记下 owner 的真实来源端点（NAT 后的公网 ip:port）。</li>
 *   <li>目标优先用反向腿学到的端点；反向腿还没收到包时，退回对端 CMD 2 上报的公网端点。</li>
 *   <li>从反向腿的 socket 发出去，这样对端看到的源端口就是它发往的那个端口（端口受限 NAT 也收）。</li>
 * </ul>
 */
public final class UdpPairRelay {

    public static final boolean ENABLED = !isLoopback(ServerConfig.PEER_UDP_HOST)
            && Boolean.parseBoolean(System.getProperty("s1a5.pvp.relay", "true"));

    private static final int BASE = Integer.getInteger("s1a5.pvp.relayBase", 30000);
    private static final int COUNT = Integer.getInteger("s1a5.pvp.relayCount", 512);

    private static final Map<Key, Leg> LEGS = new ConcurrentHashMap<>();
    private static int cursor;

    private UdpPairRelay() {
    }

    private record Key(GameSession owner, GameSession peer) {
    }

    private static final class Leg {
        final GameSession owner;
        final GameSession peer;
        final DatagramSocket sock;
        volatile InetSocketAddress learned;

        Leg(GameSession owner, GameSession peer, DatagramSocket sock) {
            this.owner = owner;
            this.peer = peer;
            this.sock = sock;
        }
    }

    /** owner 往这个端口发 = 发给 peer。没开 / 端口用完返回 0。 */
    public static synchronized int portFor(GameSession owner, GameSession peer) {
        if (!ENABLED || owner == null || peer == null || owner == peer) {
            return 0;
        }
        Key k = new Key(owner, peer);
        Leg leg = LEGS.get(k);
        if (leg != null && !leg.sock.isClosed()) {
            return leg.sock.getLocalPort();
        }
        for (int i = 0; i < COUNT; i++) {
            int port = BASE + (cursor++ % COUNT);
            try {
                DatagramSocket sock = new DatagramSocket(port);
                Leg l = new Leg(owner, peer, sock);
                LEGS.put(k, l);
                Thread t = new Thread(() -> pump(l), "pvp-udp-relay-" + port);
                t.setDaemon(true);
                t.start();
                System.out.println("[PvP 中转] " + owner.characName() + " → " + peer.characName()
                        + " 端口 " + port);
                return port;
            } catch (IOException busy) {
                // 端口被别的进程/别的腿占了，试下一个
            }
        }
        System.out.println("[PvP 中转] 端口 " + BASE + "–" + (BASE + COUNT - 1) + " 用完");
        return 0;
    }

    /** s 离开房间 / 下线：关掉所有和 s 有关的腿。 */
    public static void closeAll(GameSession s) {
        if (s == null) {
            return;
        }
        LEGS.entrySet().removeIf(e -> {
            if (e.getKey().owner() == s || e.getKey().peer() == s) {
                e.getValue().sock.close();
                return true;
            }
            return false;
        });
    }

    /** 按角色号关腿：断线时不一定还拿得到 GameSession。 */
    public static void closeForCharac(int characNo) {
        if (characNo <= 0) {
            return;
        }
        LEGS.entrySet().removeIf(e -> {
            if (e.getKey().owner().characNo() == characNo || e.getKey().peer().characNo() == characNo) {
                e.getValue().sock.close();
                return true;
            }
            return false;
        });
    }

    private static void pump(Leg leg) {
        byte[] buf = new byte[4096];
        while (!leg.sock.isClosed()) {
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                leg.sock.receive(p);
            } catch (IOException e) {
                return;
            }
            try {
                forward(leg, p);
            } catch (IOException e) {
                // 对端还没开 NAT / 临时不可达：丢这一个包
            } catch (RuntimeException e) {
                System.out.println("[PvP 中转] 处理异常：" + e);
            }
        }
    }

    private static void forward(Leg leg, DatagramPacket p) throws IOException {
        String ownerIp = leg.owner.clientIp();
        String srcIp = p.getAddress().getHostAddress();
        if (ownerIp != null && !ownerIp.isEmpty() && !ownerIp.equals(srcIp)) {
            return;
        }
        InetSocketAddress src = new InetSocketAddress(p.getAddress(), p.getPort());
        boolean first = leg.learned == null;
        leg.learned = src;
        if (first) {
            System.out.println("[PvP 中转] " + leg.owner.characName() + " 首包 " + src
                    + " → " + leg.peer.characName() + " 端口 " + leg.sock.getLocalPort()
                    + " len=" + p.getLength());
        }
        Leg back = LEGS.get(new Key(leg.peer, leg.owner));
        InetSocketAddress dst = back == null ? null : back.learned;
        if (dst == null) {
            dst = PartyInviteService.udpEndpointOf(leg.peer);
        }
        if (dst == null) {
            if (first) {
                System.out.println("[PvP 中转] 还没有 " + leg.peer.characName()
                        + " 的端点，先丢弃（等它 CMD 2 或首包）");
            }
            return;
        }
        DatagramSocket out = back == null ? leg.sock : back.sock;
        out.send(new DatagramPacket(p.getData(), p.getOffset(), p.getLength(), dst));
    }

    /** 中转对外的公网 ip。 */
    public static byte[] relayIp() {
        try {
            return InetAddress.getByName(ServerConfig.PEER_UDP_HOST).getAddress();
        } catch (IOException e) {
            return new byte[] {127, 0, 0, 1};
        }
    }

    private static boolean isLoopback(String host) {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (IOException e) {
            return true;
        }
    }
}
