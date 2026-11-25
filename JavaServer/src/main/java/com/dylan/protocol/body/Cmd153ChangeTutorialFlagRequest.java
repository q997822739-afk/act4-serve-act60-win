package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Cmd153ChangeTutorialFlagRequest(int bit, int value) {
    public static final int PACKET_ID = 153;

    public static final int LENGTH = 5;

    public static final int MAX_BIT = 65;

    public Cmd153ChangeTutorialFlagRequest {
        if (bit < 0 || bit > MAX_BIT) {
            throw new PacketFormatException("教程位号超出 ACT5 的 0.." + MAX_BIT + "：" + bit);
        }
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException("教程位的值超出 u8：" + value);
        }
    }

    public static Cmd153ChangeTutorialFlagRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException("CHANGE_TUTORIAL_FLAG 包体至少要 " + LENGTH
                    + " 字节，实际 " + (plain == null ? "null" : plain.length));
        }
        return new Cmd153ChangeTutorialFlagRequest(u32(plain, 0), plain[4] & 0xFF);
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(LENGTH);
        w.u32(bit);
        w.u8(value);
        return w.toArray();
    }

    private static int u32(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }
}
