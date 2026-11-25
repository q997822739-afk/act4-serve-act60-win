package com.dylan.protocol.body;

public final class ChatType {
    private ChatType() {
    }

    public static final int SYSTEM = 0;

    public static final int WHISPER = 1;

    public static final int PARTY = 2;

    public static final int NORMAL = 3;

    public static final int GUILD = 6;

    public static final int WHISPER_BY_NAME = 7;

    public static final int MEMBER = 8;

    public static final int NUMBER = 12;

    public static final int ITEM_BROADCAST_36 = 13;

    public static final int MEGAPHONE = 14;

    public static final int ITEM_BROADCAST_1120 = 15;

    public static final int ALERT = 32;

    public static final int WHISPER_MESSENGER = 33;

    public static final int ITEM_BROADCAST_7582 = 34;

    public static boolean spendsItem(int type) {
        return type == ITEM_BROADCAST_36 || type == MEGAPHONE
                || type == ITEM_BROADCAST_1120 || type == ITEM_BROADCAST_7582;
    }

    public static boolean isWhisper(int type) {
        return type == WHISPER || type == WHISPER_BY_NAME || type == WHISPER_MESSENGER;
    }

    public static boolean hasTargetName(int type) {
        return type == WHISPER || type == WHISPER_BY_NAME || type == WHISPER_MESSENGER;
    }

    public static boolean creatureScriptHasTargetName(int type) {
        return type == WHISPER || type == WHISPER_BY_NAME;
    }

    public static boolean isServerOnly(int type) {
        return type == SYSTEM || type == NUMBER || type == ALERT;
    }

    public static String name(int type) {
        return switch (type) {
            case SYSTEM -> "SYSTEM";
            case WHISPER -> "WHISPER";
            case PARTY -> "PARTY";
            case NORMAL -> "NORMAL";
            case GUILD -> "GUILD";
            case WHISPER_BY_NAME -> "WHISPER_BY_NAME";
            case NUMBER -> "NUMBER(下行专用)";
            case ITEM_BROADCAST_36 -> "ITEM_BROADCAST(36)";
            case MEGAPHONE -> "MEGAPHONE(37)";
            case ITEM_BROADCAST_1120 -> "ITEM_BROADCAST(1120)";
            case ALERT -> "ALERT(下行专用)";
            case WHISPER_MESSENGER -> "WHISPER(1:1 对话窗)";
            case ITEM_BROADCAST_7582 -> "ITEM_BROADCAST(7582)";
            default -> "频道 " + type;
        };
    }
}
