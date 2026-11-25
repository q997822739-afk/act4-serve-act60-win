package com.dylan.protocol.body;

public enum CashShopError {
    BAD_REQUEST(1, 264, "请求购买信息错误"),

    CANNOT_UPGRADE(18, 0, "无法继续扩充"),

    INVENTORY_FULL(4, 255, "物品栏空间不足"),

    NOT_ENOUGH_GOLD(10, 256, "金币不足"),

    NOT_ENOUGH_CERA(11, 258, "点券不足"),

    NOT_ENOUGH_MEDAL(12, 257, "胜点不足"),

    LEVEL_TOO_LOW(13, 259, "Lv不足"),

    NO_SUCH_ITEM(17, 260, "不存在的物品"),

    BAD_PERIOD(19, 261, "使用期限设定错误"),

    NOT_PURCHASABLE(20, 253, "无法购买的物品"),

    UNKNOWN_COMMODITY(21, 254, "错误的商品编号"),

    NOT_AVATAR_TYPE(23, 262, "物品功能设定错误"),

    BILLING_ERROR(25, 263, "Billing服务器错误"),

    PVP_CHANNEL(7, 44032, "在决斗场频道中无法购买[遗忘之契约]");

    private final int code;

    private final int messageId;

    private final String message;

    CashShopError(int code, int messageId, String message) {
        this.code = code;
        this.messageId = messageId;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
