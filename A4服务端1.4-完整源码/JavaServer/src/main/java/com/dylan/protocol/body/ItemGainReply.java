package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketWriter;

import java.util.List;

/**
 * REPLY 171（DNF.exe {@code 0041B5D9}..{@code 0041B6FD}）。
 * A4 没有单独的“获得物品”NOTI。摆摊 REPLY 85 把东西放进背包（{@code 0088B810}），
 * 交易 NOTI 18 在条数为 0 时只关窗口，两条路都不打开获得窗。
 *
 * <p>成功体（结果字节 1 已由分发器吃掉，这里跟着别的 REPLY 一起写进包体）：
 * {@code u16 未使用, u32 未使用, u16 n, n × {u32 itemId, u32 数量}}。
 * n &gt; 0 时客户端建窗口 0x2A（和交易结算、魔盒同一类），按物品号和数量弹出。
 * 不改背包。前两个字段在成功分支里只暂存，不写金币、不扣物品。
 */
public final class ItemGainReply {
    public static final int PACKET_ID = 171;

    private ItemGainReply() {
    }

    public record Line(int itemId, int count) {
    }

    public static byte[] of(List<Line> lines) {
        PacketWriter w = new PacketWriter(7 + lines.size() * 8);
        w.u8(1).u16(0).u32(0).u16(lines.size());
        for (Line line : lines) {
            int count = line.count() <= 0 ? 1 : line.count();
            w.u32(line.itemId()).u32(count);
        }
        return w.toArray();
    }
}
