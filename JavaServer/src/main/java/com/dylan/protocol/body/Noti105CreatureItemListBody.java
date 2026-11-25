package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti105CreatureItemListBody(List<Item> items) {

    public static final int PACKET_ID = 105;

    public static final int MAX_ITEMS = 0xFF;

    public static final int NAME_MAX = 0x1E;

    public record Item(long uid, int stomach, long exp, int level, String name, int noCharge) {

        void writeTo(PacketWriter w) {
            if (stomach < 0 || stomach > 0xFF) {
                throw new PacketFormatException("饱食度是 u8，超出 0..255：" + stomach);
            }
            if (level < 0 || level > 0xFF) {
                throw new PacketFormatException("宠物等级是 u8，超出 0..255：" + level);
            }
            if (noCharge < 0 || noCharge > 0xFF) {
                throw new PacketFormatException("noCharge 是 u8，超出 0..255：" + noCharge);
            }
            byte[] raw = name.getBytes(PacketWriter.DEFAULT_CHARSET);
            if (raw.length >= NAME_MAX) {
                throw new PacketFormatException("宠物名 " + raw.length + " 字节，必须严格小于 "
                        + NAME_MAX + "：长度撞上容量时客户端只吃长度不读内容，后面全错位");
            }
            w.u32((int) uid);
            w.u8(stomach);
            w.u32((int) exp);
            w.u8(level);
            w.string(name);
            w.u8(noCharge);
        }
    }

    public Noti105CreatureItemListBody {
        items = List.copyOf(items);
        if (items.size() > MAX_ITEMS) {
            throw new PacketFormatException("宠物条数是 u8，超出 0.." + MAX_ITEMS + "："
                    + items.size());
        }
    }

    public static Noti105CreatureItemListBody empty() {
        return new Noti105CreatureItemListBody(List.of());
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(1 + items.size() * 32);
        w.u8(items.size());
        for (Item item : items) {
            item.writeTo(w);
        }
        return w.toArray();
    }
}
