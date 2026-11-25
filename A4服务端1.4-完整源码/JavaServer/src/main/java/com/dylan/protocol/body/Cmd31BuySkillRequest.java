package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;
import com.dylan.protocol.io.PacketWriter;

public record Cmd31BuySkillRequest(int skillId) {

    public static final int PACKET_ID = 31;

    public static final int LENGTH = 1;

    public Cmd31BuySkillRequest {
        if (skillId < 0 || skillId > 0xFF) {
            throw new PacketFormatException("skillId 超出 u8：" + skillId);
        }
    }

    public static Cmd31BuySkillRequest parse(byte[] body) {
        if (body == null || body.length < LENGTH) {
            throw new PacketFormatException("CMD " + PACKET_ID + " BUY_SKILL 包体只有 "
                    + (body == null ? 0 : body.length) + " 字节，至少要 " + LENGTH);
        }
        PacketReader r = new PacketReader(body);
        return new Cmd31BuySkillRequest(r.u8());
    }

    public byte[] toBytes() {
        return new PacketWriter(LENGTH).u8(skillId).toArray();
    }

    @Override
    public String toString() {
        return "学技能 #" + skillId;
    }
}
