package com.dylan.store.blob;

public enum ItemType {

    TYPE_0(0),
    EQUIPMENT(1),
    TYPE_2(2),
    TYPE_3(3),
    TYPE_4(4),
    CREATURE_EQUIP(5),
    CREATURE_6(6),
    CREATURE_7(7),
    AVATAR(8),
    TYPE_9(9),
    TYPE_10(10);

    public static final int MAX_VALUE = 10;

    private static final ItemType[] BY_VALUE = new ItemType[MAX_VALUE + 1];

    static {
        for (ItemType t : values()) {
            BY_VALUE[t.value] = t;
        }
    }

    private final int value;

    ItemType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static ItemType of(int value) {
        return value >= 0 && value <= MAX_VALUE ? BY_VALUE[value] : null;
    }
}
