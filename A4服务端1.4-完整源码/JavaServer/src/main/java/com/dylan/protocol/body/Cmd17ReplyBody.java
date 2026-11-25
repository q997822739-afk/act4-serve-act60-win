package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;

public final class Cmd17ReplyBody {
    public static final int PACKET_ID = 17;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    private Cmd17ReplyBody() {
    }

    public static byte[] success() {
        return new byte[]{(byte) RESULT_OK};
    }

    public static byte[] rejected(int errorCode) {
        if (errorCode < 0 || errorCode > 0xFF) {
            throw new PacketFormatException("错误码超出 u8：" + errorCode);
        }
        return new byte[]{(byte) RESULT_FAIL, (byte) errorCode};
    }
}
