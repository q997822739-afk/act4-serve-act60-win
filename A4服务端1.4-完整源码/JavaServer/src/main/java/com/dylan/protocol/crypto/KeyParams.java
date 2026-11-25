package com.dylan.protocol.crypto;

public record KeyParams(int xorKey, int rolCount) {
    public KeyParams {
        if (xorKey < 0 || xorKey > 0xFF) {
            throw new IllegalArgumentException("xorKey out of range: " + xorKey);
        }
        if (rolCount < 0 || rolCount > 7) {
            throw new IllegalArgumentException("rolCount out of range: " + rolCount);
        }
    }

    public static KeyParams fromSeedValue(int seedValue) {
        return new KeyParams(seedValue & 0xFF, (seedValue >> 8) & 7);
    }

    @Override
    public String toString() {
        return String.format("KeyParams[k=0x%02X, r=%d]", xorKey, rolCount);
    }
}
