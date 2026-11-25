package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.ArrayList;
import java.util.List;

public record Noti3UserStateBody(List<Entry> users) {
    public static final int PACKET_ID = 3;

    public static final int STATE_IN_TOWN = 0;

    public static final int STATE_IN_DUNGEON = 1;

    public static final int MAX_USERS = 0xFF;

    public Noti3UserStateBody {
        if (users == null) {
            throw new PacketFormatException("NOTI " + PACKET_ID + " 的名单不能是 null");
        }
        if (users.size() > MAX_USERS) {
            throw new PacketFormatException("NOTI " + PACKET_ID + " 一条最多装 " + MAX_USERS
                    + " 个人，给了 " + users.size() + " 个");
        }
        users = List.copyOf(users);
    }

    public static Noti3UserStateBody single(int userId, int state) {
        List<Entry> one = new ArrayList<>(1);
        one.add(new Entry(userId, state));
        return new Noti3UserStateBody(one);
    }

    public int length() {
        return 1 + users.size() * Entry.LENGTH;
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(length());
        w.u8(users.size());
        for (Entry e : users) {
            w.u16(e.userId());
            w.u8(e.state());
        }
        return w.toArray();
    }

    public record Entry(int userId, int state) {
        public static final int LENGTH = 3;

        public Entry {
            if (userId < 0 || userId > 0xFFFF) {
                throw new PacketFormatException("userId 超出 u16：" + userId);
            }
            if (state < 0 || state > 0xFF) {
                throw new PacketFormatException("state 超出 u8：" + state);
            }
        }
    }
}
