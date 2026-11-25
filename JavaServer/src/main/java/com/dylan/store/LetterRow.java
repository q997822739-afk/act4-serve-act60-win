package com.dylan.store;

import java.time.Instant;

public record LetterRow(long letterId, int characNo, int senderCharacNo, String senderName,
                        String text, Instant regDate, int stat) {
    public static final int KEEP_SECONDS = MailRow.KEEP_SECONDS;

    public static final int MAX_TEXT_BYTES = 255;

    public long registeredAtEpochSecond() {
        return regDate.getEpochSecond();
    }

    public LetterRow withStat(int newStat) {
        return new LetterRow(letterId, characNo, senderCharacNo, senderName, text, regDate,
                newStat);
    }
}
