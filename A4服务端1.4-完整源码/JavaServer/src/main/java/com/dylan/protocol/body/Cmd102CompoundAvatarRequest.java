package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd102CompoundAvatarRequest(int materialSlot, int slotA, int itemIdA,
                                          int slotB, int itemIdB, int targetItemId,
                                          int abilityNo, int rare, int eventType) {
    public static final int PACKET_ID = 102;

    public static final int LENGTH = 22;

    public static final int EVENT_NONE = 0;

    public static Cmd102CompoundAvatarRequest parse(byte[] plain) {
        if (plain.length < LENGTH) {
            throw new PacketFormatException("COMPOUND_AVATAR 至少 " + LENGTH
                    + " 字节，实际 " + plain.length);
        }
        PacketReader r = new PacketReader(plain);
        int materialSlot = r.u16();
        int slotA = r.u16();
        int itemIdA = r.u32();
        int slotB = r.u16();
        int itemIdB = r.u32();
        int targetItemId = r.u32();
        int abilityNo = r.u16();
        int rare = r.u8();
        int eventType = r.u8();
        return new Cmd102CompoundAvatarRequest(materialSlot, slotA, itemIdA, slotB, itemIdB,
                targetItemId, abilityNo, rare, eventType);
    }

    public boolean isRare() {
        return rare == 1;
    }

    @Override
    public String toString() {
        return "COMPOUND_AVATAR[装扮栏槽 " + slotA + "(" + itemIdA + ") + 槽 " + slotB
                + "(" + itemIdB + ")，材料在主背包槽 " + materialSlot
                + (isRare() ? "，合成稀有" : "，指定合成 " + targetItemId)
                + "，属性下标 " + abilityNo
                + (eventType == EVENT_NONE ? "" : "，活动 " + eventType) + "]";
    }
}
