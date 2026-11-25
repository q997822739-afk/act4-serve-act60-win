package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti38Body(int monsterSeqId, List<Drop> drops, int more, int apcRoleType,
                         boolean fakeBoss, int bossX, int bossY) {

    public static final int PACKET_ID = 38;

    public static final int NO_APC_ROLE = 0xFF;

    public static final int DROP_LENGTH = 17;

    public static final int MIN_LENGTH = 6;

    public record Drop(int itemNo, int itemId, int count, int durability,
                       int amplifyA, int amplifyB, int hint) {

        public static final int NO_ITEM = 0xFFFFFFFF;

        public static final int NO_HINT = 0xFFFF;

        public Drop {
            checkU16("itemNo", itemNo);
            checkU16("durability", durability);
            checkU8("amplifyA", amplifyA);
            checkU16("amplifyB", amplifyB);
            checkU16("hint", hint);
        }

        public static Drop of(int itemNo, int itemId, int count, int durability) {
            return new Drop(itemNo, itemId, count, durability, 0, 0, NO_HINT);
        }
    }

    public Noti38Body {
        checkU16("monsterSeqId", monsterSeqId);
        checkU8("more", more);
        checkU8("apcRoleType", apcRoleType);
        if (drops == null) {
            throw new PacketFormatException("drops 不能为 null");
        }
        drops = List.copyOf(drops);
        if (drops.size() > 0xFF) {
            throw new PacketFormatException("dropCount 是 u8，最多 255 件");
        }
        if (fakeBoss) {
            checkU8("bossX", bossX);
            checkU8("bossY", bossY);
        }
    }

    public static Noti38Body plain(int monsterSeqId) {
        return new Noti38Body(monsterSeqId, List.of(), 0, NO_APC_ROLE, false, 0, 0);
    }

    public static Noti38Body withDrops(int monsterSeqId, List<Drop> drops) {
        return new Noti38Body(monsterSeqId, drops, 0, NO_APC_ROLE, false, 0, 0);
    }

    public static Noti38Body fakeBossDrops(int monsterSeqId, List<Drop> drops,
                                           int bossX, int bossY) {
        return new Noti38Body(monsterSeqId, drops, 0, NO_APC_ROLE, true, bossX, bossY);
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(
                MIN_LENGTH + drops.size() * DROP_LENGTH + (fakeBoss ? 2 : 0));
        w.u16(monsterSeqId);
        w.u8(drops.size());
        for (Drop d : drops) {
            w.u16(d.itemNo());
            w.u32(d.itemId());
            w.u32(d.count());
            w.u16(d.durability());
            // A4 掉落每条 14 字节（客户端 DNF.exe 004274C9：u16, u32, u32, u16, u16），没有 ACT5 的增幅 u8+u16。
            // 官方 boss 掉金币：`02 00 | 01 | 0b 40 00000000 1c000000 0000 0000 | 00 ff ff 00 00 00`…
            if (!com.dylan.net.ServerConfig.A4) {
                w.u8(d.amplifyA());
                w.u16(d.amplifyB());
            }
            w.u16(d.hint());
        }
        w.u8(more);
        w.u8(apcRoleType);
        w.u8(fakeBoss ? 1 : 0);
        if (fakeBoss) {
            w.u8(bossX);
            w.u8(bossY);
        }
        return w.toArray();
    }

    private static void checkU8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(name + " 超出 u8：" + value);
        }
    }

    private static void checkU16(String name, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new PacketFormatException(name + " 超出 u16：" + value);
        }
    }
}
