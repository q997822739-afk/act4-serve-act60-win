package com.dylan.pvf.script;

import java.util.Locale;

public enum QuestRewardType {

    ITEM("[item]", 0),

    GROW_TYPE("[grow type]", 1),

    AWAKENING("[awakening type]", 2),

    HELL_CHALLENGE("[hell challenge]", 3),

    CREATURE_EVOLUTION("[creature evolution]", 10),

    EXPERT_JOB("[expert job]", 20);

    private final String tag;
    private final int code;

    QuestRewardType(String tag, int code) {
        this.tag = tag;
        this.code = code;
    }

    public String tag() {
        return tag;
    }

    public int code() {
        return code;
    }

    public boolean isItem() {
        return this == ITEM;
    }

    public boolean carriesSkillPayload() {
        return this == GROW_TYPE || this == AWAKENING || this == EXPERT_JOB;
    }

    public static QuestRewardType byTag(String raw) {
        if (raw == null) {
            return ITEM;
        }
        String normalized = raw.trim().replace("`", "").toLowerCase(Locale.ROOT);
        for (QuestRewardType t : values()) {
            if (t.tag.equals(normalized)) {
                return t;
            }
        }
        return ITEM;
    }
}
