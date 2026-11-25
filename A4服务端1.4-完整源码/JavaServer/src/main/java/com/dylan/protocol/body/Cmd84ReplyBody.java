package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd84ReplyBody {

    private Cmd84ReplyBody() {
    }

    public static final int PACKET_ID = 84;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int KIND_RESEAL = 1;

    public static final int KIND_RESET_GRADE = 2;

    public static final int ERROR_NO_SUCH_ITEM = 4;

    public static final int ERROR_RESEAL_COUNT_MAX = 13;

    public static final int ERROR_NO_ITEM = 17;

    public static final int ERROR_ALREADY_SEALED = 18;

    public static final int ERROR_NOT_APPLICABLE = 19;

    public static final int ERROR_NOT_ENOUGH_ITEM = 22;

    public static byte[] accepted(int boxSlot, long boxLeft, int kind) {
        if (kind != KIND_RESEAL && kind != KIND_RESET_GRADE) {
            throw new PacketFormatException("kind " + kind + " 客户端不认（只有 1 和 2）");
        }
        if (boxSlot < 0 || boxSlot > 0xFFFF) {
            throw new PacketFormatException("道具槽放不进 u16：" + boxSlot);
        }
        if (boxLeft < 0 || boxLeft > 0xFFFF_FFFFL) {
            throw new PacketFormatException("道具余量放不进 u32：" + boxLeft);
        }
        return new PacketWriter(9)
                .u8(RESULT_OK)
                .u16(boxSlot)
                .u32((int) boxLeft)
                .u16(kind)
                .toArray();
    }

    public static byte[] rejected(int errorCode) {
        if (errorCode < 0 || errorCode > 0xFF) {
            throw new PacketFormatException("错误码放不进 u8：" + errorCode);
        }
        return new byte[]{(byte) RESULT_FAIL, (byte) errorCode};
    }
}
