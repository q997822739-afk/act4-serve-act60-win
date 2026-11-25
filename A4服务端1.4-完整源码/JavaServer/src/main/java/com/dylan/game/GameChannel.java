package com.dylan.game;


public final class GameChannel {
    public static final int TYPE_UNKNOWN = -1;

    public static final int TYPE_NORMAL = 0;

    private static final int MASK_PVP = 0x1E7F0;

    public static final int TYPE_GUILD_WAR = 6;

    public static final int TYPE_TOURNAMENT = 7;

    public static final int TYPE_FAIR_PVP = 15;

    public static final int PVP_VILLAGE = 2;

    private static volatile int channelType = TYPE_NORMAL;

    private GameChannel() {
    }

    public static int channelType() {
        return channelType;
    }

    /**
     * 登录时记下这条频道的类型（原版 A5 有这个字段但没人赋值）。进程里同时开多个频道时以最后一次登录为准，
     * 所以**按连接**要用的地方请走 {@code GameSession.channelKind()} / {@link #isPvpKind(int)}。
     */
    public static void setType(int type) {
        channelType = type;
    }

    /**
     * 一个频道类型是不是决斗频道。{@code MASK_PVP} 是原版 A5 的表（4 决斗给胜点、5 自由、6 公会战、
     * 7 大会/观战、13–16）。
     *
     * <p>2026-10-28 排查「切到决斗场频道黑屏」时发现：原来那个 {@link #channelType} 静态量
     * <b>从来没人赋值</b>（原版 A5 也一样），所以 {@link #isPvpChannel()} 恒为 false，
     * 决斗频道被当普通频道：人落在存档城镇（1 号）而不是赫顿玛尔的酒馆（决斗场大厅）。
     * 现在按连接登录回包的频道类型判，见 {@code GameSession.channelKind()}。
     */
    public static boolean isPvpKind(int type) {
        return type >= 0 && type <= 0x10 && ((1 << type) & MASK_PVP) != 0;
    }

    public static boolean isPvpChannel() {
        return inMask(MASK_PVP);
    }

    public static int currentVillage(int storedVillage) {
        return isPvpChannel() ? PVP_VILLAGE : storedVillage;
    }

    /** 按连接判：决斗频道一律去 {@link #PVP_VILLAGE}（赫顿玛尔），普通频道用存档城镇。 */
    public static int currentVillage(int kind, int storedVillage) {
        return isPvpKind(kind) ? PVP_VILLAGE : storedVillage;
    }

    public static boolean villageIsPersisted() {
        return !isPvpChannel();
    }

    public static String describe(int type) {
        return switch (type) {
            case TYPE_UNKNOWN -> "未知（没 init 或者查不到）";
            case 0 -> "0 普通";
            case 4 -> "4 决斗·给胜点";
            case 5 -> "5 决斗·自由";
            case TYPE_GUILD_WAR -> "6 公会战";
            case TYPE_TOURNAMENT -> "7 决斗·大会/观战";
            case TYPE_FAIR_PVP -> "15 fair pvp";
            default -> String.valueOf(type);
        };
    }

    private static boolean inMask(int mask) {
        int type = channelType;
        return type >= 0 && type <= 0x10 && ((1 << type) & mask) != 0;
    }
}
