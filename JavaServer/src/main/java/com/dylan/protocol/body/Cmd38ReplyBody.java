package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd38ReplyBody {
    public static final int PACKET_ID = 38;

    public static final int RESULT_FAIL = 0;

    public static final int ERROR_21 = 0x15;

    private Cmd38ReplyBody() {
    }

    public static byte[] rejected(int errorCode, int trailing) {
        if (errorCode < 0 || errorCode > 0xFF) {
            throw new PacketFormatException("errorCode 超出 u8：" + errorCode);
        }
        if (trailing < 0 || trailing > 0xFF) {
            throw new PacketFormatException("trailing 超出 u8：" + trailing);
        }
        PacketWriter w = new PacketWriter(3);
        w.u8(RESULT_FAIL);
        w.u8(errorCode);
        w.u8(trailing);
        return w.toArray();
    }
}
