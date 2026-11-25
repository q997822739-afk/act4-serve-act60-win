package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd46GetItemRequest(int itemNo, int routingFlag, int pickupType,
                                  int checkA, int checkB, int check7,
                                  int x, int y, int unknown) {

    public static final int PACKET_ID = 46;

    public static final int LENGTH = 16;

    public static final int CHECK_DIVISOR = 7;

    public static Cmd46GetItemRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException("GET_ITEM 包体至少要 " + LENGTH + " 字节，实际 "
                    + (plain == null ? "null" : plain.length));
        }
        PacketReader r = new PacketReader(plain);
        return new Cmd46GetItemRequest(r.u16(), r.u8(), r.u8(),
                r.u16(), r.u16(), r.u16(), r.u16(), r.u16(), r.u16());
    }

    public boolean check7Passes() {
        return check7 % CHECK_DIVISOR == 0;
    }
}
