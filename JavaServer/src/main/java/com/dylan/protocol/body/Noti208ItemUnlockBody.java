package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketWriter;

public record Noti208ItemUnlockBody(int itemSpace, int slot) {

    public static final int PACKET_ID = 208;

    public static final int LENGTH = 3;

    public Noti208ItemUnlockBody {
        Cmd229ReplyBody.check(itemSpace, slot);
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(LENGTH);
        w.u8(itemSpace);
        w.u16(slot);
        return w.toArray();
    }

    @Override
    public String toString() {
        return "容器 " + itemSpace + " 第 " + slot + " 格解锁";
    }
}
