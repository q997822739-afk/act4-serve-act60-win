package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd179TryEnchantRequest(int type, long recipeItemId, int itemSpace, int slot,
                                      int positions) {
    public static final int PACKET_ID = 179;

    public static final int TYPE_MINE = 0;

    public static final int TYPE_OTHER = 1;

    public static final int TYPE_CANCEL = 2;

    public static final int TYPE_REFUSED = 3;

    public static final int CANCEL_LENGTH = 1;

    public static final int FULL_LENGTH = 9;

    public Cmd179TryEnchantRequest {
        if (type < 0 || type > 0xFF) {
            throw new PacketFormatException("type 是 u8，超出 0..255：" + type);
        }
        if (recipeItemId < 0 || recipeItemId > 0xFFFF_FFFFL) {
            throw new PacketFormatException("recipeItemId 是 u32：" + recipeItemId);
        }
        if (itemSpace < 0 || itemSpace > 0xFF) {
            throw new PacketFormatException("itemSpace 是 u8：" + itemSpace);
        }
        if (slot < 0 || slot > 0xFFFF) {
            throw new PacketFormatException("slot 是 u16：" + slot);
        }
    }

    public boolean hasPosition() {
        return positions > 0;
    }

    public static Cmd179TryEnchantRequest parse(byte[] plain) {
        if (plain == null || plain.length < CANCEL_LENGTH) {
            throw new PacketFormatException("CMD " + PACKET_ID + " 至少 " + CANCEL_LENGTH
                    + " 字节，实际 " + (plain == null ? "null" : plain.length));
        }
        PacketReader r = new PacketReader(plain);
        int type = r.u8();
        if (type == TYPE_CANCEL) {
            return new Cmd179TryEnchantRequest(TYPE_CANCEL, 0, 0, 0, 0);
        }
        if (plain.length < FULL_LENGTH - 3) {
            throw new PacketFormatException("CMD " + PACKET_ID + " type=" + type
                    + " 至少 5 字节，实际 " + plain.length);
        }
        long recipeItemId = r.u32() & 0xFFFF_FFFFL;
        int count = r.remaining() > 0 ? r.u8() : 0;
        int itemSpace = 0;
        int slot = 0;
        if (count > 0 && r.remaining() >= 3) {
            itemSpace = r.u8();
            slot = r.u16();
        }
        return new Cmd179TryEnchantRequest(type, recipeItemId, itemSpace, slot, count);
    }
}
