package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti103CreatureStateBody(long uiId, int stomach) {

    public static final int PACKET_ID = 103;

    public byte[] toBytes() {
        if (uiId < 0 || uiId > 0xFFFFFFFFL) {
            throw new PacketFormatException("ui_id 是 u32，超出范围：" + uiId);
        }
        if (stomach < 0 || stomach > 0xFFFFFFFFL) {
            throw new PacketFormatException("饱食度是 u32，超出范围：" + stomach);
        }
        PacketWriter w = new PacketWriter(8);
        w.u32((int) uiId);
        w.u32(stomach);
        return w.toArray();
    }
}
