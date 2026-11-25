package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Cmd258GiveupExpertJobReplyBody(int result, long money, int giveupCount, int error) {

    public static final int PACKET_ID = 258;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int ERROR_NOT_ENOUGH_MONEY = 10;

    public static final int ERROR_NO_EXPERT_JOB = 19;

    public static final int ERROR_GIVEUP_LIMIT = 1;

    public static final int OK_LENGTH = 6;

    public static final int FAIL_LENGTH = 2;

    public static Cmd258GiveupExpertJobReplyBody given(long money, int giveupCount) {
        if (money < 0 || money > 0xFFFF_FFFFL) {
            throw new PacketFormatException("金币是 u32，超出 0..4294967295：" + money);
        }
        if (giveupCount < 0 || giveupCount > 0xFF) {
            throw new PacketFormatException("放弃次数是 u8，超出 0..255：" + giveupCount);
        }
        return new Cmd258GiveupExpertJobReplyBody(RESULT_OK, money, giveupCount, 0);
    }

    public static Cmd258GiveupExpertJobReplyBody rejected(int error) {
        if (error < 0 || error > 0xFF) {
            throw new PacketFormatException("错误码是 u8，超出 0..255：" + error);
        }
        return new Cmd258GiveupExpertJobReplyBody(RESULT_FAIL, 0, 0, error);
    }

    public boolean ok() {
        return result != RESULT_FAIL;
    }

    public byte[] toBytes() {
        if (ok()) {
            PacketWriter w = new PacketWriter(OK_LENGTH);
            w.u8(result);
            w.u32((int) money);
            w.u8(giveupCount);
            return w.toArray();
        }
        PacketWriter w = new PacketWriter(FAIL_LENGTH);
        w.u8(RESULT_FAIL);
        w.u8(error);
        return w.toArray();
    }
}
