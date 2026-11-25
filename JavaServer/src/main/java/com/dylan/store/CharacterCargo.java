package com.dylan.store;

import com.dylan.store.blob.ItemArray;

public record CharacterCargo(int characNo, int capacity, ItemArray items) {

    public static final int DEFAULT_CAPACITY = 8;

    public CharacterCargo {
        if (items == null) {
            throw new IllegalArgumentException("金库数组不能为 null");
        }
    }

    public static CharacterCargo empty(int characNo) {
        return new CharacterCargo(characNo, 0, ItemArray.emptyCargo());
    }

    public int usableSlots() {
        return capacity > 0 ? Math.min(capacity, items.slots()) : DEFAULT_CAPACITY;
    }
}
