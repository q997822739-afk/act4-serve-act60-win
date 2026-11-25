package com.dylan.game;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TutorialFlags {
    public static final int STORAGE_BITS = 128;

    public static final int LOW_BITS = 32;

    public static final int HIGH_BYTES = (STORAGE_BITS - LOW_BITS) / 8;

    private int low;

    private final byte[] high = new byte[HIGH_BYTES];

    public boolean set(int bit) {
        if (bit < 0 || bit >= STORAGE_BITS) {
            return false;
        }
        if (bit < LOW_BITS) {
            if ((low & (1 << bit)) != 0) {
                return false;
            }
            low |= 1 << bit;
            return true;
        }
        int index = (bit - LOW_BITS) / 8;
        int shift = (bit - LOW_BITS) % 8;
        if ((high[index] >> shift & 1) != 0) {
            return false;
        }
        high[index] |= (byte) (1 << shift);
        return true;
    }

    public boolean clear(int bit) {
        if (bit < 0 || bit >= STORAGE_BITS) {
            return false;
        }
        if (bit < LOW_BITS) {
            if ((low & (1 << bit)) == 0) {
                return false;
            }
            low &= ~(1 << bit);
            return true;
        }
        int index = (bit - LOW_BITS) / 8;
        int shift = (bit - LOW_BITS) % 8;
        if ((high[index] >> shift & 1) == 0) {
            return false;
        }
        high[index] &= (byte) ~(1 << shift);
        return true;
    }

    public boolean get(int bit) {
        if (bit < 0 || bit >= STORAGE_BITS) {
            return false;
        }
        if (bit < LOW_BITS) {
            return (low & (1 << bit)) != 0;
        }
        return (high[(bit - LOW_BITS) / 8] >> ((bit - LOW_BITS) % 8) & 1) != 0;
    }

    public void reset() {
        low = 0;
        Arrays.fill(high, (byte) 0);
    }

    public List<Integer> completed() {
        List<Integer> out = new ArrayList<>();
        for (int bit = 0; bit < STORAGE_BITS; bit++) {
            if (get(bit)) {
                out.add(bit);
            }
        }
        return out;
    }

    public int lowFlag() {
        return low;
    }

    public byte[] highBytes() {
        return high.clone();
    }

    public boolean isEmpty() {
        if (low != 0) {
            return false;
        }
        for (byte b : high) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    public void load(int lowFlag, byte[] highBytes) {
        low = lowFlag;
        Arrays.fill(high, (byte) 0);
        if (highBytes != null) {
            System.arraycopy(highBytes, 0, high, 0, Math.min(highBytes.length, HIGH_BYTES));
        }
    }

    public static TutorialFlags of(int lowFlag, byte[] highBytes) {
        TutorialFlags flags = new TutorialFlags();
        flags.load(lowFlag, highBytes);
        return flags;
    }

    @Override
    public String toString() {
        return completed().toString();
    }
}
