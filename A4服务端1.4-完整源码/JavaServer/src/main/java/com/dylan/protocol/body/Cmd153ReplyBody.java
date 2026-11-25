package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Cmd153ReplyBody(List<Entry> entries) {

    public static final int PACKET_ID = 153;

    public static final int RESULT_OK = 1;

    public static final int ENTRY_LENGTH = 10;

    public record Entry(int a, int b, int c) {
        public Entry {
            if (a < 0 || a > 0xFFFF) {
                throw new PacketFormatException("a 超出 u16：" + a);
            }
        }
    }

    public Cmd153ReplyBody {
        if (entries == null) {
            throw new PacketFormatException("entries 不能为 null");
        }
        entries = List.copyOf(entries);
        if (entries.size() > 0xFF) {
            throw new PacketFormatException("count 是 u8，装不下 " + entries.size() + " 条");
        }
    }

    public static Cmd153ReplyBody empty() {
        return new Cmd153ReplyBody(List.of());
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(2 + entries.size() * ENTRY_LENGTH);
        w.u8(RESULT_OK);
        w.u8(entries.size());
        for (Entry e : entries) {
            w.u16(e.a());
            w.u32(e.b());
            w.u32(e.c());
        }
        return w.toArray();
    }
}
