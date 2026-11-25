package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti12MessageBody(int msgType, int userId, String text) {

    public static final int PACKET_ID = 12;

    public static final int TEXT_CAPACITY = 256;

    public static final int MAX_TEXT_BYTES = TEXT_CAPACITY - 1;

    public Noti12MessageBody {
        if (msgType < 0 || msgType > 0xFF) {
            throw new PacketFormatException("msgType 放不进 u8：" + msgType);
        }
        if (userId < 0 || userId > 0xFFFF) {
            throw new PacketFormatException("userId 放不进 u16：" + userId);
        }
        text = sanitize(text);
    }

    public static Noti12MessageBody system(String text) {
        return new Noti12MessageBody(ChatType.SYSTEM, 0, text);
    }

    public static Noti12MessageBody from(int msgType, int userId, String text) {
        return new Noti12MessageBody(msgType, userId, text);
    }

    public byte[] toBytes() {
        byte[] raw = text.getBytes(PacketWriter.DEFAULT_CHARSET);
        if (raw.length > MAX_TEXT_BYTES) {
            throw new PacketFormatException("正文 " + raw.length + " 字节，超过客户端的 "
                    + MAX_TEXT_BYTES + " 字节容量（超了它会静默换成自己的默认串）");
        }
        return new PacketWriter(7 + raw.length)
                .u8(msgType)
                .u16(userId)
                .string(text)
                .toArray();
    }

    public static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(c == '%' || c < 0x20 || c == 0x7F ? ' ' : c);
        }
        return sb.toString();
    }

    public static String fit(String text) {
        return fit(text, MAX_TEXT_BYTES);
    }

    public static String fit(String text, int maxBytes) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.getBytes(PacketWriter.DEFAULT_CHARSET).length <= maxBytes) {
            return text;
        }
        int used = 0;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            int width = String.valueOf(text.charAt(i))
                    .getBytes(PacketWriter.DEFAULT_CHARSET).length;
            if (used + width > maxBytes) {
                break;
            }
            used += width;
            sb.append(text.charAt(i));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "NOTI 12 " + ChatType.name(msgType)
                + (msgType == ChatType.SYSTEM ? "" : " userId=" + userId)
                + " 「" + text + "」";
    }
}
