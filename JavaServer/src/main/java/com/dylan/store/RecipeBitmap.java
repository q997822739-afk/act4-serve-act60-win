package com.dylan.store;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RecipeBitmap {
    public static final int BYTES = 30;

    public static final int BITS = BYTES * 8;

    public static final int MAX_INDEX = BITS - 1;

    private final byte[] bits;

    private RecipeBitmap(byte[] bits) {
        this.bits = bits;
    }

    public static RecipeBitmap empty() {
        return new RecipeBitmap(new byte[BYTES]);
    }

    public static RecipeBitmap decode(byte[] blob) {
        byte[] out = new byte[BYTES];
        if (blob != null && blob.length > 0) {
            System.arraycopy(blob, 0, out, 0, Math.min(blob.length, BYTES));
        }
        return new RecipeBitmap(out);
    }

    public byte[] encode() {
        return bits.clone();
    }

    public boolean has(int index) {
        if (index < 0 || index > MAX_INDEX) {
            return false;
        }
        return (bits[index / 8] >> (index % 8) & 1) != 0;
    }

    public boolean set(int index) {
        if (index < 0 || index > MAX_INDEX) {
            return false;
        }
        if (has(index)) {
            return false;
        }
        bits[index / 8] |= (byte) (1 << (index % 8));
        return true;
    }

    public void clear() {
        Arrays.fill(bits, (byte) 0);
    }

    public int count() {
        int n = 0;
        for (byte b : bits) {
            n += Integer.bitCount(b & 0xFF);
        }
        return n;
    }

    public List<Integer> indices() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i <= MAX_INDEX; i++) {
            if (has(i)) {
                out.add(i);
            }
        }
        return out;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RecipeBitmap other && Arrays.equals(bits, other.bits);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bits);
    }

    @Override
    public String toString() {
        return "已学 " + count() + " 条配方";
    }
}
