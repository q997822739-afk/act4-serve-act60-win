package com.dylan.store;

import com.dylan.store.blob.ItemArray;

public record CharacterInventory(
        int characNo,
        long money,
        long coin,
        long payCoin,
        long eventCoin,
        ItemArray equipslot,
        ItemArray inventory,
        ItemArray creature) {

    public CharacterInventory {
        if (equipslot == null || inventory == null || creature == null) {
            throw new IllegalArgumentException("三个物品数组都不能为 null");
        }
    }

    public static CharacterInventory empty(int characNo) {
        return new CharacterInventory(characNo, 0, 0, 0, 0,
                ItemArray.emptyEquipslot(),
                ItemArray.emptyInventory(),
                ItemArray.emptyCreature());
    }
}
