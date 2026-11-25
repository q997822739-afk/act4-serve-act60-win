package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketWriter;

public record Noti158DeathTowerEplpBody(boolean canRetry) {
    public static final int PACKET_ID = 158;

    public static final int LENGTH = 1;

    public byte[] toBytes() {
        return new PacketWriter(LENGTH).u8(canRetry ? 1 : 0).toArray();
    }
}
