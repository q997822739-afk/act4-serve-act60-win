package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Cmd127BossDieCheckRequest(int killerId, int monsterSeqId, int guard) {

    public static final int PACKET_ID = 127;

    public static final int LENGTH = 8;

    public Cmd127BossDieCheckRequest {
        if (killerId < 0 || killerId > 0xFFFF || monsterSeqId < 0 || monsterSeqId > 0xFFFF) {
            throw new PacketFormatException("killerId 和 monsterSeqId 都是 u16："
                    + killerId + "," + monsterSeqId);
        }
    }

    public static Cmd127BossDieCheckRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException("BOSS_DIE_CHECK 包体至少要 " + LENGTH + " 字节，实际 "
                    + (plain == null ? "null" : plain.length));
        }
        int killerId = (plain[0] & 0xFF) | ((plain[1] & 0xFF) << 8);
        int monsterSeqId = (plain[2] & 0xFF) | ((plain[3] & 0xFF) << 8);
        int guard = (plain[4] & 0xFF) | ((plain[5] & 0xFF) << 8)
                | ((plain[6] & 0xFF) << 16) | ((plain[7] & 0xFF) << 24);
        return new Cmd127BossDieCheckRequest(killerId, monsterSeqId, guard);
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(LENGTH);
        w.u16(killerId);
        w.u16(monsterSeqId);
        w.u32(guard);
        return w.toArray();
    }
}
