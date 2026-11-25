package com.dylan.pvf.script;

import java.util.Locale;

public enum QuestGrade {

    EPIC("[epic]", 0),

    TRAINING("[training]", 1),

    ACHIEVEMENT("[achievement]", 2),

    DAILY("[daily]", 3),

    NORMALY_REPEAT("[normaly repeat]", 4),

    COMMON_UNIQUE("[common unique]", 5),

    SPECIAL("[special]", 6),

    TITLE("[title]", 7),

    URGENT("[urgent]", 8);

    private final String tag;
    private final int code;

    QuestGrade(String tag, int code) {
        this.tag = tag;
        this.code = code;
    }

    public String tag() {
        return tag;
    }

    public int code() {
        return code;
    }

    public boolean repeatable() {
        return this == NORMALY_REPEAT || this == URGENT;
    }

    public static QuestGrade byTag(String raw) {
        if (raw == null) {
            return NORMALY_REPEAT;
        }
        String normalized = raw.trim().replace("`", "").toLowerCase(Locale.ROOT);
        if ("[special daily]".equals(normalized)) {
            return DAILY;
        }
        for (QuestGrade grade : values()) {
            if (grade.tag.equals(normalized)) {
                return grade;
            }
        }
        return NORMALY_REPEAT;
    }
}
