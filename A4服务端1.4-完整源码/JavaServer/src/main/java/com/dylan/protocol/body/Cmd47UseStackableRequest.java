package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd47UseStackableRequest(int slot, int itemSpace, long echo) {
    public static final int PACKET_ID = 47;

    public static final int LENGTH = 7;

    public static Cmd47UseStackableRequest parse(byte[] plain) {
        if (plain.length < LENGTH) {
            throw new PacketFormatException("USE_STACKABLE 至少 " + LENGTH
                    + " 字节，实际 " + plain.length);
        }
        PacketReader r = new PacketReader(plain);
        int slot = r.u16();
        int itemSpace = r.u8();
        long echo = r.u32() & 0xFFFFFFFFL;
        return new Cmd47UseStackableRequest(slot, itemSpace, echo);
    }

    public static int firstNonZeroTail(byte[] plain) {
        for (int i = LENGTH; i < plain.length; i++) {
            if (plain[i] != 0) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String toString() {
        return "USE_STACKABLE[容器 " + itemSpace + " 槽 " + slot
                + " 回声 0x" + Long.toHexString(echo) + "]";
    }
}
