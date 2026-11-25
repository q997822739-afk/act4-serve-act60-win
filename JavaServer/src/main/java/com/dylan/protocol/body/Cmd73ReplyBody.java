package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd73ReplyBody {
    public static final int PACKET_ID = 73;

    public static final int SLOT_COUNT = 4;

    public static final int LENGTH = 1 + SLOT_COUNT * 2;

    public static final int GRANTED = 1;

    public static final int FATIGUE_SPENT = 0;

    public static final int EMPTY_SLOT = 0xFFFF;

    private Cmd73ReplyBody() {
    }

    public static byte[] of(int... rights) {
        if (rights.length != SLOT_COUNT) {
            throw new PacketFormatException("翻牌权必须正好 " + SLOT_COUNT + " 个槽位，收到 "
                    + rights.length);
        }
        PacketWriter w = new PacketWriter(LENGTH);
        w.u8(1);
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (rights[i] < 0 || rights[i] > 0xFFFF) {
                throw new PacketFormatException("槽位 " + i + " 的翻牌权超出 u16：" + rights[i]);
            }
            w.u16(rights[i]);
        }
        return w.toArray();
    }
}
