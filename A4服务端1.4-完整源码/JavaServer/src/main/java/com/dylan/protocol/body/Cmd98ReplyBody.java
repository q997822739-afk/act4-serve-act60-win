package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd98ReplyBody {

    public static final int PACKET_ID = 98;

    public static final int SUCCESS_LENGTH = 5;

    public static final int FAILURE_LENGTH = 2;

    private Cmd98ReplyBody() {
    }

    public static byte[] success(long mailIdx) {
        if (mailIdx < 0 || mailIdx > 0xFFFF_FFFFL) {
            throw new PacketFormatException("mailIdx 超出 u32：" + mailIdx);
        }
        return new PacketWriter(SUCCESS_LENGTH)
                .u8(1)
                .u32((int) mailIdx)
                .toArray();
    }

    public static byte[] failure(MailError error) {
        return new PacketWriter(FAILURE_LENGTH)
                .u8(0)
                .u8(error.code())
                .toArray();
    }
}
