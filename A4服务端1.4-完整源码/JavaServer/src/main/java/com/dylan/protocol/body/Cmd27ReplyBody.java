package com.dylan.protocol.body;

import java.util.List;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd27ReplyBody {

    private Cmd27ReplyBody() {
    }

    public static final int PACKET_ID = 27;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int CONTAINER_MAIN = 0;

    public static final int ERROR_CANNOT_MAKE = 17;

    public static final int ERROR_NOT_ENOUGH_MATERIAL = 21;

    public static final int ERROR_SKILL = 22;

    public static final int ERROR_MULTI_EQUIP = 23;

    public static final int ERROR_INVENTORY_FULL = 4;

    public static final int ERROR_ITEM_LOCKED = 205;

    public record Change(int container, int slot, long count) {

        public Change {
            if (container < 0 || container > 0xFF) {
                throw new PacketFormatException("container 放不进 u8：" + container);
            }
            if (slot < 0 || slot > 0xFFFF) {
                throw new PacketFormatException("slot 放不进 u16：" + slot);
            }
            if (count < 0 || count > 0xFFFFFFFFL) {
                throw new PacketFormatException("count 放不进 u32：" + count);
            }
        }

        public static Change main(int slot, long count) {
            return new Change(CONTAINER_MAIN, slot, count);
        }
    }

    public record Output(int slot, long itemId, long count, int upgrade, int endurance,
                         int abilityType, int abilityValue) {

        public Output {
            if (slot < 0 || slot > 0xFFFF) {
                throw new PacketFormatException("slot 放不进 u16：" + slot);
            }
            if (itemId < 0 || itemId > 0xFFFFFFFFL) {
                throw new PacketFormatException("itemId 放不进 u32：" + itemId);
            }
            if (count < 0 || count > 0xFFFFFFFFL) {
                throw new PacketFormatException("count 放不进 u32：" + count);
            }
            if (upgrade < 0 || upgrade > 0xFF) {
                throw new PacketFormatException("upgrade 放不进 u8：" + upgrade);
            }
            if (endurance < 0 || endurance > 0xFFFF) {
                throw new PacketFormatException("endurance 放不进 u16：" + endurance);
            }
            if (abilityType < 0 || abilityType > 0xFF) {
                throw new PacketFormatException("abilityType 放不进 u8：" + abilityType);
            }
            if (abilityValue < 0 || abilityValue > 0xFFFF) {
                throw new PacketFormatException("abilityValue 放不进 u16：" + abilityValue);
            }
        }
    }

    public static final int HEADER_LENGTH = 2;

    public static final int CHANGE_LENGTH = 7;

    public static final int OUTPUT_LENGTH = 16;

    public static byte[] made(List<Change> changes, List<Output> outputs) {
        if (changes.size() > 0xFF) {
            throw new PacketFormatException("变动条数放不进 u8：" + changes.size());
        }
        if (outputs.size() > 0xFF) {
            throw new PacketFormatException("产出条数放不进 u8：" + outputs.size());
        }
        PacketWriter w = new PacketWriter(HEADER_LENGTH + changes.size() * CHANGE_LENGTH
                + 1 + outputs.size() * OUTPUT_LENGTH)
                .u8(RESULT_OK)
                .u8(changes.size());
        for (Change c : changes) {
            w.u8(c.container()).u16(c.slot()).u32((int) c.count());
        }
        w.u8(outputs.size());
        // A4 客户端每条产出只读 u16, u32, u32, u8, u16 = 13 字节（DNF.exe 00413985..004139B5），
        // 没有 ACT5 末尾的增幅 u8 + u16。
        boolean a4 = com.dylan.net.ServerConfig.A4;
        for (Output o : outputs) {
            w.u16(o.slot())
             .u32((int) o.itemId())
             .u32((int) o.count())
             .u8(o.upgrade())
             .u16(o.endurance());
            if (!a4) {
                w.u8(o.abilityType()).u16(o.abilityValue());
            }
        }
        return w.toArray();
    }

    public static byte[] rejected(int errorCode) {
        return new PacketWriter(2).u8(RESULT_FAIL).u8(errorCode).toArray();
    }
}
