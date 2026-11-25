package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti154DeathTowerInfoBody(int dungeonIndex, int endStage) {
    public static final int PACKET_ID = 154;

    public static final int LENGTH = 6;

    public Noti154DeathTowerInfoBody {
        if (dungeonIndex < 0) {
            throw new PacketFormatException("dungeonIndex 不能是负数：" + dungeonIndex);
        }
        if (endStage < 0 || endStage > 0xFFFF) {
            throw new PacketFormatException("endStage 超出 u16：" + endStage);
        }
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(LENGTH);
        w.u32(dungeonIndex);
        w.u16(endStage);
        return w.toArray();
    }
}
