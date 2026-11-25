package com.dylan.protocol.body;

import java.util.List;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti168TryEnchantBody(int successRate, List<Long> itemIds) {

    public static final int PACKET_ID = 168;

    public static final int FLAG = 1;

    public static final int MAX_RATE = 100;

    public Noti168TryEnchantBody {
        if (successRate < 0 || successRate > 0xFFFF) {
            throw new PacketFormatException("成功率是 u16：" + successRate);
        }
        itemIds = List.copyOf(itemIds);
        if (itemIds.size() > 0xFF) {
            throw new PacketFormatException("条数是 u8，装不下 " + itemIds.size() + " 条");
        }
    }

    public static Noti168TryEnchantBody of(int successRate, long recipeItemId) {
        return new Noti168TryEnchantBody(Math.min(successRate, MAX_RATE), List.of(recipeItemId));
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(4 + itemIds.size() * 4);
        w.u8(FLAG);
        w.u16(successRate);
        w.u8(itemIds.size());
        for (long id : itemIds) {
            if (id < 0 || id > 0xFFFF_FFFFL) {
                throw new PacketFormatException("物品编号是 u32：" + id);
            }
            w.u32((int) id);
        }
        return w.toArray();
    }
}
