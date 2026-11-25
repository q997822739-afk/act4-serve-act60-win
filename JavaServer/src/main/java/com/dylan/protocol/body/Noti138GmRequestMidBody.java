package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketWriter;

public record Noti138GmRequestMidBody(long accountId, String name) {

    public static final int PACKET_ID = 138;

    public static final long NOT_FOUND = 0xFFFF_FFFFL;

    public static final int NAME_CAPACITY = 31;

    public static Noti138GmRequestMidBody found(long accountId, String name) {
        return new Noti138GmRequestMidBody(accountId, name);
    }

    public static Noti138GmRequestMidBody notFound() {
        return new Noti138GmRequestMidBody(NOT_FOUND, "");
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter();
        w.u32((int) accountId);
        if (accountId != NOT_FOUND) {
            w.string(name);
        }
        return w.toArray();
    }

    @Override
    public String toString() {
        return accountId == NOT_FOUND
                ? "NOTI 138 GM_REQUEST_MID 没找到"
                : "NOTI 138 GM_REQUEST_MID「" + name + "」→ 账号 " + accountId;
    }
}
