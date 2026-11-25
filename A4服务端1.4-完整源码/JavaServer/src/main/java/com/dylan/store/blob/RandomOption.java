package com.dylan.store.blob;

import java.util.List;

public record RandomOption(
        List<RandomOptionField> options,
        int seed,
        RandomOptionField changed,
        int changedSeed) {
    public static final int BYTES = 14;

    public static final int OPTION_SLOTS = 3;

    public static final RandomOption EMPTY = new RandomOption(
            List.of(RandomOptionField.EMPTY, RandomOptionField.EMPTY, RandomOptionField.EMPTY),
            0, RandomOptionField.EMPTY, 0);

    public RandomOption {
        if (options == null || options.size() != OPTION_SLOTS) {
            throw new IllegalArgumentException("options 必须正好 " + OPTION_SLOTS + " 条");
        }
        options = List.copyOf(options);
        if (seed < 0 || seed > 0xFF) {
            throw new IllegalArgumentException("seed 超出 u8：" + seed);
        }
        if (changedSeed < 0 || changedSeed > 0xFF) {
            throw new IllegalArgumentException("changedSeed 超出 u8：" + changedSeed);
        }
        if (changed == null) {
            throw new IllegalArgumentException("changed 不能为 null");
        }
    }

    public boolean isEmpty() {
        return options.get(0).isEmpty();
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= OPTION_SLOTS) {
            throw new IllegalArgumentException("随机属性槽号越界：" + slot);
        }
    }

    static RandomOption read(byte[] src, int offset) {
        return new RandomOption(
                List.of(
                        RandomOptionField.read(src, offset),
                        RandomOptionField.read(src, offset + 3),
                        RandomOptionField.read(src, offset + 6)),
                src[offset + 9] & 0xFF,
                RandomOptionField.read(src, offset + 10),
                src[offset + 13] & 0xFF);
    }

    void write(byte[] dst, int offset) {
        for (int i = 0; i < OPTION_SLOTS; i++) {
            options.get(i).write(dst, offset + 3 * i);
        }
        dst[offset + 9] = (byte) seed;
        changed.write(dst, offset + 10);
        dst[offset + 13] = (byte) changedSeed;
    }
}
