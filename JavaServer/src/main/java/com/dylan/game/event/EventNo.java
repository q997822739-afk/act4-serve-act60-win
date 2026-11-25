package com.dylan.game.event;

public final class EventNo {
    private EventNo() {
    }

    public static final int MAX_EVENT_NO = 165;

    public static final int SLOT_COUNT = MAX_EVENT_NO + 1;

    public static final int UNLIMIT_FATIGUE = 1;

    public static final int MAX_FATIGUE_FACTOR = 2;

    public static final int EXP_DOUBLE = 3;

    public static final int COIN_PER_DAY = 4;

    public static final int COIN_ON_CHAR_CREATE = 5;

    public static final int ITEM_DROP_RATIO = 7;

    public static final int PC_ROOM_BURNING = 8;

    public static final int BURNING_FATIGUE = 15;

    public static final int CLEAR_REWARD_CARD = 16;

    public static final int PARTY_EXP_BONUS = 24;

    public static final int GOLDEN_GOBLIN = 36;

    public static final int FREE_GUILD_CREATE = 38;

    public static final int DEATH_TOWER_WIN_POINT = 39;

    public static final int CHARACTER_DAY = 41;

    public static final int REDUCE_UPGRADE_PAY = 50;

    public static final int WEEKEND_BONUS = 87;

    public static final int STOP_OVERLAP_EXP = 102;

    public record ClientText(int no, String text, int openStrId, int closeStrId) {
    }

    private static final ClientText[] CLIENT_TEXTS = {
            new ClientText(1, "正在进行[无限疲劳值]活动", 3070, 3078),
            new ClientText(2, "正在进行[最大疲劳值]活动", 3071, 3079),
            new ClientText(3, "正在进行[加倍经验值]活动", 3072, 3080),
            new ClientText(4, "正在进行[复活币每日赠送]活动", 3073, 3081),
            new ClientText(5, "正在进行[新建角色赠送物品]活动", 3074, 3082),
            new ClientText(6, "正在进行[引导频道]活动", 3075, 3083),
            new ClientText(7, "正在进行[物品掉落率加倍]活动", 3076, 3084),
            new ClientText(8, "正在进行[网吧疯狂时间]活动", 3077, 3085),
            new ClientText(9, "超级学校对战", 25029, 25030),
            new ClientText(10, "正在进行[网吧无限疲劳值]活动", 3088, 3089),
            new ClientText(12, "正在进行[土罐改版]活动", 3090, 3091),
            new ClientText(15, "正在进行[疯狂疲劳值]活动", 3092, 3093),
            new ClientText(16, "正在进行[多送一张黄金卡牌]活动", 3140, 3141),
            new ClientText(17, "正在进行[商城奖励]活动", 3142, 3143),
            new ClientText(19, "黄金卡牌活动已经开始", 27001, 27002),
            new ClientText(24, "开始进行[组队经验]活动", 13022, 13023),
            new ClientText(25, "已开启快速组队奖励经验活动", 13027, 13028),
            new ClientText(29, "网吧卡牌活动开始了", 13045, 13046),
            new ClientText(30, "势力战活动开始了", 19103, 19104),
            new ClientText(32, "刺杀周活动（实战玩家的格斗）", 19111, 19112),
            new ClientText(33, "公会战", 3086, 3087),
            new ClientText(36, "开始黄金哥布林活动！", 13058, 13059),
            new ClientText(38, "开始免费创建公会活动！", 42140, 42141),
            new ClientText(39, "开始死亡/迷妄之塔的胜点活动！", 42142, 42143),
            new ClientText(40, "开始 COF 指数活动！", 21470, 21471),
            new ClientText(41, "角色日活动", 44000, 44001),
            new ClientText(42, "防止街头争霸活动", 19167, 19168),
            new ClientText(43, "疲劳度 Buff 活动", 42176, 42177),
    };

    public static final int CLIENT_TEXT_COUNT = 28;

    private static final ClientText[] BY_NO = new ClientText[SLOT_COUNT];

    static {
        for (ClientText t : CLIENT_TEXTS) {
            BY_NO[t.no()] = t;
        }
        if (CLIENT_TEXTS.length != CLIENT_TEXT_COUNT) {
            throw new IllegalStateException(
                    "客户端文案表有 " + CLIENT_TEXTS.length + " 条，和实证的 "
                            + CLIENT_TEXT_COUNT + " 条对不上");
        }
    }

    public static ClientText clientText(int no) {
        return no < 0 || no > MAX_EVENT_NO ? null : BY_NO[no];
    }

    public static boolean clientSpeaks(int no) {
        return clientText(no) != null;
    }

    public static ClientText[] clientTexts() {
        return CLIENT_TEXTS.clone();
    }
}
