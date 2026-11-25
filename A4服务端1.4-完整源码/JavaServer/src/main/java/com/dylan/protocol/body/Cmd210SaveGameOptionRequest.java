package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd210SaveGameOptionRequest(byte[] option) {

    public static final int PACKET_ID = 210;

    public static final int OPTION_LENGTH = 84;

    public static final int OPTION_COUNT = OPTION_LENGTH / 4;

    public static final int MIN_LENGTH = 4;

    public Cmd210SaveGameOptionRequest {
        if (option == null || option.length != OPTION_LENGTH) {
            throw new PacketFormatException("系统选项块必须是 " + OPTION_LENGTH + " 字节");
        }
    }

    public static Cmd210SaveGameOptionRequest parse(byte[] plain) {
        if (plain == null) {
            throw new PacketFormatException("CMD " + PACKET_ID + " 包体解不开");
        }
        PacketReader r = new PacketReader(plain);
        int length = r.u32();
        if (length < 0 || length > OPTION_LENGTH) {
            throw new PacketFormatException("CMD " + PACKET_ID + " 的选项块长度 " + length
                    + " 不在 0.." + OPTION_LENGTH + " 之间");
        }
        byte[] option = new byte[OPTION_LENGTH];
        System.arraycopy(r.bytes(length), 0, option, 0, length);
        return new Cmd210SaveGameOptionRequest(option);
    }

    public long value(int index) {
        if (index < 0 || index >= OPTION_COUNT) {
            throw new IndexOutOfBoundsException("系统选项只有 " + OPTION_COUNT + " 项：" + index);
        }
        int at = index * 4;
        return (option[at] & 0xFFL)
                | ((option[at + 1] & 0xFFL) << 8)
                | ((option[at + 2] & 0xFFL) << 16)
                | ((option[at + 3] & 0xFFL) << 24);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("系统选项 21 项 [");
        for (int i = 0; i < OPTION_COUNT; i++) {
            sb.append(i == 0 ? "" : ",").append(value(i));
        }
        return sb.append(']').toString();
    }
}
