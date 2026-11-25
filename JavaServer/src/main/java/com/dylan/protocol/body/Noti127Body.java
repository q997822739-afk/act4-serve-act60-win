package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti127Body(int confirmed, int clearRoom, int seqId) {

    public static final int PACKET_ID = 127;

    public static final int DENIED_LENGTH = 2;

    public static final int CONFIRMED_LENGTH = 4;

    public Noti127Body {
        if (confirmed < 0 || confirmed > 0xFF || clearRoom < 0 || clearRoom > 0xFF) {
            throw new PacketFormatException("confirmed 和 clearRoom 都是 u8："
                    + confirmed + "," + clearRoom);
        }
        if (seqId < 0 || seqId > 0xFFFF) {
            throw new PacketFormatException("seqId 超出 u16：" + seqId);
        }
    }

    public static Noti127Body confirmAndClear(int seqId) {
        return new Noti127Body(1, 1, seqId);
    }

    public static Noti127Body confirmOnly(int seqId) {
        return new Noti127Body(1, 0, seqId);
    }

    public static Noti127Body denied(int error) {
        return new Noti127Body(0, 0, error & 0xFF);
    }

    public byte[] toBytes() {
        if (confirmed == 0) {
            PacketWriter w = new PacketWriter(DENIED_LENGTH);
            w.u8(0);
            w.u8(seqId & 0xFF);
            return w.toArray();
        }
        PacketWriter w = new PacketWriter(CONFIRMED_LENGTH);
        w.u8(confirmed);
        w.u8(clearRoom);
        w.u16(seqId);
        return w.toArray();
    }
}
