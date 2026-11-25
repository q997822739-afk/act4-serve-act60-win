package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd33AcceptQuestRequest(int questId) {

    public static final int PACKET_ID = 33;

    public static final int LENGTH = 2;

    public Cmd33AcceptQuestRequest {
        if (questId < 0 || questId > 0xFFFF) {
            throw new PacketFormatException("questId 超出 u16：" + questId);
        }
    }

    public static Cmd33AcceptQuestRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException("ACCEPT_QUEST 包体至少要 " + LENGTH + " 字节，实际 "
                    + (plain == null ? "null" : plain.length));
        }
        return new Cmd33AcceptQuestRequest(new PacketReader(plain).u16());
    }
}
