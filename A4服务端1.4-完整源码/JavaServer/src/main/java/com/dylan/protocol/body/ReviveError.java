package com.dylan.protocol.body;

public final class ReviveError {

    public static final int FAILED = 1;

    public static final int NO_COIN = 17;

    public static final int NOT_DEAD = 18;

    public static final int WRONG_STATE = 19;

    public static final int NO_SUCH_MEMBER = 21;

    public static final int COIN_LIMIT = 22;

    public static final int SENTINEL_SEND_NOTHING = 0x7FFF_FFFF;

    private ReviveError() {
    }

    public static String name(int code) {
        return switch (code) {
            case FAILED -> "兜底失败";
            case NO_COIN -> "没有复活币";
            case NOT_DEAD -> "他没死";
            case WRONG_STATE -> "状态不对";
            case NO_SUCH_MEMBER -> "队伍名单里没这个人";
            case COIN_LIMIT -> "本局复活次数用光了";
            case SENTINEL_SEND_NOTHING -> "哨兵：一个字节都不发";
            default -> "未知码 " + code;
        };
    }
}
