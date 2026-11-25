package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd25ReplyBody {
    private Cmd25ReplyBody() {
    }

    public static final int PACKET_ID = 25;

    public static final int SUCCESS_LENGTH = 8;

    public static final int ERROR_NOT_ENOUGH_MONEY = 10;

    public static final int ERROR_INVALID = 17;

    public static final int ERROR_BUSY = 19;

    public static byte[] repaired(long gold, int itemSpace, int slot) {
        return new PacketWriter(SUCCESS_LENGTH)
                .u8(1)
                .u32((int) requireU32("金币", gold))
                .u8(requireU8("容器", itemSpace))
                .u16(requireU16("槽号", slot))
                .toArray();
    }

    public static byte[] rejected(int errorCode) {
        return new byte[]{0, (byte) requireU8("错误码", errorCode)};
    }

    private static long requireU32(String field, long value) {
        if (value < 0 || value > 0xFFFFFFFFL) {
            throw new PacketFormatException(field + " " + value + " 放不进 u32");
        }
        return value;
    }

    private static int requireU16(String field, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new PacketFormatException(field + " " + value + " 放不进 u16");
        }
        return value;
    }

    private static int requireU8(String field, int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(field + " " + value + " 放不进 u8");
        }
        return value;
    }
}
