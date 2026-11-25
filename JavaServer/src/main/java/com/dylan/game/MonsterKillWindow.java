package com.dylan.game;

public final class MonsterKillWindow {
    public static final int WINDOW_MS = 1000;

    public static final int SPREAD_LIMIT = 600;

    public static final int UNKNOWN_ID_STREAK = 5;

    private int minX;
    private int maxX;
    private int minY;
    private int maxY;
    private long windowStart;
    private int killsInWindow;

    private boolean opened;

    private int unknownIdStreak;

    public boolean report(long tickMs, int x, int y) {
        if (!opened || tickMs - windowStart > WINDOW_MS) {
            opened = true;
            windowStart = tickMs;
            killsInWindow = 0;
            minX = x;
            maxX = x;
            minY = y;
            maxY = y;
            return false;
        }
        killsInWindow++;
        minX = Math.min(minX, x);
        maxX = Math.max(maxX, x);
        minY = Math.min(minY, y);
        maxY = Math.max(maxY, y);
        return Math.abs(maxX - minX) >= SPREAD_LIMIT || Math.abs(maxY - minY) >= SPREAD_LIMIT;
    }

    public int killsInWindow() {
        return killsInWindow;
    }

    public int spreadX() {
        return opened ? Math.abs(maxX - minX) : 0;
    }

    public int spreadY() {
        return opened ? Math.abs(maxY - minY) : 0;
    }

    public boolean reportUnknownId() {
        if (++unknownIdStreak > UNKNOWN_ID_STREAK - 1) {
            unknownIdStreak = 0;
            return true;
        }
        return false;
    }

    public void resetUnknownIdStreak() {
        unknownIdStreak = 0;
    }
}
