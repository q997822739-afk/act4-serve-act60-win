package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti23Body(int userId, int villageId, int areaId,
                         int x, int y, int direction, int visibleValues) {
    public static final int PACKET_ID = 23;

    public static final int LENGTH = 10;

    public static final int AREA_DUNGEON = 255;

    public Noti23Body {
        checkU16("userId", userId);
        checkU8("villageId", villageId);
        checkU8("areaId", areaId);
        checkU16("x", x);
        checkU16("y", y);
        checkU8("direction", direction);
        checkU8("visibleValues", visibleValues);
    }

    public static Noti23Body enterDungeon(int userId, int villageId, int x, int y,
                                          int direction, int visibleValues) {
        return new Noti23Body(userId, villageId, AREA_DUNGEON, x, y, direction, visibleValues);
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(LENGTH);
        w.u16(userId);
        w.u8(villageId);
        w.u8(areaId);
        w.u16(x);
        w.u16(y);
        w.u8(direction);
        w.u8(visibleValues);
        return w.toArray();
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
