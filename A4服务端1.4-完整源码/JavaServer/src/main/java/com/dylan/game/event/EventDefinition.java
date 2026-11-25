package com.dylan.game.event;

import java.util.ArrayList;
import java.util.List;

public record EventDefinition(
        int no,
        String officialClass,
        int applyType,
        String label,
        String paramMeaning,
        int defaultParam1,
        int defaultParam2) {

    public static final int ONE_PER_BYTE_HALF = 0x0101;

    private static final EventDefinition[] TABLE = {
            new EventDefinition(EventNo.UNLIMIT_FATIGUE, "CUnlimitFatigueEvent", 0,
                    "无限疲劳值", "", 0, 0),
            new EventDefinition(EventNo.MAX_FATIGUE_FACTOR, "CMaxFatigueFactorEvent", 1,
                    "最大疲劳值", "百分比（100 = 原样，200 = 上限翻倍）", 100, 0),
            new EventDefinition(EventNo.EXP_DOUBLE, "CExpDoubleEvent", 1,
                    "加倍经验值", "百分比（100 = 额外再给一倍）", 100, 0),
            new EventDefinition(EventNo.COIN_PER_DAY, "CCoinEventPerDay", 4,
                    "复活币每日赠送",
                    "四个 u8：param1 装「1~17 级|18~26 级」，param2 装「27 级以上|未使用」",
                    ONE_PER_BYTE_HALF, ONE_PER_BYTE_HALF),
            new EventDefinition(EventNo.COIN_ON_CHAR_CREATE, "CCoinEventOnCharCreate", 1,
                    "新建角色赠送复活币", "复活币数", 5, 0),
            new EventDefinition(EventNo.ITEM_DROP_RATIO, "CItemDropRatioEvent", 1,
                    "物品掉落率加倍", "倍数（2 = 两倍）", 1, 0),
            new EventDefinition(EventNo.PC_ROOM_BURNING, "CPCRoomBurningEvent", 1,
                    "网吧疯狂时间",
                    "百分比，但官方是整数除 100（100 = 再给一倍，150 也是一倍，200 = 两倍）",
                    100, 0),
            new EventDefinition(EventNo.BURNING_FATIGUE, "CBurningFatigueEvent", 0,
                    "疯狂疲劳值", "", 0, 0),
            new EventDefinition(EventNo.CLEAR_REWARD_CARD, "CClearRewardCardEvent", 0,
                    "多送一张黄金卡牌", "", 0, 0),
            new EventDefinition(EventNo.PARTY_EXP_BONUS, "CPartyExpBonusEvent", 1,
                    "组队经验", "百分比（额外加成，0 = 没有）", 0, 0),
            new EventDefinition(EventNo.GOLDEN_GOBLIN, "CBurnigGoldMonsterEvent", 0,
                    "黄金哥布林", "", 0, 0),
            new EventDefinition(EventNo.FREE_GUILD_CREATE, "CNoNeedGoldOnGuildCreateEvent", 0,
                    "免费创建公会", "", 0, 0),
            new EventDefinition(EventNo.DEATH_TOWER_WIN_POINT, "CDeathTowerWinPointEvent", 1,
                    "死亡/迷妄之塔胜点", "百分比（额外加成，0 = 没有）", 0, 0),
            new EventDefinition(EventNo.CHARACTER_DAY, "CCharacterDayEvent", 1,
                    "角色日", "职业号 0 鬼剑士 / 1 格斗家 / 2 神枪手 / 3 魔法师 / 4 圣职者", 0, 0),
            new EventDefinition(EventNo.REDUCE_UPGRADE_PAY, "CReduceUpgradeItemPay", 1,
                    "强化费用折扣", "折扣百分比（30 = 便宜三成）", 0, 0),
            new EventDefinition(EventNo.WEEKEND_BONUS, "CWeekendBonusEvent", 0,
                    "周末疲劳奖励", "", 0, 0),
            new EventDefinition(EventNo.STOP_OVERLAP_EXP, "CStopOverlabExpEvent", 0,
                    "成长秘方经验不叠加", "", 0, 0),
    };

    private static final EventDefinition[] BY_NO = new EventDefinition[EventNo.SLOT_COUNT];

    static {
        for (EventDefinition d : TABLE) {
            if (BY_NO[d.no()] != null) {
                throw new IllegalStateException("活动号 " + d.no() + " 定义了两遍");
            }
            BY_NO[d.no()] = d;
        }
    }

    public static EventDefinition of(int no) {
        return no < 0 || no > EventNo.MAX_EVENT_NO ? null : BY_NO[no];
    }

    public static int count() {
        return TABLE.length;
    }

    public static List<EventDefinition> all() {
        return List.of(TABLE);
    }

    public static List<Integer> numbers() {
        List<Integer> out = new ArrayList<>(TABLE.length);
        for (EventDefinition d : TABLE) {
            out.add(d.no());
        }
        return out;
    }

    public boolean needsParam() {
        return applyType != 0;
    }

    public String describe() {
        return no + " [" + label + "] " + officialClass;
    }
}
