package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd44ReplyBody {

    public static final int PACKET_ID = 44;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    private Cmd44ReplyBody() {
    }

    public static byte[] success(int targetUserId) {
        if (targetUserId < 0 || targetUserId > 0xFFFF) {
            throw new PacketFormatException("连接编号超出 u16：" + targetUserId);
        }
        return new PacketWriter(3).u8(RESULT_OK).u16(targetUserId).toArray();
    }

    public static byte[] rejected(int errorCode) {
        if (errorCode < 0 || errorCode > 0xFF) {
            throw new PacketFormatException("错误码超出 u8：" + errorCode);
        }
        return new byte[]{(byte) RESULT_FAIL, (byte) errorCode};
    }
}
