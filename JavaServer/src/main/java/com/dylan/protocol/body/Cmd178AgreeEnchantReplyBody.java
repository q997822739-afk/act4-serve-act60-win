package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Cmd178AgreeEnchantReplyBody(int result, int success, int error) {

    public static final int PACKET_ID = Cmd178AgreeEnchantRequest.PACKET_ID;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int LENGTH = 2;

    public static Cmd178AgreeEnchantReplyBody done(boolean success) {
        return new Cmd178AgreeEnchantReplyBody(RESULT_OK, success ? 1 : 0, EnchantError.NONE);
    }

    public static Cmd178AgreeEnchantReplyBody rejected(int error) {
        if (error < 0 || error > 0xFF) {
            throw new PacketFormatException("错误码是 u8：" + error);
        }
        return new Cmd178AgreeEnchantReplyBody(RESULT_FAIL, 0, error);
    }

    public boolean ok() {
        return result != RESULT_FAIL;
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(LENGTH);
        w.u8(result);
        w.u8(ok() ? success : error);
        return w.toArray();
    }
}
