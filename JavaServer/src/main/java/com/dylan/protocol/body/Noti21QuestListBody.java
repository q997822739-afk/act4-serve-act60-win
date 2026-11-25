package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti21QuestListBody(int characterLevel, List<Integer> questIds) {

    public static final int PACKET_ID = 21;

    public static final int MAX_QUESTS = 0xFFFF;

    public Noti21QuestListBody {
        if (characterLevel < 0 || characterLevel > 0xFF) {
            throw new PacketFormatException("characterLevel 超出 u8：" + characterLevel);
        }
        if (questIds == null) {
            throw new PacketFormatException("questIds 不能为 null");
        }
        questIds = List.copyOf(questIds);
        if (questIds.size() > MAX_QUESTS) {
            throw new PacketFormatException("条数是 u16，装不下 " + questIds.size() + " 条");
        }
        for (int id : questIds) {
            if (id < 0 || id > 0xFFFF) {
                throw new PacketFormatException("questId 超出 u16：" + id);
            }
        }
    }

    public static Noti21QuestListBody empty(int characterLevel) {
        return new Noti21QuestListBody(characterLevel, List.of());
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(3 + questIds.size() * 2);
        w.u8(characterLevel);
        w.u16(questIds.size());
        for (int id : questIds) {
            w.u16(id);
        }
        return w.toArray();
    }
}
