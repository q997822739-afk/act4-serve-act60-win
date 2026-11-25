package com.dylan.protocol.body;

public record Noti26Body(int hostSlot) {

    public static final int PACKET_ID = 26;

    public static final int LENGTH = 1;

    public static final int SOLO_HOST_SLOT = 0;

    public Noti26Body {
        if (hostSlot < 0 || hostSlot > 0xFF) {
            throw new IllegalArgumentException("hostSlot 超出 u8：" + hostSlot);
        }
    }

    public static Noti26Body solo() {
        return new Noti26Body(SOLO_HOST_SLOT);
    }

    public byte[] toBytes() {
        return new byte[] {(byte) hostSlot};
    }
}
