package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti35ClearRewardBody(int ignored, int gainedExp, int rankBonus,
                                    int partyBonus, int avatarBonus, int eventKind,
                                    int eventBonus, int expItemBonus, int premiumBonus,
                                    int pcRoomBonus, int channelBonus, int memberBonus,
                                    int creatureBonus,
                                    List<RewardItem> items, List<List<SlotEntry>> groupA,
                                    int goldCardCost, List<List<SlotEntry>> groupB,
                                    List<List<SlotEntry>> groupC, int partyOnly) {

    public static final int PACKET_ID = 35;

    public static final int SLOT_COUNT = 4;

    public static final int PROBE_BASE = 1000;

    private static final long MAX_VALUE = 0xFFFFFFFFL;

    public record RewardItem(int a, int b, int c, int d, int e, int f) {}

    public record SlotEntry(int itemId, int value) {}

    public Noti35ClearRewardBody {
        items = List.copyOf(items);
        groupA = copySlots(groupA);
        groupB = copySlots(groupB);
        groupC = copySlots(groupC);
    }

    private static List<List<SlotEntry>> copySlots(List<List<SlotEntry>> groups) {
        if (groups.size() != SLOT_COUNT) {
            throw new PacketFormatException("每组必须正好 " + SLOT_COUNT + " 个槽位，收到 "
                    + groups.size());
        }
        return groups.stream().map(List::copyOf).toList();
    }

    public static List<List<SlotEntry>> emptySlots() {
        return List.of(List.of(), List.of(), List.of(), List.of());
    }

    public static Noti35ClearRewardBody empty() {
        return of(com.dylan.game.ClearExpBonus.zero(), emptySlots(), 0);
    }

    public static Noti35ClearRewardBody of(com.dylan.game.ClearExpBonus bonus,
                                           List<List<SlotEntry>> groupA, int goldCardCost) {
        return new Noti35ClearRewardBody(0,
                clamp(bonus.base()), clamp(bonus.rank()), clamp(bonus.party()),
                clamp(bonus.avatar()), bonus.kind(),
                clamp(bonus.event()), clamp(bonus.expItem()), clamp(bonus.premium()),
                clamp(bonus.pcRoom()), clamp(bonus.channel()), clamp(bonus.member()),
                clamp(bonus.creature()),
                List.of(), groupA, goldCardCost, emptySlots(), emptySlots(), 0);
    }

    private static int clamp(long value) {
        return (int) Math.max(0, Math.min(MAX_VALUE, value));
    }

    public static Noti35ClearRewardBody probe(com.dylan.game.ClearExpBonus bonus,
                                              List<List<SlotEntry>> groupA) {
        return new Noti35ClearRewardBody(0,
                clamp(bonus.base()), PROBE_BASE + 1, PROBE_BASE + 2, PROBE_BASE + 3,
                com.dylan.game.ClearExpBonus.KIND_EVENT,
                PROBE_BASE + 4, PROBE_BASE + 5, PROBE_BASE + 6, PROBE_BASE + 7,
                PROBE_BASE + 8, PROBE_BASE + 9, PROBE_BASE + 10,
                List.of(), groupA, PROBE_BASE + 11, emptySlots(), emptySlots(), 0);
    }

    public Noti35ClearRewardBody withPartyOnly(int percent) {
        return new Noti35ClearRewardBody(ignored, gainedExp, rankBonus, partyBonus,
                avatarBonus, eventKind, eventBonus, expItemBonus, premiumBonus, pcRoomBonus,
                channelBonus, memberBonus, creatureBonus,
                items, groupA, goldCardCost, groupB, groupC, percent);
    }

    public byte[] toBytes() {
        if (ignored < 0 || ignored > 0xFF) {
            throw new PacketFormatException("ignored 超出 u8：" + ignored);
        }
        if (com.dylan.net.ServerConfig.A4) {
            // A4 结算奖励 9 个 u32 + 12 字节物品（ACT5 11 个 u32 + 15 字节）；按 ACT5 写客户端读穿，打完 boss 卡结算
            return toA4Bytes();
        }
        if (eventKind < 0 || eventKind > 0xFF) {
            throw new PacketFormatException("eventKind 超出 u8：" + eventKind);
        }
        if (items.size() > 0xFF) {
            throw new PacketFormatException("itemCount 是 u8，最多 255 条，收到 " + items.size());
        }
        PacketWriter w = new PacketWriter(64 + items.size() * 15);
        w.u8(ignored);
        w.u32(gainedExp).u32(rankBonus).u32(partyBonus).u32(avatarBonus);
        w.u8(eventKind);
        w.u32(eventBonus).u32(expItemBonus).u32(premiumBonus).u32(pcRoomBonus);
        w.u32(channelBonus).u32(memberBonus).u32(creatureBonus);
        w.u8(items.size());
        for (RewardItem item : items) {
            w.u16(item.a()).u32(item.b()).u32(item.c()).u16(item.d()).u8(item.e()).u16(item.f());
        }
        writeSlots(w, groupA);
        w.u32(goldCardCost);
        writeSlots(w, groupB);
        writeSlots(w, groupC);
        w.u32(partyOnly);
        return w.toArray();
    }

    /**
     * A4 客户端读法（DNF.exe 0042675B）：u8、9×u32、u8 条数 + 条数×(u16, u32, u32, u16)、
     * 4 槽 ×(u8 n + n×(u32, u32))、u32 黄金牌价、再两组 4 槽。没有 ACT5 的 eventKind 字节、
     * memberBonus / creatureBonus 和末尾 partyOnly，奖励物品每条 12 字节（ACT5 15）。
     * 官方 dungeon 1 通关 62 字节：{@code 00 7B010000 …×8 00 | 01 00000000 12000000 00 00 00 | 64000000 | 8×00}。
     * 9 个 u32 只确认了第 1 个是通关经验（官方日志 clearExp=379）；其余按 ACT5 顺序放，含义未逐一核对。
     */
    private byte[] toA4Bytes() {
        PacketWriter w = new PacketWriter(62 + items.size() * 12);
        w.u8(ignored);
        w.u32(gainedExp).u32(rankBonus).u32(partyBonus).u32(avatarBonus);
        w.u32(eventBonus).u32(expItemBonus).u32(premiumBonus).u32(pcRoomBonus).u32(channelBonus);
        w.u8(items.size());
        for (RewardItem item : items) {
            w.u16(item.a()).u32(item.b()).u32(item.c()).u16(item.d());
        }
        writeSlots(w, groupA);
        w.u32(goldCardCost);
        writeSlots(w, groupB);
        writeSlots(w, groupC);
        return w.toArray();
    }

    private static void writeSlots(PacketWriter w, List<List<SlotEntry>> groups) {
        for (List<SlotEntry> slot : groups) {
            if (slot.size() > 0xFF) {
                throw new PacketFormatException("每槽的条目数是 u8，最多 255 条，收到 "
                        + slot.size());
            }
            w.u8(slot.size());
            for (SlotEntry entry : slot) {
                w.u32(entry.itemId()).u32(entry.value());
            }
        }
    }

    public static Noti35ClearRewardBody parse(byte[] body) {
        PacketReader r = new PacketReader(body);
        int ignored = r.u8();
        int gainedExp = r.u32();
        int rankBonus = r.u32();
        int partyBonus = r.u32();
        int avatarBonus = r.u32();
        int eventKind = r.u8();
        int eventBonus = r.u32();
        int expItemBonus = r.u32();
        int premiumBonus = r.u32();
        int pcRoomBonus = r.u32();
        int channelBonus = r.u32();
        int memberBonus = r.u32();
        int creatureBonus = r.u32();
        int itemCount = r.u8();
        var items = new java.util.ArrayList<RewardItem>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            items.add(new RewardItem(r.u16(), r.u32(), r.u32(), r.u16(), r.u8(), r.u16()));
        }
        var groupA = readSlots(r);
        int goldCardCost = r.u32();
        var groupB = readSlots(r);
        var groupC = readSlots(r);
        return new Noti35ClearRewardBody(ignored, gainedExp, rankBonus, partyBonus, avatarBonus,
                eventKind, eventBonus, expItemBonus, premiumBonus, pcRoomBonus, channelBonus,
                memberBonus, creatureBonus,
                items, groupA, goldCardCost, groupB, groupC, r.u32());
    }

    private static List<List<SlotEntry>> readSlots(PacketReader r) {
        var groups = new java.util.ArrayList<List<SlotEntry>>(SLOT_COUNT);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            int n = r.u8();
            var entries = new java.util.ArrayList<SlotEntry>(n);
            for (int i = 0; i < n; i++) {
                entries.add(new SlotEntry(r.u32(), r.u32()));
            }
            groups.add(List.copyOf(entries));
        }
        return List.copyOf(groups);
    }
}
