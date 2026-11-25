package com.dylan.game;

import com.dylan.pvf.script.ItemScript;

public final class BagLayout {
    private BagLayout() {
    }

    public static final int FIRST_ITEM_SLOT = 3;

    public static final int SLOT_GOLD = 0;

    public static final int SLOT_COIN = 1;

    public static final int SLOT_WIN_POINT = 2;

    public static final int QUICK_FIRST = 3;

    public static final int QUICK_LAST = 8;

    public static final Section TAB1 = new Section(9, 40, 0, "装备");

    public static final Section TAB2 = new Section(41, 72, 1, "消耗品");

    public static final Section TAB3 = new Section(73, 104, 2, "材料");

    public static final Section TAB4 = new Section(105, 136, 3, "任务");

    public static final Section TAB5 = new Section(137, 168, 4, "副职业材料");

    public static final Section[] TABS = {TAB1, TAB2, TAB3, TAB4, TAB5};

    public static final int LAST_TAB_SLOT = 168;

    public record Section(int firstSlot, int lastSlot, int tab, String label) {
        public boolean contains(int slot) {
            return slot >= firstSlot && slot <= lastSlot;
        }

        public int slots() {
            return lastSlot - firstSlot + 1;
        }
    }

    public static final Section QUICK = new Section(QUICK_FIRST, QUICK_LAST, -1, "快捷栏");

    public static boolean isCurrencySlot(int slot) {
        return slot >= 0 && slot < FIRST_ITEM_SLOT;
    }

    public static Section tabAt(int slot) {
        for (Section s : TABS) {
            if (s.contains(slot)) {
                return s;
            }
        }
        return null;
    }

    public static Section of(ItemScript script) {
        if (script == null) {
            return TAB2;
        }
        return switch (script.stackableType()) {
            case ItemScript.STACKABLE_TYPE_NONE -> TAB1;
            case ItemScript.STACKABLE_QUEST -> TAB4;
            case ItemScript.STACKABLE_MATERIAL -> TAB3;
            case ItemScript.STACKABLE_MATERIAL_EXPERT_JOB -> TAB5;
            default -> TAB2;
        };
    }

    public static int allocate(ItemScript script, java.util.function.IntPredicate occupied) {
        Section home = of(script);
        boolean consumable = home == TAB2;

        Section first = consumable ? QUICK : home;
        Section second = consumable ? home : QUICK;
        for (Section s : new Section[]{first, second}) {
            int slot = firstFree(s, occupied);
            if (slot >= 0) {
                return slot;
            }
        }
        return -1;
    }

    public static Section[] stackSections(ItemScript script) {
        return new Section[]{QUICK, of(script)};
    }

    public static int freeSlots(Section section, java.util.function.IntPredicate occupied) {
        int free = 0;
        for (int slot = section.firstSlot(); slot <= section.lastSlot(); slot++) {
            if (!occupied.test(slot)) {
                free++;
            }
        }
        return free;
    }

    public static boolean fits(java.util.Map<Section, Integer> needBySection,
                               java.util.function.IntPredicate occupied) {
        int shortfall = 0;
        for (var e : needBySection.entrySet()) {
            shortfall += Math.max(0, e.getValue() - freeSlots(e.getKey(), occupied));
        }
        return shortfall <= freeSlots(QUICK, occupied);
    }

    private static int firstFree(Section s, java.util.function.IntPredicate occupied) {
        for (int slot = s.firstSlot(); slot <= s.lastSlot(); slot++) {
            if (!occupied.test(slot)) {
                return slot;
            }
        }
        return -1;
    }
}
