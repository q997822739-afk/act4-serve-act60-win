package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd99ReplyBody {

    public static final int PACKET_ID = 99;

    public static final int SUCCESS_LENGTH = 3;

    public static final int FAILURE_LENGTH = 2;

    private Cmd99ReplyBody() {
    }

    public static byte[] success(int notLoadedMailCount) {
        if (notLoadedMailCount < 0 || notLoadedMailCount > 0xFFFF) {
            throw new PacketFormatException("notLoadedMailCount 超出 u16：" + notLoadedMailCount);
        }
        return new PacketWriter(SUCCESS_LENGTH)
                .u8(1)
                .u16(notLoadedMailCount)
                .toArray();
    }

    public static byte[] failure(MailError error) {
        return new PacketWriter(FAILURE_LENGTH)
                .u8(0)
                .u8(error.code())
                .toArray();
    }
}
