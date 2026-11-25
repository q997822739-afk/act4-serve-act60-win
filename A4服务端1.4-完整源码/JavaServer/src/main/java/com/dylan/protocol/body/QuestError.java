package com.dylan.protocol.body;

public final class QuestError {
    private QuestError() {
    }

    public static final int NONE = 0;

    public static final int FAIL = 1;

    public static final int SLOT_FULL = 4;

    public static final int INVENTORY_FULL = 17;

    public static final int ALREADY = 18;

    public static final int NOT_RUNNING = 19;

    public static final int NOT_CLEARABLE = 22;

    public static final long FINISH_RATE_WINDOW_MILLIS = 1000;

    public static final int FINISH_RATE_ALLOWANCE = 1;
}
