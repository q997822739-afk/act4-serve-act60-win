package com.dylan.store.blob;

public record RandomOptionField(int index, int firstValue, int secondValue) {

    public static final int BYTES = 3;

    public static final RandomOptionField EMPTY = new RandomOptionField(0, 0, 0);

    public RandomOptionField {
        checkU8("index", index);
        checkU8("firstValue", firstValue);
        checkU8("secondValue", secondValue);
    }

    private static void checkU8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException(name + " 超出 u8：" + value);
        }
    }

    public boolean isEmpty() {
        return index == 0;
    }

    static RandomOptionField read(byte[] src, int offset) {
        return new RandomOptionField(
                src[offset] & 0xFF,
                src[offset + 1] & 0xFF,
                src[offset + 2] & 0xFF);
    }

    void write(byte[] dst, int offset) {
        dst[offset] = (byte) index;
        dst[offset + 1] = (byte) firstValue;
        dst[offset + 2] = (byte) secondValue;
    }
}
