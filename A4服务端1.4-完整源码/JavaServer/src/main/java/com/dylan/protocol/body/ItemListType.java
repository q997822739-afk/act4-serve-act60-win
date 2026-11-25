package com.dylan.protocol.body;

public final class ItemListType {
    private ItemListType() {
    }

    public static final int LIST_MAIN = 0;

    public static final int LIST_AVATAR = 1;

    public static final int LIST_CARGO = 2;

    public static final int LIST_WITH_SUBVALUE = LIST_CARGO;

    public static final int LIST_EQUIPPED = 3;

    public static final int LIST_TRADE = 4;

    public static final int LIST_CREATURE = 7;
}
