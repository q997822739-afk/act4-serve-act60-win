package com.dylan.protocol.body;

public final class EnchantError {
    private EnchantError() {
    }

    public static final int NONE = 0;

    /**
     * 没有附魔会话（客户端没开附魔窗口 / 已经关了）。
     *
     * <p>依据：A4 官方实抓（sweep_town CMD 180 包体 {@code 01000000…}、CMD 179 包体 {@code 02000000…}，都没开附魔）
     * 回 REPLY 179 / 180 都是 {@code 00 01}；A5 的 ACT5 错误码表里没有 1，含义按「没有会话」记，照官方字节抄。
     * 客户端 CMD 179 的读包函数 0041BF6D 只读一个 u8 错误码。
     */
    public static final int NO_SESSION = 1;

    public static final int NOT_YOURS = 3;

    public static final int WRONG_MATERIAL = 17;

    public static final int SLOT_TAKEN = 20;

    public static final int NOT_IN_INVENTORY = 21;

    public static final int NO_SESSION_OR_NO_MONEY = 22;

    public static final int WRONG_TYPE = 23;

    public static final int NOT_ENOUGH_MONEY = 95;

    public static String name(int error) {
        return switch (error) {
            case NONE -> "成功";
            case NO_SESSION -> "没有附魔会话(1)";
            case NOT_YOURS -> "不是该你操作的(3)";
            case WRONG_MATERIAL -> "材料对不上(17)";
            case SLOT_TAKEN -> "那个槽已经放过东西了(20)";
            case NOT_IN_INVENTORY -> "东西不在背包里(21)";
            case NO_SESSION_OR_NO_MONEY -> "没有会话/钱不够(22)";
            case WRONG_TYPE -> "类型或部位不对(23)";
            case NOT_ENOUGH_MONEY -> "金额不合法(95)";
            default -> "未知(" + error + ")";
        };
    }
}
