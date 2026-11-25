package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti169PutItemForEnchantBody(int kind, long value) {

    public static final int PACKET_ID = 169;

    public static final int LENGTH = 5;

    public Noti169PutItemForEnchantBody {
        if (kind < 0 || kind > 0xFF) {
            throw new PacketFormatException("kind 是 u8：" + kind);
        }
        if (value < 0 || value > 0xFFFF_FFFFL) {
            throw new PacketFormatException("value 是 u32：" + value);
        }
    }

    public byte[] toBytes() {
        return new PacketWriter(LENGTH).u8(kind).u32((int) value).toArray();
    }
}
