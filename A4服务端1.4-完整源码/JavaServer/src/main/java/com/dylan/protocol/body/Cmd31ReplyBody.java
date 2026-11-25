package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd31ReplyBody {

    public static final int PACKET_ID = 31;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int LENGTH_OK = 6;

    public static final int LENGTH_FAIL = 2;

    public static final int SLOT_GUILD_SKILL = 0xFF;

    public static final int GUILD_SKILL_ID_FIRST = 0xC8;

    public static final int GUILD_SKILL_ID_LAST = 0xD0;

    private Cmd31ReplyBody() {
    }

    public static boolean isGuildSkillId(int skillId) {
        return skillId >= GUILD_SKILL_ID_FIRST && skillId <= GUILD_SKILL_ID_LAST;
    }

    public static byte[] accepted(int remainSp, int slot, int skillId, int newLevel) {
        if (remainSp < 0 || remainSp > 0xFFFF) {
            throw new PacketFormatException("remainSp 超出 u16：" + remainSp);
        }
        checkU8("skillId", skillId);
        int wireSlot = isGuildSkillId(skillId) ? SLOT_GUILD_SKILL : slot;
        checkU8("slot", wireSlot);
        checkU8("newLevel", newLevel);
        PacketWriter w = new PacketWriter(LENGTH_OK);
        w.u8(RESULT_OK);
        w.u16(remainSp);
        w.u8(wireSlot);
        w.u8(skillId);
        w.u8(newLevel);
        return w.toArray();
    }

    public static byte[] rejected(int errorCode) {
        checkU8("errorCode", errorCode);
        PacketWriter w = new PacketWriter(LENGTH_FAIL);
        w.u8(RESULT_FAIL);
        w.u8(errorCode);
        return w.toArray();
    }

    private static void checkU8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(name + " 超出 u8：" + value);
        }
    }
}
