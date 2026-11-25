package com.dylan.game;

public final class TimerMessage {

    private TimerMessage() {
    }

    public static final int MAP_LOADING = 11;

    public static final int DUNGEON_CLEAR = 13;

    public static final int DUNGEON_FAIL = 14;

    public static final int CARD_SELECT = 16;

    public static final int CLEAR_DUNGEON_REWARD = 17;

    public static final int CLEAR_DUNGEON_SCORE_SCROLL = 18;

    public static final int CLEAR_DUNGEON_CARD_SELECT_RIGHT = 19;

    public static final int EPLP_RETURN_VILLAGE = 20;

    public static final int EPLP_COMMAND_WAIT = 21;

    public static final int DEATH_TOWER_FIRST = 73;

    public static final int DEATH_TOWER_PREPARE_FINISH = 74;

    public static final int DEATH_TOWER_REWARD = 75;

    public static final int DEATH_TOWER_EPLP = 76;

    public static final int DEATH_TOWER_EPLP_TIMEOUT = 77;

    public static final int DEATH_TOWER_EPLP_SELECTED = 78;

    public static final int DEATH_TOWER_LAST = 78;

    public static final int FINISH_ITEM_EFFECT = 118;

    public static final int DETECT_DISCONNECT = 109;

    public static final int PVP_RANK_RESULT = 50;

    public static final int PVP_CHECK_ROOM_USER = 60;

    public static final int PVP_CHECK_RECV_END_RESULT = 61;

    public static final int START_RELAY_BATTLE = 99;

    public static final int COUNT = FINISH_ITEM_EFFECT + 1;

    public static String name(int message) {
        return switch (message) {
            case MAP_LOADING -> "TimerMapLoading";
            case DUNGEON_CLEAR -> "TimerDungeonClear";
            case DUNGEON_FAIL -> "TimerDungeonFail";
            case CARD_SELECT -> "TimerCardSelect";
            case CLEAR_DUNGEON_REWARD -> "TimerClearDungeonReward";
            case CLEAR_DUNGEON_SCORE_SCROLL -> "TimerClearDungeonScoreScoll";
            case CLEAR_DUNGEON_CARD_SELECT_RIGHT -> "TimerClearDungeonCardSelectRight";
            case EPLP_RETURN_VILLAGE -> "TimerEPLPReturnVillage";
            case EPLP_COMMAND_WAIT -> "TimerEPLPCommandWait";
            case DEATH_TOWER_PREPARE_FINISH -> "TimerDeathTower[74 收尾准备]";
            case DEATH_TOWER_REWARD -> "TimerDeathTower[75 奖励页]";
            case DEATH_TOWER_EPLP -> "TimerDeathTower[76 发物品+EPLP]";
            case DEATH_TOWER_EPLP_TIMEOUT -> "TimerDeathTower[77 EPLP 超时]";
            case DEATH_TOWER_EPLP_SELECTED -> "TimerDeathTower[78 点了按钮]";
            case FINISH_ITEM_EFFECT -> "TimerFinishItemEffect";
            case DETECT_DISCONNECT -> "TimerDetectDisconnect";
            case PVP_RANK_RESULT -> "TimerPvpRankRes";
            case PVP_CHECK_ROOM_USER -> "TimerCheckPvPRoomUser";
            case PVP_CHECK_RECV_END_RESULT -> "TimerCheckRecvPvpEndResult";
            case START_RELAY_BATTLE -> "TimerStartRelayBattle";
            default -> message >= DEATH_TOWER_FIRST && message <= DEATH_TOWER_LAST
                    ? "TimerDeathTower[" + message + "]"
                    : "TIMER_MESSAGE(" + message + ")";
        };
    }
}
