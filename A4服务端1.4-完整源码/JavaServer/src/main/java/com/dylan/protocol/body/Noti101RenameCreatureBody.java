package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti101RenameCreatureBody(int uniqueId, String name) {

    public static final int PACKET_ID = 101;

    public static final int NAME_MAX = 0x14;

    public byte[] toBytes() {
        byte[] raw = name.getBytes(PacketWriter.DEFAULT_CHARSET);
        if (raw.length >= NAME_MAX) {
            throw new PacketFormatException("宠物名 " + raw.length + " 字节，必须严格小于 "
                    + NAME_MAX);
        }
        if (uniqueId < 0 || uniqueId > 0xFFFF) {
            throw new PacketFormatException("uniqueId 是 u16，超出 0..65535：" + uniqueId);
        }
        PacketWriter w = new PacketWriter(6 + raw.length);
        w.u16(uniqueId);
        w.string(name);
        return w.toArray();
    }
}
