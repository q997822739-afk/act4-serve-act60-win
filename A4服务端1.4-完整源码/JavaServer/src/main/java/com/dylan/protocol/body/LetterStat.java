package com.dylan.protocol.body;

public final class LetterStat {
    private LetterStat() {
    }

    public static final int DELETE = 0;

    public static final int UNREAD = 1;

    public static final int READ = 2;

    public static final int KEPT = 3;

    public static final int MAX_KEPT = 10;

    public static boolean isValidRequest(int stat) {
        return stat == DELETE || stat == READ || stat == KEPT;
    }
}
