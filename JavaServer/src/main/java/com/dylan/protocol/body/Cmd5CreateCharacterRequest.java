package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.nio.charset.Charset;

public record Cmd5CreateCharacterRequest(int job, String name) {

    public static final int PACKET_ID = 5;

    public static final int NAME_MAX_BYTES = 64;

    public static Cmd5CreateCharacterRequest parse(byte[] plain) {
        if (plain == null || plain.length < 5) {
            throw new PacketFormatException("CREATE_CHARACTER 包体至少要 5 字节，实际 "
                    + (plain == null ? "null" : plain.length));
        }
        int job = plain[0] & 0xFF;
        int len = (plain[1] & 0xFF) | ((plain[2] & 0xFF) << 8)
                | ((plain[3] & 0xFF) << 16) | ((plain[4] & 0xFF) << 24);
        if (len <= 0 || len > NAME_MAX_BYTES) {
            throw new PacketFormatException("CREATE_CHARACTER 的名字长度前缀不合理："
                    + len + "（包体 " + plain.length + " 字节）");
        }
        if (5 + len > plain.length) {
            throw new PacketFormatException("CREATE_CHARACTER 说名字有 " + len
                    + " 字节，但包体只剩 " + (plain.length - 5) + " 字节 ——"
                    + " 多半是切帧或解密不对");
        }
        String name = new String(plain, 5, len, charset());
        return new Cmd5CreateCharacterRequest(job, name);
    }

    private static Charset charset() {
        return PacketWriter.DEFAULT_CHARSET;
    }

    @Override
    public String toString() {
        return "CREATE_CHARACTER[职业=" + job + " 名字=" + name + "]";
    }
}
