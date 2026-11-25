package com.dylan.store;

public record CreatureItemRow(long uiId, int characNo, int slot, int itemId, String name,
                              int stomach, long exp, int level, int endurance,
                              int creatureType, int noCharge, int stat) {

    public static final long NOT_PERSISTED = 0L;

    public static final int EQUIP_SLOT_BASE = 216;

    public static final int FULL_STOMACH = 100;

    public static final int TYPE_CREATURE = 1;

    public static final int STAT_OWNED = 0;

    public static final int NAME_MAX = 12;

    public boolean equipped() {
        return slot >= EQUIP_SLOT_BASE;
    }

    public int equipSlot() {
        return equipped() ? slot - EQUIP_SLOT_BASE : -1;
    }

    public int bagSlot() {
        return equipped() ? -1 : slot;
    }

    public static int ofEquipSlot(int equipSlot) {
        return EQUIP_SLOT_BASE + equipSlot;
    }

    public CreatureItemRow withSlot(int newSlot) {
        return new CreatureItemRow(uiId, characNo, newSlot, itemId, name, stomach, exp, level,
                endurance, creatureType, noCharge, stat);
    }

    public CreatureItemRow withExp(long newExp) {
        return new CreatureItemRow(uiId, characNo, slot, itemId, name, stomach, newExp, level,
                endurance, creatureType, noCharge, stat);
    }

    public CreatureItemRow withStomach(int newStomach) {
        return new CreatureItemRow(uiId, characNo, slot, itemId, name, newStomach, exp, level,
                endurance, creatureType, noCharge, stat);
    }

    public CreatureItemRow withEvolved(int evolvedItemId) {
        return new CreatureItemRow(uiId, characNo, slot, evolvedItemId, name, stomach, exp,
                level, endurance, creatureType, noCharge, stat);
    }

    public CreatureItemRow withName(String newName) {
        return new CreatureItemRow(uiId, characNo, slot, itemId, newName, stomach, exp, level,
                endurance, creatureType, noCharge, stat);
    }

    public CreatureItemRow withHatched(int hatchedItemId) {
        return new CreatureItemRow(uiId, characNo, slot, hatchedItemId, name, FULL_STOMACH,
                exp, level, endurance, TYPE_CREATURE, noCharge, stat);
    }

    public static CreatureItemRow ofNew(int characNo, int slot, int itemId, int creatureType) {
        return new CreatureItemRow(NOT_PERSISTED, characNo, slot, itemId, "", FULL_STOMACH,
                0L, 0, 0, creatureType, 0, STAT_OWNED);
    }

    public CreatureItemRow withUiId(long newUiId) {
        return new CreatureItemRow(newUiId, characNo, slot, itemId, name, stomach, exp, level,
                endurance, creatureType, noCharge, stat);
    }

    public boolean dead() {
        return stomach < 1;
    }
}
