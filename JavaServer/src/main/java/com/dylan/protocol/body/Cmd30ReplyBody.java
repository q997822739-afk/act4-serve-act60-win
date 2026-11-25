package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd30ReplyBody {

    public static final int PACKET_ID = 30;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int LENGTH_OK = 3;

    public static final int LENGTH_FAIL = 2;

    private Cmd30ReplyBody() {
    }

    public static byte[] accepted(int slotA, int slotB) {
        checkU8("slotA", slotA);
        checkU8("slotB", slotB);
        PacketWriter w = new PacketWriter(LENGTH_OK);
        w.u8(RESULT_OK);
        w.u8(slotA);
        w.u8(slotB);
        return w.toArray();
    }

    public static byte[] rejected(int errorCode) {
        checkU8("errorCode", errorCode);
        PacketWriter w = new PacketWriter(LENGTH_FAIL);
        w.u8(RESULT_FAIL);
        w.u8(errorCode);
        return w.toArray();
    }

    private static void checkU8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(name + " 超出 u8：" + value);
        }
    }
}
