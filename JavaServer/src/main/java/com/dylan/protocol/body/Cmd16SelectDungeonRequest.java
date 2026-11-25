package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Cmd16SelectDungeonRequest(int dungeonIndex, int difficulty,
                                        int dungeonType, int quickParty) {

    public static final int PACKET_ID = 16;

    public static final int LENGTH = 5;

    public static final int DUNGEON_TYPE_NORMAL = 0;

    public static final int DUNGEON_TYPE_HELL_PARTY = 1;

    public static final int DUNGEON_TYPE_NO_PARTY_CHECK = 2;

    public static final int QUICK_PARTY_ON = 1;

    public static final int TUTORIAL_DUNGEON_INDEX = 10000;

    public Cmd16SelectDungeonRequest {
        if (dungeonIndex < 0 || dungeonIndex > 0xFFFF) {
            throw new PacketFormatException("dungeonIndex 超出 u16：" + dungeonIndex);
        }
        if (difficulty < 0 || difficulty > 0xFF
                || dungeonType < 0 || dungeonType > 0xFF
                || quickParty < 0 || quickParty > 0xFF) {
            throw new PacketFormatException("difficulty / dungeonType / quickParty 都是 u8");
        }
    }

    public static Cmd16SelectDungeonRequest of(int dungeonIndex, int difficulty) {
        return new Cmd16SelectDungeonRequest(dungeonIndex, difficulty,
                DUNGEON_TYPE_NORMAL, 0);
    }

    public static Cmd16SelectDungeonRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH - 1) {
            throw new PacketFormatException("SELECT_DUNGEON 包体至少要 " + (LENGTH - 1) + " 字节，实际 "
                    + (plain == null ? "null" : plain.length));
        }
        int dungeonIndex = (plain[0] & 0xFF) | ((plain[1] & 0xFF) << 8);
        int quickParty = plain.length >= LENGTH ? plain[4] & 0xFF : 0;
        return new Cmd16SelectDungeonRequest(dungeonIndex, plain[2] & 0xFF,
                plain[3] & 0xFF, quickParty);
    }

    public boolean isTutorial() {
        return dungeonIndex == TUTORIAL_DUNGEON_INDEX;
    }

    public boolean isHellParty() {
        return dungeonType == DUNGEON_TYPE_HELL_PARTY;
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(LENGTH);
        w.u16(dungeonIndex);
        w.u8(difficulty);
        w.u8(dungeonType);
        w.u8(quickParty);
        return w.toArray();
    }
}
