package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd49ReplyBody {
    public static final int PACKET_ID = 49;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    private Cmd49ReplyBody() {
    }

    public static byte[] success() {
        PacketWriter w = new PacketWriter(1);
        w.u8(RESULT_OK);
        return w.toArray();
    }

    public static byte[] rejected(int errorCode) {
        if (errorCode < 0 || errorCode > 0xFF) {
            throw new PacketFormatException("errorCode 超出 u8：" + errorCode);
        }
        PacketWriter w = new PacketWriter(2);
        w.u8(RESULT_FAIL);
        w.u8(errorCode);
        return w.toArray();
    }
}
