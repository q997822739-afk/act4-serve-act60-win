package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketWriter;

public final class Cmd72ReplyBody {

    public static final int PACKET_ID = 72;

    private Cmd72ReplyBody() {
    }

    public static byte[] success() {
        PacketWriter w = new PacketWriter(1);
        w.u8(1);
        return w.toArray();
    }
}
