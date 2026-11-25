package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.Set;

public final class Cmd16ReplyBody {

    public static final int PACKET_ID = 16;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int ERROR_ALREADY_INSIDE = 0x11;

    private static final Set<Integer> NEEDS_EXTRA_U8 =
            Set.of(0x11, 0xAB, 0xAC, 0xBB, 0xB2, 0x0A, 0x5F);

    private Cmd16ReplyBody() {
    }

    public static boolean needsExtraByte(int errorCode) {
        return NEEDS_EXTRA_U8.contains(errorCode);
    }

    public static byte[] success() {
        return new byte[]{(byte) RESULT_OK};
    }

    public static byte[] failure(int errorCode) {
        checkU8("errorCode", errorCode);
        if (NEEDS_EXTRA_U8.contains(errorCode)) {
            throw new PacketFormatException("错误码 " + errorCode
                    + " 客户端后面还要读一个 u8，用 failureWithDetail");
        }
        PacketWriter w = new PacketWriter(2);
        w.u8(RESULT_FAIL);
        w.u8(errorCode);
        return w.toArray();
    }

    public static byte[] failureWithDetail(int errorCode, int detail) {
        checkU8("errorCode", errorCode);
        checkU8("detail", detail);
        if (!NEEDS_EXTRA_U8.contains(errorCode)) {
            throw new PacketFormatException("错误码 " + errorCode
                    + " 客户端不读附加字节，多发一个会让后面的包错位");
        }
        PacketWriter w = new PacketWriter(3);
        w.u8(RESULT_FAIL);
        w.u8(errorCode);
        w.u8(detail);
        return w.toArray();
    }

    private static void checkU8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(name + " 超出 u8：" + value);
        }
    }
}
