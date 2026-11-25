package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.nio.charset.Charset;

public record Cmd6DeleteCharacterRequest(int listIndex, String name) {

    public static final int PACKET_ID = 6;

    public static final int NAME_CAPACITY = 30;

    public static Cmd6DeleteCharacterRequest parse(byte[] plain) {
        if (plain == null || plain.length < 5) {
            throw new PacketFormatException("DELETE_CHARACTER 包体至少要 5 字节，实际 "
                    + (plain == null ? "null" : plain.length));
        }
        int listIndex = plain[0];
        int len = (plain[1] & 0xFF) | ((plain[2] & 0xFF) << 8)
                | ((plain[3] & 0xFF) << 16) | ((plain[4] & 0xFF) << 24);
        if (len < 0 || len > NAME_CAPACITY) {
            throw new PacketFormatException("DELETE_CHARACTER 的名字长度前缀不合理："
                    + len + "（官方上限 " + NAME_CAPACITY + "，包体 " + plain.length + " 字节）");
        }
        if (5 + len > plain.length) {
            throw new PacketFormatException("DELETE_CHARACTER 说名字有 " + len
                    + " 字节，但包体只剩 " + (plain.length - 5) + " 字节 ——"
                    + " 多半是切帧或解密不对");
        }
        return new Cmd6DeleteCharacterRequest(listIndex, new String(plain, 5, len, charset()));
    }

    private static Charset charset() {
        return PacketWriter.DEFAULT_CHARSET;
    }

    @Override
    public String toString() {
        return "DELETE_CHARACTER[序号=" + listIndex + " 确认名=" + name + "]";
    }
}
