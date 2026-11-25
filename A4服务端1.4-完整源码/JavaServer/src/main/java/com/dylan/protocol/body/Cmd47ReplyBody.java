package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketWriter;

public final class Cmd47ReplyBody {
    private Cmd47ReplyBody() {
    }

    public static final int PACKET_ID = 47;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int SUCCESS_LENGTH = 4;

    public static final int FAILURE_LENGTH = 3;

    public static final int ERROR_NOT_ENOUGH = 1;

    public static final int ERROR_BAD_SLOT = 4;

    public static final int ERROR_WRONG_JOB = 8;

    public static final int ERROR_LEVEL_TOO_LOW = 14;

    public static final int ERROR_SILENT = 17;

    public static final int ERROR_NOT_NOW = 19;

    public static final int ERROR_NOT_CONSUMABLE = 23;

    public static final int ERROR_NO_FATIGUE = 95;

    public static byte[] used(int slot, int itemSpace) {
        return new PacketWriter(SUCCESS_LENGTH)
                .u8(RESULT_OK)
                .u16(slot)
                .u8(itemSpace)
                .toArray();
    }

    public static byte[] rejected(int errorCode, int itemSpace) {
        return new PacketWriter(FAILURE_LENGTH)
                .u8(RESULT_FAIL)
                .u8(errorCode & 0xFF)
                .u8(itemSpace)
                .toArray();
    }
}
