package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Cmd74SelectCardReply(List<Position> positions) {

    public static final int PACKET_ID = 74;

    public static final int SLOT_COUNT = 4;

    public static final int NOBODY = 0xFF;

    public static final int CARD_TYPE_FREE = 0;

    public static final int CARD_TYPE_GOLD = 1;

    public record Entry(int itemId, int value) {}

    public static final Entry GOLD_MONEY_PLACEHOLDER = new Entry(0, 0);

    public record Position(int freeOwner, int goldOwner, List<Entry> entries) {

        public Position {
            entries = List.copyOf(entries);
            if (goldOwner == NOBODY && !entries.isEmpty()) {
                throw new PacketFormatException("没人点金卡的牌位不能带条目");
            }
            if (goldOwner != NOBODY
                    && (entries.isEmpty() || entries.get(0).itemId() != GOLD_MONEY_PLACEHOLDER.itemId())) {
                throw new PacketFormatException(
                        "有人点了金卡的牌位，第一条必须是 itemId 0 的金币占位");
            }
        }

        public static Position untouched() {
            return new Position(NOBODY, NOBODY, List.of());
        }
    }

    public Cmd74SelectCardReply {
        if (positions.size() != SLOT_COUNT) {
            throw new PacketFormatException("必须正好 " + SLOT_COUNT + " 个牌位，收到 "
                    + positions.size());
        }
        positions = List.copyOf(positions);
    }

    public static Cmd74SelectCardReply untouched() {
        var out = new java.util.ArrayList<Position>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            out.add(Position.untouched());
        }
        return new Cmd74SelectCardReply(out);
    }

    /**
     * 拒绝翻牌：00, u8 错误码（2 字节）。
     *
     * <p>依据：A4 官方实抓（pair_probe / sweep_dungeon CMD 74，还没到翻牌阶段就发翻牌）= REPLY 74 {@code 00 04}；
     * catalog_cmd「命令 74：翻牌布局未生成、已翻牌、请求无效或当前不在副本中」。
     * 客户端读 u8 结果，0 = 失败，后面再读一个 u8 错误码。
     */
    public static byte[] rejected(int error) {
        if (error < 0 || error > 0xFF) {
            throw new PacketFormatException("错误码超出 u8：" + error);
        }
        return new PacketWriter(2).u8(0).u8(error).toArray();
    }

    /** 布局没生成 / 不在副本里 / 已经翻完（官方实抓 00 04）。 */
    public static final int ERROR_BAD_STATE = 4;

    public static Cmd74SelectCardReply eachCardInfo(int[] freeOwners, int[] goldOwners,
                                                    Entry[] goldItems) {
        if (freeOwners.length != SLOT_COUNT || goldOwners.length != SLOT_COUNT
                || goldItems.length != SLOT_COUNT) {
            throw new PacketFormatException("三组都必须正好 " + SLOT_COUNT + " 项");
        }
        var out = new java.util.ArrayList<Position>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (goldOwners[i] == NOBODY) {
                out.add(new Position(freeOwners[i], NOBODY, List.of()));
            } else if (goldItems[i] == null) {
                out.add(new Position(freeOwners[i], goldOwners[i],
                        List.of(GOLD_MONEY_PLACEHOLDER)));
            } else {
                out.add(new Position(freeOwners[i], goldOwners[i],
                        List.of(GOLD_MONEY_PLACEHOLDER, goldItems[i])));
            }
        }
        return new Cmd74SelectCardReply(out);
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(1 + SLOT_COUNT * 3);
        w.u8(1);
        for (Position p : positions) {
            requireU8("freeOwner", p.freeOwner());
            requireU8("goldOwner", p.goldOwner());
            if (p.entries().size() > 0xFF) {
                throw new PacketFormatException("条目数是 u8，最多 255 条");
            }
            w.u8(p.freeOwner()).u8(p.goldOwner()).u8(p.entries().size());
            for (Entry e : p.entries()) {
                w.u32(e.itemId()).u32(e.value());
            }
        }
        return w.toArray();
    }

    private static void requireU8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(name + " 超出 u8：" + value);
        }
    }
}
