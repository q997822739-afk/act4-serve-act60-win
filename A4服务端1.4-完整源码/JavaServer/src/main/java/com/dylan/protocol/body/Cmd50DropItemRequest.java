package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd50DropItemRequest(int x, int y, int itemSpace, int slot,
                                   int count, int noGuard) {

    public static final int PACKET_ID = 50;

    public static final int LENGTH = 12;

    public static Cmd50DropItemRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException("DROP_ITEM 包体至少要 " + LENGTH + " 字节，实际 "
                    + (plain == null ? "null" : plain.length));
        }
        PacketReader r = new PacketReader(plain);
        return new Cmd50DropItemRequest(r.u16(), r.u16(), r.u8(), r.u16(), r.u32(), r.u8());
    }

    @Override
    public String toString() {
        return "丢弃 容器 " + itemSpace + " 第 " + slot + " 格 x" + count
                + "（站在 " + x + "," + y + (noGuard == 1 ? "，已确认" : "") + "）";
    }
}
