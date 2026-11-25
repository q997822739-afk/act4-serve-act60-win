package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd46ReplyBody {

    public static final int PACKET_ID = 46;

    public static final int RESULT_FAIL = 0;

    public static final int ERROR_BAG_FULL = 4;

    public static final int ERROR_BAG_FULL_ALT = 22;

    public static final int ERROR_SILENT = 21;

    public static final int ERROR_TAKEN_BY_OTHER = 81;

    private Cmd46ReplyBody() {
    }

    public static byte[] rejected(int errorCode) {
        if (errorCode < 0 || errorCode > 0xFF) {
            throw new PacketFormatException("errorCode 超出 u8：" + errorCode);
        }
        PacketWriter w = new PacketWriter(2);
        w.u8(RESULT_FAIL);
        w.u8(errorCode);
        return w.toArray();
    }

    public static String describe(int errorCode) {
        return switch (errorCode) {
            case ERROR_BAG_FULL, ERROR_BAG_FULL_ALT -> "背包放不下";
            case ERROR_SILENT -> "静默失败（客户端不弹提示）";
            case ERROR_TAKEN_BY_OTHER -> "已被其他角色拾取";
            default -> "错误码 " + errorCode;
        };
    }
}
