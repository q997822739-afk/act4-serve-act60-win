package com.dylan.store;

import java.time.Instant;

public record MailRow(long postalId, Instant occTime, int senderCharacNo, String senderName,
                      long itemId, long addInfo, int endurance, int upgrade,
                      int amplifyOption, int amplifyValue, long enchant, long gold,
                      boolean sealed, boolean avatar, boolean unlimited, int letterId) {
    public static final int KEEP_SECONDS = 15 * 24 * 3600;

    public Instant expireAt() {
        return unlimited ? null : occTime.plusSeconds(KEEP_SECONDS);
    }

    public long remainingSeconds(Instant now) {
        if (unlimited) {
            return 0L;
        }
        long remaining = expireAt().getEpochSecond() - now.getEpochSecond();
        return Math.max(1L, remaining);
    }

    public boolean hasItem() {
        return itemId != 0;
    }

    public static final int DELETE_FLAG_TAKEN = 1;

    public static final int DELETE_FLAG_EXPIRED = 2;

    public static final int PAGE_SIZE = 20;
}
