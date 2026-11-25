package com.dylan.game;

import com.dylan.store.blob.ItemArray;
import com.dylan.store.blob.InvenItem;

public final class CreatureBagLayout {
    private CreatureBagLayout() {
    }

    public static final int SLOTS = 242;

    public static final int LAST_SLOT = 237;

    public record Section(int firstSlot, int lastSlot, int itemType, String label) {
        public boolean contains(int slot) {
            return slot >= firstSlot && slot <= lastSlot;
        }

        public int slots() {
            return lastSlot - firstSlot + 1;
        }
    }

    public static final Section PET = new Section(0, 139, 5, "宠物");

    public static final Section ARTIFACT = new Section(140, 188, 6, "宠物装备");

    public static final Section SUPPLY = new Section(189, 237, 7, "宠物用品");

    public static final Section[] SECTIONS = {PET, ARTIFACT, SUPPLY};

    public static Section sectionOf(int itemType) {
        for (Section s : SECTIONS) {
            if (s.itemType() == itemType) {
                return s;
            }
        }
        return null;
    }

    public static Section sectionAt(int slot) {
        for (Section s : SECTIONS) {
            if (s.contains(slot)) {
                return s;
            }
        }
        return null;
    }

    public static int firstFree(ItemArray bag, int itemType) {
        Section s = sectionOf(itemType);
        if (bag == null || s == null) {
            return -1;
        }
        int limit = Math.min(s.lastSlot(), bag.slots() - 1);
        for (int slot = s.firstSlot(); slot <= limit; slot++) {
            if (bag.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    public static int relocate(ItemArray bag) {
        if (bag == null) {
            return 0;
        }
        int moved = 0;
        int limit = Math.min(LAST_SLOT, bag.slots() - 1);
        for (int slot = 0; slot <= limit; slot++) {
            InvenItem item = bag.get(slot);
            if (item.isEmpty()) {
                continue;
            }
            Section here = sectionAt(slot);
            Section home = sectionOf(item.itemType());
            if (home == null || home == here) {
                continue;
            }
            int target = firstFree(bag, item.itemType());
            if (target < 0) {
                continue;
            }
            bag.set(target, item);
            bag.set(slot, InvenItem.EMPTY);
            moved++;
        }
        return moved;
    }

    public static int firstOf(ItemArray bag, int itemType, long itemId) {
        Section s = sectionOf(itemType);
        if (bag == null || s == null) {
            return -1;
        }
        int limit = Math.min(s.lastSlot(), bag.slots() - 1);
        for (int slot = s.firstSlot(); slot <= limit; slot++) {
            InvenItem cur = bag.get(slot);
            if (!cur.isEmpty() && cur.itemId() == itemId) {
                return slot;
            }
        }
        return -1;
    }
}
