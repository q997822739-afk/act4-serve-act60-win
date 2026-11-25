package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti191ShopCoinEventBody(int opencoin) {

    public static final int PACKET_ID = 191;

    public static final int LENGTH = 1;

    public static Noti191ShopCoinEventBody claimable() {
        return new Noti191ShopCoinEventBody(0);
    }

    public static Noti191ShopCoinEventBody done() {
        return new Noti191ShopCoinEventBody(1);
    }

    public Noti191ShopCoinEventBody {
        if (opencoin < 0 || opencoin > 0xFF) {
            throw new PacketFormatException("opencoin 是 u8：" + opencoin);
        }
    }

    public byte[] toBytes() {
        return new PacketWriter(LENGTH).u8(opencoin).toArray();
    }
}
