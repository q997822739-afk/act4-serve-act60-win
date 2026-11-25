package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public record Cmd2SetUdpIpPortRequest(int navType, InetAddress localIp,
                                      InetSocketAddress endpoint,
                                      int maxDatagram, String machineTag) {
    public static final int PACKET_ID = 2;

    public static final int NAV_UNKNOWN = 0;

    public static final int NAV_DIRECT = 1;

    public static final int NAV_CONE = 2;

    public static final int NAV_SYMMETRIC = 3;

    public static final int NAV_PORT_RESTRICT = 4;

    public static final int NAV_RELAY = 5;

    public static final int MACHINE_TAG_CAPACITY = 65;

    public static final int FIXED_LENGTH = 15;

    public static Cmd2SetUdpIpPortRequest parse(byte[] body) {
        return parse(new PacketReader(body));
    }

    public static Cmd2SetUdpIpPortRequest parse(PacketReader r) {
        int navType = r.u8();
        InetAddress localIp = readIpv4(r);
        InetAddress publicIp = readIpv4(r);
        // 小端主机序：探测回包 0085FE2E 先 htons 转成主机序存 +0x15C，CMD 2 原样写出。
        // 外网实测 4395 写成 2B 11；按大端读成 11025，NOTI 11 给对方的端口就错了，打洞失败转中转掉线。
        int publicPort = r.u8() | (r.u8() << 8);
        int maxDatagram = r.u32();
        String machineTag = r.remaining() > 0
                ? r.string(MACHINE_TAG_CAPACITY, "machineTag") : "";
        return new Cmd2SetUdpIpPortRequest(navType, localIp,
                new InetSocketAddress(publicIp, publicPort), maxDatagram, machineTag);
    }

    private static InetAddress readIpv4(PacketReader r) {
        byte[] octets = r.bytes(4);
        try {
            return Inet4Address.getByAddress(octets);
        } catch (UnknownHostException e) {
            throw new PacketFormatException("上报的地址不合法：" + e.getMessage());
        }
    }

    public boolean usable() {
        return endpoint.getPort() > 0 && endpoint.getAddress() != null
                && !endpoint.getAddress().isAnyLocalAddress() && maxDatagram > 0;
    }

    @Override
    public String toString() {
        return "CMD 2 SET_UDP_IP_PORT[navType=" + navType + "（" + navTypeName() + "）"
                + " 本机=" + localIp.getHostAddress()
                + " 探测端点=" + endpoint.getAddress().getHostAddress() + ":" + endpoint.getPort()
                + " maxDatagram=" + maxDatagram
                + " machineTag=\"" + machineTag + "\"]";
    }

    private String navTypeName() {
        return switch (navType) {
            case NAV_UNKNOWN -> "unknown 没探测过 ★ 官方拒绝组队的就是这一档";
            case NAV_DIRECT -> "real 不在 NAT 后";
            case NAV_CONE -> "restrict 受限锥形";
            case NAV_SYMMETRIC -> "symmetric 对称，最难打洞";
            case NAV_PORT_RESTRICT -> "port restrict 端口受限锥形";
            case NAV_RELAY -> "relay 走中转";
            default -> "未知";
        };
    }
}
