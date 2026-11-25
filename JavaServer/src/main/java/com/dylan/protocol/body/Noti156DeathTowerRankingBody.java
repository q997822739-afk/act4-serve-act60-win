package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti156DeathTowerRankingBody(int playTimeMs, int stage, boolean renewed,
                                           int rank, Record myBest,
                                           List<Record> topRankers) {
    public static final int PACKET_ID = 156;

    public static final int TOP_RANKERS = 5;

    public static final int RECORD_MEMBERS = 4;

    public record Member(String name, int job, int growType) {
        public static final Member EMPTY = new Member("", 0, 0);

        public Member {
            name = name == null ? "" : name;
            if (job < 0 || job > 0xFF || growType < 0 || growType > 0xFF) {
                throw new PacketFormatException("job / growType 都是 u8：" + job + "/" + growType);
            }
        }
    }

    public record Record(List<Member> members, int stage, int playTimeMs, int rank) {
        public Record {
            if (members == null || members.size() != RECORD_MEMBERS) {
                throw new PacketFormatException("一条记录必须正好 " + RECORD_MEMBERS
                        + " 个成员（客户端跑满四轮），给了 "
                        + (members == null ? "null" : members.size()));
            }
            members = List.copyOf(members);
            if (stage < 0 || stage > 0xFFFF) {
                throw new PacketFormatException("stage 超出 u16：" + stage);
            }
        }

        public static Record of(List<Member> members, int stage, int playTimeMs, int rank) {
            List<Member> padded = new java.util.ArrayList<>(RECORD_MEMBERS);
            padded.addAll(members);
            while (padded.size() < RECORD_MEMBERS) {
                padded.add(Member.EMPTY);
            }
            return new Record(padded.subList(0, RECORD_MEMBERS), stage, playTimeMs, rank);
        }
    }

    public static final Record EMPTY_RECORD = Record.of(List.of(), 0, 0, 0);

    public Noti156DeathTowerRankingBody {
        if (topRankers == null || topRankers.size() != TOP_RANKERS) {
            throw new PacketFormatException("榜首必须正好 " + TOP_RANKERS + " 条，给了 "
                    + (topRankers == null ? "null" : topRankers.size()));
        }
        topRankers = List.copyOf(topRankers);
    }

    public static Noti156DeathTowerRankingBody of(int playTimeMs, int stage, boolean renewed,
                                                  int rank, Record myBest,
                                                  List<Record> top) {
        List<Record> padded = new java.util.ArrayList<>(TOP_RANKERS);
        padded.addAll(top);
        while (padded.size() < TOP_RANKERS) {
            padded.add(EMPTY_RECORD);
        }
        return new Noti156DeathTowerRankingBody(playTimeMs, stage, renewed, rank, myBest,
                padded.subList(0, TOP_RANKERS));
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(256);
        w.u32(playTimeMs);
        w.u32(stage);
        w.u8(renewed ? 1 : 0);
        w.u32(rank);
        if (myBest == null) {
            w.u8(0);
        } else {
            w.u8(1);
            writeRecord(w, myBest);
        }
        for (Record record : topRankers) {
            writeRecord(w, record);
        }
        return w.toArray();
    }

    private static void writeRecord(PacketWriter w, Record record) {
        for (Member member : record.members()) {
            w.string(member.name());
            w.u8(member.job());
            w.u8(member.growType());
        }
        w.u16(record.stage());
        w.u32(record.playTimeMs());
        w.u32(record.rank());
    }
}
