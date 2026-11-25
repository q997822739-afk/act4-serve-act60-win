package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Cmd1ReplyBody(
        int result,
        int unknown1,
        int unknown2,
        int channelKind,
        int pcRoom,
        int unknown5,
        String text,
        int unknown7,
        int unknown8,
        int unknown9,
        int unknown10,
        int unknown11,
        int unknown12,
        int unknown13,
        int unknown14,
        int unknown15,
        int unknown16) {

    public static final int PACKET_ID = 1;

    public static final int LENGTH = 29;

    public static final int TEXT_MAX = 0x10;

    public static final byte CHANNEL_KIND_NORMAL = 0;

    public static final int ERROR_CODE_MIN = 2;

    public static final int ERROR_CODE_MAX = 2 + 0xAE;

    public static Cmd1ReplyBody accepted() {
        return new Cmd1ReplyBody(1, 0, 0, CHANNEL_KIND_NORMAL, 0, 0, "",
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * A4 登录成功回包（官方成品服逐字节）：字段顺序同 {@link #toBytes()}，文本是服务器地址，
     * 两个 u32 是入口端口，末尾只有 7 个 u8（ACT5 是 8 个）。
     * <pre>01 00 00 kind 00 | u32 0 | "127.0.0.1" | u32 7001 | u32 7001 | 00×7</pre>
     *
     * <p><b>2026-10-28：这三个字段其实是 P2P 中转参数</b>，官方服只发单人都不组队，所以全都填了
     * 网关地址/端口/0，看不出语义。对照 2006 JP 的 LOGIN 成功体
     * （{@code 3_服务端尝试/45JP_完整源码_20260909/jp2006_server/handlers/character_manage.py}）：
     * <pre>
     *   age:u8, is_pc_room:u8, channel_type:u8, udp_identity:u32,
     *   relay_host:dstring(max 15), relay_udp_port:u32, relay_tcp_port:u32
     * </pre>
     * 位置一一对上（多出来的前两个 u8 是 A4 自己的 result/未知位）。客户端把这组值存进 NUDP 模块
     * （A4 {@code sub_A66BC0}: +2224 存 ip、+2484=tcp、+2488=udp），组队时做第 3 个候选端点，
     * 中转登记帧里的 identity 也取自这里 —— 官方服发 0，所以 A4 客户端登记帧里 acc 是 0（外网实抓）。
     */
    public static byte[] a4Accepted(int channelKind, String host, int entrancePort) {
        return a4Accepted(channelKind, host, entrancePort, entrancePort, 0);
    }

    /**
     * 同上，但把中转地址和身份填真：{@code relayUdpPort / relayTcpPort} 是客户端组队时打的中转端口，
     * {@code udpIdentity} 是它认自己的身份（填账号 id，与 NOTI 11 每行的 acc 一致）。
     */
    public static byte[] a4Accepted(int channelKind, String host,
                                    int relayUdpPort, int relayTcpPort, int udpIdentity) {
        requireU8(channelKind, "字段 3（频道类型）");
        if (host == null || host.getBytes(PacketWriter.DEFAULT_CHARSET).length >= TEXT_MAX) {
            throw new PacketFormatException("登录回包地址必须 0.." + (TEXT_MAX - 1) + " 字节：" + host);
        }
        PacketWriter w = new PacketWriter(48);
        w.u8(1);
        w.u8(0);
        w.u8(0);
        w.u8(channelKind);
        w.u8(0);
        w.u32(udpIdentity);
        w.string(host);
        w.u32(relayUdpPort);
        w.u32(relayTcpPort);
        for (int i = 0; i < 7; i++) {
            w.u8(0);
        }
        return w.toArray();
    }

    public static byte[] rejected(int errorCode) {
        if (errorCode < ERROR_CODE_MIN || errorCode > ERROR_CODE_MAX) {
            throw new PacketFormatException("LOGIN 拒绝码必须落在 " + ERROR_CODE_MIN + ".."
                    + ERROR_CODE_MAX + "（客户端失败支认的区间），实际 " + errorCode);
        }
        return new byte[]{0, (byte) errorCode};
    }

    public byte[] toBytes() {
        validate();
        PacketWriter w = new PacketWriter(64);
        w.u8(result);
        w.u8(unknown1);
        w.u8(unknown2);
        w.u8(channelKind);
        w.u8(pcRoom);
        w.u32(unknown5);
        w.string(text);
        w.u32(unknown7);
        w.u32(unknown8);
        w.u8(unknown9);
        w.u8(unknown10);
        w.u8(unknown11);
        w.u8(unknown12);
        w.u8(unknown13);
        w.u8(unknown14);
        w.u8(unknown15);
        w.u8(unknown16);
        return w.toArray();
    }

    private void validate() {
        if (result == 0) {
            throw new PacketFormatException("结果字节为 0 时客户端走失败支，"
                    + "那条路只读一个 u8 错误码 —— 用 rejected(code) 拼，别用这个 record");
        }
        requireU8(result, "字段 0（结果）");
        requireU8(unknown1, "字段 1");
        requireU8(unknown2, "字段 2");
        requireU8(channelKind, "字段 3（频道类型）");
        requireU8(pcRoom, "字段 4");
        requireU8(unknown9, "字段 9");
        requireU8(unknown10, "字段 10");
        requireU8(unknown11, "字段 11");
        requireU8(unknown12, "字段 12");
        requireU8(unknown13, "字段 13");
        requireU8(unknown14, "字段 14");
        requireU8(unknown15, "字段 15");
        requireU8(unknown16, "字段 16");

        if (text == null) {
            throw new PacketFormatException("字段 6 不能为 null，空串请写 \"\"");
        }
        int n = text.getBytes(PacketWriter.DEFAULT_CHARSET).length;
        if (n >= TEXT_MAX) {
            throw new PacketFormatException("字段 6 的字节数必须落在 0.." + (TEXT_MAX - 1)
                    + "，实际 " + n + "：超宽时客户端只吃掉 4 字节长度、不读内容，"
                    + "后面 4 个字段会整体错位");
        }
        if (unknown16 != 0) {
            throw new PacketFormatException("字段 16 非 0 会让客户端弹一个消息框，"
                    + "握手阶段只能发 0");
        }
    }

    private static void requireU8(int value, String what) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(what + " 是 u8，超出 0..255：" + value);
        }
    }
}
