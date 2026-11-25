package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti17TradeStateBody(int userId, int state) {

    public static final int PACKET_ID = 17;

    public static final int LENGTH = 3;

    public Noti17TradeStateBody {
        if (userId < 0 || userId > 0xFFFF) {
            throw new PacketFormatException("连接编号放不进 u16：" + userId);
        }
        if (state < 0 || state > 0xFF) {
            throw new PacketFormatException("state 超出 u8：" + state);
        }
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(LENGTH);
        w.u16(userId);
        w.u8(state);
        return w.toArray();
    }
}
