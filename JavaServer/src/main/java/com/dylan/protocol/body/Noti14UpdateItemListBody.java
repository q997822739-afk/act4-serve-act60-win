package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti14UpdateItemListBody(int listType, List<Noti13ItemListBody.Item> items) {

    public static final int PACKET_ID = 14;

    public static final int EMPTY_SLOT_ITEM_ID = -1;

    public static final int HEADER_LENGTH = 3;

    public Noti14UpdateItemListBody {
        if (listType < 0 || listType > 0xFF) {
            throw new PacketFormatException("listType 超出 u8：" + listType);
        }
        if (items == null) {
            throw new PacketFormatException("items 不能为 null");
        }
        items = List.copyOf(items);
        if (items.isEmpty()) {
            throw new PacketFormatException("NOTI " + PACKET_ID + " 至少要带一格，"
                    + "没有要更新的格子就别发这条包");
        }
        if (items.size() > 0xFFFF) {
            throw new PacketFormatException("条数是 u16，装不下 " + items.size() + " 条");
        }
    }

    public static Noti14UpdateItemListBody of(int listType, Noti13ItemListBody.Item item) {
        return new Noti14UpdateItemListBody(listType, List.of(item));
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(HEADER_LENGTH
                + items.size() * Noti13ItemListBody.AVATAR_ITEM_LENGTH);
        w.u8(listType);
        w.u16(items.size());
        for (Noti13ItemListBody.Item it : items) {
            Noti13ItemListBody.writeItem(w, listType, it);
        }
        return w.toArray();
    }
}
