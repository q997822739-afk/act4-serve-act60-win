package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd144ReplyBody {

    public static final int PACKET_ID = 144;

    public static final int SUCCESS_LENGTH = 7;

    public static final int FAILURE_LENGTH = 2;

    public static final int DB_FAILED_CODE = 2;

    private Cmd144ReplyBody() {
    }

    public static byte[] success(long letterId, int stat) {
        if (letterId <= 0 || letterId > 0xFFFF_FFFFL) {
            throw new PacketFormatException("成功应答的 letterId 必须非 0：" + letterId);
        }
        if (stat < 0 || stat > 0xFFFF) {
            throw new PacketFormatException("stat 超出 u16：" + stat);
        }
        return new PacketWriter(SUCCESS_LENGTH)
                .u8(1)
                .u32((int) letterId)
                .u16(stat)
                .toArray();
    }

    public static byte[] failure() {
        return new PacketWriter(FAILURE_LENGTH).u8(0).u8(DB_FAILED_CODE).toArray();
    }

    public static byte[] failure(MailError error) {
        return new PacketWriter(FAILURE_LENGTH).u8(0).u8(error.code()).toArray();
    }
}
