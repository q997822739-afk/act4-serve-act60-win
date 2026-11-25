package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti155StartDeathTowerMapBody(int stage, int opaque, int mapIndex,
                                            List<Monster> monsters,
                                            List<Noti29Body.MapObject> objects) {
    public static final int PACKET_ID = 155;

    public static final int MONSTER_LENGTH = 9;

    public static final int HEADER_LENGTH = 9;

    public record Monster(int spawnIndex, int seqId, int monsterId, int level, int type,
                          int named, int namedGroup) {
        public Monster {
            checkU8("spawnIndex", spawnIndex);
            checkU16("seqId", seqId);
            checkU16("monsterId", monsterId);
            checkU8("level", level);
            checkU8("type", type);
            checkU8("named", named);
            checkU8("namedGroup", namedGroup);
        }

        public static Monster of(int spawnIndex, int seqId, int monsterId, int level, int type) {
            return new Monster(spawnIndex, seqId, monsterId, level, type, 0, 0);
        }
    }

    public Noti155StartDeathTowerMapBody {
        checkU16("stage", stage);
        checkU16("mapIndex", mapIndex);
        if (monsters == null || objects == null) {
            throw new PacketFormatException("monsters 和 objects 都不能为 null");
        }
        monsters = List.copyOf(monsters);
        objects = List.copyOf(objects);
        if (monsters.size() > 0xFF || objects.size() > 0xFF) {
            throw new PacketFormatException("monsterCount 和 objectCount 都是 u8");
        }
    }

    public static Noti155StartDeathTowerMapBody floor(int stage, int mapIndex,
                                                      List<Monster> monsters) {
        return new Noti155StartDeathTowerMapBody(stage, 0, mapIndex, monsters, List.of());
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(HEADER_LENGTH + 1
                + monsters.size() * MONSTER_LENGTH
                + objects.size() * Noti29Body.OBJECT_LENGTH);
        w.u16(stage);
        w.u32(opaque);
        w.u16(mapIndex);
        w.u8(monsters.size());
        for (Monster m : monsters) {
            w.u8(m.spawnIndex());
            w.u16(m.seqId());
            w.u16(m.monsterId());
            w.u8(m.level());
            w.u8(m.type());
            w.u8(m.named());
            w.u8(m.namedGroup());
        }
        w.u8(objects.size());
        for (Noti29Body.MapObject o : objects) {
            w.u8(o.objectIndex() & 0xFF);
            w.u16(o.itemNo());
            w.u32(o.itemId());
            w.u32(o.count());
            w.u16(o.durability());
            // A4 物件 13 字节：客户端（DNF.exe 00424E3F..00424E75）读 u8 u16 u32 u32 u16，没有 ACT5 的增幅 u8+u16。
            if (!com.dylan.net.ServerConfig.A4) {
                w.u8(o.amplifyA());
                w.u16(o.amplifyB());
            }
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
