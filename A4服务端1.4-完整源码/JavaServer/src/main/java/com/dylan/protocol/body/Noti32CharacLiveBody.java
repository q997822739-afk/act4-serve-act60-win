package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti32CharacLiveBody(int userId, int state) {
    public static final int PACKET_ID = 32;

    public static final int LENGTH = 3;

    public static final int STATE_DEAD = 0;

    public static final int STATE_ALIVE = 1;

    public Noti32CharacLiveBody {
        if (userId < 0 || userId > 0xFFFF) {
            throw new PacketFormatException("连接编号超出 u16：" + userId);
        }
        if (state < 0 || state > 0xFF) {
            throw new PacketFormatException("state 超出 u8：" + state);
        }
    }

    public static Noti32CharacLiveBody dead(int userId) {
        return new Noti32CharacLiveBody(userId, STATE_DEAD);
    }

    public byte[] toBytes() {
        return new PacketWriter(LENGTH).u16(userId).u8(state).toArray();
    }

    @Override
    public String toString() {
        return "NOTI 32 SET_CHARAC_LIVE[连接编号=" + userId + " state=" + state + "（"
                + (state == STATE_DEAD ? "倒下" : "复活") + "）]";
    }
}
