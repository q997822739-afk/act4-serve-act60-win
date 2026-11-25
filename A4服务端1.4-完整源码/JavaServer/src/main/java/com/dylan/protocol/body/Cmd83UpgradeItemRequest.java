package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd83UpgradeItemRequest(int amplify, int slot, int itemId, int materialSlot) {

    public static final int PACKET_ID = 83;

    public static final int LENGTH = 9;

    /** A4 请求没有开头的增幅 u8。 */
    public static final int A4_LENGTH = 8;

    public static Cmd83UpgradeItemRequest parse(byte[] plain) {
        if (com.dylan.net.ServerConfig.A4) {
            // A4 客户端（DNF.exe 0047438C..004743F0）写 u16 装备槽、u32 物品号、u16 材料槽，共 8 字节，
            // 没有 ACT5 开头的增幅 u8（client_sends.py：83 = 2+4+2）。官方成品服实测：8 字节包成功强化，
            // 带增幅字节的 9 字节包回 00 04（中间数据/craft/report.txt）。A4 没有增幅 → 恒 0。
            if (plain.length < A4_LENGTH) {
                throw new PacketFormatException("UPGRADE_ITEM(A4) 至少 " + A4_LENGTH
                        + " 字节，实际 " + plain.length);
            }
            PacketReader r = new PacketReader(plain);
            int slot = r.u16();
            int itemId = r.u32();
            int materialSlot = r.u16();
            return new Cmd83UpgradeItemRequest(0, slot, itemId, materialSlot);
        }
        if (plain.length < LENGTH) {
            throw new PacketFormatException("UPGRADE_ITEM 至少 " + LENGTH
                    + " 字节，实际 " + plain.length);
        }
        PacketReader r = new PacketReader(plain);
        int amplify = r.u8();
        int slot = r.u16();
        int itemId = r.u32();
        int materialSlot = r.u16();
        return new Cmd83UpgradeItemRequest(amplify, slot, itemId, materialSlot);
    }

    public boolean isAmplify() {
        return amplify != 0;
    }

    @Override
    public String toString() {
        return "UPGRADE_ITEM[背包槽 " + slot + " 的 " + itemId + "，材料在槽 " + materialSlot
                + (isAmplify() ? "，增幅标志 " + amplify : "") + "]";
    }
}
