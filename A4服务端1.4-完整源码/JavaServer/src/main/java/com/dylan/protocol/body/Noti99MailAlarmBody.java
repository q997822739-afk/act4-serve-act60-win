package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti99MailAlarmBody(int notLoadedMailCount) {

    public static final int PACKET_ID = 99;

    public static final int LENGTH = 2;

    public Noti99MailAlarmBody {
        if (notLoadedMailCount < 0 || notLoadedMailCount > 0xFFFF) {
            throw new PacketFormatException("notLoadedMailCount 超出 u16：" + notLoadedMailCount);
        }
    }

    public byte[] toBytes() {
        return new PacketWriter(LENGTH).u16(notLoadedMailCount).toArray();
    }
}
