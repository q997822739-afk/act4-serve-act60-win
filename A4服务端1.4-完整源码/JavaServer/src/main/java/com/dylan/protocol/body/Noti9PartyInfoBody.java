package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.ArrayList;
import java.util.List;

public record Noti9PartyInfoBody(List<Entry> entries) {
    public static final int PACKET_ID = 9;

    public static final int MAX_MEMBERS = 4;

    public static final int EMPTY_SLOT = 0xFFFF;

    public static final int EMPTY_SEX = 0xFF;

    public static final int SEX_FALLBACK = 1;

    public static final int FULL = 0;

    public static final int HEADER_ONLY = 1;

    public static final int ROSTER_ONLY = 2;

    public static final int DISBAND = 3;

    public static final int TITLE_CAPACITY = 0x40;

    public static final int TITLE_ON_THE_WIRE = 0;

    public Noti9PartyInfoBody {
        entries = List.copyOf(entries);
        if (entries.size() > 0xFFFF) {
            throw new PacketFormatException("条数 " + entries.size() + " 放不进 u16");
        }
    }

    public record Entry(int partyIndex, int infoType, Header header, Roster roster) {
        public Entry {
            if (partyIndex < 0 || partyIndex > 0xFFFF) {
                throw new PacketFormatException("队伍编号放不进 u16：" + partyIndex);
            }
            if (infoType < 0 || infoType > 0xFF) {
                throw new PacketFormatException("infoType 超出 u8：" + infoType);
            }
            if (carriesHeader(infoType) == (header == null)) {
                throw new PacketFormatException("infoType " + infoType
                        + (carriesHeader(infoType) ? " 必须带头段" : " 不能带头段"));
            }
            if (carriesRoster(infoType) == (roster == null)) {
                throw new PacketFormatException("infoType " + infoType
                        + (carriesRoster(infoType) ? " 必须带名单段" : " 不能带名单段"));
            }
        }

        public static boolean carriesHeader(int infoType) {
            return infoType == FULL || infoType == HEADER_ONLY;
        }

        public static boolean carriesRoster(int infoType) {
            return infoType == FULL || infoType == ROSTER_ONLY;
        }
    }

    public record Header(int titleKind, String title, int capacity) {
        public Header {
            if (titleKind < 0 || titleKind > 0xFF) {
                throw new PacketFormatException("titleKind 超出 u8：" + titleKind);
            }
            if (capacity < 0 || capacity > 0xFF) {
                throw new PacketFormatException("capacity 超出 u8：" + capacity);
            }
        }

        public static Header named(String title, int capacity) {
            return new Header(TITLE_ON_THE_WIRE, title == null ? "" : title, capacity);
        }

        public boolean carriesTitle() {
            return titleKind == TITLE_ON_THE_WIRE;
        }
    }

    public record Roster(List<Member> members, int managerSlot) {
        public Roster {
            members = List.copyOf(members);
            if (members.size() != MAX_MEMBERS) {
                throw new PacketFormatException("名单段恒 " + MAX_MEMBERS + " 格，给了 "
                        + members.size() + " 格");
            }
            if (managerSlot < 0 || managerSlot >= MAX_MEMBERS) {
                throw new PacketFormatException("队长槽 " + managerSlot + " 不在 0.."
                        + (MAX_MEMBERS - 1) + " 里");
            }
        }

        public static Roster of(List<Member> occupied, int managerSlot) {
            List<Member> padded = new ArrayList<>(MAX_MEMBERS);
            padded.addAll(occupied);
            while (padded.size() < MAX_MEMBERS) {
                padded.add(Member.empty());
            }
            return new Roster(padded, managerSlot);
        }
    }

    public record Member(int userId, int sex) {
        public Member {
            if (userId < 0 || userId > 0xFFFF) {
                throw new PacketFormatException("连接编号放不进 u16：" + userId);
            }
            if (sex < 0 || sex > 0xFF) {
                throw new PacketFormatException("sex 超出 u8：" + sex);
            }
        }

        public static Member empty() {
            return new Member(EMPTY_SLOT, EMPTY_SEX);
        }

        public static Member of(int userId, int sex) {
            return new Member(userId, sex < 0 ? SEX_FALLBACK : sex);
        }

        public boolean present() {
            return userId != EMPTY_SLOT;
        }
    }

    public static Noti9PartyInfoBody of(Entry entry) {
        return new Noti9PartyInfoBody(List.of(entry));
    }

    public static Noti9PartyInfoBody full(int partyIndex, Header header, Roster roster) {
        return of(new Entry(partyIndex, FULL, header, roster));
    }

    public static Noti9PartyInfoBody disband(int partyIndex) {
        return of(new Entry(partyIndex, DISBAND, null, null));
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(8 + entries.size() * 32);
        w.u16(entries.size());
        for (Entry e : entries) {
            w.u16(e.partyIndex());
            w.u8(e.infoType());
            if (e.header() != null) {
                writeHeader(w, e.header());
            }
            if (e.roster() != null) {
                writeRoster(w, e.roster());
            }
        }
        return w.toArray();
    }

    private static void writeHeader(PacketWriter w, Header h) {
        w.u8(h.titleKind());
        if (h.carriesTitle()) {
            int bytes = h.title().getBytes(PacketWriter.DEFAULT_CHARSET).length;
            if (bytes >= TITLE_CAPACITY) {
                throw new PacketFormatException("队伍名编码后 " + bytes
                        + " 字节，客户端的缓冲是 " + TITLE_CAPACITY + "（判据是 len < capacity）");
            }
            w.string(h.title());
        }
        w.u8(h.capacity());
    }

    private static void writeRoster(PacketWriter w, Roster r) {
        for (Member m : r.members()) {
            w.u16(m.userId());
            w.u8(m.sex());
        }
        w.u8(r.managerSlot());
    }
}
