package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti157DeathTowerRewardBody(int exp, List<List<RewardItem>> rows) {
    public static final int PACKET_ID = 157;

    public static final int ROWS = 4;

    public static final int EMPTY_ITEM_ID = -1;

    public record RewardItem(int itemId, int addInfo) {}

    public Noti157DeathTowerRewardBody {
        if (rows == null) {
            throw new PacketFormatException("rows 不能为 null");
        }
        if (rows.size() != ROWS) {
            throw new PacketFormatException("必须正好 " + ROWS + " 段（客户端跑满四轮），"
                    + "给了 " + rows.size() + " 段");
        }
        rows = rows.stream().map(List::copyOf).toList();
        for (List<RewardItem> row : rows) {
            if (row.size() > 0xFF) {
                throw new PacketFormatException("每段的 count 是 u8：" + row.size());
            }
        }
    }

    public static Noti157DeathTowerRewardBody of(int exp, List<List<RewardItem>> bySlot) {
        if (bySlot == null || bySlot.size() != ROWS) {
            throw new PacketFormatException("bySlot 必须正好 " + ROWS + " 段");
        }
        return new Noti157DeathTowerRewardBody(exp, bySlot);
    }

    public byte[] toBytes() {
        int items = rows.stream().mapToInt(List::size).sum();
        PacketWriter w = new PacketWriter(4 + ROWS + items * 8);
        w.u32(exp);
        for (List<RewardItem> row : rows) {
            w.u8(row.size());
            for (RewardItem item : row) {
                w.u32(item.itemId());
                w.u32(item.addInfo());
            }
        }
        return w.toArray();
    }
}
