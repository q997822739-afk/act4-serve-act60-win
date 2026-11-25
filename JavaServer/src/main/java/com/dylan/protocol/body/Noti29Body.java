package com.dylan.protocol.body;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti29Body(int roomX, int roomY, int opaque, int hellPartyDifficulty, int mode,
                         int mapIndex, List<Entity> entities, List<MapObject> objects,
                         int hellPartyAppeared) {
    public static final int PACKET_ID = 29;

    public static final int MODE_PLAIN = 1;

    public static final int MODE_EXTRA = 2;

    public static final int MODE_VISITED = 0;

    public static final int ENTITY_LENGTH = 12;

    public static final int OBJECT_LENGTH = 16;

    public record Entity(int prefix, int extraFlag, int spawnIndex, int seqId,
                         int monsterId, int level, int type, int named, int namedGroup) {
        public Entity {
            checkU16("prefix", prefix);
            checkU8("extraFlag", extraFlag);
            checkU8("spawnIndex", spawnIndex);
            checkU16("seqId", seqId);
            checkU16("monsterId", monsterId);
            checkU8("level", level);
            checkU8("type", type);
            checkU8("named", named);
            checkU8("namedGroup", namedGroup);
        }

        public static Entity of(int spawnIndex, int seqId, int monsterId, int level, int type) {
            return new Entity(0, 0, spawnIndex, seqId, monsterId, level, type, 0, 0);
        }
    }

    public record MapObject(int objectIndex, int itemNo, int itemId, int count,
                            int durability, int amplifyA, int amplifyB) {
        public MapObject {
            checkS8("objectIndex", objectIndex);
            checkU16("itemNo", itemNo);
            checkU16("durability", durability);
            checkU8("amplifyA", amplifyA);
            checkU16("amplifyB", amplifyB);
        }
    }

    public Noti29Body {
        checkU8("roomX", roomX);
        checkU8("roomY", roomY);
        checkU8("hellPartyDifficulty", hellPartyDifficulty);
        checkU8("mode", mode);
        checkU8("hellPartyAppeared", hellPartyAppeared);
        if (entities == null || objects == null) {
            throw new PacketFormatException("entities 和 objects 都不能为 null");
        }
        entities = List.copyOf(entities);
        objects = List.copyOf(objects);
        if (mode != MODE_PLAIN && mode != MODE_EXTRA
                && (!entities.isEmpty() || !objects.isEmpty())) {
            throw new PacketFormatException("mode " + mode + " 会让客户端读完第五个字段就停，"
                    + "后面的实体和物件一条都不会被解析");
        }
        checkU16("mapIndex", mapIndex);
        if (entities.size() > 0xFF || objects.size() > 0xFF) {
            throw new PacketFormatException("entityCount 和 objectCount 都是 u8");
        }
    }

    public static Noti29Body visitedRoom(int roomX, int roomY) {
        return visitedRoom(roomX, roomY, 0);
    }

    public static Noti29Body visitedRoom(int roomX, int roomY, int hellPartyDifficulty) {
        return new Noti29Body(roomX, roomY, 0, hellPartyDifficulty, MODE_VISITED, 0,
                List.of(), List.of(), 0);
    }

    /**
     * A4 与 ACT5 的区别（官方成品服教程房间 61000，48 字节逐字节）：头部没有 hellPartyDifficulty，
     * 实体 9 字节，没有开头的 u16 prefix 和 u8 extraFlag。物件段官方样本里条数为 0，A4 布局未验证，沿用 ACT5。
     */
    public static final int A4_ENTITY_LENGTH = 9;

    public byte[] toBytes() {
        boolean a4 = ServerConfig.A4;
        PacketWriter w = new PacketWriter(16 + entities.size() * ENTITY_LENGTH
                + objects.size() * OBJECT_LENGTH);
        w.u8(roomX);
        w.u8(roomY);
        w.u32(opaque);
        if (!a4) {
            w.u8(hellPartyDifficulty);
        }
        w.u8(mode);
        if (mode != MODE_PLAIN && mode != MODE_EXTRA) {
            return w.toArray();
        }

        w.u16(mapIndex);
        w.u8(entities.size());
        for (Entity e : entities) {
            if (!a4) {
                w.u16(e.prefix());
                w.u8(e.extraFlag());
            }
            w.u8(e.spawnIndex());
            w.u16(e.seqId());
            w.u16(e.monsterId());
            w.u8(e.level());
            w.u8(e.type());
            w.u8(e.named());
            w.u8(e.namedGroup());
        }
        w.u8(objects.size());
        for (MapObject o : objects) {
            w.u8(o.objectIndex() & 0xFF);
            w.u16(o.itemNo());
            w.u32(o.itemId());
            w.u32(o.count());
            w.u16(o.durability());
            // A4 物件每条 13 字节（客户端 004253E5..00425415：u8 u16 u32 u32 u16），没有 ACT5 的增幅 u8+u16
            if (!com.dylan.net.ServerConfig.A4) {
                w.u8(o.amplifyA());
                w.u16(o.amplifyB());
            }
        }
        w.u8(hellPartyAppeared);
        return w.toArray();
    }

    private static void checkS8(String name, int value) {
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
            throw new PacketFormatException(name + " 超出 s8：" + value);
        }
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
