package com.dylan.game;

import com.dylan.pvf.script.ItemScript;
import com.dylan.store.blob.InvenItem;
import com.dylan.store.blob.ItemArray;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.IntFunction;

public final class Cargo {

    private Cargo() {
    }

    public static final int LAST_SLOT = ItemArray.CARGO_SLOTS - 1;

    public static final int NOT_OPERATORABLE = -2;

    public static final int NO_ROOM = -1;

    public static boolean isOperatorable(int capacity, int slot) {
        return slot >= 0 && slot < capacity && slot <= LAST_SLOT;
    }

    public static boolean checkSlotEmpty(ItemArray items, int capacity, int slot) {
        return isOperatorable(capacity, slot) && items.get(slot).isEmpty();
    }

    public static int checkItemExist(ItemArray items, long itemId) {
        for (int slot = 0; slot <= LAST_SLOT && slot < items.slots(); slot++) {
            if (items.get(slot).itemId() == itemId) {
                return slot;
            }
        }
        return -1;
    }

    public static int getEmptySlot(ItemArray items, int capacity) {
        int slot = 0;
        while (slot <= LAST_SLOT && slot < items.slots() && !items.get(slot).isEmpty()) {
            slot++;
        }
        return isOperatorable(capacity, slot) ? slot : -1;
    }

    public static int itemCount(ItemArray items) {
        int n = 0;
        for (int slot = 0; slot <= LAST_SLOT && slot < items.slots(); slot++) {
            if (!items.get(slot).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    public static int tryInsert(ItemArray items, int capacity, InvenItem item, int slot,
                                IntFunction<ItemScript> scripts) {
        if (!isOperatorable(capacity, slot)) {
            return NOT_OPERATORABLE;
        }
        if (!item.equipable()) {
            if (items.get(slot).itemId() == item.itemId()) {
                int r = tryAddStack(items, item, slot, scripts);
                if (r >= 0) {
                    return slot;
                }
                if (r == OVER_STACK_LIMIT) {
                    return NO_ROOM;
                }
            } else {
                for (int i = 0; i <= LAST_SLOT && i < items.slots(); i++) {
                    int r = tryAddStack(items, item, i, scripts);
                    if (r >= 0) {
                        return i;
                    }
                    if (r == OVER_STACK_LIMIT) {
                        return NO_ROOM;
                    }
                }
            }
        }
        return items.get(slot).isEmpty() ? slot : NO_ROOM;
    }

    public static int insert(ItemArray items, int capacity, InvenItem item, int slot,
                             IntFunction<ItemScript> scripts) {
        if (!isOperatorable(capacity, slot)) {
            return NOT_OPERATORABLE;
        }
        if (!item.equipable()) {
            if (items.get(slot).itemId() == item.itemId()) {
                int r = addStack(items, item, slot, scripts);
                if (r >= 0) {
                    return slot;
                }
                if (r == OVER_STACK_LIMIT) {
                    return NO_ROOM;
                }
            } else {
                for (int i = 0; i <= LAST_SLOT && i < items.slots(); i++) {
                    int r = tryAddStack(items, item, i, scripts);
                    if (r >= 0) {
                        return i;
                    }
                    if (r == OVER_STACK_LIMIT) {
                        return NO_ROOM;
                    }
                }
            }
        }
        if (!items.get(slot).isEmpty()) {
            return NO_ROOM;
        }
        items.set(slot, item);
        return slot;
    }

    private static int tryAddStack(ItemArray items, InvenItem item, int slot,
                                   IntFunction<ItemScript> scripts) {
        InvenItem here = items.get(slot);
        if (here.itemId() != item.itemId()) {
            return -1;
        }
        if (Integer.MAX_VALUE - item.addInfo() < here.addInfo()) {
            return -1;
        }
        if (!checkStackableLimit(scripts, here.itemId(), here.addInfo() + item.addInfo())) {
            return OVER_STACK_LIMIT;
        }
        return slot;
    }

    private static int addStack(ItemArray items, InvenItem item, int slot,
                                IntFunction<ItemScript> scripts) {
        InvenItem here = items.get(slot);
        if (here.itemId() != item.itemId()) {
            return -1;
        }
        if (Integer.MAX_VALUE - item.addInfo() >= here.addInfo()) {
            if (!checkStackableLimit(scripts, here.itemId(), here.addInfo() + item.addInfo())) {
                return OVER_STACK_LIMIT;
            }
            items.set(slot, here.withAddInfo(here.addInfo() + item.addInfo()));
        } else {
            items.set(slot, here.withAddInfo(0));
        }
        return slot;
    }

    private static boolean checkStackableLimit(IntFunction<ItemScript> scripts, long itemId,
                                               long total) {
        if (scripts == null) {
            return true;
        }
        ItemScript script = scripts.apply((int) itemId);
        if (script == null || script.stackLimit() <= 0) {
            return true;
        }
        return total <= script.stackLimit() && total >= 0;
    }

    private static final int OVER_STACK_LIMIT = -10;

    public static boolean deleteItem(ItemArray items, int capacity, int slot, long count) {
        if (!isOperatorable(capacity, slot)) {
            return false;
        }
        InvenItem here = items.get(slot);
        if (here.isEmpty() || count <= 0) {
            return false;
        }
        if (here.equipable() || here.addInfo() == count) {
            items.set(slot, InvenItem.EMPTY);
            return true;
        }
        if (here.addInfo() - count <= 0) {
            return false;
        }
        items.set(slot, here.withAddInfo(here.addInfo() - count));
        return true;
    }

    public static boolean moveItem(ItemArray items, int capacity, int slotA, int slotB) {
        if (!isOperatorable(capacity, slotA) || !isOperatorable(capacity, slotB)) {
            return false;
        }
        if (items.get(slotA).isEmpty() && items.get(slotB).isEmpty()) {
            return false;
        }
        InvenItem a = items.get(slotA);
        items.set(slotA, items.get(slotB));
        items.set(slotB, a);
        return true;
    }

    public static void sort(ItemArray items) {
        int n = Math.min(LAST_SLOT + 1, items.slots());
        InvenItem[] slice = new InvenItem[n];
        for (int i = 0; i < n; i++) {
            slice[i] = items.get(i);
        }
        Arrays.sort(slice, Comparator
                .comparingInt(Cargo::sortKey)
                .thenComparingLong(item -> item.isEmpty() ? 0 : item.itemId()));
        for (int i = 0; i < n; i++) {
            items.set(i, slice[i]);
        }
    }

    private static int sortKey(InvenItem item) {
        return item.isEmpty() ? EMPTY_SORT_KEY : item.itemType();
    }

    private static final int EMPTY_SORT_KEY = 9999;
}
