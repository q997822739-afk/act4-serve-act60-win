package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd118ReplyBody {
    public static final int PACKET_ID = 118;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    private Cmd118ReplyBody() {
    }

    public static byte[] success() {
        return new byte[]{(byte) RESULT_OK};
    }

    public static byte[] failure(int errorCode) {
        if (errorCode < 0 || errorCode > 0xFFFF) {
            throw new PacketFormatException("errorCode 超出 u16：" + errorCode);
        }
        if (com.dylan.net.ServerConfig.A4) {
            // A4 客户端失败分支只读 u8 错误码（DNF.exe 0041AECC，0x50 时弹提示），ACT5 是 u16。
            if (errorCode > 0xFF) {
                throw new PacketFormatException("A4 错误码是 u8，放不下 " + errorCode);
            }
            return new byte[]{(byte) RESULT_FAIL, (byte) errorCode};
        }
        PacketWriter w = new PacketWriter(3);
        w.u8(RESULT_FAIL);
        w.u16(errorCode);
        return w.toArray();
    }
}
