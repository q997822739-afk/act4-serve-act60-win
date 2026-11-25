package com.dylan.store;

import java.time.Instant;

public record AvatarItemRow(long uiId, int characNo, int slot, int itemId,
                            Instant expireDate, int abilityNo, long commodityNo) {

    public static final long NOT_PERSISTED = 0L;

    public AvatarItemRow withSlot(int newSlot) {
        return new AvatarItemRow(uiId, characNo, newSlot, itemId, expireDate, abilityNo,
                commodityNo);
    }

    public AvatarItemRow withCharacNo(int newCharacNo) {
        return new AvatarItemRow(uiId, newCharacNo, slot, itemId, expireDate, abilityNo,
                commodityNo);
    }

    public static final Instant NO_EXPIRE = Instant.EPOCH;

    public boolean permanent() {
        return expireDate == null;
    }

    public long remainingSeconds(Instant now) {
        if (expireDate == null) {
            return 0L;
        }
        long remaining = expireDate.getEpochSecond() - now.getEpochSecond();
        return remaining <= 0 ? 0L : Math.min(MAX_REMAINING_SECONDS, remaining);
    }

    public static final long MAX_REMAINING_SECONDS = Integer.MAX_VALUE / 2;
}
