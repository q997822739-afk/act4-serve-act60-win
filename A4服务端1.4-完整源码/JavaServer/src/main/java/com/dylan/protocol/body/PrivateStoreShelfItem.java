package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketWriter;

public record PrivateStoreShelfItem(int shelfIndex, long price, long itemId, long count,
                                    int itemAttr, int durability, long enchant,
                                    int amplifyType, int amplifyValue) {

    public static final int LENGTH = 24;

    public void write(PacketWriter w) {
        w.u16(shelfIndex);
        w.u32((int) price);
        w.u32((int) itemId);
        w.u32((int) count);
        w.u8(itemAttr);
        w.u16(durability);
        w.u32((int) enchant);
        w.u8(amplifyType);
        w.u16(amplifyValue);
    }
}
