package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti18FinishTradeBody(List<Entry> entries) {
    public static final int PACKET_ID = 18;

    public static final int ENTRY_LENGTH = 4;

    public record Entry(int tradeSlot, int landedSlot) {
        public Entry {
            if (tradeSlot < 0 || tradeSlot > 0xFFFF) {
                throw new PacketFormatException("tradeSlot 放不进 u16：" + tradeSlot);
            }
            if (landedSlot < Short.MIN_VALUE || landedSlot > 0xFFFF) {
                throw new PacketFormatException("landedSlot 放不进 u16：" + landedSlot);
            }
        }
    }

    public Noti18FinishTradeBody {
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
            w.u16(e.landedSlot() & 0xFFFF);
        }
        return w.toArray();
    }
}
