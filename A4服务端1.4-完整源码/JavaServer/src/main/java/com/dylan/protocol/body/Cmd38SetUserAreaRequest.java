package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;

public record Cmd38SetUserAreaRequest(int townId, int areaId, int x, int y, int direction) {

    public static final int PACKET_ID = 38;

    public static final int LENGTH = 7;

    public Cmd38SetUserAreaRequest {
        checkU8("townId", townId);
        checkU8("areaId", areaId);
        checkU16("x", x);
        checkU16("y", y);
        checkU8("direction", direction);
    }

    public static Cmd38SetUserAreaRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException("SET_USER_AREA 包体至少要 " + LENGTH + " 字节，实际 "
                    + (plain == null ? "null" : plain.length));
        }
        int x = (plain[2] & 0xFF) | ((plain[3] & 0xFF) << 8);
        int y = (plain[4] & 0xFF) | ((plain[5] & 0xFF) << 8);
        return new Cmd38SetUserAreaRequest(plain[0] & 0xFF, plain[1] & 0xFF, x, y,
                plain[6] & 0xFF);
    }

    private static void checkU8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(name + " 超出 u8：" + value);
        }
    }

    private static void checkU16(String name, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new PacketFormatException(name + " 超出 u16：" + value);
        }
    }
}
