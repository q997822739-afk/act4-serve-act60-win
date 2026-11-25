package com.dylan.protocol.body;

public record Cmd2ReplyBody(int code) {

    public static final int PACKET_ID = 2;

    public static final int LENGTH = 1;

    public static final int OK = 1;

    public Cmd2ReplyBody {
        if (code < 0 || code > 0xFF) {
            throw new IllegalArgumentException("code 超出 u8：" + code);
        }
    }

    public static Cmd2ReplyBody ok() {
        return new Cmd2ReplyBody(OK);
    }

    public byte[] toBytes() {
        return new byte[] {(byte) code};
    }
}
