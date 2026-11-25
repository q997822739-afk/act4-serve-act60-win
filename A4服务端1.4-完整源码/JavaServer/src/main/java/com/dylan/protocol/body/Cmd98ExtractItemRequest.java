package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd98ExtractItemRequest(long mailIdx) {

    public static final int PACKET_ID = 98;

    public static final int LENGTH = 4;

    public static Cmd98ExtractItemRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException("CMD " + PACKET_ID + " 包体应当是 " + LENGTH
                    + " 字节，实际 " + (plain == null ? "null" : plain.length));
        }
        PacketReader r = new PacketReader(plain);
        return new Cmd98ExtractItemRequest(r.u32() & 0xFFFF_FFFFL);
    }

    @Override
    public String toString() {
        return "领取邮件 " + mailIdx;
    }
}
