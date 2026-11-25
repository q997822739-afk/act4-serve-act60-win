package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public final class Cmd18ReplyBody {

    public static final int PACKET_ID = 18;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int ERROR_CANNOT_DELETE = 23;

    public static final int ERROR_SILENT = 21;

    public static final int HEADER_LENGTH = 3;

    public static final int ENTRY_LENGTH = 6;

    public static final int LENGTH_FAIL = 3;

    private Cmd18ReplyBody() {
    }

    public static byte[] accepted(int listType, List<int[]> deleted) {
        checkU8("listType", listType);
        if (deleted.size() > 0xFF) {
            throw new PacketFormatException("一次最多报告 255 格，给了 " + deleted.size());
        }
        PacketWriter w = new PacketWriter(HEADER_LENGTH + deleted.size() * ENTRY_LENGTH);
        w.u8(RESULT_OK);
        w.u8(listType);
        w.u8(deleted.size());
        for (int[] entry : deleted) {
            if (entry[0] < 0 || entry[0] > 0xFFFF) {
                throw new PacketFormatException("槽位放不进 u16：" + entry[0]);
            }
            w.u16(entry[0]);
            w.u32(entry[1]);
        }
        return w.toArray();
    }

    public static byte[] rejected(int errorCode, int listType) {
        checkU8("errorCode", errorCode);
        checkU8("listType", listType);
        return new byte[]{(byte) RESULT_FAIL, (byte) errorCode, (byte) listType};
    }

    private static void checkU8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(name + " 超出 u8：" + value);
        }
    }
}
