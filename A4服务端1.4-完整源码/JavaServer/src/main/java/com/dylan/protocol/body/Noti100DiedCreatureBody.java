package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti100DiedCreatureBody(int uniqueId) {

    public static final int PACKET_ID = 100;

    public byte[] toBytes() {
        if (uniqueId < 0 || uniqueId > 0xFFFF) {
            throw new PacketFormatException("uniqueId 是 u16，超出 0..65535：" + uniqueId);
        }
        PacketWriter w = new PacketWriter(2);
        w.u16(uniqueId);
        return w.toArray();
    }
}
