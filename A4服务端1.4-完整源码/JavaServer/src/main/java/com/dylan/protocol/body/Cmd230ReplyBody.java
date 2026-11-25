package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Cmd230ReplyBody(int result, int itemSpace, int slot, int remainSec,
                              int error, int arg) {

    public static final int PACKET_ID = Cmd229ItemLockRequest.PACKET_ID_UNLOCK;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int ERROR_TOO_FREQUENT = 7;

    public static final int OK_LENGTH = 8;

    public static final int FAIL_LENGTH = 6;

    public static Cmd230ReplyBody accepted(int itemSpace, int slot, int remainSec) {
        Cmd229ReplyBody.check(itemSpace, slot);
        return new Cmd230ReplyBody(RESULT_OK, itemSpace, slot, checkSec(remainSec), 0, 0);
    }

    public static Cmd230ReplyBody rejected(int error) {
        return rejected(error, 0);
    }

    public static Cmd230ReplyBody rejected(int error, int arg) {
        if (error < 0 || error > 0xFF) {
            throw new PacketFormatException("错误码是 u8，超出 0..255：" + error);
        }
        return new Cmd230ReplyBody(RESULT_FAIL, 0, 0, 0, error, checkSec(arg));
    }

    private static int checkSec(int value) {
        if (value < 0) {
            throw new PacketFormatException("秒数不能是负的：" + value);
        }
        return value;
    }

    public boolean ok() {
        return result != RESULT_FAIL;
    }

    public byte[] toBytes() {
        if (ok()) {
            PacketWriter w = new PacketWriter(OK_LENGTH);
            w.u8(result);
            w.u8(itemSpace);
            w.u16(slot);
            w.u32(remainSec);
            return w.toArray();
        }
        PacketWriter w = new PacketWriter(FAIL_LENGTH);
        w.u8(RESULT_FAIL);
        w.u8(error);
        w.u32(arg);
        return w.toArray();
    }
}
