package com.dylan.protocol.body;

public final class Cmd3ReplyBody {

    public static final int PACKET_ID = 3;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    private Cmd3ReplyBody() {
    }

    public static byte[] success() {
        return new byte[]{(byte) RESULT_OK};
    }
}
