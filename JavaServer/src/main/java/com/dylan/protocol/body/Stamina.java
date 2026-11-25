package com.dylan.protocol.body;

public final class Stamina {

    public static final int FULL = 100;

    public static final int MAX = 100;

    private Stamina() {
    }

    public static int clamp(int value) {
        return Math.max(0, Math.min(MAX, value));
    }
}
