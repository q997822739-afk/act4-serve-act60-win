package com.dylan.protocol.body;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record CombatStats(
        int maximumHp, int maximumMp,
        int physicalAttack, int physicalDefense,
        int magicalAttack, int magicalDefense,
        int fireResistance, int waterResistance,
        int darkResistance, int lightResistance,
        List<Integer> extraSlots,
        int inventoryLimit,
        int hpRegeneration, int mpRegeneration,
        int movementSpeed, int attackSpeed, int castSpeed,
        int hitRecovery, int jumpPower,
        int weight, int statPercent) {
    public static final int LENGTH = 83;

    /**
     * A4 属性块 85 字节（官方新角色 NOTI 2/1 的 u32 长度 = 0x55）：movementSpeed 是 u16（ACT5 u32），
     * 末尾多 4 字节（官方恒 0，含义未知）。ACT5 的 u32 移速让攻速/施放速度整体后移 2 字节，客户端放技能卡住。
     */
    public static final int A4_LENGTH = 85;

    static final int A4_TAIL_UNKNOWN = 4;

    public static final int EXTRA_SLOT_COUNT = 17;

    public static final int NEUTRAL_STAT_PERCENT = 100;

    public static final int DEFAULT_MOVEMENT_SPEED = 8_500;

    public static final List<Integer> NO_EXTRA_SLOTS =
            java.util.Collections.nCopies(EXTRA_SLOT_COUNT, 0);

    public CombatStats {
        if (extraSlots == null) {
            throw new PacketFormatException("extraSlots 不能为 null，没有值就传 NO_EXTRA_SLOTS");
        }
        extraSlots = List.copyOf(extraSlots);
        if (extraSlots.size() != EXTRA_SLOT_COUNT) {
            throw new PacketFormatException("extraSlots 必须正好 " + EXTRA_SLOT_COUNT
                    + " 项，给了 " + extraSlots.size());
        }
    }

    public static CombatStats defaults() {
        return new CombatStats(
                11_600, 11_900,
                75, 75, 45, 45,
                0, 0, 0, 0,
                NO_EXTRA_SLOTS,
                480_000,
                0, 500,
                DEFAULT_MOVEMENT_SPEED,
                8_500, 7_000,
                6_000, 4_300,
                500_000,
                NEUTRAL_STAT_PERCENT);
    }

    public static CombatStats probe() {
        var slots = new java.util.ArrayList<Integer>(EXTRA_SLOT_COUNT);
        for (int i = 0; i < EXTRA_SLOT_COUNT; i++) {
            slots.add(atOffset(24 + 2 * i));
        }
        return new CombatStats(
                atOffset(0), atOffset(4),
                atOffset(8), atOffset(10), atOffset(12), atOffset(14),
                atOffset(16), atOffset(18), atOffset(20), atOffset(22),
                slots,
                atOffset(58),
                atOffset(62), atOffset(64),
                atOffset(66),
                atOffset(70), atOffset(72),
                atOffset(74), atOffset(76),
                atOffset(78),
                NEUTRAL_STAT_PERCENT);
    }

    private static int atOffset(int offset) {
        return (1000 + offset) * 10;
    }

    public CombatStats withMovementSpeed(int speed) {
        return new CombatStats(maximumHp, maximumMp, physicalAttack, physicalDefense,
                magicalAttack, magicalDefense, fireResistance, waterResistance,
                darkResistance, lightResistance, extraSlots, inventoryLimit, hpRegeneration,
                mpRegeneration, speed, attackSpeed, castSpeed, hitRecovery, jumpPower,
                weight, statPercent);
    }

    public CombatStats plus(CombatStats other) {
        var slots = new java.util.ArrayList<Integer>(EXTRA_SLOT_COUNT);
        for (int i = 0; i < EXTRA_SLOT_COUNT; i++) {
            slots.add(addU16(extraSlots.get(i), other.extraSlots.get(i)));
        }
        return new CombatStats(
                maximumHp + other.maximumHp,
                maximumMp + other.maximumMp,
                addU16(physicalAttack, other.physicalAttack),
                addU16(physicalDefense, other.physicalDefense),
                addU16(magicalAttack, other.magicalAttack),
                addU16(magicalDefense, other.magicalDefense),
                addI16(fireResistance, other.fireResistance),
                addI16(waterResistance, other.waterResistance),
                addI16(darkResistance, other.darkResistance),
                addI16(lightResistance, other.lightResistance),
                slots,
                inventoryLimit + other.inventoryLimit,
                addI16(hpRegeneration, other.hpRegeneration),
                addI16(mpRegeneration, other.mpRegeneration),
                movementSpeed + other.movementSpeed,
                addU16(attackSpeed, other.attackSpeed),
                addU16(castSpeed, other.castSpeed),
                addI16(hitRecovery, other.hitRecovery),
                addI16(jumpPower, other.jumpPower),
                weight + other.weight,
                statPercent);
    }

    private static int addU16(int a, int b) {
        return (a + b) & 0xFFFF;
    }

    private static int addI16(int a, int b) {
        return (short) (a + b);
    }

    public static CombatStats zero() {
        return new CombatStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, NO_EXTRA_SLOTS,
                0, 0, 0, 0, 0, 0, 0, 0, 0, NEUTRAL_STAT_PERCENT);
    }

    public boolean isZero() {
        return maximumHp == 0 && maximumMp == 0
                && physicalAttack == 0 && physicalDefense == 0
                && magicalAttack == 0 && magicalDefense == 0
                && fireResistance == 0 && waterResistance == 0
                && darkResistance == 0 && lightResistance == 0
                && extraSlots.stream().allMatch(v -> v == 0)
                && inventoryLimit == 0 && hpRegeneration == 0 && mpRegeneration == 0
                && movementSpeed == 0 && attackSpeed == 0 && castSpeed == 0
                && hitRecovery == 0 && jumpPower == 0 && weight == 0;
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(LENGTH);
        w.u32(maximumHp);
        w.u32(maximumMp);
        u16(w, "physicalAttack", physicalAttack);
        u16(w, "physicalDefense", physicalDefense);
        u16(w, "magicalAttack", magicalAttack);
        u16(w, "magicalDefense", magicalDefense);
        i16(w, fireResistance);
        i16(w, waterResistance);
        i16(w, darkResistance);
        i16(w, lightResistance);
        for (int slot : extraSlots) {
            u16(w, "extraSlot", slot);
        }
        boolean a4 = ServerConfig.A4;
        w.u32(inventoryLimit);
        i16(w, hpRegeneration);
        i16(w, mpRegeneration);
        if (a4) {
            u16(w, "movementSpeed", movementSpeed);
        } else {
            w.u32(movementSpeed);
        }
        u16(w, "attackSpeed", attackSpeed);
        u16(w, "castSpeed", castSpeed);
        i16(w, hitRecovery);
        i16(w, jumpPower);
        w.u32(weight);
        if (statPercent < 0 || statPercent > 0xFF) {
            throw new PacketFormatException("statPercent 超出 u8：" + statPercent);
        }
        w.u8(statPercent);
        if (a4) {
            w.zeros(A4_TAIL_UNKNOWN);
        }

        byte[] out = w.toArray();
        int want = a4 ? A4_LENGTH : LENGTH;
        if (out.length != want) {
            throw new IllegalStateException("战斗属性块必须是 " + want
                    + " 字节，构造出来的是 " + out.length);
        }
        return out;
    }

    private static void u16(PacketWriter w, String field, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new PacketFormatException(field + " 超出 u16：" + value);
        }
        w.u16(value);
    }

    private static void i16(PacketWriter w, int value) {
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new PacketFormatException("有符号字段超出 i16：" + value);
        }
        w.u16(value & 0xFFFF);
    }
}
