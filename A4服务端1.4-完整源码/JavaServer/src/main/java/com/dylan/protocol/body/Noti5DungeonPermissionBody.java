package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti5DungeonPermissionBody(List<Entry> entries) {
    public record Entry(int dungeonId, int difficulty) {
        public Entry {
            if (dungeonId < 0 || dungeonId > 0xFFFF) {
                throw new PacketFormatException("副本号超出 u16：" + dungeonId);
            }
            if (difficulty < 0 || difficulty > 0xFF) {
                throw new PacketFormatException("难度超出 u8：" + difficulty);
            }
        }
    }

    public static final int PACKET_ID = 5;

    public static final int SENTINEL_COUNT = 0xFFFF;

    public Noti5DungeonPermissionBody {
        entries = List.copyOf(entries);
        if (entries.size() >= SENTINEL_COUNT) {
            throw new PacketFormatException("条数 " + entries.size()
                    + " 撞上客户端的错误哨兵 " + SENTINEL_COUNT + "，那会让它把整包当 0 条");
        }
    }

    public static Noti5DungeonPermissionBody none() {
        return new Noti5DungeonPermissionBody(List.of());
    }

    public static Noti5DungeonPermissionBody one(int dungeonId, int difficulty) {
        return new Noti5DungeonPermissionBody(List.of(new Entry(dungeonId, difficulty)));
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(2 + entries.size() * 3);
        w.u16(entries.size());
        for (Entry e : entries) {
            w.u16(e.dungeonId());
            w.u8(e.difficulty());
        }
        return w.toArray();
    }
}
