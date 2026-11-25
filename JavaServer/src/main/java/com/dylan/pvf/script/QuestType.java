package com.dylan.pvf.script;

public enum QuestType {

    SEEKING("[seeking]"),

    CONDITION_UNDER_CLEAR("[condition under clear]"),

    HUNT_MONSTER("[hunt monster]"),

    CLEAR_MAP("[clear map]"),

    MEET_NPC("[meet npc]"),

    PVP_RANK("[pvp rank]"),

    HUNT_ENEMY("[hunt enemy]"),

    POWERWAR_WIN("[powerwar win]"),

    POWERWAR_POINT("[powerwar point]"),

    MOBILE("[mobile]"),

    USE_ITEM("[use item]"),

    GET_SCORE("[get score]"),

    CLEAR_QUEST("[clear quest]"),

    CHECK_TIME("[check time]"),

    CHECK_LIFE("[check life]"),

    AMPLIFY_ITEM("[amplify item]"),

    DISJOINT_ITEM("[disjoint item]"),

    EQUIPPED_ITEM("[equipped item]"),

    GET_ITEM("[get item]"),

    CUSTOM_QUEST("[custom quest]"),

    SEND_CHATTING("[send chatting]"),

    TURN_GOLD_CARD("[turn gold card]"),

    USE_FORTUNE_COIN("[use fortune coin]"),

    MEET_SECRET_NPC("[meet secret npc]"),

    UI_CLICK("[ui click]"),

    SEEK_N_MEET_NPC("[seek n meet npc]"),

    ASSAULT_COUNT("[assault count]");

    private final String tag;

    QuestType(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }

    public int code() {
        return ordinal();
    }

    public boolean consumesItems() {
        return this == SEEKING || this == SEEK_N_MEET_NPC;
    }

    public static QuestType byTag(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().replace("`", "").toLowerCase(java.util.Locale.ROOT);
        for (QuestType type : values()) {
            if (type.tag.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
