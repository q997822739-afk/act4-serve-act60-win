package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd29UseLotteryItemRequest(int slot) {

    public static final int PACKET_ID = 29;

    public static final int LENGTH = 2;

    public static Cmd29UseLotteryItemRequest parse(byte[] plain) {
        if (plain.length < LENGTH) {
            throw new PacketFormatException("USE_LOTTERY_ITEM 至少 " + LENGTH
                    + " 字节，实际 " + plain.length);
        }
        return new Cmd29UseLotteryItemRequest(new PacketReader(plain).u16());
    }

    @Override
    public String toString() {
        return "USE_LOTTERY_ITEM[主背包槽 " + slot + "]";
    }
}
