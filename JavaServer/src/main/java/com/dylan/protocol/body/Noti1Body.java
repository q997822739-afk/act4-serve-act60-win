package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti1Body(
        int cipherSeed,
        String account,
        int unknown3,
        int unknown4,
        int serverGroupNumber,
        int channelNumber,
        int unknown7,
        List<String> addresses,
        int udpProbePortA,
        int udpProbePortB) {
    public static final int ACCOUNT_MAX = 0x40;

    public static final int ADDRESS_MAX = 0x104;

    public Noti1Body {
        addresses = List.copyOf(addresses);
    }

    public static Noti1Body handshake(String account) {
        return handshake(account, 0);
    }

    public static Noti1Body handshake(String account, int cipherSeed) {
        return handshake(account, cipherSeed,
                com.dylan.net.ServerConfig.MY_GROUP,
                com.dylan.net.ServerConfig.MY_CHANNEL);
    }

    public static Noti1Body handshake(String account, int cipherSeed,
                                      int serverGroupNumber, int channelNumber) {
        return new Noti1Body(cipherSeed, account, 0, 0,
                serverGroupNumber, channelNumber, 0, List.of(), 0, 0);
    }

    public static Noti1Body handshake(String account, int cipherSeed,
                                      int serverGroupNumber, int channelNumber,
                                      String probeHost, int probePortA, int probePortB) {
        if (probeHost == null || probeHost.isBlank()) {
            return handshake(account, cipherSeed, serverGroupNumber, channelNumber);
        }
        return new Noti1Body(cipherSeed, account, 0, 0,
                serverGroupNumber, channelNumber, 0, List.of(probeHost.trim()),
                probePortA, probePortB);
    }

    /**
     * A4 官方频道 NOTI 1：账号串长度 0、组/频道各 1 字节、一个地址、两个 UDP 口相同。
     * 字段顺序与 {@link #toBytes()} 相同；不走它是因为 {@link #validate()} 拒绝空账号。
     */
    public static byte[] a4Channel(int group, int channel, String host, int udpPort) {
        if (group < 0 || group > 0xFF || channel < 0 || channel > 0xFF) {
            throw new PacketFormatException("组号/频道号是 u8：" + group + "/" + channel);
        }
        PacketWriter w = new PacketWriter(64);
        w.u32(0);
        w.u32(0);
        w.u32(0);
        w.u32(0);
        w.u8(group);
        w.u8(channel);
        w.u32(0);
        w.u32(1);
        w.string(host);
        w.u32(udpPort);
        w.u32(udpPort);
        return w.toArray();
    }

    public byte[] toBytes() {
        validate();
        PacketWriter w = new PacketWriter(64);
        w.u32(cipherSeed);
        w.string(account);
        w.u32(unknown3);
        w.u32(unknown4);
        w.u8(serverGroupNumber);
        w.u8(channelNumber);
        w.u32(unknown7);
        w.u32(addresses.size());
        for (String address : addresses) {
            w.string(address);
        }
        w.u32(udpProbePortA);
        w.u32(udpProbePortB);
        return w.toArray();
    }

    private void validate() {
        requireStringWidth(account, ACCOUNT_MAX, "字段 2（账号名）");
        for (String address : addresses) {
            requireStringWidth(address, ADDRESS_MAX, "字段 9（地址表）");
        }
        if (serverGroupNumber < 0 || serverGroupNumber > 0xFF) {
            throw new PacketFormatException(
                    "字段 5（服务器组号）是 u8，超出 0..255：" + serverGroupNumber);
        }
        if (channelNumber < 0 || channelNumber > 0xFF) {
            throw new PacketFormatException("字段 6（频道号）是 u8，超出 0..255：" + channelNumber);
        }
    }

    private static void requireStringWidth(String value, int max, String what) {
        if (value == null) {
            throw new PacketFormatException(what + " 不能为 null");
        }
        int n = value.getBytes(PacketWriter.DEFAULT_CHARSET).length;
        if (n == 0 || n >= max) {
            throw new PacketFormatException(what + " 的字节数必须落在 1.." + (max - 1)
                    + "，实际 " + n + "：长度为 0 或超宽时客户端只吃掉 4 字节长度、"
                    + "不读内容，后面的字段会整体错位");
        }
    }
}
