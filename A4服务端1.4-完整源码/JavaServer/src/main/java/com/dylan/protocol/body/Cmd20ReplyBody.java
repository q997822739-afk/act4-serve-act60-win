package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd20ReplyBody {

    public static final int PACKET_ID = 20;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int ERROR_SILENT = 21;

    public static final int LENGTH_OK = 2;

    public static final int LENGTH_FAIL = 3;

    private Cmd20ReplyBody() {
    }

    public static byte[] accepted(int listType) {
        checkU8("listType", listType);
        PacketWriter w = new PacketWriter(LENGTH_OK);
        w.u8(RESULT_OK);
        w.u8(listType);
        return w.toArray();
    }

    public static byte[] rejected(int errorCode, int listType) {
        checkU8("errorCode", errorCode);
        checkU8("listType", listType);
        PacketWriter w = new PacketWriter(LENGTH_FAIL);
        w.u8(RESULT_FAIL);
        w.u8(errorCode);
        w.u8(listType);
        return w.toArray();
    }

    private static void checkU8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(name + " 超出 u8：" + value);
        }
    }
}
