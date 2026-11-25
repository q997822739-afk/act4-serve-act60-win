package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd21BuyItemRequest(long itemId, long count, long shopNo) {

    public static final int PACKET_ID = 21;

    public static final int LENGTH = 12;

    public static Cmd21BuyItemRequest parse(byte[] plain) {
        if (plain.length < LENGTH) {
            throw new PacketFormatException("CMD " + PACKET_ID + " BUY_ITEM 包体只有 "
                    + plain.length + " 字节，至少要 " + LENGTH);
        }
        PacketReader r = new PacketReader(plain);
        return new Cmd21BuyItemRequest(Integer.toUnsignedLong(r.u32()),
                Integer.toUnsignedLong(r.u32()), Integer.toUnsignedLong(r.u32()));
    }

    @Override
    public String toString() {
        return "BUY_ITEM[物品 " + itemId + " count=" + count + " 店 " + shopNo + "]";
    }
}
