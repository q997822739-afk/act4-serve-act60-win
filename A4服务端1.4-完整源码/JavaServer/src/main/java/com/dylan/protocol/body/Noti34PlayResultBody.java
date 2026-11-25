package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti34PlayResultBody(int rank, int condition0, int condition1, int condition2,
                                   int unused) {

    public static final int PACKET_ID = 34;

    public static final int LENGTH = 5;

    public static Noti34PlayResultBody withRank(int rank) {
        return new Noti34PlayResultBody(rank, 0, 0, 0, 0);
    }

    public byte[] toBytes() {
        requireU8("rank", rank);
        requireU8("condition0", condition0);
        requireU8("condition1", condition1);
        requireU8("condition2", condition2);
        requireU8("unused", unused);
        PacketWriter w = new PacketWriter(LENGTH);
        w.u8(rank).u8(condition0).u8(condition1).u8(condition2).u8(unused);
        return w.toArray();
    }

    private static void requireU8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(name + " 超出 u8：" + value);
        }
    }
}
