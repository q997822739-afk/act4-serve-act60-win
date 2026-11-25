package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti36FatigueBody(int totalFatigue, int totalMaxFatigue, int usedFatigue,
                                int grownUpBuff) {

    public static final int PACKET_ID = 36;

    public static final int LENGTH = 8;

    public Noti36FatigueBody {
        checkU16("totalFatigue", totalFatigue);
        checkU16("totalMaxFatigue", totalMaxFatigue);
        checkU16("usedFatigue", usedFatigue);
        checkU16("grownUpBuff", grownUpBuff);
    }

    public static Noti36FatigueBody of(int totalFatigue, int totalMaxFatigue, int usedFatigue) {
        return new Noti36FatigueBody(totalFatigue, totalMaxFatigue, usedFatigue, 0);
    }

    public int remaining() {
        return Math.max(0, totalMaxFatigue - totalFatigue);
    }

    /** A4 客户端（DNF.exe 00426CE3）只读 3 个 u16，官方 6 字节 {@code 00 00 9C 00 00 00}；没有 grownUpBuff。 */
    public static final int A4_LENGTH = 6;

    public byte[] toBytes() {
        boolean a4 = com.dylan.net.ServerConfig.A4;
        PacketWriter w = new PacketWriter(a4 ? A4_LENGTH : LENGTH);
        w.u16(totalFatigue);
        w.u16(totalMaxFatigue);
        w.u16(usedFatigue);
        if (!a4) {
            w.u16(grownUpBuff);
        }
        return w.toArray();
    }

    private static void checkU16(String name, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new PacketFormatException(name + " 超出 u16：" + value);
        }
    }
}
