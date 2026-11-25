package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Cmd273ReplyBody(int result, int emotion, int error) {

    public static final int PACKET_ID = 273;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int ERROR_INVALID = 1;

    public static final int ERROR_BAD_STATE = 3;

    public static final int OK_LENGTH = 3;

    public static final int FAIL_LENGTH = 2;

    public static Cmd273ReplyBody changed(int emotion) {
        if (emotion < 0 || emotion > 0xFFFF) {
            throw new PacketFormatException("表情号是 u16，超出 0..65535：" + emotion);
        }
        return new Cmd273ReplyBody(RESULT_OK, emotion, 0);
    }

    public static Cmd273ReplyBody rejected(int error) {
        if (error < 0 || error > 0xFF) {
            throw new PacketFormatException("错误码是 u8，超出 0..255：" + error);
        }
        return new Cmd273ReplyBody(RESULT_FAIL, 0, error);
    }

    public boolean ok() {
        return result != RESULT_FAIL;
    }

    public byte[] toBytes() {
        if (ok()) {
            PacketWriter w = new PacketWriter(OK_LENGTH);
            w.u8(result);
            w.u16(emotion);
            return w.toArray();
        }
        PacketWriter w = new PacketWriter(FAIL_LENGTH);
        w.u8(RESULT_FAIL);
        w.u8(error);
        return w.toArray();
    }
}
