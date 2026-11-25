package com.dylan.store;

public record EventLogRow(
        long logId,
        long occTime,
        int eventType,
        int parameter1,
        int parameter2,
        int serverId,
        long startTime,
        long endTime,
        long mId,
        String expl) {

    public boolean open() {
        return endTime == 0;
    }
}
