package com.dylan.pvf.script;

public enum ItemDropType {

    GOLD,

    CONSUMABLE,

    EQUIPMENT,

    RECIPE,

    ARTIFACT;

    public static final ItemDropType[] ITEMS = {CONSUMABLE, EQUIPMENT, RECIPE, ARTIFACT};

    public static final int COUNT = 5;

    public static boolean accepts(ItemDropType type, ItemScript item) {
        if (item.isStackableItem()) {
            if (item.stackableType() == ItemScript.STACKABLE_RECIPE && type == RECIPE) {
                return true;
            }
            return type == CONSUMABLE;
        }
        if (item.isArtifact() && type == ARTIFACT) {
            return true;
        }
        return type == EQUIPMENT;
    }
}
