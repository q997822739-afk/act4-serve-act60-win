package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd28DisjointItemRequest(int slot, int itemSpace) {

    public static final int PACKET_ID = 28;

    public static final int LENGTH = 3;

    public static Cmd28DisjointItemRequest parse(byte[] plain) {
        if (plain.length < LENGTH) {
            throw new PacketFormatException("DISJOINT_ITEM 至少 " + LENGTH
                    + " 字节，实际 " + plain.length);
        }
        PacketReader r = new PacketReader(plain);
        int slot = r.u16();
        int itemSpace = r.u8();
        return new Cmd28DisjointItemRequest(slot, itemSpace);
    }

    @Override
    public String toString() {
        return "DISJOINT_ITEM[容器 " + itemSpace + " 的槽 " + slot + "]";
    }
}
