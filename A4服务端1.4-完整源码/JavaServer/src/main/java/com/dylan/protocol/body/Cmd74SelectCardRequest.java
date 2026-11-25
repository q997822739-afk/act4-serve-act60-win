package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd74SelectCardRequest(int cardType, int cardNo) {

    public static final int PACKET_ID = 74;

    public static final int LENGTH = 2;

    public static Cmd74SelectCardRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException("CMD 74 包体至少 " + LENGTH + " 字节，收到 "
                    + (plain == null ? 0 : plain.length));
        }
        PacketReader r = new PacketReader(plain);
        return new Cmd74SelectCardRequest(r.u8(), r.u8());
    }

    public boolean validType() {
        return cardType == Cmd74SelectCardReply.CARD_TYPE_FREE
                || cardType == Cmd74SelectCardReply.CARD_TYPE_GOLD;
    }

    @Override
    public String toString() {
        return (cardType == Cmd74SelectCardReply.CARD_TYPE_GOLD ? "金卡" : "免费")
                + "排第 " + cardNo + " 张";
    }
}
