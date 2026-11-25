package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd32IncreaseStatusRequest(int slot) {

    public static final int PACKET_ID = 32;

    public static final int LENGTH = 2;

    public static final int QUEST_ITEM_SLOT_LIMIT = 152;

    public static Cmd32IncreaseStatusRequest parse(byte[] plain) {
        if (plain.length < LENGTH) {
            throw new PacketFormatException("INCREASE_STATUS 至少 " + LENGTH
                    + " 字节，实际 " + plain.length);
        }
        return new Cmd32IncreaseStatusRequest(new PacketReader(plain).u16());
    }

    public boolean isQuestItemSlot() {
        return slot > QUEST_ITEM_SLOT_LIMIT;
    }

    @Override
    public String toString() {
        return "INCREASE_STATUS[主背包槽 " + slot + "]";
    }
}
