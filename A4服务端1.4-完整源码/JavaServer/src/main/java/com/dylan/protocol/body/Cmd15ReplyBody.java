package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd15ReplyBody {

    public static final int PACKET_ID = 15;

    public static final int RESULT_FAIL = 0;

    public static final int ERROR_NEEDS_EXTRA_U8 = 0x16;

    public static final int ERROR_NEEDS_EXTRA_U8_U32 = 0x44;

    private Cmd15ReplyBody() {
    }

    public static byte[] failure(int errorCode) {
        if (errorCode < 0 || errorCode > 0xFF) {
            throw new PacketFormatException("errorCode 超出 u8：" + errorCode);
        }
        if (errorCode == ERROR_NEEDS_EXTRA_U8 || errorCode == ERROR_NEEDS_EXTRA_U8_U32) {
            throw new PacketFormatException("错误码 " + errorCode
                    + " 客户端后面还要读附加字段，本类没实现，发出去客户端会读越界");
        }
        if (com.dylan.net.ServerConfig.A4) {
            // A4 客户端失败分支（DNF.exe 00411938/41）不论错误码都连读两个 u8：错误码 + 1 字节（含义未知，
            // 0x16 时当成员号用）。只写错误码会让客户端多读 1 字节。未知字节填 0。
            return new PacketWriter(3).u8(RESULT_FAIL).u8(errorCode).u8(0).toArray();
        }
        PacketWriter w = new PacketWriter(2);
        w.u8(RESULT_FAIL);
        w.u8(errorCode);
        return w.toArray();
    }
}
