package com.dylan.protocol.body;

public final class Cmd65ReplyBody {

    private Cmd65ReplyBody() {
    }

    public static final int PACKET_ID = Cmd65DebugCommandRequest.PACKET_ID;

    public static final int MAX_TALKING_SUB_COMMAND = 6;

    public static byte[] done(int subCommandId) {
        return new byte[]{1, (byte) subCommandId};
    }

    public static byte[] refused() {
        return new byte[]{0};
    }
}
