package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Cmd229ReplyBody(int result, int itemSpace, int slot, int error) {

    public static final int PACKET_ID = Cmd229ItemLockRequest.PACKET_ID_LOCK;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int ERROR_CANNOT = 19;

    public static final int ERROR_NO_ITEM = 21;

    public static final int ERROR_LOCK_FULL = 22;

    public static final int OK_LENGTH = 4;

    public static final int FAIL_LENGTH = 2;

    public static Cmd229ReplyBody locked(int itemSpace, int slot) {
        check(itemSpace, slot);
        return new Cmd229ReplyBody(RESULT_OK, itemSpace, slot, 0);
    }

    public static Cmd229ReplyBody rejected(int error) {
        if (error < 0 || error > 0xFF) {
            throw new PacketFormatException("错误码是 u8，超出 0..255：" + error);
        }
        return new Cmd229ReplyBody(RESULT_FAIL, 0, 0, error);
    }

    static void check(int itemSpace, int slot) {
        if (itemSpace < 0 || itemSpace > 0xFF) {
            throw new PacketFormatException("容器号是 u8，超出 0..255：" + itemSpace);
        }
        if (slot < 0 || slot > 0xFFFF) {
            throw new PacketFormatException("格号是 u16，超出 0..65535：" + slot);
        }
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
            return w.toArray();
        }
        PacketWriter w = new PacketWriter(FAIL_LENGTH);
        w.u8(RESULT_FAIL);
        w.u8(error);
        return w.toArray();
    }
}
