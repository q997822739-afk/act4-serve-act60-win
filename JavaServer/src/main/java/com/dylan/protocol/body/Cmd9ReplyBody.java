package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd9ReplyBody {
    public static final int PACKET_ID = 9;

    public static final int ERROR_NOT_WEAKENED = 18;

    public static final int ERROR_NOT_ENOUGH_MONEY = 22;

    private Cmd9ReplyBody() {
    }

    public static byte[] recovered(long money) {
        if (money < 0 || money > 0xFFFFFFFFL) {
            throw new PacketFormatException("金币超出 u32：" + money);
        }
        PacketWriter w = new PacketWriter(5);
        w.u8(1);
        w.u32((int) money);
        return w.toArray();
    }

    public static byte[] rejected(int error) {
        if (error < 0 || error > 0xFF) {
            throw new PacketFormatException("错误码超出 u8：" + error);
        }
        PacketWriter w = new PacketWriter(2);
        w.u8(0);
        w.u8(error);
        return w.toArray();
    }
}
