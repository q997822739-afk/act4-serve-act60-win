package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti19SkillInfoBody(int skillPoints, List<Skill> skills,
                                  List<LevelCap> levelCaps) {

    public static final int PACKET_ID = 19;

    public static final int SKILL_LENGTH = 3;

    public static final int LEVEL_CAP_LENGTH = 3;

    public record Skill(int slot, int skillId, int level) {}

    public record LevelCap(int skillId, int value) {}

    public Noti19SkillInfoBody {
        checkU16("skillPoints", skillPoints);
        if (skills == null || levelCaps == null) {
            throw new PacketFormatException("skills 和 levelCaps 都不能为 null");
        }
        skills = List.copyOf(skills);
        levelCaps = List.copyOf(levelCaps);
        if (skills.size() > 0xFF || levelCaps.size() > 0xFF) {
            throw new PacketFormatException("两个条数都是 u8");
        }
        for (Skill s : skills) {
            checkU8("slot", s.slot());
            checkU8("skillId", s.skillId());
            checkU8("level", s.level());
        }
        for (LevelCap c : levelCaps) {
            checkU8("skillId", c.skillId());
            checkU16("value", c.value());
        }
    }

    public static Noti19SkillInfoBody empty(int skillPoints) {
        return new Noti19SkillInfoBody(skillPoints, List.of(), List.of());
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(8 + skills.size() * SKILL_LENGTH
                + levelCaps.size() * LEVEL_CAP_LENGTH);
        w.u16(skillPoints);
        w.u8(skills.size());
        for (Skill s : skills) {
            w.u8(s.slot());
            w.u8(s.skillId());
            w.u8(s.level());
        }
        w.u8(levelCaps.size());
        for (LevelCap c : levelCaps) {
            w.u8(c.skillId());
            w.u16(c.value());
        }
        return w.toArray();
    }

    private static void checkU8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(name + " 超出 u8：" + value);
        }
    }

    private static void checkU16(String name, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new PacketFormatException(name + " 超出 u16：" + value);
        }
    }
}
