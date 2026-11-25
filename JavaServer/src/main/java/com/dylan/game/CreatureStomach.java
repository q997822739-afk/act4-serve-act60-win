package com.dylan.game;

public final class CreatureStomach {
    public static final int STATE_FIGHTING = 1;

    public static final int STATE_UNEQUIPPED = 2;

    public static final int STATE_TOWN = 3;

    public static final double MAX = 100.0;

    public static final double DRAIN_SECONDS_PER_POINT = 60.0;

    public static final double RECOVER_SECONDS_PER_POINT = 360.0;

    public static final double FEED_AMOUNT = 30.0;

    public static final double DEAD_BELOW = 1.0;

    public static final double REVIVE_VALUE = 1.0;

    private double value;
    private long lastSecs;

    public CreatureStomach(double value, long lastSecs) {
        this.value = value;
        this.lastSecs = lastSecs;
    }

    public double valueAt(int state, long nowSecs) {
        double elapsed = nowSecs - lastSecs;
        if (elapsed > 0) {
            if (state == STATE_FIGHTING) {
                value -= elapsed / DRAIN_SECONDS_PER_POINT;
                if (value < 0) {
                    value = 0;
                }
            } else if (state == STATE_UNEQUIPPED || state == STATE_TOWN) {
                value += elapsed / RECOVER_SECONDS_PER_POINT;
                if (value > MAX) {
                    value = MAX;
                }
            }
        }
        lastSecs = nowSecs;
        return value;
    }

    public double peek(int state, long nowSecs) {
        double elapsed = nowSecs - lastSecs;
        if (elapsed <= 0) {
            return value;
        }
        if (state == STATE_FIGHTING) {
            return Math.max(0.0, value - elapsed / DRAIN_SECONDS_PER_POINT);
        }
        if (state == STATE_UNEQUIPPED || state == STATE_TOWN) {
            return Math.min(MAX, value + elapsed / RECOVER_SECONDS_PER_POINT);
        }
        return value;
    }

    public boolean worthSaving(int state) {
        if (state == STATE_FIGHTING) {
            return value > 0.0;
        }
        if (state == STATE_UNEQUIPPED || state == STATE_TOWN) {
            return value != MAX;
        }
        return true;
    }

    public double feed() {
        value += FEED_AMOUNT;
        if (value > MAX) {
            value = MAX;
        }
        return value;
    }

    public void set(double newValue) {
        this.value = newValue;
    }

    public void resume(long nowSecs) {
        this.lastSecs = nowSecs;
    }

    public double raw() {
        return value;
    }

    public long lastSecs() {
        return lastSecs;
    }

    @Override
    public String toString() {
        return String.format("%.2f（%d 秒时）", value, lastSecs);
    }
}
