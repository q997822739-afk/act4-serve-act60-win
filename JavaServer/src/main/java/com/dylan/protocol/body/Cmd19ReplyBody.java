package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd19ReplyBody {

    public static final int PACKET_ID = 19;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int ERROR_CANNOT_MOVE = 17;

    public static final int ERROR_CARGO_FULL = 4;

    public static final int ERROR_CANNOT_STORE = 23;

    public static final int LENGTH_OK = 11;

    public static final int LENGTH_FAIL = 4;

    private Cmd19ReplyBody() {
    }

    public static byte[] accepted(int srcType, int srcSlot, int count, int dstType, int dstSlot) {
        checkU8("srcType", srcType);
        checkU16("srcSlot", srcSlot);
        checkU8("dstType", dstType);
        checkU16("dstSlot", dstSlot);
        PacketWriter w = new PacketWriter(LENGTH_OK);
        w.u8(RESULT_OK);
        w.u8(srcType);
        w.u16(srcSlot);
        w.u32(count);
        w.u8(dstType);
        w.u16(dstSlot);
        return w.toArray();
    }

    public static byte[] rejected(int errorCode, int srcType, int dstType) {
        checkU8("errorCode", errorCode);
        checkU8("srcType", srcType);
        checkU8("dstType", dstType);
        PacketWriter w = new PacketWriter(LENGTH_FAIL);
        w.u8(RESULT_FAIL);
        w.u8(errorCode);
        w.u8(srcType);
        w.u8(dstType);
        return w.toArray();
    }

    private static void checkU8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(name + " 超出 u8：" + value);
        }
    }

    private static void checkU16(String name, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new PacketFormatException(name + " 超出 u16：" + value);
        }
    }
}
