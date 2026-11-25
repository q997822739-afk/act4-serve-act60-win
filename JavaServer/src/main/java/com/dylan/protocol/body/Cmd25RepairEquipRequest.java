package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd25RepairEquipRequest(int itemSpace, int slot) {

    public static final int PACKET_ID = 25;

    public static final int LENGTH = 3;

    public static final int SLOT_ALL = 0xFFFF;

    public boolean isRepairAll() {
        return slot == SLOT_ALL;
    }

    public static Cmd25RepairEquipRequest parse(byte[] plain) {
        if (plain.length < LENGTH) {
            throw new PacketFormatException("CMD " + PACKET_ID + " REPAIR_EQUIPMENT 包体只有"
                    + plain.length + " 字节，至少要 " + LENGTH);
        }
        PacketReader r = new PacketReader(plain);
        return new Cmd25RepairEquipRequest(r.u8(), r.u16());
    }

    @Override
    public String toString() {
        if (isRepairAll()) {
            return "REPAIR_EQUIPMENT[全部]";
        }
        return "REPAIR_EQUIPMENT[容器 " + itemSpace + " 第 " + slot + " 格]";
    }
}
