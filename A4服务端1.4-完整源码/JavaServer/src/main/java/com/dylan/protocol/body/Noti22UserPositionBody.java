package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti22UserPositionBody(int userId, int x, int y, int direction, int moveSpeed) {
    public static final int PACKET_ID = 22;

    public static final int LENGTH = 9;

    public Noti22UserPositionBody {
        checkU16("userId", userId);
        checkU16("x", x);
        checkU16("y", y);
        checkU8("direction", direction);
        checkU16("moveSpeed", moveSpeed);
    }

    public static Noti22UserPositionBody of(int userId, Cmd37SetUserPositionRequest reported) {
        return new Noti22UserPositionBody(userId, reported.x(), reported.y(),
                reported.direction(), reported.movement());
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(LENGTH);
        w.u16(userId);
        w.u16(x);
        w.u16(y);
        w.u8(direction);
        w.u16(moveSpeed);
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
