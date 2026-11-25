package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketWriter;

public record Cmd103ReplyBody(boolean ok, int slot, int itemSpace, int errorCode) {
    public static final int PACKET_ID = 103;

    public static final int ERROR_NO_CREATURE = 4;

    public static final int ERROR_BAD_NAME = 159;

    public static final int ERROR_NO_ITEM = 23;

    public static Cmd103ReplyBody accepted(int slot, int itemSpace) {
        return new Cmd103ReplyBody(true, slot, itemSpace, 0);
    }

    public static Cmd103ReplyBody rejected(int errorCode) {
        return new Cmd103ReplyBody(false, 0, 0, errorCode);
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(4);
        if (ok) {
            w.u8(1);
            w.u16(slot);
            w.u8(itemSpace);
        } else {
            w.u8(0);
            w.u8(errorCode);
        }
        return w.toArray();
    }
}
