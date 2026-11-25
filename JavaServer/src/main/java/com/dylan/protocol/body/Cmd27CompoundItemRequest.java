package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd27CompoundItemRequest(long slotOrItemId, boolean byItemId, int count) {

    public static final int PACKET_ID = 27;

    public static final int LENGTH = 7;

    public Cmd27CompoundItemRequest {
        if (slotOrItemId < 0 || slotOrItemId > 0xFFFFFFFFL) {
            throw new PacketFormatException("slotOrItemId 放不进 u32：" + slotOrItemId);
        }
        if (count < 0 || count > 0xFFFF) {
            throw new PacketFormatException("count 放不进 u16：" + count);
        }
    }

    public static Cmd27CompoundItemRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException(
                    "做东西的包体至少 " + LENGTH + " 字节，实际 "
                            + (plain == null ? "null" : plain.length));
        }
        PacketReader r = new PacketReader(plain);
        long first = r.u32() & 0xFFFFFFFFL;
        int flag = r.u8();
        int count = r.u16();
        return new Cmd27CompoundItemRequest(first, flag == 1, count);
    }

    @Override
    public String toString() {
        return (byItemId ? "配方 " : "槽 ") + slotOrItemId + " × " + count;
    }
}
