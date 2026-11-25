package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;
import com.dylan.protocol.io.PacketWriter;

public record Noti37ExpBody(int level, int totalExp, int bonusA, int bonusB, int totalSp,
                            int winPoint, int bonusC) {

    public static final int PACKET_ID = 37;

    public static final int LENGTH = 1 + 4 + 4 + 4 + 2 + 4 + 4;

    /** A4 客户端（DNF.exe 00426E6B）读 u8 u32 u32 u32 u16 u32，官方 19 字节；没有末尾 bonusC。 */
    public static final int A4_LENGTH = LENGTH - 4;

    public static Noti37ExpBody plain(int level, int totalExp, int totalSp, long winPoint) {
        return new Noti37ExpBody(level, totalExp, 0, 0, totalSp, clampWinPoint(winPoint), 0);
    }

    public static int clampWinPoint(long winPoint) {
        return (int) Math.clamp(winPoint, 0, 0xFFFF_FFFFL);
    }

    public byte[] toBytes() {
        if (level < 0 || level > 0xFF) {
            throw new PacketFormatException("level 超出 u8：" + level);
        }
        if (totalSp < 0 || totalSp > 0xFFFF) {
            throw new PacketFormatException("totalSp 超出 u16：" + totalSp);
        }
        boolean a4 = com.dylan.net.ServerConfig.A4;
        PacketWriter w = new PacketWriter(a4 ? A4_LENGTH : LENGTH);
        w.u8(level).u32(totalExp).u32(bonusA).u32(bonusB).u16(totalSp).u32(winPoint);
        if (!a4) {
            w.u32(bonusC);
        }
        return w.toArray();
    }

    public static Noti37ExpBody parse(byte[] body) {
        PacketReader r = new PacketReader(body);
        return new Noti37ExpBody(r.u8(), r.u32(), r.u32(), r.u32(), r.u16(), r.u32(), r.u32());
    }
}
