package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Cmd229ItemLockRequest(int itemSpace, int slot) {

    public static final int PACKET_ID_LOCK = 229;

    public static final int PACKET_ID_UNLOCK = 230;

    public static final int PACKET_ID_UNLOCK_CANCEL = 231;

    /**
     * A4 官方枚举 RequestItemLock / Unlock / UnlockCancel。
     * 上锁发包点 0086E5BD 是 u8 容器 + u16 槽，和 229 一样。
     * 回包（结果字节已读走）：203 成功 u8+u16、失败 u8（0041D32A）；
     * 204 成功 u8+u16+u32、失败 u8+u32（0041D3D6）；205 成功 u8+u16、失败 u8（0041D4DD）。
     * A4 的 204 不是剧情暂停，见 DungeonHandler。
     */
    public static final int A4_LOCK = 203;

    public static final int A4_UNLOCK = 204;

    public static final int A4_CANCEL = 205;

    public static final int LENGTH = 3;

    public Cmd229ItemLockRequest {
        if (itemSpace < 0 || itemSpace > 0xFF) {
            throw new PacketFormatException("容器号是 u8，超出 0..255：" + itemSpace);
        }
        if (slot < 0 || slot > 0xFFFF) {
            throw new PacketFormatException("格号是 u16，超出 0..65535：" + slot);
        }
    }

    public static Cmd229ItemLockRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException("物品锁定包体至少要 " + LENGTH
                    + " 字节，实际 " + (plain == null ? "null" : plain.length));
        }
        int space = plain[0] & 0xFF;
        int slot = (plain[1] & 0xFF) | ((plain[2] & 0xFF) << 8);
        return new Cmd229ItemLockRequest(space, slot);
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(LENGTH);
        w.u8(itemSpace);
        w.u16(slot);
        return w.toArray();
    }

    @Override
    public String toString() {
        return "容器 " + itemSpace + " 第 " + slot + " 格";
    }
}
