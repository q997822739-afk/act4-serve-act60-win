package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti53CeraBody(boolean ok, long cera) {

    public static final int PACKET_ID = 53;

    public static final int LENGTH = 5;

    public static final long MAX_CERA = 0xFFFFFFFFL;

    public static Noti53CeraBody of(long cera) {
        return new Noti53CeraBody(true, cera);
    }

    public static Noti53CeraBody failed() {
        return new Noti53CeraBody(false, 0L);
    }

    public byte[] toBytes() {
        if (cera < 0 || cera > MAX_CERA) {
            throw new PacketFormatException("点券余额 " + cera + " 超出 u32");
        }
        PacketWriter w = new PacketWriter(LENGTH);
        w.u8(ok ? 1 : 0);
        w.u32((int) cera);
        return w.toArray();
    }
}
