package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti187GameOptionBody(byte[] option, byte[] hotkeys) {

    public static final int PACKET_ID = 187;

    public static final int LENGTH = 4 + Cmd210SaveGameOptionRequest.OPTION_LENGTH
            + 4 + Cmd211SaveHotkeyOptionRequest.HOTKEY_LENGTH;

    public Noti187GameOptionBody {
        if (option == null || option.length != Cmd210SaveGameOptionRequest.OPTION_LENGTH) {
            throw new PacketFormatException("系统选项块必须是 "
                    + Cmd210SaveGameOptionRequest.OPTION_LENGTH + " 字节");
        }
        if (hotkeys == null || hotkeys.length != Cmd211SaveHotkeyOptionRequest.HOTKEY_LENGTH) {
            throw new PacketFormatException("快捷键块必须是 "
                    + Cmd211SaveHotkeyOptionRequest.HOTKEY_LENGTH + " 字节");
        }
    }

    public static Noti187GameOptionBody of(byte[] option, byte[] hotkeys) {
        return new Noti187GameOptionBody(option, hotkeys);
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(LENGTH);
        w.u32(option.length);
        w.bytes(option);
        w.u32(hotkeys.length);
        w.bytes(hotkeys);
        return w.toArray();
    }
}
