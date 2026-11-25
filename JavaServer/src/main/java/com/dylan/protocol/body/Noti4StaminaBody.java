package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Noti4StaminaBody {

    public static final int PACKET_ID = 4;

    public static final int LENGTH = 1;

    private Noti4StaminaBody() {
    }

    public static byte[] of(int stamina) {
        if (stamina < 0 || stamina > 0xFF) {
            throw new PacketFormatException("体力超出 u8：" + stamina);
        }
        PacketWriter w = new PacketWriter(LENGTH);
        w.u8(stamina);
        return w.toArray();
    }
}
