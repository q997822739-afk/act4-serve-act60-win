package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

import java.util.List;

public record Cmd49PlayResultRequest(List<Integer> stats, int protectedA, int protectedB,
                                     int rank, int memberStat54, int memberStat6C) {

    public static final int PACKET_ID = 49;

    public static final int LENGTH = 6 * 2 + 4 + 2 + 2 + 2 + 2 + 4;

    public static final int STAT_COUNT = 7;

    public Cmd49PlayResultRequest {
        if (stats.size() != STAT_COUNT) {
            throw new PacketFormatException("stats 必须正好 " + STAT_COUNT + " 项，收到 "
                    + stats.size());
        }
        stats = List.copyOf(stats);
    }

    public static Cmd49PlayResultRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException("CMD 49 包体至少 " + LENGTH + " 字节，收到 "
                    + (plain == null ? 0 : plain.length));
        }
        PacketReader r = new PacketReader(plain);
        var stats = new java.util.ArrayList<Integer>(STAT_COUNT);
        for (int i = 0; i < STAT_COUNT - 1; i++) {
            stats.add(r.u16());
        }
        stats.add(r.u32());
        return new Cmd49PlayResultRequest(stats, r.u16(), r.u16(), r.u16(), r.u16(), r.u32());
    }

    @Override
    public String toString() {
        return "统计=" + stats + " 保护槽=(" + protectedA + "," + protectedB + ")"
                + " 评分档位=" + rank + " 成员量=(" + memberStat54 + "," + memberStat6C + ")";
    }
}
