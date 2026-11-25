package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti131CreatureScriptMessageBody(int msgType, int userId, String text) {

    public static final int PACKET_ID = 131;

    public static final int TEXT_CAPACITY = 256;

    public static final int MAX_TEXT_BYTES = TEXT_CAPACITY - 1;

    public Noti131CreatureScriptMessageBody {
        if (msgType < 0 || msgType > 0xFF) {
            throw new PacketFormatException("msgType 放不进 u8：" + msgType);
        }
        if (userId < 0 || userId > 0xFFFF) {
            throw new PacketFormatException("userId 放不进 u16：" + userId);
        }
        text = Noti12MessageBody.sanitize(text);
    }

    public static Noti131CreatureScriptMessageBody from(int msgType, int userId, String text) {
        return new Noti131CreatureScriptMessageBody(msgType, userId, text);
    }

    public byte[] toBytes() {
        byte[] raw = text.getBytes(PacketWriter.DEFAULT_CHARSET);
        if (raw.length > MAX_TEXT_BYTES) {
            throw new PacketFormatException("台词 " + raw.length + " 字节，超过客户端的 "
                    + MAX_TEXT_BYTES + " 字节容量（超了它会静默换成自己的默认串）");
        }
        return new PacketWriter(7 + raw.length)
                .u8(msgType)
                .u16(userId)
                .string(text)
                .toArray();
    }

    @Override
    public String toString() {
        return "NOTI 131 CREATURE_SCRIPT_MESSAGE " + ChatType.name(msgType)
                + " userId=" + userId + " 「" + text + "」";
    }
}
