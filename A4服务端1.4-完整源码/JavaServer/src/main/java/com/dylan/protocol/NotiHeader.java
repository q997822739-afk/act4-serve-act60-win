package com.dylan.protocol;

public record NotiHeader(int category, int packetId, int totalLength) {
    public static final int SIZE = 7;

    public static final int CATEGORY_NOTI = 0;

    public static final int CATEGORY_CMD_REPLY = 1;

    public static final int MAX_DISPATCHABLE_NOTI_ID = 258;

    public static final int MAX_DISPATCHABLE_CMD_ID = 278;

    public static final int ID_ANTIBOT = 259;
    public static final int ID_DPROTO = 260;

    public int bodyLength() {
        return totalLength - SIZE;
    }

    public boolean isDispatchable() {
        if (packetId == ID_ANTIBOT || packetId == ID_DPROTO) {
            return false;
        }
        return switch (category) {
            case CATEGORY_NOTI -> packetId >= 0 && packetId <= MAX_DISPATCHABLE_NOTI_ID;
            case CATEGORY_CMD_REPLY -> packetId >= 1 && packetId <= MAX_DISPATCHABLE_CMD_ID;
            default -> false;
        };
    }

    @Override
    public String toString() {
        return String.format("NotiHeader[cat=%d id=%d len=%d body=%d]",
                category, packetId, totalLength, bodyLength());
    }
}
