package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti106EvoluteCreatureBody(int creatureId, int uniqueId) {

    public static final int PACKET_ID = 106;

    public byte[] toBytes() {
        if (creatureId < 0 || creatureId > 0xFF) {
            throw new PacketFormatException("creature id 是 u8，超出 0..255：" + creatureId);
        }
        if (uniqueId < 0 || uniqueId > 0xFFFF) {
            throw new PacketFormatException("uniqueId 是 u16，超出 0..65535：" + uniqueId);
        }
        PacketWriter w = new PacketWriter(3);
        w.u8(creatureId);
        w.u16(uniqueId);
        return w.toArray();
    }
}
