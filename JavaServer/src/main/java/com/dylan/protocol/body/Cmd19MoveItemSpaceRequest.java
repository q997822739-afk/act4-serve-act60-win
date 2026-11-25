package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd19MoveItemSpaceRequest(int srcType, int srcSlot, int srcItemId, int count,
                                        int dstType, int dstSlot, int dstItemId) {
    public static final int PACKET_ID = 19;

    public static final int LENGTH_WITHOUT_STAMP = 18;

    public static final int LENGTH = 22;

    public static Cmd19MoveItemSpaceRequest parse(byte[] plain) {
        if (plain.length < LENGTH_WITHOUT_STAMP) {
            throw new PacketFormatException("MOVE_ITEMSPACE 至少 " + LENGTH_WITHOUT_STAMP
                    + " 字节，实际 " + plain.length);
        }
        PacketReader r = new PacketReader(plain);
        int srcType = r.u8();
        int srcSlot = r.u16();
        int srcItemId = r.u32();
        int count = r.u32();
        int dstType = r.u8();
        int dstSlot = r.u16();
        int dstItemId = r.u32();
        return new Cmd19MoveItemSpaceRequest(srcType, srcSlot, srcItemId, count,
                dstType, dstSlot, dstItemId);
    }

    public boolean isEquipping() {
        return dstType == ItemListType.LIST_EQUIPPED && srcType != ItemListType.LIST_EQUIPPED;
    }

    public boolean isUnequipping() {
        return srcType == ItemListType.LIST_EQUIPPED && dstType != ItemListType.LIST_EQUIPPED;
    }

    @Override
    public String toString() {
        return "容器" + srcType + " 槽" + srcSlot + "(物品" + srcItemId + ") x" + count
                + " → 容器" + dstType + " 槽" + dstSlot + "(物品" + dstItemId + ")";
    }
}
