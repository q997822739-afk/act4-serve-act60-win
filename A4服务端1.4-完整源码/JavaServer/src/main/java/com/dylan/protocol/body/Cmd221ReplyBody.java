package com.dylan.protocol.body;

import java.util.List;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd221ReplyBody {

    private Cmd221ReplyBody() {
    }

    public static final int PACKET_ID = 221;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int HEADER_LENGTH = 7;

    public static final int ENTRY_LENGTH = 13;

    public static final int ERROR_CANNOT_OPEN = 17;

    public static final int ERROR_INVENTORY_FULL = 4;

    public static final int ERROR_NOT_ENOUGH_SPACE = 19;

    public record Entry(int insertedSlot, int itemId, long addInfo, int endurance, int itemAttr) {

        public Entry {
            if (insertedSlot < 0 || insertedSlot > 0xFFFF) {
                throw new PacketFormatException("insertedSlot 放不进 u16：" + insertedSlot);
            }
            if (endurance < 0 || endurance > 0xFFFF) {
                throw new PacketFormatException("endurance 放不进 u16：" + endurance);
            }
            if (itemAttr < 0 || itemAttr > 0xFF) {
                throw new PacketFormatException("itemAttr 放不进 u8：" + itemAttr);
            }
        }

        public static final int SLOT_NONE = 0xFFFF;
    }

    public static byte[] opened(int boxSlot, int keySlot, List<Entry> entries) {
        if (entries.size() > 0xFFFF) {
            throw new PacketFormatException("条目数放不进 u16：" + entries.size());
        }
        PacketWriter w = new PacketWriter(HEADER_LENGTH + entries.size() * ENTRY_LENGTH)
                .u8(RESULT_OK)
                .u16(boxSlot)
                .u16(keySlot)
                .u16(entries.size());
        for (Entry e : entries) {
            w.u16(e.insertedSlot())
             .u32((int) e.itemId())
             .u32((int) e.addInfo())
             .u16(e.endurance())
             .u8(e.itemAttr());
        }
        return w.toArray();
    }

    public static byte[] rejected(int errorCode) {
        return new PacketWriter(2).u8(RESULT_FAIL).u8(errorCode).toArray();
    }
}
