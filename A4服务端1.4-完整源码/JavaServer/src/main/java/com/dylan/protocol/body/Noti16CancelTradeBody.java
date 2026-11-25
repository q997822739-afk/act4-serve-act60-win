package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti16CancelTradeBody(List<Entry> entries) {
    public static final int PACKET_ID = 16;

    public static final int ENTRY_LENGTH = 5;

    public record Entry(int tradeSlot, int itemSpace, int landedSlot) {
        public Entry {
            checkU16("tradeSlot", tradeSlot);
            checkU8("itemSpace", itemSpace);
            if (landedSlot < Short.MIN_VALUE || landedSlot > 0xFFFF) {
                throw new PacketFormatException("landedSlot 放不进 u16：" + landedSlot);
            }
        }
    }

    public Noti16CancelTradeBody {
        if (entries == null) {
            throw new PacketFormatException("entries 不能为 null");
        }
        entries = List.copyOf(entries);
        if (entries.size() > 0xFFFF) {
            throw new PacketFormatException("条数是 u16，装不下 " + entries.size() + " 条");
        }
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(2 + entries.size() * ENTRY_LENGTH);
        w.u16(entries.size());
        for (Entry e : entries) {
            w.u16(e.tradeSlot());
            w.u8(e.itemSpace());
            w.u16(e.landedSlot() & 0xFFFF);
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
