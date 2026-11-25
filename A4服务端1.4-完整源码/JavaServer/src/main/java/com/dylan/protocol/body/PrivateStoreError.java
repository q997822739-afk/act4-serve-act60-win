package com.dylan.protocol.body;

public enum PrivateStoreError {

    INVENTORY_FULL(1, "物品栏空间不足"),

    NOT_ENOUGH_GOLD(4, "金币不足"),

    BAD_COUNT(10, "数量错误"),

    BAD_ITEM(17, "错误的物品"),

    ALREADY_OPEN(20, "已经开着个人商店"),

    CANNOT_BUY_OWN(21, "不能购买自己的物品"),

    CANNOT_RECALL(22, "无法收回物品"),

    TOO_CLOSE(56, "与其它个人商店距离太近"),

    BAD_STATE(57, "个人商店状态错误"),

    NO_STORE(58, "找不到个人商店"),

    BAD_STORE_STATE(59, "错误的个人商店"),

    BUSY_PRIVATE_STORE(60, "正在使用个人商店"),

    BAD_AREA(62, "此区域无法开设个人商店"),

    NO_PERMISSION(63, "没有开设个人商店的权限"),

    SELLER_GONE(70, "摊主已经离开"),

    NEAR_NPC(82, "此处无法开设个人商店");

    private final int code;

    private final String message;

    PrivateStoreError(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
