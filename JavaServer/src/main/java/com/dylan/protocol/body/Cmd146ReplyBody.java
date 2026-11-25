package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Cmd146ReplyBody(String name, int level, int job, int growType, int sex,
                              int giftRecvCount) {
    public static final int PACKET_ID = 146;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int ERROR_SELF = 7;

    public static final int ERROR_NO_SUCH_CHARACTER = 21;

    public Cmd146ReplyBody {
        checkU16("level", level);
        checkU8("job", job);
        checkU8("growType", growType);
        checkU8("sex", sex);
        checkS8OrU8("giftRecvCount", giftRecvCount);
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(32);
        w.u8(RESULT_OK);
        w.string(name);
        w.u16(level);
        w.u8(job);
        w.u8(growType);
        w.u8(sex);
        w.u8(giftRecvCount & 0xFF);
        return w.toArray();
    }

    public static byte[] failure(int errorCode) {
        if (errorCode < 0 || errorCode > 0xFF) {
            throw new PacketFormatException("errorCode 超出 u8：" + errorCode);
        }
        PacketWriter w = new PacketWriter(2);
        w.u8(RESULT_FAIL);
        w.u8(errorCode);
        return w.toArray();
    }

    private static void checkU8(String what, int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(what + " 超出 u8：" + value);
        }
    }

    private static void checkS8OrU8(String what, int value) {
        if (value < Byte.MIN_VALUE || value > 0xFF) {
            throw new PacketFormatException(what + " 既不是 s8 也不是 u8：" + value);
        }
    }

    private static void checkU16(String what, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new PacketFormatException(what + " 超出 u16：" + value);
        }
    }
}
