package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd24SellItemRequest(int itemSpace, int slot, int count) {

    public static final int PACKET_ID = 24;

    public static final int LENGTH = 5;

    public static Cmd24SellItemRequest parse(byte[] plain) {
        if (plain.length < LENGTH) {
            throw new PacketFormatException("CMD " + PACKET_ID + " SELL_ITEM 包体只有 "
                    + plain.length + " 字节，至少要 " + LENGTH);
        }
        PacketReader r = new PacketReader(plain);
        return new Cmd24SellItemRequest(r.u8(), r.u16(), r.u16());
    }

    @Override
    public String toString() {
        return "SELL_ITEM[容器 " + itemSpace + " 第 " + slot + " 格 ×" + count + "]";
    }
}
