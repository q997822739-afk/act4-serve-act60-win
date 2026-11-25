package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti86Body(boolean success, int uniqueId, int itemId, int upgrade) {

    public static final int PACKET_ID = 86;

    public static final int SUBTYPE_UPGRADE = 1;

    public static final int SUBTYPE_LOTTERY = 2;

    public static final int SUBTYPE_MULTIBOX = 4;

    public static final int LENGTH = 9;

    public static final int MULTIBOX_LENGTH = 13;

    public Noti86Body {
        if (uniqueId < 0 || uniqueId > 0xFFFF) {
            throw new PacketFormatException("uniqueId 放不进 u16：" + uniqueId);
        }
        if (upgrade < 0 || upgrade > 0xFF) {
            throw new PacketFormatException("强化等级放不进 u8：" + upgrade);
        }
    }

    public byte[] toBytes() {
        return encode(SUBTYPE_UPGRADE, success ? 1 : 0, uniqueId, itemId, upgrade);
    }

    public static byte[] lottery(int uniqueId, int itemId, int upgrade) {
        return encode(SUBTYPE_LOTTERY, 1, uniqueId, itemId, upgrade);
    }

    public static byte[] multiBox(int uniqueId, int itemId, int upgrade, int boxItemId) {
        return new PacketWriter(MULTIBOX_LENGTH)
                .u8(SUBTYPE_MULTIBOX)
                .u8(1)
                .u16(uniqueId)
                .u32(itemId)
                .u8(upgrade)
                .u32(boxItemId)
                .toArray();
    }

    private static byte[] encode(int subType, int flag, int uniqueId, int itemId, int upgrade) {
        return new PacketWriter(LENGTH)
                .u8(subType)
                .u8(flag)
                .u16(uniqueId)
                .u32(itemId)
                .u8(upgrade)
                .toArray();
    }
}
