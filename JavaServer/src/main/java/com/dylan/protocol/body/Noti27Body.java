package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti27Body(int openingValue, int abyssFlag, List<Integer> blockers,
                         List<Group> groups, int closingValue) {

    public static final int PACKET_ID = 27;

    public static final int EMPTY_SLOT = 0xFFFF;

    public static final int ABYSS_OK = 1;

    public static final int MAX_BLOCKERS = 4;

    public record Group(int groupId, List<Entry> entries) {
        public Group {
            checkU16("groupId", groupId);
            if (entries == null) {
                throw new PacketFormatException("entries 不能为 null");
            }
            entries = List.copyOf(entries);
            if (entries.size() > 0xFF) {
                throw new PacketFormatException("entryCount 是 u8，装不下 " + entries.size() + " 条");
            }
        }
    }

    public record Entry(int a, int b, int c, int d) {

        public static final int LENGTH = 14;

        public Entry {
            checkU16("a", a);
        }
    }

    public Noti27Body {
        checkU8("abyssFlag", abyssFlag);
        if (blockers == null || groups == null) {
            throw new PacketFormatException("blockers 和 groups 都不能为 null");
        }
        blockers = List.copyOf(blockers);
        groups = List.copyOf(groups);
        if (blockers.size() > MAX_BLOCKERS) {
            throw new PacketFormatException("blockers 最多 " + MAX_BLOCKERS
                    + " 项（客户端栈缓冲就这么大），收到 " + blockers.size() + " 条");
        }
        if (groups.size() > 0xFF) {
            throw new PacketFormatException("groupCount 是 u8，装不下 " + groups.size() + " 条");
        }
        for (int id : blockers) {
            checkU16("memberId", id);
        }
    }

    public static Noti27Body noAbyssBlockers() {
        return new Noti27Body(0, ABYSS_OK, List.of(), List.of(), 0);
    }

    public byte[] toBytes() {
        if (com.dylan.net.ServerConfig.A4) {
            // A4 客户端（DNF.exe 00424483）只读 u8 标志 + u8 条数 + 条数×u16 成员号，
            // 没有 ACT5 前后两个 u32 和分组表（官方 00 01 00 00 / 01 01 00 00）。
            PacketWriter w = new PacketWriter(2 + blockers.size() * 2);
            w.u8(abyssFlag);
            w.u8(blockers.size());
            for (int id : blockers) {
                w.u16(id);
            }
            return w.toArray();
        }
        int size = 11 + blockers.size() * 2;
        for (Group g : groups) {
            size += 3 + g.entries().size() * Entry.LENGTH;
        }
        PacketWriter w = new PacketWriter(size);
        w.u32(openingValue);
        w.u8(abyssFlag);
        w.u8(blockers.size());
        for (int id : blockers) {
            w.u16(id);
        }
        w.u8(groups.size());
        for (Group g : groups) {
            w.u16(g.groupId());
            w.u8(g.entries().size());
            for (Entry e : g.entries()) {
                w.u16(e.a());
                w.u32(e.b());
                w.u32(e.c());
                w.u32(e.d());
            }
        }
        w.u32(closingValue);
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
