package com.dylan.protocol.body;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti13ItemListBody(int listType, int subValue, List<Item> items) {

    public static final int PACKET_ID = 13;

    public static final int ITEM_LENGTH = 21;

    public record Item(int slot, int itemId, int quantity, int itemAttr, int durability,
                       int sealFlag, int enchant, int amplifyType, int amplifyValue) {
        public Item {
            checkU16("slot", slot);
            checkU8("itemAttr", itemAttr);
            checkU16("durability", durability);
            checkU8("sealFlag", sealFlag);
            checkU8("amplifyType", amplifyType);
            checkU16("amplifyValue", amplifyValue);
        }
    }

    public static final int SEAL_SEALED = 1;

    public static final int SEAL_UNSEALED = 0;

    public static final int MAX_UPGRADE = 31;

    public static final int MAX_TRADE_LIMIT_COUNT = 7;

    public static int itemAttr(int upgrade, int tradeLimitCount) {
        if (upgrade < 0 || upgrade > MAX_UPGRADE) {
            throw new PacketFormatException("强化等级 " + upgrade + " 超出 0.." + MAX_UPGRADE);
        }
        if (tradeLimitCount < 0 || tradeLimitCount > MAX_TRADE_LIMIT_COUNT) {
            throw new PacketFormatException("交易次数 " + tradeLimitCount + " 超出 0.."
                    + MAX_TRADE_LIMIT_COUNT);
        }
        return (upgrade & 0x1F) | (tradeLimitCount << 5);
    }

    public Noti13ItemListBody {
        checkU8("listType", listType);
        checkU16("subValue", subValue);
        if (items == null) {
            throw new PacketFormatException("items 不能为 null");
        }
        items = List.copyOf(items);
        if (items.size() > 0xFFFF) {
            throw new PacketFormatException("条数是 u16，装不下 " + items.size() + " 条");
        }
    }

    public static Noti13ItemListBody empty(int listType) {
        return new Noti13ItemListBody(listType, 0, List.of());
    }

    public static Noti13ItemListBody of(int listType, List<Item> items) {
        return new Noti13ItemListBody(listType, 0, items);
    }

    public static Noti13ItemListBody cargo(int capacity, List<Item> items) {
        return new Noti13ItemListBody(ItemListType.LIST_CARGO, capacity, items);
    }

    public static final int AVATAR_TAIL_LENGTH = 30;

    public static final int AVATAR_ITEM_LENGTH = ITEM_LENGTH + 4 + AVATAR_TAIL_LENGTH;

    public byte[] toBytes() {
        boolean avatar = listType == ItemListType.LIST_AVATAR;
        int perItem = avatar ? AVATAR_ITEM_LENGTH : ITEM_LENGTH;
        PacketWriter w = new PacketWriter(8 + items.size() * perItem);
        w.u8(listType);
        if (listType == ItemListType.LIST_WITH_SUBVALUE) {
            w.u16(subValue);
        }
        w.u16(items.size());
        for (Item it : items) {
            writeItem(w, listType, it);
        }
        return w.toArray();
    }

    /**
     * A4 物品 18 字节：没有末尾的 amplifyType/amplifyValue。field_scan 对官方服改库验证过
     * 槽位/编号/数量/属性/耐久/封印的偏移（NOTI 13/0、13/2 同一布局）。
     */
    public static final int A4_ITEM_LENGTH = 18;

    static void writeItem(PacketWriter w, int listType, Item it) {
        w.u16(it.slot());
        w.u32(it.itemId());
        w.u32(it.quantity());
        w.u8(it.itemAttr());
        w.u16(it.durability());
        w.u8(it.sealFlag());
        w.u32(it.enchant());
        if (!ServerConfig.A4) {
            w.u8(it.amplifyType());
            w.u16(it.amplifyValue());
        }
        // A4 装扮栏（13/1）也是 18 字节一件：官方买时装后 9 件 165 字节 = 3 + 9×18，没有 ACT5 的 u32 30 + 30 字节尾巴
        if (listType == ItemListType.LIST_AVATAR && !ServerConfig.A4) {
            w.u32(AVATAR_TAIL_LENGTH);
            w.zeros(AVATAR_TAIL_LENGTH);
        }
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
