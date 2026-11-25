package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd211SaveHotkeyOptionRequest(byte[] hotkeys) {
    public static final int PACKET_ID = 211;

    public static final int HOTKEY_LENGTH = 128;

    public static final int HOTKEY_COUNT = HOTKEY_LENGTH / 2;

    public Cmd211SaveHotkeyOptionRequest {
        if (hotkeys == null || hotkeys.length != HOTKEY_LENGTH) {
            throw new PacketFormatException("快捷键块必须是 " + HOTKEY_LENGTH + " 字节");
        }
    }

    public static Cmd211SaveHotkeyOptionRequest parse(byte[] plain) {
        if (plain == null) {
            throw new PacketFormatException("CMD " + PACKET_ID + " 包体解不开");
        }
        PacketReader r = new PacketReader(plain);
        int length = r.u32();
        if (length < 0 || length > HOTKEY_LENGTH) {
            throw new PacketFormatException("CMD " + PACKET_ID + " 的快捷键块长度 " + length
                    + " 不在 0.." + HOTKEY_LENGTH + " 之间");
        }
        byte[] hotkeys = new byte[HOTKEY_LENGTH];
        System.arraycopy(r.bytes(length), 0, hotkeys, 0, length);
        return new Cmd211SaveHotkeyOptionRequest(hotkeys);
    }

    public int key(int index) {
        if (index < 0 || index >= HOTKEY_COUNT) {
            throw new IndexOutOfBoundsException("快捷键只有 " + HOTKEY_COUNT + " 项：" + index);
        }
        int at = index * 2;
        return (hotkeys[at] & 0xFF) | ((hotkeys[at + 1] & 0xFF) << 8);
    }

    public boolean isBlank() {
        for (int i = 0; i < HOTKEY_COUNT; i++) {
            if (key(i) != 0) {
                return false;
            }
        }
        return true;
    }

    public static final int SKILL_SLOT_FIRST = 20;

    public static final int SKILL_SLOT_LAST = 37;

    @Override
    public String toString() {
        int bound = 0;
        for (int i = 0; i < HOTKEY_COUNT; i++) {
            if (key(i) != 0) {
                bound++;
            }
        }
        StringBuilder slots = new StringBuilder();
        for (int i = SKILL_SLOT_FIRST; i <= SKILL_SLOT_LAST; i++) {
            slots.append(i == SKILL_SLOT_FIRST ? "" : ",").append(key(i));
        }
        return "快捷键 " + HOTKEY_COUNT + " 项，其中 " + bound + " 项非 0；技能栏 #"
                + SKILL_SLOT_FIRST + ".." + SKILL_SLOT_LAST + " = [" + slots + "]";
    }
}
