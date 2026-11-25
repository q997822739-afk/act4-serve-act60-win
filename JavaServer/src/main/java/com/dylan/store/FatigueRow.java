package com.dylan.store;

import java.time.Instant;

public record FatigueRow(int fatigue, int usedFatigue, int premiumFatigue, int grownUpBuff,
                         Instant lastPlayTime) {

    public static FatigueRow fresh() {
        return new FatigueRow(0, 0, 0, 0, Instant.EPOCH);
    }
}
