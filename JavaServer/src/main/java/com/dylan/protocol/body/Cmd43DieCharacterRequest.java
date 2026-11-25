package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketReader;

public record Cmd43DieCharacterRequest(int killer, int how) {

    public static final int PACKET_ID = 43;

    public static final int LENGTH = 4;

    public static Cmd43DieCharacterRequest parse(PacketReader r) {
        if (com.dylan.net.ServerConfig.A4 && r.remaining() < LENGTH) {
            // A4 客户端唯一发包点 004B8860：push 0x2B → 009E4900 → 直接 009E49A0 发出，包体是空的。
            // 按 2+2 读会越界，handler 抛异常把人踢下线（2026-10-27 sweep：副本里倒下就断开）。
            // DeathService.dieCharacter 不用这两个字段，空包当 0 / 0。
            return new Cmd43DieCharacterRequest(0, 0);
        }
        return new Cmd43DieCharacterRequest(r.u16(), r.u16());
    }

    @Override
    public String toString() {
        return "CMD 43 DIE_CHARACTER[#1=" + killer + " #2=" + how + "]";
    }
}
