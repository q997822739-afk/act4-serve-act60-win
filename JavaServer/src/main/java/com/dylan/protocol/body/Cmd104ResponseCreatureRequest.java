package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketReader;

public record Cmd104ResponseCreatureRequest(int trailing) {

    public static final int PACKET_ID = 104;

    public static Cmd104ResponseCreatureRequest parse(byte[] plain) {
        PacketReader r = new PacketReader(plain);
        return new Cmd104ResponseCreatureRequest(r.remaining() >= 2 ? r.u16() : -1);
    }

    @Override
    public String toString() {
        return trailing < 0 ? "让身上那只宠物叫一声（空体）"
                : "让身上那只宠物叫一声（客户端多发的 2 字节 = " + trailing + "，照官方不用）";
    }
}
