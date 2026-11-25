package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Cmd180PutItemForEnchantReplyBody(int error) {

    public static final int PACKET_ID = Cmd180PutItemForEnchantRequest.PACKET_ID;

    public static final int RESULT_FAIL = 0;

    public static final int LENGTH = 2;

    public Cmd180PutItemForEnchantReplyBody {
        if (error < 0 || error > 0xFF) {
            throw new PacketFormatException("错误码是 u8：" + error);
        }
    }

    public static Cmd180PutItemForEnchantReplyBody rejected(int error) {
        return new Cmd180PutItemForEnchantReplyBody(error);
    }

    public byte[] toBytes() {
        return new PacketWriter(LENGTH).u8(RESULT_FAIL).u8(error).toArray();
    }
}
