package com.dylan.store.blob;

public record AmplifyOption(byte rawTypeByte, int abilityValue, int lock) {

    public static final int BYTES = 4;

    private static final int UNIDENTIFIED_BIT = 0x80;

    public static final AmplifyOption EMPTY = new AmplifyOption((byte) 0, 0, 0);

    public AmplifyOption {
        if (abilityValue < 0 || abilityValue > 0xFFFF) {
            throw new IllegalArgumentException("abilityValue 超出 u16：" + abilityValue);
        }
        if (lock < 0 || lock > 0xFF) {
            throw new IllegalArgumentException("lock 超出 u8：" + lock);
        }
    }

    public int abilityType() {
        return rawTypeByte & 0x7F;
    }

    public boolean hasAbility() {
        return rawTypeByte != 0;
    }

    public boolean identified() {
        return (rawTypeByte & UNIDENTIFIED_BIT) == 0;
    }

    public int visibleAbilityType() {
        if (!hasAbility()) {
            return 0;
        }
        if (!identified()) {
            return -128;
        }
        return rawTypeByte & 0xFF;
    }

    public int visibleAbilityValue() {
        return hasAbility() && identified() ? abilityValue : 0;
    }

    public static AmplifyOption of(int abilityType, int abilityValue, boolean identified) {
        if (abilityType < 0 || abilityType > 0x7F) {
            throw new IllegalArgumentException("abilityType 超出 7 位：" + abilityType);
        }
        int raw = identified ? abilityType : (abilityType | UNIDENTIFIED_BIT);
        return new AmplifyOption((byte) raw, abilityValue, 0);
    }

    static AmplifyOption read(byte[] src, int offset) {
        return new AmplifyOption(
                src[offset],
                LittleEndian.u16(src, offset + 1),
                src[offset + 3] & 0xFF);
    }

    void write(byte[] dst, int offset) {
        dst[offset] = rawTypeByte;
        LittleEndian.putU16(dst, offset + 1, abilityValue);
        dst[offset + 3] = (byte) lock;
    }
}
