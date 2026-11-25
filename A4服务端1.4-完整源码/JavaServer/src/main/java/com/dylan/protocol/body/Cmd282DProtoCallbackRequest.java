package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd282DProtoCallbackRequest(int length, int innerLength, int serial) {
    public static final int PACKET_ID = 282;

    public static final int MAX_LENGTH = 0x567;

    private static final int MIN_BLOB = 18;

    public static Cmd282DProtoCallbackRequest parse(byte[] body) {
        PacketReader r = new PacketReader(body);
        int length = r.u32();
        if (length < 0 || 4 + length > body.length) {
            throw new PacketFormatException(
                    "CMD 282 DPROTO_CALLBACK 的长度字段是 " + length + "，而明文只有 "
                            + body.length + " 字节（判据 4 + length <= 明文长度）");
        }
        if (length > MAX_LENGTH) {
            throw new PacketFormatException(
                    "CMD 282 DPROTO_CALLBACK 声明了 " + length + " 字节，超过官方上限 "
                            + MAX_LENGTH + "（0x567）—— 官方在这里 AbnormalUser + 断连");
        }
        if (length < MIN_BLOB) {
            return new Cmd282DProtoCallbackRequest(length, -1, -1);
        }
        byte[] blob = r.bytes(MIN_BLOB);
        int innerLength = ((blob[0] & 0xFF) << 8) | (blob[1] & 0xFF);
        int serial = ((blob[14] & 0xFF) << 24) | ((blob[15] & 0xFF) << 16)
                | ((blob[16] & 0xFF) << 8) | (blob[17] & 0xFF);
        return new Cmd282DProtoCallbackRequest(length, innerLength, serial);
    }

    public boolean followsAfter(int previousSerial) {
        return previousSerial <= 0 || serial < 0 || previousSerial + 1 == serial;
    }

    @Override
    public String toString() {
        return "CMD 282 DPROTO_CALLBACK[blob=" + length + " 字节 内层自述=" + innerLength
                + " serial=" + serial + "]";
    }
}
