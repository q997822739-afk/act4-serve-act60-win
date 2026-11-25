package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd180PutItemForEnchantRequest(int kind, long money, int itemSpace, int slot) {

    public static final int PACKET_ID = 180;

    public static final int KIND_MONEY = 0;

    public static final int KIND_EQUIPMENT = 1;

    public static final int KIND_MONSTER_CARD = 2;

    public static final int ITEM_LENGTH = 4;

    public static final int MONEY_LENGTH = 5;

    public Cmd180PutItemForEnchantRequest {
        if (kind < 0 || kind > 0xFF) {
            throw new PacketFormatException("kind 是 u8：" + kind);
        }
        if (money < 0 || money > 0xFFFF_FFFFL) {
            throw new PacketFormatException("money 是 u32：" + money);
        }
        if (itemSpace < 0 || itemSpace > 0xFF) {
            throw new PacketFormatException("itemSpace 是 u8：" + itemSpace);
        }
        if (slot < 0 || slot > 0xFFFF) {
            throw new PacketFormatException("slot 是 u16：" + slot);
        }
    }

    public String describe() {
        return switch (kind) {
            case KIND_MONEY -> "金币 " + money;
            case KIND_EQUIPMENT -> "装备 容器" + itemSpace + " 槽" + slot;
            case KIND_MONSTER_CARD -> "怪物卡 容器" + itemSpace + " 槽" + slot;
            default -> "未知 kind " + kind;
        };
    }

    public static Cmd180PutItemForEnchantRequest parse(byte[] plain) {
        if (plain == null || plain.length < ITEM_LENGTH) {
            throw new PacketFormatException("CMD " + PACKET_ID + " 至少 " + ITEM_LENGTH
                    + " 字节，实际 " + (plain == null ? "null" : plain.length));
        }
        PacketReader r = new PacketReader(plain);
        int kind = r.u8();
        if (kind == KIND_MONEY) {
            if (plain.length < MONEY_LENGTH) {
                throw new PacketFormatException("CMD " + PACKET_ID + " kind 0 至少 "
                        + MONEY_LENGTH + " 字节，实际 " + plain.length);
            }
            return new Cmd180PutItemForEnchantRequest(KIND_MONEY, r.u32() & 0xFFFF_FFFFL, 0, 0);
        }
        return new Cmd180PutItemForEnchantRequest(kind, 0, r.u8(), r.u16());
    }
}
