package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd280AntiBotRequest(int length, int serial) {

    public static final int PACKET_ID = 280;

    public static final int SERIAL_OFFSET = 2;

    public static final int MIN_BLOB = SERIAL_OFFSET + 4;

    public static Cmd280AntiBotRequest parse(byte[] body) {
        PacketReader r = new PacketReader(body);
        int length = r.u32();
        if (length < 0 || 4 + length > body.length) {
            throw new PacketFormatException(
                    "CMD 280 ANTIBOT 的长度字段是 " + length + "，而明文只有 " + body.length
                            + " 字节（判据 4 + length <= 明文长度，尾部允许有 AES 填充）");
        }
        if (length < MIN_BLOB) {
            return new Cmd280AntiBotRequest(length, -1);
        }
        r.skip(SERIAL_OFFSET);
        return new Cmd280AntiBotRequest(length, beU32(r.bytes(4)));
    }

    private static int beU32(byte[] b) {
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16)
                | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    }

    public boolean followsAfter(int previousSerial) {
        return previousSerial == 0 || serial < 0 || previousSerial + 1 == serial;
    }

    @Override
    public String toString() {
        return "CMD 280 ANTIBOT[blob=" + length + " 字节 serial="
                + Integer.toUnsignedString(serial) + "]";
    }
}
