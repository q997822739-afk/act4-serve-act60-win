package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti183DungeonEventStoryPauseBody(int userId, int flag) {

    public static final int PACKET_ID = 183;

    public static final int LENGTH = 3;

    public Noti183DungeonEventStoryPauseBody {
        if (userId < 0 || userId > 0xFFFF) {
            throw new PacketFormatException("userId 是 u16：" + userId);
        }
        if (flag < 0 || flag > 0xFF) {
            throw new PacketFormatException("flag 是 u8：" + flag);
        }
    }

    public byte[] toBytes() {
        return new PacketWriter(LENGTH).u16(userId).u8(flag).toArray();
    }
}
