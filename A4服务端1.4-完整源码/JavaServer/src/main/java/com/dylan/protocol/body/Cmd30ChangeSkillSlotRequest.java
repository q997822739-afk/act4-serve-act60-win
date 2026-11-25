package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd30ChangeSkillSlotRequest(int slotA, int slotB) {

    public static final int PACKET_ID = 30;

    public static final int LENGTH = 2;

    public static Cmd30ChangeSkillSlotRequest parse(byte[] body) {
        if (body == null || body.length < LENGTH) {
            throw new PacketFormatException("CMD " + PACKET_ID + " CHANGE_SKILLSLOT 包体只有 "
                    + (body == null ? 0 : body.length) + " 字节，至少要 " + LENGTH);
        }
        PacketReader r = new PacketReader(body);
        return new Cmd30ChangeSkillSlotRequest(r.u8(), r.u8());
    }

    public boolean isNoOp() {
        return slotA == slotB;
    }

    @Override
    public String toString() {
        return "槽 " + slotA + " → 槽 " + slotB + (isNoOp() ? "（拖回原位）" : "");
    }
}
