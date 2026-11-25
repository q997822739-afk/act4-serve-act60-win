package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti6UserLeaveBody(int userId) {

    public static final int PACKET_ID = 6;

    public static final int LENGTH = 2;

    public Noti6UserLeaveBody {
        if (userId < 0 || userId > 0xFFFF) {
            throw new PacketFormatException("userId 超出 u16：" + userId);
        }
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(LENGTH);
        w.u16(userId);
        return w.toArray();
    }
}
