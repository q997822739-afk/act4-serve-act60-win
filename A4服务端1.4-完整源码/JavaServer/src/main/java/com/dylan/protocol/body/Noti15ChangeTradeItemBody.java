package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti15ChangeTradeItemBody(int tradeSlot, int itemId, int quantity, int itemAttr,
                                        int durability, int enchant, int amplifyType,
                                        int amplifyValue, boolean avatarTail) {

    public static final int PACKET_ID = 15;

    public static final int LENGTH = 20;

    public static final int ITEM_ID_CLEARED = -1;

    public static final int ITEM_ID_MONEY = 0;

    public static final int MONEY_SLOT = 0;

    public static final int AVATAR_TAIL_LENGTH = Noti13ItemListBody.AVATAR_TAIL_LENGTH;

    public Noti15ChangeTradeItemBody {
        checkU16("tradeSlot", tradeSlot);
        checkU8("itemAttr", itemAttr);
        checkU16("durability", durability);
        checkU8("amplifyType", amplifyType);
        checkU16("amplifyValue", amplifyValue);
    }

    public static Noti15ChangeTradeItemBody cleared(int tradeSlot) {
        return new Noti15ChangeTradeItemBody(tradeSlot, ITEM_ID_CLEARED, 0, 0, 0, 0, 0, 0, false);
    }

    public static Noti15ChangeTradeItemBody money(long total) {
        return new Noti15ChangeTradeItemBody(MONEY_SLOT,
                total <= 0 ? ITEM_ID_CLEARED : ITEM_ID_MONEY,
                clampU32(total), 0, 0, 0, 0, 0, false);
    }

    public static Noti15ChangeTradeItemBody item(int tradeSlot, int itemId, long quantity,
                                                 int itemAttr, int durability, long enchant,
                                                 int amplifyType, int amplifyValue,
                                                 boolean avatarTail) {
        return new Noti15ChangeTradeItemBody(tradeSlot, itemId, clampU32(quantity), itemAttr,
                durability, (int) enchant, amplifyType, amplifyValue, avatarTail);
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(avatarTail ? LENGTH + 4 + AVATAR_TAIL_LENGTH : LENGTH);
        w.u16(tradeSlot);
        w.u32(itemId);
        w.u32(quantity);
        w.u8(itemAttr);
        w.u16(durability);
        w.u32(enchant);
        // A4 17 字节：客户端（DNF.exe 00422DC6..00422E02）顺序读 u16 u32 u32 u8 u16 u32 后就处理，
        // 没有 ACT5 的增幅 u8+u16 和时装尾巴（A4 别的物品表也都没有）。官方日志无交易样本。
        if (!com.dylan.net.ServerConfig.A4) {
            w.u8(amplifyType);
            w.u16(amplifyValue);
            if (avatarTail) {
                w.u32(AVATAR_TAIL_LENGTH);
                w.zeros(AVATAR_TAIL_LENGTH);
            }
        }
        return w.toArray();
    }

    private static int clampU32(long value) {
        return (int) Math.max(0L, Math.min(0xFFFFFFFFL, value));
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
