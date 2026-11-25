package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketWriter;

public final class Cmd97ReplyBody {

    public static final int PACKET_ID = 97;

    public static final int SUCCESS_LENGTH = 1;

    public static final int FAILURE_LENGTH = 2;

    private Cmd97ReplyBody() {
    }

    public static byte[] success() {
        return new PacketWriter(SUCCESS_LENGTH).u8(1).toArray();
    }

    public static byte[] failure(MailError error) {
        return new PacketWriter(FAILURE_LENGTH)
                .u8(0)
                .u8(error.code())
                .toArray();
    }
}
