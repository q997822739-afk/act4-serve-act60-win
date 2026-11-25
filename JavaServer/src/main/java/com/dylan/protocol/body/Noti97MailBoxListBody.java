package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti97MailBoxListBody(int listMode, List<Mail> mails, int notLoadedMailCount,
                                    List<Letter> letters) {

    public static final int PACKET_ID = 97;

    public static final int MODE_REPLACE = 0;

    public static final int MODE_APPEND = 1;

    public static final int AVATAR_TAIL_LENGTH = Noti13ItemListBody.AVATAR_TAIL_LENGTH;

    public Noti97MailBoxListBody {
        if (listMode != MODE_REPLACE && listMode != MODE_APPEND) {
            throw new PacketFormatException("listMode 只能是 0（替换）或 1（追加）：" + listMode);
        }
        mails = List.copyOf(mails == null ? List.of() : mails);
        letters = List.copyOf(letters == null ? List.of() : letters);
        if (mails.size() > 0xFF) {
            throw new PacketFormatException("邮件条数是 u8，装不下 " + mails.size() + " 封");
        }
        if (letters.size() > 0xFFFF) {
            throw new PacketFormatException("信件条数是 u16，装不下 " + letters.size() + " 封");
        }
        if (notLoadedMailCount < 0 || notLoadedMailCount > 0xFFFF) {
            throw new PacketFormatException("notLoadedMailCount 超出 u16：" + notLoadedMailCount);
        }
    }

    public static Noti97MailBoxListBody replace(List<Mail> mails, int notLoadedMailCount,
                                                List<Letter> letters) {
        return new Noti97MailBoxListBody(MODE_REPLACE, mails, notLoadedMailCount, letters);
    }

    public static Noti97MailBoxListBody append(List<Mail> mails, int notLoadedMailCount,
                                               List<Letter> letters) {
        return new Noti97MailBoxListBody(MODE_APPEND, mails, notLoadedMailCount, letters);
    }

    public record Mail(long mailIdx, String senderName, long gold, long itemId, int sealFlag,
                       long quantity, int durability, int itemAttr, long enchant,
                       int amplifyType, int amplifyValue, boolean avatar,
                       long remainSeconds, long letterId) {

        public Mail {
            if (senderName == null) {
                throw new PacketFormatException("寄件人名字不能是 null");
            }
            checkU8("sealFlag", sealFlag);
            checkU16("durability", durability);
            checkU8("itemAttr", itemAttr);
            checkU8("amplifyType", amplifyType);
            checkU16("amplifyValue", amplifyValue);
        }
    }

    public record Letter(long letterId, int senderCharacNo, String senderName, String text,
                         long time, int stat) {

        public Letter {
            if (senderName == null || text == null) {
                throw new PacketFormatException("信件的名字和正文不能是 null");
            }
            checkU16("stat", stat);
        }
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(64 + mails.size() * 96 + letters.size() * 320);
        w.u8(mails.size());
        w.u8(listMode);
        for (Mail m : mails) {
            w.u32((int) m.mailIdx());
            w.string(m.senderName());
            w.u32((int) m.gold());
            w.u32((int) m.itemId());
            w.u8(m.sealFlag());
            w.u32((int) m.quantity());
            w.u16(m.durability());
            w.u8(m.itemAttr());
            w.u32((int) m.enchant());
            // A4 邮件条目没有 ACT5 的增幅 u8+u16（客户端 DNF.exe 0042DE77..0042DF3D：enchant 后直接两个 u32）。
            // 官方 52 字节 / 44 字节两份实抓按这个布局逐字节对得上。时装尾巴 A4 别处（NOTI 13/1）也没有，这里同样不写（未核对时装邮件）。
            if (!com.dylan.net.ServerConfig.A4) {
                w.u8(m.amplifyType());
                w.u16(m.amplifyValue());
                if (m.avatar()) {
                    w.u32(AVATAR_TAIL_LENGTH);
                    w.zeros(AVATAR_TAIL_LENGTH);
                }
            }
            w.u32((int) m.remainSeconds());
            w.u32((int) m.letterId());
        }
        w.u16(notLoadedMailCount);
        w.u16(letters.size());
        for (Letter l : letters) {
            w.u32((int) l.letterId());
            w.u32(l.senderCharacNo());
            w.string(l.senderName());
            w.string(l.text());
            w.u32((int) l.time());
            w.u16(l.stat());
        }
        return w.toArray();
    }

    public static Noti97MailBoxListBody empty() {
        return replace(List.of(), 0, List.of());
    }

    private static void checkU8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(name + " 超出 u8：" + value);
        }
    }

    private static void checkU16(String name, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new PacketFormatException(name + " 超出 u16：" + value);
        }
    }
}
