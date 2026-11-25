package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;
import com.dylan.protocol.io.PacketWriter;

import java.nio.charset.Charset;

public record Cmd97MailBoxSendRequest(String receiverName, long gold, int itemSpace, int slot,
                                      long itemId, long count, String text, long guildMail) {

    public static final int PACKET_ID = 97;

    public static final int MAX_RECEIVER_BYTES = 29;

    public static final int MAX_TEXT_BYTES = 256;

    private static final Charset CHARSET = PacketWriter.DEFAULT_CHARSET;

    public static final long NO_ITEM = 0L;

    public boolean hasItem() {
        return itemId != NO_ITEM;
    }

    public boolean guild() {
        return guildMail != 0L;
    }

    public static Cmd97MailBoxSendRequest parse(byte[] plain) {
        if (plain == null) {
            throw new PacketFormatException("CMD " + PACKET_ID + " 包体是 null");
        }
        PacketReader r = new PacketReader(plain);
        String receiver = readString(r, MAX_RECEIVER_BYTES, "收件人名字");
        long gold = r.u32() & 0xFFFF_FFFFL;
        int itemSpace = r.u8();
        int slot = r.u16();
        long itemId = r.u32() & 0xFFFF_FFFFL;
        long count = r.u32() & 0xFFFF_FFFFL;
        String text = readString(r, MAX_TEXT_BYTES, "留言");
        long guildMail = r.u32() & 0xFFFF_FFFFL;
        return new Cmd97MailBoxSendRequest(receiver, gold, itemSpace, slot, itemId, count, text,
                guildMail);
    }

    private static String readString(PacketReader r, int limit, String what) {
        int len = r.u32();
        if (len < 0 || len > limit) {
            throw new PacketFormatException(what + "长度 " + len + " 超出官方那个 " + limit
                    + " 字节的缓冲区");
        }
        return new String(r.bytes(len), CHARSET).trim();
    }

    @Override
    public String toString() {
        return "寄给「" + receiverName + "」：金币 " + gold
                + (hasItem() ? "，物品 " + itemId + " x" + count
                        + "（容器 " + itemSpace + " 槽 " + slot + "）" : "，不带物品")
                + (text.isEmpty() ? "" : "，留言 " + text.length() + " 字")
                + (guild() ? "，★公会群发" : "");
    }
}
