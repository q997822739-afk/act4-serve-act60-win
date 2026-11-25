package com.dylan.game;

import java.util.List;
import java.util.Map;

import com.dylan.protocol.body.CombatStats;

public final class ElixirStats {
    private ElixirStats() {
    }

    public static final int ITEM_STRENGTH = 1039;

    public static final int ITEM_INTELLIGENCE = 1040;

    public static final int ITEM_VITALITY = 1041;

    public static final int ITEM_SPIRIT = 1042;

    public static final int ITEM_MAX_HP = 1043;

    public static final int ITEM_MAX_MP = 1044;

    public static final int ITEM_MOVE_SPEED = 1045;

    public static final int ITEM_ALL_RESISTANCE = 1046;

    public static final int STAT_PER_STONE = 50;

    public static final int VITAL_PER_STONE = 250;

    public static final int MINOR_PER_STONE = 10;

    public static final List<Integer> ITEMS = List.of(
            ITEM_STRENGTH, ITEM_INTELLIGENCE, ITEM_VITALITY, ITEM_SPIRIT,
            ITEM_MAX_HP, ITEM_MAX_MP, ITEM_MOVE_SPEED, ITEM_ALL_RESISTANCE);

    public static final Map<Integer, Integer> TYPES = Map.of(
            ITEM_MAX_HP, com.dylan.protocol.body.Cmd32ReplyBody.TYPE_MAX_HP,
            ITEM_MAX_MP, com.dylan.protocol.body.Cmd32ReplyBody.TYPE_MAX_MP,
            ITEM_STRENGTH, com.dylan.protocol.body.Cmd32ReplyBody.TYPE_STRENGTH,
            ITEM_VITALITY, com.dylan.protocol.body.Cmd32ReplyBody.TYPE_VITALITY,
            ITEM_INTELLIGENCE, com.dylan.protocol.body.Cmd32ReplyBody.TYPE_INTELLIGENCE,
            ITEM_SPIRIT, com.dylan.protocol.body.Cmd32ReplyBody.TYPE_SPIRIT,
            ITEM_MOVE_SPEED, com.dylan.protocol.body.Cmd32ReplyBody.TYPE_MOVE_SPEED,
            ITEM_ALL_RESISTANCE, com.dylan.protocol.body.Cmd32ReplyBody.TYPE_ALL_RESISTANCE);

    public static CombatStats apply(int itemId, int count) {
        if (count <= 0) {
            return CombatStats.zero();
        }
        CombatStats z = CombatStats.zero();
        return switch (itemId) {
            case ITEM_MAX_HP -> withMaximumHp(z, VITAL_PER_STONE * count);
            case ITEM_MAX_MP -> withMaximumMp(z, VITAL_PER_STONE * count);
            case ITEM_STRENGTH -> withPhysicalAttack(z, STAT_PER_STONE * count);
            case ITEM_VITALITY -> withPhysicalDefense(z, STAT_PER_STONE * count);
            case ITEM_INTELLIGENCE -> withMagicalAttack(z, STAT_PER_STONE * count);
            case ITEM_SPIRIT -> withMagicalDefense(z, STAT_PER_STONE * count);
            case ITEM_MOVE_SPEED -> z.withMovementSpeed(MINOR_PER_STONE * count);
            case ITEM_ALL_RESISTANCE -> withResistances(z, MINOR_PER_STONE * count);
            default -> z;
        };
    }

    public static CombatStats total(Map<Integer, Integer> used) {
        CombatStats sum = CombatStats.zero();
        for (var e : used.entrySet()) {
            sum = sum.plus(apply(e.getKey(), e.getValue()));
        }
        return sum;
    }

    private static CombatStats withMaximumHp(CombatStats z, int v) {
        return new CombatStats(v, z.maximumMp(), z.physicalAttack(), z.physicalDefense(),
                z.magicalAttack(), z.magicalDefense(), z.fireResistance(), z.waterResistance(),
                z.darkResistance(), z.lightResistance(), z.extraSlots(), z.inventoryLimit(),
                z.hpRegeneration(), z.mpRegeneration(), z.movementSpeed(), z.attackSpeed(),
                z.castSpeed(), z.hitRecovery(), z.jumpPower(), z.weight(), z.statPercent());
    }

