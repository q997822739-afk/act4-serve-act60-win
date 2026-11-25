package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti39Body(int itemNo, int pickerUserId, int winnerUserId, int invSlot,
                         int[] dice) {

    public static final int PACKET_ID = 39;

    public static final int PARTY_SIZE = 4;

    public static final int ITEM_LENGTH = 2 + 2 + PARTY_SIZE + 2 + 2 + 1;

    public static final int[] NO_DICE = new int[PARTY_SIZE];

    public Noti39Body {
        dice = dice == null ? NO_DICE.clone() : dice.clone();
        if (dice.length != PARTY_SIZE) {
            throw new PacketFormatException("dice 要 " + PARTY_SIZE + " 个，给了 " + dice.length);
        }
        checkU16("itemNo", itemNo);
        checkU16("pickerUserId", pickerUserId);
        checkU16("winnerUserId", winnerUserId);
        checkU16("invSlot", invSlot);
    }

    public static Noti39Body solo(int itemNo, int uniqueId, int invSlot) {
        return new Noti39Body(itemNo, uniqueId, uniqueId, invSlot, NO_DICE);
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(ITEM_LENGTH);
        w.u16(itemNo);
        w.u16(pickerUserId);
        for (int d : dice) {
            w.u8(d);
        }
        w.u16(winnerUserId);
        w.u16(invSlot);
        // A4 物品形态 12 字节：客户端（DNF.exe 004281F8）读 4×u8 骰子、u16、u16 就结束，没有 ACT5 的末尾 u8；
        // 官方 NOTI 39 实抓全是 12 字节（如 `00 40 00000000 0000 0000 0000`）。
        if (!com.dylan.net.ServerConfig.A4) {
            w.u8(0);
        }
        return w.toArray();
    }

    @Override
    public int[] dice() {
        return dice.clone();
    }

    public record MoneyShare(boolean valid, long amount, long[] extras) {

        public static final MoneyShare NONE = new MoneyShare(false, 0, new long[0]);

        public MoneyShare {
            extras = extras == null ? new long[0] : extras.clone();
            if (amount < 0 || amount > 0xFFFF_FFFFL) {
                throw new PacketFormatException("金额 " + amount + " 放不进 u32");
            }
            if (extras.length > 0xFF) {
                throw new PacketFormatException("加成明细最多 255 条，给了 " + extras.length);
            }
            for (long extra : extras) {
                if (extra < 0 || extra > 0xFFFF_FFFFL) {
                    throw new PacketFormatException("加成 " + extra + " 放不进 u32");
                }
            }
        }

        public static MoneyShare of(long amount) {
            return new MoneyShare(true, amount, null);
        }

        @Override
        public long[] extras() {
            return extras.clone();
        }

        int length() {
            return valid ? 1 + 4 + 1 + 4 * extras.length : 1 + 4;
        }
    }

    public static final int MONEY_SLOTS = PARTY_SIZE;

    public static final int GOLD_BAG_ITEM_ID = 1199;

    public static byte[] money(int itemNo, int pickerUserId, MoneyShare[] shares) {
        checkU16("itemNo", itemNo);
        checkU16("pickerUserId", pickerUserId);
        if (shares == null || shares.length != MONEY_SLOTS) {
            throw new PacketFormatException("金币形态要 " + MONEY_SLOTS + " 个槽，给了 "
                    + (shares == null ? "null" : String.valueOf(shares.length)));
        }
        int length = 2 + 2;
        for (MoneyShare share : shares) {
            if (share == null) {
                throw new PacketFormatException("槽位不能是 null —— 空槽用 MoneyShare.NONE");
            }
            length += share.length();
        }
        PacketWriter w = new PacketWriter(length);
        w.u16(itemNo);
        w.u16(pickerUserId);
        for (MoneyShare share : shares) {
            w.u8(share.valid() ? 1 : 0);
            w.u32((int) share.amount());
            if (!share.valid()) {
                continue;
            }
            long[] extras = share.extras();
            w.u8(extras.length);
            for (long extra : extras) {
                w.u32((int) extra);
            }
        }
        return w.toArray();
    }

    private static void checkU16(String name, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new PacketFormatException(name + " 超出 u16：" + value);
        }
    }
}
