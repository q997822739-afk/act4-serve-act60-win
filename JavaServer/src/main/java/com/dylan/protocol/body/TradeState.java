package com.dylan.protocol.body;

public final class TradeState {
    public static final int UNLOCK = 0;

    public static final int LOCK = 1;

    public static final int CANCEL = 2;

    public static final int CONFIRM = 3;

    public static final int UNLOCK_BOTH = 4;

    private TradeState() {
    }

    public static String name(int state) {
        return switch (state) {
            case UNLOCK -> "解除锁定";
            case LOCK -> "锁定";
            case CANCEL -> "取消交易";
            case CONFIRM -> "确认成交";
            case UNLOCK_BOTH -> "双方解锁";
            default -> "未知(" + state + ")";
        };
    }
}
