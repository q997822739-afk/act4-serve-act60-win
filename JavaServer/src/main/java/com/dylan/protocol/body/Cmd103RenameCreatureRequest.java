package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;
import com.dylan.protocol.io.PacketWriter;

public record Cmd103RenameCreatureRequest(int slot, int itemSpace, String name) {

    public static final int PACKET_ID = 103;

    public static final int NAME_CAPACITY = 13;

    public static final byte[] NO_NAME = {
            (byte) 0xC0, (byte) 0xCC, (byte) 0xB8, (byte) 0xA7,
            (byte) 0xBE, (byte) 0xF8, (byte) 0xC0, (byte) 0xBD,
    };

    public static final int FIXED_LENGTH = 7;

    public static Cmd103RenameCreatureRequest parse(byte[] plain) {
        if (plain.length < FIXED_LENGTH) {
            throw new PacketFormatException("RENAME_CREATURE 至少 " + FIXED_LENGTH
                    + " 字节，实际 " + plain.length);
        }
        PacketReader r = new PacketReader(plain);
        int slot = r.u16();
        int itemSpace = r.u8();
        int nameLen = r.u32();
        if (nameLen < 0 || nameLen >= NAME_CAPACITY) {
            throw new PacketFormatException("宠物名 " + nameLen + " 字节，必须落在 0.."
                    + (NAME_CAPACITY - 1) + "（官方 get_str 的容量是 " + NAME_CAPACITY + "）");
        }
        byte[] raw = r.bytes(nameLen);
        String name = isNoName(raw) ? "" : new String(raw, PacketWriter.DEFAULT_CHARSET);
        return new Cmd103RenameCreatureRequest(slot, itemSpace, name);
    }

    private static boolean isNoName(byte[] raw) {
        if (raw.length != NO_NAME.length) {
            return false;
        }
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] != NO_NAME[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "容器" + itemSpace + " 槽 " + slot + " 的道具 → 宠物改名「" + name + "」";
    }
}
