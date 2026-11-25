package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketWriter;

public record Cmd105ReplyBody(boolean ok, int errorCode) {
    public static final int PACKET_ID = 105;

    public static final int ERROR_NOT_FOUND = 21;

    public static final int ERROR_NOT_EGG = 23;

    public static final int ERROR_HATCH_FAILED = 17;

    public static final int ERROR_ITEM_LOCKED = 213;

    public static Cmd105ReplyBody accepted() {
        return new Cmd105ReplyBody(true, 0);
    }

    public static Cmd105ReplyBody rejected(int errorCode) {
        return new Cmd105ReplyBody(false, errorCode);
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(2);
        if (ok) {
            w.u8(1);
        } else {
            w.u8(0);
            w.u8(errorCode);
        }
        return w.toArray();
    }
}
