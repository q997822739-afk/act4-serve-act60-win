package com.dylan.game;

public final class HackType {
    private HackType() {
    }

    public static final int KILL_MONSTER_OVER_DAMAGE = 107;

    public static final int KILL_MONSTER_FIRST_ATTACKER = 109;

    public static final int KILL_MONSTER_ZERO_HIT_COUNT = 110;

    public static final int KILL_MONSTER_HIT_OVERFLOW = 111;

    public static final int KILL_MONSTER_ZERO_HIT = 127;

    public static final int KILL_MONSTER_UNKNOWN_ID = 205;

    public static final int KILL_MONSTER_UNKNOWN_ID_OUTSIDE_BOSS = 206;

    public static final int KILL_MONSTER_POSITION_SPREAD = 209;

    public static final int FIX_MOB_DIE_POSITION = 210;

    public static final int GET_ITEM_BAD_SEED = 303;

    public static final int QUEST_NOT_IN_LIST = 401;

    public static final int QUEST_FINISH_TOO_FAST = 402;

    public static final int UPLINK_CHECKSUM_A = 603;

    public static final int UPLINK_CHECKSUM_B = 604;

    public static final int LOTTERY_TOO_FAST = 820;

    public static final int SKILL_NOT_LEARNED = 825;

    public static final int SHOP_ITEM_NOT_ON_SHELF = 969;

    public static final int MEMORY_MODIFIED = 956;

    public static final int REPORT_FLAG_957 = 957;

    public static final int REPORT_FLAG_958 = 958;

    public static final int DIFFICULTY_MODIFIED = 959;

    public static final int MONSTER_DIE_ABNORMAL = 960;

    public static final int SPEED_HACK = 961;

    public static final int EQUIP_UPGRADE_MISMATCH = 963;

    public static final int REPORT_FLAG_965_PUNISH = 965;

    public static final int REPORT_FLAG_966 = 966;

    public static final int PVP_HIT_COUNT_OVER = 0x76;

    public static final int PVP_HIT_RATE_OVER = 0x73;

    public static int points(int code) {
        return switch (code) {
            case KILL_MONSTER_ZERO_HIT_COUNT -> 250;
            case KILL_MONSTER_UNKNOWN_ID, KILL_MONSTER_UNKNOWN_ID_OUTSIDE_BOSS -> 250;
            case MEMORY_MODIFIED -> 200;
            case SHOP_ITEM_NOT_ON_SHELF, QUEST_NOT_IN_LIST -> 250;

            case QUEST_FINISH_TOO_FAST -> 50;

            case KILL_MONSTER_OVER_DAMAGE, KILL_MONSTER_HIT_OVERFLOW -> 0;
            case KILL_MONSTER_POSITION_SPREAD -> 0;
            case KILL_MONSTER_ZERO_HIT -> 0;
            case GET_ITEM_BAD_SEED -> 0;
            case SKILL_NOT_LEARNED -> 0;
            case UPLINK_CHECKSUM_A, UPLINK_CHECKSUM_B -> 0;
            case KILL_MONSTER_FIRST_ATTACKER, FIX_MOB_DIE_POSITION -> 0;
            case PVP_HIT_COUNT_OVER, PVP_HIT_RATE_OVER -> 0;

            default -> 0;
        };
    }

    public static String describe(int code) {
        return switch (code) {
            case KILL_MONSTER_OVER_DAMAGE -> "单击伤害超上限";
            case KILL_MONSTER_FIRST_ATTACKER -> "击杀上报采样（官方 0x6D，未抄）";
            case KILL_MONSTER_ZERO_HIT_COUNT -> "击中次数为 0";
            case KILL_MONSTER_HIT_OVERFLOW -> "击中次数超 1500";
            case KILL_MONSTER_ZERO_HIT -> "零命中击杀";
            case KILL_MONSTER_UNKNOWN_ID -> "连续五次上报不存在的怪";
            case KILL_MONSTER_UNKNOWN_ID_OUTSIDE_BOSS -> "连续五次上报不存在的怪（不在 BOSS 房）";
            case KILL_MONSTER_POSITION_SPREAD -> "一秒内跨越 600 以上各杀一只";
            case FIX_MOB_DIE_POSITION -> "固定位置怪的死亡坐标越界（官方 0xD2，未抄）";
            case GET_ITEM_BAD_SEED -> "拾取包的防重放种子不整除";
            case QUEST_NOT_IN_LIST -> "接了不在可接列表里的任务";
            case QUEST_FINISH_TOO_FAST -> "一秒内第三次交任务";
            case UPLINK_CHECKSUM_A, UPLINK_CHECKSUM_B -> "上行包校验和对不上";
            case LOTTERY_TOO_FAST -> "两秒内连开抽奖箱";
            case SKILL_NOT_LEARNED -> "放了没学会的技能";
            case SHOP_ITEM_NOT_ON_SHELF -> "买货架上没有的东西";
            case MEMORY_MODIFIED -> "客户端账本和服务端对不上";
            case DIFFICULTY_MODIFIED -> "改副本难度";
            case MONSTER_DIE_ABNORMAL -> "怪物死亡异常";
            case SPEED_HACK -> "加速外挂";
            case EQUIP_UPGRADE_MISMATCH -> "装备强化等级对不上";
            case REPORT_FLAG_957, REPORT_FLAG_958, REPORT_FLAG_966 -> "客户端上报位（这一版收不到）";
            case REPORT_FLAG_965_PUNISH -> "客户端上报位（官方直接处罚）";
            case PVP_HIT_COUNT_OVER -> "决斗本局出手次数超 10000";
            case PVP_HIT_RATE_OVER -> "决斗出手频率超上限";
            default -> "未知码 " + code;
        };
    }
}
