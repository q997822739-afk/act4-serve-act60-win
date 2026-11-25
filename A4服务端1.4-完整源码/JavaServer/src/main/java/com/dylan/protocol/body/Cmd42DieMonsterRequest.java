package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

import java.util.List;

public record Cmd42DieMonsterRequest(int monsterSeqId, int killerId, int damageTotal,
                                     int hitCount, List<Attacker> attackers,
                                     int dieX, int dieY, int nonce) {
    public static final int PACKET_ID = 42;

    public static final int FIXED_LENGTH = 29;

    public static final int ATTACKER_LENGTH = 10;

    public record Attacker(int objectKind, int objectId, int damage, int hitCount) {
        public static final int KIND_CHARACTER = 0x111;

        public boolean isCharacter() {
            return objectKind == KIND_CHARACTER;
        }

        public int damagePerHit() {
            return hitCount == 0 ? damage : damage / hitCount;
        }
    }

    public Cmd42DieMonsterRequest {
        attackers = List.copyOf(attackers);
    }

    public static Cmd42DieMonsterRequest parse(byte[] plain) {
        if (plain == null || plain.length < FIXED_LENGTH) {
            throw new PacketFormatException("DIE_MONSTER 包体至少要 " + FIXED_LENGTH + " 字节，实际 "
                    + (plain == null ? "null" : plain.length));
        }
        PacketReader r = new PacketReader(plain);
        int monsterSeqId = r.u16();
        int killerId = r.u16();
        r.skip(8);
        int damageTotal = r.u32();
        int hitCount = r.u32();
        int attackerCount = r.u8();
        int needed = FIXED_LENGTH + attackerCount * ATTACKER_LENGTH;
        if (com.dylan.net.ServerConfig.A4 && plain.length < needed) {
            // A4 客户端的 CMD 42 恒 48 字节（明文缓冲区定长）：2 条伤害记录时尾部被截掉
            // （官方日志 2 例：`… 02 11 02 09 00 4d000000 0100 | 11 01 01 00 3e560000 0100 | 5801 7101 00 fd25`）。
            // 能读满的伤害记录照读，坐标 / nonce 读不到就当 0，不再整包丢弃（丢了怪就永远不死）。
            Attacker[] got = new Attacker[attackerCount];
            int n = 0;
            for (; n < attackerCount && r.remaining() >= ATTACKER_LENGTH; n++) {
                got[n] = new Attacker(r.u16(), r.u16(), r.u32(), r.u16());
            }
            int dieX = r.remaining() >= 2 ? r.u16() : 0;
            int dieY = r.remaining() >= 2 ? r.u16() : 0;
            return new Cmd42DieMonsterRequest(monsterSeqId, killerId, damageTotal, hitCount,
                    List.of(java.util.Arrays.copyOf(got, n)), dieX, dieY, 0);
        }
        if (plain.length < needed) {
            throw new PacketFormatException("DIE_MONSTER 说有 " + attackerCount
                    + " 条伤害记录，那要 " + needed + " 字节，明文只有 " + plain.length);
        }
        Attacker[] attackers = new Attacker[attackerCount];
        for (int i = 0; i < attackerCount; i++) {
            attackers[i] = new Attacker(r.u16(), r.u16(), r.u32(), r.u16());
        }
        int dieX = r.u16();
        int dieY = r.u16();
        r.skip(2);
        int nonce = r.u16();
        return new Cmd42DieMonsterRequest(monsterSeqId, killerId, damageTotal, hitCount,
                List.of(attackers), dieX, dieY, nonce);
    }
}
