package com.dylan.game;

import com.dylan.pvf.script.ItemScript;

public final class RepairCost {

    private RepairCost() {
    }

    public static final double RATE = 0.1f;

    public static final int GRADE_BIAS = 5;

    public static final int PRICE_DIVISOR = 10;

    public static long of(ItemScript script, int endurance, double upgradeRate) {
        int max = script.durability();
        int current = Math.min(Math.max(0, endurance), max);
        if (max <= 0 || current == max) {
            return 0;
        }
        int base = script.price() * (script.grade() + GRADE_BIAS) / PRICE_DIVISOR;
        double v = (double) base * RATE / max * (max - current);
        return (long) (float) (v * upgradeRate);
    }

    public static String explain(ItemScript script, int endurance, double upgradeRate) {
        long byPrice = of(script, endurance, upgradeRate);
        int max = script.durability();
        int current = Math.min(Math.max(0, endurance), max);
        long byValue = 0;
        if (max > 0 && current != max) {
            int base = script.shopBasePrice() * (script.grade() + GRADE_BIAS) / PRICE_DIVISOR;
            byValue = (long) (float) ((double) base * RATE / max * (max - current) * upgradeRate);
        }
        return "[price]=" + script.price() + " [value]优先=" + script.shopBasePrice()
                + " [grade]=" + script.grade() + " 耐久=" + current + "/" + max
                + " 强化倍率=" + upgradeRate
                + " → " + byPrice + (byPrice == byValue ? "" : "（若按 [value] 算是 " + byValue + "）");
    }
}
