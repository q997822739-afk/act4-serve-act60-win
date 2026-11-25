package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti102GainExpCreatureBody(int level, long exp) {

    public static final int PACKET_ID = 102;

    public byte[] toBytes() {
        if (level < 0 || level > 0xFF) {
            throw new PacketFormatException("宠物等级是 u8，超出 0..255：" + level);
        }
        if (exp < 0 || exp > 0xFFFFFFFFL) {
            throw new PacketFormatException("宠物经验是 u32，超出范围：" + exp);
        }
        PacketWriter w = new PacketWriter(5);
        w.u8(level);
        w.u32((int) exp);
        return w.toArray();
    }
}
