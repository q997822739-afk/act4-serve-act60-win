package com.dylan.store.blob;

public record EnchantedSkillOption(boolean enabled, int kind, int level) {

    public static final int BYTES = 1;

    public static final EnchantedSkillOption EMPTY = new EnchantedSkillOption(false, 0, 0);

    public EnchantedSkillOption {
        if (kind < 0 || kind > 0b11) {
            throw new IllegalArgumentException("kind 超出 2 位：" + kind);
        }
        if (level < 0 || level > 0x1F) {
            throw new IllegalArgumentException("level 超出 5 位：" + level);
        }
    }

    static EnchantedSkillOption read(byte[] src, int offset) {
        int b = src[offset] & 0xFF;
        return new EnchantedSkillOption((b & 0b1) != 0, (b >>> 1) & 0b11, (b >>> 3) & 0x1F);
    }

    void write(byte[] dst, int offset) {
        dst[offset] = (byte) ((enabled ? 1 : 0) | (kind << 1) | (level << 3));
    }
}
