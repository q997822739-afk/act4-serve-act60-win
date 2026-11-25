package com.dylan.protocol.body;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti2PartyEntry(int userId, int totalExp, byte[] statBlob,
                              List<Equipment> equipment, List<Skill> skills,
                              String name, Tail tail) implements Noti2Entry {
    public static final int SUBTYPE = 1;

    public static final int NAME_CAPACITY = 0x20;

    public static final int EQUIPMENT_LENGTH = 23;

    public static final int AVATAR_TAIL_LENGTH = 30;

    public static boolean isAvatarSlot(int slot) {
        return slot >= 0 && slot <= EquipmentSlot.SKIN_AVATAR;
    }

    public record Equipment(int slot, int itemId, int addInfo, int itemAttr, int endurance,
                            int clearAvatarItemId, int enchant,
                            int amplifyType, int amplifyValue, boolean avatarTail) {
        public Equipment {
            checkU8("slot", slot);
            checkU8("itemAttr", itemAttr);
            checkU16("endurance", endurance);
            checkU8("amplifyType", amplifyType);
            checkU16("amplifyValue", amplifyValue);
        }

        public boolean avatarTail() {
            return avatarTail;
        }
    }

    public record Skill(int skillId, int level) {}

    public record Tail(int f1, int f2, int f3, int f4,
                       int duelWins, int duelLosses, int f7,
                       int f8, int f9, int f10, int f11,
                       int cofDenominator, int f13, int f14,
                       int f15, int f16, int f17) {
        public static final int COF_DENOMINATOR = 100;

        public static Tail defaults() {
            return new Tail(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    COF_DENOMINATOR, 0, 0, 0, 0, 0);
        }

        public static Tail probe() {
            return new Tail(11, 22, 33, 44,
                    1234, 5678, 5100,
                    55, 66, 77, 88,
                    2000, 4321, 9000,
                    99, 111, 122);
        }
    }

    public Noti2PartyEntry {
        checkU16("userId", userId);
        if (statBlob == null || equipment == null || skills == null || name == null) {
            throw new PacketFormatException("statBlob / equipment / skills / name 都不能为 null");
        }
        if (tail == null) {
            throw new PacketFormatException("tail 不能为 null，没东西可填就用 Tail.defaults()");
        }
        statBlob = statBlob.clone();
        equipment = List.copyOf(equipment);
        skills = List.copyOf(skills);
        if (equipment.size() > 0xFF || skills.size() > 0xFF) {
            throw new PacketFormatException("equipCount 和 skillCount 都是 u8");
        }
        for (Skill s : skills) {
            checkU8("skillId", s.skillId());
            checkU8("level", s.level());
        }
    }

    public static Noti2PartyEntry withStats(int userId, String name, int totalExp,
                                            CombatStats stats) {
        return withStats(userId, name, totalExp, stats, List.of(), Tail.defaults());
    }

    public static Noti2PartyEntry withStats(int userId, String name, int totalExp,
                                            CombatStats stats, Tail tail) {
        return withStats(userId, name, totalExp, stats, List.of(), tail);
    }

    public static Noti2PartyEntry withStats(int userId, String name, int totalExp,
                                            CombatStats stats, List<Equipment> equipment,
                                            Tail tail) {
        return withStats(userId, name, totalExp, stats, equipment, List.of(), tail);
    }

    public static Noti2PartyEntry withStats(int userId, String name, int totalExp,
                                            CombatStats stats, List<Equipment> equipment,
                                            List<Skill> skills, Tail tail) {
        return new Noti2PartyEntry(userId, totalExp, stats.toBytes(),
                equipment, skills, name, tail);
    }

    public Noti2PartyEntry withTotalExp(int totalExp) {
        return totalExp == this.totalExp ? this
                : new Noti2PartyEntry(userId, totalExp, statBlob, equipment, skills, name, tail);
    }

    @Override
    public int subType() {
        return SUBTYPE;
    }

    public static final int SUBTYPE_OTHER_USER = 3;

    @Override
    public boolean encodesFor(int subType) {
        return subType == SUBTYPE || subType == SUBTYPE_OTHER_USER;
    }

    public byte[] toBytes(int subType) {
        PacketWriter w = new PacketWriter(64 + statBlob.length
                + equipment.size() * (EQUIPMENT_LENGTH + 4 + AVATAR_TAIL_LENGTH)
                + skills.size() * 2);
        writeTo(w, subType);
        return w.toArray();
    }

    /**
     * A4（官方新角色 NOTI 2/1 125 字节，field_scan 验证装备各字段偏移）：装备 20 字节，没有 amplify 3 字节；
     * 子类型 1 技能表后只有 1 字节（ACT5 4 字节）。子类型 3 的尾部未验证，沿用 ACT5。
     */
    @Override
    public void writeTo(PacketWriter w, int subType) {
        boolean a4 = ServerConfig.A4;
        w.u16(userId);
        w.u32(totalExp);

        w.u32(statBlob.length);
        w.bytes(statBlob);

        w.u8(equipment.size());
        for (Equipment e : equipment) {
            w.u8(e.slot());
            w.u32(e.itemId());
            w.u32(e.addInfo());
            w.u8(e.itemAttr());
            w.u16(e.endurance());
            w.u32(e.clearAvatarItemId());
            w.u32(e.enchant());
            if (!a4) {
                w.u8(e.amplifyType());
                w.u16(e.amplifyValue());
            }
            // A4 时装也是 20 字节一件：满级假人 8 件时装官方 NOTI 2/1 每件 20 字节，没有 ACT5 的 u32 30 + 30 字节尾巴
            //（与 NOTI 13/1 同一处差别）
            if (e.avatarTail() && !a4) {
                w.u32(AVATAR_TAIL_LENGTH);
                w.zeros(AVATAR_TAIL_LENGTH);
            }
        }

        List<Skill> wireSkills = a4 ? a4JobSkills(skills) : skills;
        w.u8(wireSkills.size());
        for (Skill s : wireSkills) {
            w.u8(s.skillId());
            w.u8(s.level());
        }

        w.u8(tail.f1());

        if (subType != SUBTYPE_OTHER_USER) {
            if (!a4) {
                w.u8(tail.f2()).u8(tail.f3()).u8(tail.f4());
            }
            return;
        }

        w.u32(tail.duelWins()).u32(tail.duelLosses()).u32(tail.f7());
        w.u8(tail.f8()).u8(tail.f9()).u8(tail.f10()).u8(tail.f11());
        w.u32(tail.cofDenominator());
        w.u16(tail.f13());
        w.u32(tail.f14());
        writeName(w);
        w.u8(tail.f15()).u8(tail.f16()).u8(tail.f17());
    }

    /** 五个职业的 skill/*skill.lst 从这个编号起逐条相同（后跳、护甲精通、副职业等通用技能）。 */
    public static final int A4_COMMON_SKILL_FIRST = 169;

    /**
     * A4 官方的技能表只列职业技能：12 份样本（4 职业 × 转职）全部不含编号 ≥ 169 的通用技能，
     * 例如神枪手新角色 NOTI 19 有 6 条（4、47、181、184、185、179），NOTI 2/1 只有 4、47。
     */
    private static List<Skill> a4JobSkills(List<Skill> skills) {
        return skills.stream().filter(s -> s.skillId() < A4_COMMON_SKILL_FIRST).toList();
    }

    private void writeName(PacketWriter w) {
        byte[] raw = name.isEmpty() ? new byte[0]
                : name.getBytes(PacketWriter.DEFAULT_CHARSET);
        if (raw.length == 0 || raw.length >= NAME_CAPACITY) {
            w.u32(0);
            return;
        }
        w.u32(raw.length);
        w.bytes(raw);
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
