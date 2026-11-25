package com.dylan.protocol.body;

public enum GmSubCommand {
    GM_MODE(0x00, Args.NONE, "//ehdgusrlwn"),

    LEVEL_UP(0x01, Args.NONE, "//level up"),

    LEVEL_DOWN(0x02, Args.NONE, "//level down"),

    GROW(0x03, Args.TWO_U8, "//grow"),

    CLEAR_ALL_SKILL(0x04, Args.NONE, "//clear all skill"),

    ADD_SKILL(0x05, Args.TWO_U8, "//add skill"),

    ADD_ITEM(0x06, Args.TWO_U32, "//add item"),

    LEVEL_TO(0x07, Args.U8, "//levelto"),

    PVP_RANK(0x08, Args.U8, "//pvprank"),

    UPGRADE_SUCCESS(0x09, Args.NONE, "//upgrade success"),

    UPGRADE_FAIL(0x0A, Args.NONE, "//upgrade fail"),

    UPGRADE_RANDOM(0x0B, Args.NONE, "//upgrade random"),

    KICK(0x0C, Args.STRING, "//kick"),

    WARROOM_TIME(0x0D, Args.U32, "//warroomtime"),

    CREATURE_EXP(0x0E, Args.U32, "//creature exp"),

    CREATURE_FILL(0x0F, Args.U32, "//creature fill"),

    CREATURE_EVOLUTION(0x10, Args.NONE, "//creature evolution"),

    ADD_AVATAR(0x11, Args.AVATAR, "//add avatar"),

    HELL(0x12, Args.NONE, "//hell"),

    SET_GM_ADD(0x13, Args.U32, "//setgm add"),

    SET_GM_REMOVE(0x14, Args.U32, "//setgm remove"),

    HIDE(0x15, Args.NONE, "//hide"),

    SHOW(0x16, Args.NONE, "//show"),

    MID(0x17, Args.STRING, "//mid"),

    LUCK_POINT(0x18, Args.U32, "//max lp"),

    SET_AGE(0x19, Args.U32, "//age"),

    SET_ENCHANT(0x1A, Args.THREE_U32, "//enchant"),

    EXPERT_JOB_EXP(0x1B, Args.U32, "//expertjob"),

    SET_USER_DEFINE(0x1C, Args.NONE, "//setuserdefine"),

    GUILD_LEVEL_UP(0x1D, Args.NONE, "//guilddebug"),

    POWER_WAR_START(0x1E, Args.NONE, "//power start"),

    POWER_WAR_END(0x1F, Args.NONE, "//power end"),

    POWER_UP_ON(0x20, Args.NONE, "//power up"),

    POWER_UP_OFF(0x21, Args.NONE, "//power up off"),

    USER_POWERWAR_POINT_UP(0x22, Args.U32, "//power userpoint"),

    USER_POWERWAR_POINT_INIT(0x23, Args.NONE, "//power userpoint reset"),

    POWERWAR_POINT_UP(0x24, Args.U32, "//power guildpoint"),

    SET_BLACK_COUNT(0x25, Args.U32, "//troublemaker"),

    CARGO_CAPACITY(0x26, Args.U32, "//cargo"),

    GUILD_POWERWAR_POINT(0x27, Args.U32, "//guildpoint"),

    FATIGUE(0x28, Args.I32, "//fatigue"),

    USE_COIN(0x29, Args.I32, "//coin"),

    GM_MODE_OFF(0x2A, Args.NONE, "//realmode"),

    CLEAR_AVATAR_INVEN(0x2B, Args.NONE, "//dirty avatar"),

    CLEAR_INVEN(0x2C, Args.NONE, "//cinven"),

    SET_MONEY(0x2F, Args.U32, "//money"),

    UPDATE_CRANE_SEED(0x30, Args.NONE, "//craneDay"),

    EVENT_LIST(0x31, Args.NONE, "//event list"),

    START_EVENT(0x32, Args.U32_OPT2, "//startevent"),

    END_EVENT(0x33, Args.U32, "//endevent"),

    EXPERT_JOB_TYPE(0x34, Args.U32, "//expertjob type"),

    LEARN_RECIPE(0x35, Args.STRING, "//learnrecipe"),

    CLEAR_QUEST(0x36, Args.STRING, "//clearquest"),

    QUEST_DONE(0x37, Args.STRING_OPT, "//questdone", "//questclear2", "//q"),

    SET_ADD_INFO(0x38, Args.THREE_U32, "//addinfo"),

    GRADE_PROBE(0x39, Args.THREE_U32, "//gradeprobe");

    public enum Args {
        NONE(0),

        U8(1),

        TWO_U8(2),

        U32(1),

        I32(1),

        TWO_U32(2),

        THREE_U32(3),

        U32_OPT2(1),

        AVATAR(4),

        STRING(0),

        STRING_OPT(0);

        private final int count;

        Args(int count) {
            this.count = count;
        }

        public int count() {
            return count;
        }

        public boolean isText() {
            return this == STRING || this == STRING_OPT;
        }

        public boolean isOptionalText() {
            return this == STRING_OPT;
        }
    }

    public static final int MAX_ID = 0x30;

    private static final GmSubCommand[] BY_ID = new GmSubCommand[MAX_ID + 1];

    static {
        for (GmSubCommand sub : values()) {
            if (sub.isCmd65()) {
                BY_ID[sub.id] = sub;
            }
        }
    }

    private final int id;
    private final Args args;
    private final String display;

    private final String[] aliases;

    GmSubCommand(int id, Args args, String display, String... aliases) {
        this.id = id;
        this.args = args;
        this.display = display;
        this.aliases = aliases;
    }

    public int id() {
        return id;
    }

    public boolean isCmd65() {
        return id <= MAX_ID;
    }

    public Args args() {
        return args;
    }

    public String display() {
        return display;
    }

    public static GmSubCommand byId(int id) {
        return id < 0 || id > MAX_ID ? null : BY_ID[id];
    }

    public static GmSubCommand byName(String name) {
        if (name == null) {
            return null;
        }
        String wanted = stripPrefix(name);
        for (GmSubCommand sub : values()) {
            if (stripPrefix(sub.display).equalsIgnoreCase(wanted)) {
                return sub;
            }
            for (String alias : sub.aliases) {
                if (stripPrefix(alias).equalsIgnoreCase(wanted)) {
                    return sub;
                }
            }
        }
        return null;
    }

    public static boolean hasPrefix(String text) {
        return text != null && !text.isEmpty() && PREFIX_CHARS.indexOf(text.charAt(0)) >= 0;
    }

    private static final String PREFIX_CHARS = "#＃";

    private static final String STRIPPABLE_PREFIX = PREFIX_CHARS + "/";

    private static String stripPrefix(String name) {
        int i = 0;
        while (i < name.length() && STRIPPABLE_PREFIX.indexOf(name.charAt(i)) >= 0) {
            i++;
        }
        return name.substring(i);
    }

    @Override
    public String toString() {
        return display + "(0x" + Integer.toHexString(id) + ")";
    }
}
