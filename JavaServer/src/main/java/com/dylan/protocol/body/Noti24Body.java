package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti24Body(int townId, int areaId, List<TownUser> users) {
    public static final int PACKET_ID = 24;

    public static final int ENTRY_LENGTH = 8;

    public static final int HEADER_LENGTH = 4;

    public record TownUser(int userId, int x, int y, int direction, int state) {
        public TownUser {
            checkU16("userId", userId);
            checkU16("x", x);
            checkU16("y", y);
            checkU8("direction", direction);
            checkU8("state", state);
        }
    }

    public static final int DIRECTION_RIGHT = 6;

    public static final int DEFAULT_STATE = 1;

    public Noti24Body {
        checkU8("townId", townId);
        checkU8("areaId", areaId);
        if (users == null) {
            throw new PacketFormatException("users 不能为 null");
        }
        users = List.copyOf(users);
        if (users.size() > 0xFFFF) {
            throw new PacketFormatException("人数是 u16，装不下 " + users.size() + " 条");
        }
    }

    public static Noti24Body solo(int townId, int areaId, int userId, int x, int y) {
        return new Noti24Body(townId, areaId,
                List.of(new TownUser(userId, x, y, DIRECTION_RIGHT, DEFAULT_STATE)));
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(HEADER_LENGTH + users.size() * ENTRY_LENGTH);
        w.u8(townId);
        w.u8(areaId);
        w.u16(users.size());
        for (TownUser u : users) {
            w.u16(u.userId());
            w.u16(u.x());
            w.u16(u.y());
            w.u8(u.direction());
            w.u8(u.state());
        }
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
