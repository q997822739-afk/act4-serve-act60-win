package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti28Body(int dungeonIndex, int difficulty, int mazeIndex,
                         int bossX, int bossY, int sealDoorX, int sealDoorY) {
    public static final int PACKET_ID = 28;

    public static final int LENGTH = 8;

    public Noti28Body {
        checkU16("dungeonIndex", dungeonIndex);
        checkU8("difficulty", difficulty);
        checkU8("mazeIndex", mazeIndex);
        checkU8("bossX", bossX);
        checkU8("bossY", bossY);
        checkU8("sealDoorX", sealDoorX);
        checkU8("sealDoorY", sealDoorY);
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(LENGTH);
        w.u16(dungeonIndex);
        w.u8(difficulty);
        w.u8(mazeIndex);
        w.u8(bossX);
        w.u8(bossY);
        w.u8(sealDoorX);
        w.u8(sealDoorY);
        return w.toArray();
    }

    private static void checkU8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(name + " 超出 u8：" + value);
        }
    }

    private static void checkU16(String name, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new PacketFormatException(name + " 超出 u16：" + value);
        }
    }
}
