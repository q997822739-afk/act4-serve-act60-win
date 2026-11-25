package com.dylan.game;

import com.dylan.protocol.body.CombatStats;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.CharacterScript;

public final class StatService {

    public static final int SCALE = 10;

    public static final int BASIC_GROWTH_LEVELS = 14;

    public static final int BRANCH_GROWTH_LEVELS = 49;

    public static final int MAX_FIRST_GROW = 5;

    public static final int MAX_SECOND_GROW = 2;

    private StatService() {
    }

    public static CombatStats forCharacter(GameScripts scripts, int job, int growType,
                                           int level) {
        if (scripts == null) {
            return CombatStats.defaults();
        }
        var script = scripts.characterScript(job);
        if (script.isEmpty()) {
            return CombatStats.defaults();
        }
        return build(script.get(), growType, level);
    }

    public static CombatStats build(CharacterScript script, int growType, int level) {
        return build(script, firstGrow(growType), secondGrow(growType), level);
    }

    public static CombatStats build(CharacterScript script, int firstGrow, int secondGrow,
                                    int level) {
        var base = script.initialValue();
        var basic = script.growthForGrowType(0);
        var branch = script.growthForGrowType(firstGrow);
        var awakened = secondGrow > 0
                ? script.awakeningForGrowType(firstGrow, secondGrow)
                : branch;
        return build(base, basic, branch, awakened, level);
    }

    public static CombatStats build(CharacterScript.StatBlock base,
                                    CharacterScript.StatBlock basic,
                                    CharacterScript.StatBlock branch,
                                    CharacterScript.StatBlock awakened,
                                    int level) {
        int total = Math.max(1, level) - 1;
        int basicLevels = Math.min(total, BASIC_GROWTH_LEVELS);
        int branchLevels = Math.min(total, BRANCH_GROWTH_LEVELS) - basicLevels;
        int awakenedLevels = total - basicLevels - branchLevels;

        return new CombatStats(
                scaled(base, basic, branch, awakened, "hp max",
                        basicLevels, branchLevels, awakenedLevels),
                scaled(base, basic, branch, awakened, "mp max",
                        basicLevels, branchLevels, awakenedLevels),
                scaled(base, basic, branch, awakened, "physical attack",
                        basicLevels, branchLevels, awakenedLevels),
                scaled(base, basic, branch, awakened, "physical defense",
                        basicLevels, branchLevels, awakenedLevels),
                scaled(base, basic, branch, awakened, "magical attack",
                        basicLevels, branchLevels, awakenedLevels),
                scaled(base, basic, branch, awakened, "magical defense",
                        basicLevels, branchLevels, awakenedLevels),
                scaled(base, basic, branch, awakened, "fire resistance",
                        basicLevels, branchLevels, awakenedLevels),
                scaled(base, basic, branch, awakened, "water resistance",
                        basicLevels, branchLevels, awakenedLevels),
                scaled(base, basic, branch, awakened, "dark resistance",
                        basicLevels, branchLevels, awakenedLevels),
                scaled(base, basic, branch, awakened, "light resistance",
                        basicLevels, branchLevels, awakenedLevels),
                CombatStats.NO_EXTRA_SLOTS,
                scaled(base, basic, branch, awakened, "inventory limit",
                        basicLevels, branchLevels, awakenedLevels),
                scaled(base, basic, branch, awakened, "hp regen speed",
                        basicLevels, branchLevels, awakenedLevels),
                scaled(base, basic, branch, awakened, "mp regen speed",
                        basicLevels, branchLevels, awakenedLevels),
                scaled(base, basic, branch, awakened, "move speed",
                        basicLevels, branchLevels, awakenedLevels),
                scaled(base, basic, branch, awakened, "attack speed",
                        basicLevels, branchLevels, awakenedLevels),
                scaled(base, basic, branch, awakened, "cast speed",
                        basicLevels, branchLevels, awakenedLevels),
                scaled(base, basic, branch, awakened, "hit recovery",
                        basicLevels, branchLevels, awakenedLevels),
                scaled(base, basic, branch, awakened, "jump power",
                        basicLevels, branchLevels, awakenedLevels),
                scaled(base, basic, branch, awakened, "weight",
                        basicLevels, branchLevels, awakenedLevels),
                CombatStats.NEUTRAL_STAT_PERCENT);
    }

    public static int firstGrow(int growType) {
        int nibble = (byte) (growType << 4) >> 4;
        if (nibble < 0) {
            return 0;
        }
        return Math.min(nibble, MAX_FIRST_GROW);
    }

    public static int secondGrow(int growType) {
        int bits = (byte) (growType << 1) >> 5;
        if (bits < 0) {
            return 0;
        }
        return Math.min(bits, MAX_SECOND_GROW);
    }

    public static int packGrowType(int firstGrow, int secondGrow) {
        return ((secondGrow & 0x7) << 4) | (firstGrow & 0xF);
    }

    private static int scaled(CharacterScript.StatBlock base,
                              CharacterScript.StatBlock basic,
                              CharacterScript.StatBlock branch,
                              CharacterScript.StatBlock awakened,
                              String key, int basicLevels, int branchLevels,
                              int awakenedLevels) {
        double value = base.get(key)
                + basic.get(key) * basicLevels
                + branch.get(key) * branchLevels
                + awakened.get(key) * awakenedLevels;
        return (int) Math.round(value * SCALE);
    }

    public static String describe(CombatStats s) {
        return "HP=" + s.maximumHp() + "（界面 " + s.maximumHp() / SCALE + "）"
                + " MP=" + s.maximumMp() + "（界面 " + s.maximumMp() / SCALE + "）"
                + " 力量=" + s.physicalAttack() / SCALE
                + " 体力=" + s.physicalDefense() / SCALE
                + " 智力=" + s.magicalAttack() / SCALE
                + " 精神=" + s.magicalDefense() / SCALE
                + " 移速=" + s.movementSpeed() + "（界面 " + s.movementSpeed() / SCALE + "）"
                + " 攻速=" + s.attackSpeed() / SCALE
                + " 施法=" + s.castSpeed() / SCALE
                + " 硬直=" + s.hitRecovery() / SCALE + "（界面该显示这个数）"
                + " 跳跃=" + s.jumpPower() / SCALE
                + " 负重=" + s.weight() / SCALE
                + " 背包=" + s.inventoryLimit() / SCALE
                + " HP恢复=" + Math.round(s.hpRegeneration() * 0.06)
                + " MP恢复=" + Math.round(s.mpRegeneration() * 0.06)
                + "（这两个界面显示 raw×0.06）";
    }
}
