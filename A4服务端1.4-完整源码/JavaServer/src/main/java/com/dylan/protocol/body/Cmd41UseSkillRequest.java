package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Cmd41UseSkillRequest(int skillId, int adjustedValue, int clientValue) {

    public static final int PACKET_ID = 41;

    public static final int LENGTH = 5;

    public Cmd41UseSkillRequest {
        if (skillId < 0 || skillId > 0xFF) {
            throw new PacketFormatException("skillId 是 u8：" + skillId);
        }
        if (adjustedValue < 0 || adjustedValue > 0xFFFF
                || clientValue < 0 || clientValue > 0xFFFF) {
            throw new PacketFormatException("后两个字段都是 u16："
                    + adjustedValue + "," + clientValue);
        }
    }

    public static Cmd41UseSkillRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException("USE_SKILL 包体至少要 " + LENGTH + " 字节，实际 "
                    + (plain == null ? "null" : plain.length));
        }
        int skillId = plain[0] & 0xFF;
        int adjustedValue = u16(plain, 1);
        int clientValue = u16(plain, 3);
        return new Cmd41UseSkillRequest(skillId, adjustedValue, clientValue);
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(LENGTH);
        w.u8(skillId);
        w.u16(adjustedValue);
        w.u16(clientValue);
        return w.toArray();
    }

    @Override
    public String toString() {
        return "USE_SKILL[技能 " + skillId + " adjusted=" + adjustedValue
                + " client=" + clientValue + "]";
    }

    private static int u16(byte[] b, int at) {
        return (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8);
    }
}
