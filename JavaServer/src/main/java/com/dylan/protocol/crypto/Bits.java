package com.dylan.protocol.crypto;

public final class Bits {

    private Bits() {
    }

    public static int rol8(int value, int count) {
        int b = value & 0xFF;
        int r = count & 7;
        return ((b << r) | (b >>> (8 - r))) & 0xFF;
    }

    public static int ror8(int value, int count) {
        int b = value & 0xFF;
        int r = count & 7;
        return ((b >>> r) | (b << (8 - r))) & 0xFF;
    }
}