    private static CombatStats withMaximumMp(CombatStats z, int v) {
        return new CombatStats(z.maximumHp(), v, z.physicalAttack(), z.physicalDefense(),
                z.magicalAttack(), z.magicalDefense(), z.fireResistance(), z.waterResistance(),
                z.darkResistance(), z.lightResistance(), z.extraSlots(), z.inventoryLimit(),
                z.hpRegeneration(), z.mpRegeneration(), z.movementSpeed(), z.attackSpeed(),
                z.castSpeed(), z.hitRecovery(), z.jumpPower(), z.weight(), z.statPercent());
    }

    private static CombatStats withPhysicalAttack(CombatStats z, int v) {
        return new CombatStats(z.maximumHp(), z.maximumMp(), v, z.physicalDefense(),
                z.magicalAttack(), z.magicalDefense(), z.fireResistance(), z.waterResistance(),
                z.darkResistance(), z.lightResistance(), z.extraSlots(), z.inventoryLimit(),
                z.hpRegeneration(), z.mpRegeneration(), z.movementSpeed(), z.attackSpeed(),
                z.castSpeed(), z.hitRecovery(), z.jumpPower(), z.weight(), z.statPercent());
    }

    private static CombatStats withPhysicalDefense(CombatStats z, int v) {
        return new CombatStats(z.maximumHp(), z.maximumMp(), z.physicalAttack(), v,
                z.magicalAttack(), z.magicalDefense(), z.fireResistance(), z.waterResistance(),
                z.darkResistance(), z.lightResistance(), z.extraSlots(), z.inventoryLimit(),
                z.hpRegeneration(), z.mpRegeneration(), z.movementSpeed(), z.attackSpeed(),
                z.castSpeed(), z.hitRecovery(), z.jumpPower(), z.weight(), z.statPercent());
    }

    private static CombatStats withMagicalAttack(CombatStats z, int v) {
        return new CombatStats(z.maximumHp(), z.maximumMp(), z.physicalAttack(),
                z.physicalDefense(), v, z.magicalDefense(), z.fireResistance(),
                z.waterResistance(), z.darkResistance(), z.lightResistance(), z.extraSlots(),
                z.inventoryLimit(), z.hpRegeneration(), z.mpRegeneration(), z.movementSpeed(),
                z.attackSpeed(), z.castSpeed(), z.hitRecovery(), z.jumpPower(), z.weight(),
                z.statPercent());
    }

    private static CombatStats withMagicalDefense(CombatStats z, int v) {
        return new CombatStats(z.maximumHp(), z.maximumMp(), z.physicalAttack(),
                z.physicalDefense(), z.magicalAttack(), v, z.fireResistance(),
                z.waterResistance(), z.darkResistance(), z.lightResistance(), z.extraSlots(),
                z.inventoryLimit(), z.hpRegeneration(), z.mpRegeneration(), z.movementSpeed(),
                z.attackSpeed(), z.castSpeed(), z.hitRecovery(), z.jumpPower(), z.weight(),
                z.statPercent());
    }

    private static CombatStats withResistances(CombatStats z, int v) {
        return new CombatStats(z.maximumHp(), z.maximumMp(), z.physicalAttack(),
                z.physicalDefense(), z.magicalAttack(), z.magicalDefense(),
                v, v, v, v,
                z.extraSlots(), z.inventoryLimit(), z.hpRegeneration(), z.mpRegeneration(),
                z.movementSpeed(), z.attackSpeed(), z.castSpeed(), z.hitRecovery(),
                z.jumpPower(), z.weight(), z.statPercent());
    }

    public static String describe(CombatStats bonus) {
        if (bonus.isZero()) {
            return "无";
        }
        var sb = new StringBuilder();
        append(sb, "HP", bonus.maximumHp());
        append(sb, "MP", bonus.maximumMp());
        append(sb, "力量", bonus.physicalAttack());
        append(sb, "体力", bonus.physicalDefense());
        append(sb, "智力", bonus.magicalAttack());
        append(sb, "精神", bonus.magicalDefense());
        append(sb, "四抗", bonus.fireResistance());
        append(sb, "移速", bonus.movementSpeed());
        return sb.toString();
    }

    private static void append(StringBuilder sb, String label, int value) {
        if (value != 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(label).append('+').append(value);
        }
    }
}
