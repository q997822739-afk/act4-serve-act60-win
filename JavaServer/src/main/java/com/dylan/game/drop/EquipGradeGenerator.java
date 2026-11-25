package com.dylan.game.drop;

import java.util.random.RandomGenerator;

import com.dylan.protocol.body.EquipmentSlot;
import com.dylan.pvf.script.ItemScript;

public final class EquipGradeGenerator {
    private EquipGradeGenerator() {
    }

    public static final int[] GRADE_CODES = {23207, 4006, 27171, 24048, 29296};

    public static final int RAND_MAX = Integer.MAX_VALUE;

    public static int generate(RandomGenerator random) {
        return generate(random, 0);
    }

    public static int generate(RandomGenerator random, int levelGap) {
        return random.nextInt() & RAND_MAX;
    }

    public static boolean hasGrade(ItemScript script) {
        if (script == null || script.isStackableItem()) {
            return false;
        }
        int slot = script.equipmentSlot();
        return slot >= EquipmentSlot.WEAPON && slot < EquipmentSlot.CREATURE;
    }

    public static long addInfoFor(ItemScript script, RandomGenerator random,
                                  int levelGap, long fallback) {
        return hasGrade(script) ? generate(random, levelGap) : fallback;
    }
}
