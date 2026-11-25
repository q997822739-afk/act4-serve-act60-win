package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Cmd204DungeonEventStoryPauseRequest(int flag) {
    public static final int PACKET_ID = 204;

    public static final int LENGTH = 1;

    public Cmd204DungeonEventStoryPauseRequest {
        if (flag < 0 || flag > 0xFF) {
            throw new PacketFormatException("flag 是 u8：" + flag);
        }
    }

    public static Cmd204DungeonEventStoryPauseRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException("DUNGEON_EVENT_STORY_PAUSE 包体至少要 "
                    + LENGTH + " 字节，实际 " + (plain == null ? "null" : plain.length));
        }
        return new Cmd204DungeonEventStoryPauseRequest(plain[0] & 0xFF);
    }

    public byte[] toBytes() {
        return new PacketWriter(LENGTH).u8(flag).toArray();
    }
}
