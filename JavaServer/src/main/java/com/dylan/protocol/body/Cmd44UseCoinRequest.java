package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketReader;

public record Cmd44UseCoinRequest(int targetUserId) {

    public static final int PACKET_ID = 44;

    public static final int LENGTH = 2;

    public static Cmd44UseCoinRequest parse(PacketReader r) {
        return new Cmd44UseCoinRequest(r.u16());
    }

    @Override
    public String toString() {
        return "CMD 44 USE_COIN[被复活者的连接编号=" + targetUserId + "]";
    }
}
