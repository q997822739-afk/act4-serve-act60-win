package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;

public final class Cmd43ReplyBody {

    public static final int PACKET_ID = 43;

    public static final int RESULT_FAIL = 0;

    public static final int ERROR_NOT_IN_PARTY = 1;

    public static final int ERROR_ALREADY_DEAD = 18;

    private Cmd43ReplyBody() {
    }

    public static byte[] rejected(int errorCode) {
        if (errorCode < 0 || errorCode > 0xFF) {
            throw new PacketFormatException("错误码超出 u8：" + errorCode);
        }
        return new byte[]{(byte) RESULT_FAIL, (byte) errorCode};
    }

    public static String describe(int errorCode) {
        return switch (errorCode) {
            case ERROR_NOT_IN_PARTY -> "他不在这个队伍里（客户端不认这个码，玩家看不到提示）";
            case ERROR_ALREADY_DEAD -> "他本来就是倒下的";
            default -> "官方 die_user 不发这个码";
        };
    }
}
