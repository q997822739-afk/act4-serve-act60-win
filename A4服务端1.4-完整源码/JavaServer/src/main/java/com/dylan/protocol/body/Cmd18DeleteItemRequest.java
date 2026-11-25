package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

import java.util.ArrayList;
import java.util.List;

public record Cmd18DeleteItemRequest(int listType, List<Entry> entries, long invenCheckSum) {
    public static final int PACKET_ID = 18;

    public static final int MAX_ENTRIES = 100;

    public static final int REASON_DISCARD = 1;

    public static final int FIXED_LENGTH = 2 + 4;

    public static final int ENTRY_LENGTH = 12;

    public Cmd18DeleteItemRequest {
        entries = List.copyOf(entries);
    }

    public record Entry(int reason, int slot, int itemId, int amount) {
        public boolean discard() {
            return reason == REASON_DISCARD;
        }
    }

    public static Cmd18DeleteItemRequest parse(byte[] plain) {
        PacketReader r = new PacketReader(plain);
        int listType = r.u8();
        int count = r.u8();
        if (count > MAX_ENTRIES) {
            throw new PacketFormatException("DELETE_ITEM 一次最多 " + MAX_ENTRIES
                    + " 格，客户端报了 " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int reason = r.u16();
            int slot = r.u16();
            int itemId = r.u32();
            int amount = r.u32();
            if (amount <= 0) {
                throw new PacketFormatException("DELETE_ITEM 第 " + i
                        + " 条的数量必须为正，amount=" + amount);
            }
            entries.add(new Entry(reason, slot, itemId, amount));
        }
        long invenCheckSum = r.remaining() >= 4 ? Integer.toUnsignedLong(r.u32()) : 0L;
        return new Cmd18DeleteItemRequest(listType, entries, invenCheckSum);
    }
}
