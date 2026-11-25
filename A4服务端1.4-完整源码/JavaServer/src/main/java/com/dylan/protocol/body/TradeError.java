package com.dylan.protocol.body;

public final class TradeError {

    public static final int FAILED = 1;

    public static final int PEER_BAG_FULL = 4;

    public static final int BAD_STATE = 7;

    public static final int CANNOT_MOVE = Cmd19ReplyBody.ERROR_CANNOT_MOVE;

    public static final int IN_TRADE = 19;

    public static final int TRADE_SPACE_EMPTY = 21;

    public static final int CANNOT_REGIST = 94;

    private TradeError() {
    }

    public static String name(int code) {
        return switch (code) {
            case FAILED -> "泛用失败";
            case PEER_BAG_FULL -> "对方背包放不下";
            case BAD_STATE -> "锁定状态不对";
            case CANNOT_MOVE -> "动不了这件东西";
            case IN_TRADE -> "正在交易中";
            case TRADE_SPACE_EMPTY -> "交易位已经空了";
            case CANNOT_REGIST -> "放不进交易位";
            default -> "错误码 " + code;
        };
    }
}
