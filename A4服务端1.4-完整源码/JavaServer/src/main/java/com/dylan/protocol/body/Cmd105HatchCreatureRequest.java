package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd105HatchCreatureRequest(int itemSpace, int slot) {

    public static final int PACKET_ID = 105;

    public static final int LENGTH = 3;

    public static Cmd105HatchCreatureRequest parse(byte[] plain) {
        if (plain.length < LENGTH) {
            throw new PacketFormatException("HATCH_CREATURE 至少 " + LENGTH + " 字节，实际 "
                    + plain.length);
        }
        PacketReader r = new PacketReader(plain);
        int itemSpace = r.u8();
        int slot = r.u16();
        return new Cmd105HatchCreatureRequest(itemSpace, slot);
    }

    @Override
    public String toString() {
        return "孵容器" + itemSpace + " 槽 " + slot + " 上那只蛋";
    }
}
