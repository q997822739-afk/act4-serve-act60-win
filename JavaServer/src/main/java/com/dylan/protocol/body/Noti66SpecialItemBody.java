package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti66SpecialItemBody(int kind, int premiumType, long remainSeconds, int capacity) {

    public static final int PACKET_ID = 66;

    public static final int PREMIUM_LENGTH = 7;

    public static final int CAPACITY_LENGTH = 4;

    public static final int MIN_KIND = 1;

    public static final int MAX_KIND = 7;

    public static final int MAX_PREMIUM_TYPE = 127;

    public static Noti66SpecialItemBody premium(int kind, int premiumType, long remainSeconds) {
        return new Noti66SpecialItemBody(kind, premiumType, remainSeconds, 0);
    }

    public static Noti66SpecialItemBody cargo(int kind, int capacity) {
        return new Noti66SpecialItemBody(kind, 0, 0, capacity);
    }

    public boolean premiumForm() {
        return kind == 2 || kind == 3 || kind == 4;
    }

    public byte[] toBytes() {
        if (kind < MIN_KIND || kind > MAX_KIND) {
            throw new PacketFormatException("NOTI 66 的 kind " + kind + " 超出客户端认的 "
                    + MIN_KIND + ".." + MAX_KIND);
        }
        if (premiumForm()) {
            if (premiumType < 0 || premiumType > MAX_PREMIUM_TYPE) {
                throw new PacketFormatException("会员类型 " + premiumType + " 超出 u8");
            }
            if (remainSeconds < 0 || remainSeconds > 0xFFFFFFFFL) {
                throw new PacketFormatException("会员剩余秒 " + remainSeconds + " 超出 u32");
            }
            PacketWriter w = new PacketWriter(PREMIUM_LENGTH);
            w.u16(kind);
            w.u8(premiumType);
            w.u32((int) remainSeconds);
            return w.toArray();
        }
        if (capacity < 0 || capacity > 0xFFFF) {
            throw new PacketFormatException("容量 " + capacity + " 超出 u16");
        }
        PacketWriter w = new PacketWriter(CAPACITY_LENGTH);
        w.u16(kind);
        w.u16(capacity);
        return w.toArray();
    }
}
