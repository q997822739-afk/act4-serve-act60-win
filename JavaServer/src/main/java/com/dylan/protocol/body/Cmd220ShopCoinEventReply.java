package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Cmd220ShopCoinEventReply(int give) {

    public static final int PACKET_ID = 220;

    public static final int LENGTH = 2;

    public static final int GIVE_LIMIT = 0x7F;

    private static final int RESULT_OK = 1;

    public static Cmd220ShopCoinEventReply nothing() {
        return new Cmd220ShopCoinEventReply(0);
    }

    public Cmd220ShopCoinEventReply {
        if (give < 0 || give >= GIVE_LIMIT) {
            throw new PacketFormatException(
                    "give 要落在 0.." + (GIVE_LIMIT - 1) + "，客户端对 >=0x7F 整条丢弃：" + give);
        }
    }

    public byte[] toBytes() {
        return new PacketWriter(LENGTH).u8(RESULT_OK).u8(give).toArray();
    }
}
