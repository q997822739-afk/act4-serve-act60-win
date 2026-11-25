package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd144ChangeLetterStatRequest(long letterId, int stat) {

    public static final int PACKET_ID = 144;

    public static final int LENGTH = 6;

    public static Cmd144ChangeLetterStatRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException("CMD " + PACKET_ID + " 包体应当是 " + LENGTH
                    + " 字节，实际 " + (plain == null ? "null" : plain.length));
        }
        PacketReader r = new PacketReader(plain);
        return new Cmd144ChangeLetterStatRequest(r.u32() & 0xFFFF_FFFFL, r.u16());
    }

    @Override
    public String toString() {
        String what = switch (stat) {
            case LetterStat.DELETE -> "删除";
            case LetterStat.UNREAD -> "标为未读";
            case LetterStat.READ -> "标为已读";
            case LetterStat.KEPT -> "保管";
            default -> "未知状态 " + stat;
        };
        return "信件 " + letterId + " → " + what;
    }
}
