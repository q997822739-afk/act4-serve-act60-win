package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;

public final class Cmd51ReplyBody {

    public static final int PACKET_ID = 51;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int ERROR_SILENT = 21;

    public static final int LENGTH = 2;

    private Cmd51ReplyBody() {
    }

    public static byte[] accepted(int slot) {
        checkU8(slot);
        return new byte[]{(byte) RESULT_OK, (byte) slot};
    }

    public static byte[] rejected(int errorCode) {
        checkU8(errorCode);
        return new byte[]{(byte) RESULT_FAIL, (byte) errorCode};
    }

    private static void checkU8(int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException("超出 u8：" + value);
        }
    }
}
