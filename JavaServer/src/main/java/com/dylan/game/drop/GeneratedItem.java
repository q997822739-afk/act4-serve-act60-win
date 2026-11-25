package com.dylan.game.drop;

public record GeneratedItem(int itemId, long addInfo, int durability) {

    public static GeneratedItem money(long amount) {
        return new GeneratedItem(0, amount, 0);
    }

    public static GeneratedItem item(int itemId, long addInfo, int durability) {
        return new GeneratedItem(itemId, addInfo, durability);
    }

    public boolean isMoney() {
        return itemId == 0;
    }

    @Override
    public String toString() {
        if (isMoney()) {
            return "金币 " + addInfo;
        }
        return "物品 " + itemId + (addInfo > 1 ? " x" + addInfo : "");
    }
}
