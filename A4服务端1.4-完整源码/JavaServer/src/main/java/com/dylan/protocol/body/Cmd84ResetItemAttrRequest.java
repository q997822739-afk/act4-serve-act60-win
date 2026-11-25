package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd84ResetItemAttrRequest(int slot, int itemId, int boxSlot) {

    public static final int PACKET_ID = 84;

    public static final int LENGTH = 8;

    public static Cmd84ResetItemAttrRequest parse(byte[] plain) {
        if (plain.length < LENGTH) {
            throw new PacketFormatException("RESET_ITEM_ATTR 至少 " + LENGTH
                    + " 字节，实际 " + plain.length);
        }
        PacketReader r = new PacketReader(plain);
        int slot = r.u16();
        int itemId = r.u32();
        int boxSlot = r.u16();
        return new Cmd84ResetItemAttrRequest(slot, itemId, boxSlot);
    }

    @Override
    public String toString() {
        return "RESET_ITEM_ATTR[背包槽 " + slot + " 的 " + itemId + "，道具在槽 " + boxSlot + "]";
    }
}
