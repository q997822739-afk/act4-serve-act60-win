package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;
import com.dylan.store.blob.InvenItem;

public record Noti40Body(int dropperUserId, int x, int y, int itemNo,
                         long itemId, int itemAttr, long addInfo,
                         int endurance, long enchant,
                         int amplifyType, int amplifyValue) {

    public static final int PACKET_ID = 40;

    public static final int LENGTH = 26;

    public Noti40Body {
        checkU16("dropperUserId", dropperUserId);
        checkU16("x", x);
        checkU16("y", y);
        checkU16("itemNo", itemNo);
        checkU32("itemId", itemId);
        checkU8("itemAttr", itemAttr);
        checkU32("addInfo", addInfo);
        checkU16("endurance", endurance);
        checkU32("enchant", enchant);
        checkU8("amplifyType", amplifyType);
        checkU16("amplifyValue", amplifyValue);
    }

    public static Noti40Body of(int dropperUserId, int x, int y, int itemNo,
                                InvenItem item, boolean stackable, int count) {
        return new Noti40Body(dropperUserId, x, y, itemNo,
                item.itemId(), item.itemAttr(),
                stackable ? count : item.addInfo(),
                item.endurance(), item.enchant(),
                item.amplify().visibleAbilityType() & 0xFF,
                item.amplify().visibleAbilityValue());
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(LENGTH);
        w.u16(dropperUserId);
        w.u16(x);
        w.u16(y);
        w.u16(itemNo);
        w.u32((int) itemId);
        w.u8(itemAttr);
        w.u32((int) addInfo);
        w.u16(endurance);
        w.u32((int) enchant);
        // A4 23 字节，没有 ACT5 末尾的增幅 u8+u16：客户端（DNF.exe）读 4×u16、u32、u8、u32、u16、u32 即止；
        // 官方实抓 `0100 9801 3801 0440 630C0000 01 00000000 0000 00000000`。
        if (!com.dylan.net.ServerConfig.A4) {
            w.u8(amplifyType);
            w.u16(amplifyValue);
        }
        return w.toArray();
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

    private static void checkU32(String name, long value) {
        if (value < 0 || value > 0xFFFFFFFFL) {
            throw new PacketFormatException(name + " 超出 u32：" + value);
        }
    }
}
