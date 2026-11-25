package com.dylan.store.blob;

public record ChronicleRandomOption(
        EnchantedSkillOption skill0,
        EnchantedSkillOption skill1,
        long info,
        RandomOption random) {

    public static final int BYTES = 20;

    static final int RANDOM_OFFSET = 6;

    public static final ChronicleRandomOption EMPTY = new ChronicleRandomOption(
            EnchantedSkillOption.EMPTY, EnchantedSkillOption.EMPTY, 0, RandomOption.EMPTY);

    public ChronicleRandomOption {
        if (skill0 == null || skill1 == null || random == null) {
            throw new IllegalArgumentException("ChronicleRandomOption 的字段不能为 null");
        }
        if (info < 0 || info > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("info 超出 u32：" + info);
        }
    }

    static ChronicleRandomOption read(byte[] src, int offset) {
        return new ChronicleRandomOption(
                EnchantedSkillOption.read(src, offset),
                EnchantedSkillOption.read(src, offset + 1),
                LittleEndian.u32(src, offset + 2),
                RandomOption.read(src, offset + RANDOM_OFFSET));
    }

    void write(byte[] dst, int offset) {
        skill0.write(dst, offset);
        skill1.write(dst, offset + 1);
        LittleEndian.putU32(dst, offset + 2, info);
        random.write(dst, offset + RANDOM_OFFSET);
    }
}
