package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd170UseBoosterItemRequest(int slot) {

    public static final int PACKET_ID = 170;

    public static final int LENGTH = 2;

    public Cmd170UseBoosterItemRequest {
        if (slot < 0 || slot > 0xFFFF) {
            throw new PacketFormatException("slot 放不进 u16：" + slot);
        }
    }

    public static Cmd170UseBoosterItemRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException("CMD 170 包体只有 "
                    + (plain == null ? 0 : plain.length) + " 字节，要 " + LENGTH);
        }
        return new Cmd170UseBoosterItemRequest(new PacketReader(plain).u16());
    }

    @Override
    public String toString() {
        return "槽 " + slot;
    }
}
