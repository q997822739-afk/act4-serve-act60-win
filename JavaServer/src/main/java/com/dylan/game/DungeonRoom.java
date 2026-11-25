package com.dylan.game;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DungeonRoom {
    private final Map<Integer, RoomMonster> living = new LinkedHashMap<>();

    private final Map<Integer, RoomMonster> dead = new LinkedHashMap<>();

    private final Map<Integer, GroundItem> ground = new LinkedHashMap<>();

    private int nextItemNo = 1;

    private final Map<Integer, Integer> hellGroupRemaining = new LinkedHashMap<>();

    public record GroundItem(int itemNo, com.dylan.game.drop.GeneratedItem item,
                             int fromSeqId,
                             com.dylan.store.blob.InvenItem original,
                             int objectIndex) {
        public GroundItem(int itemNo, com.dylan.game.drop.GeneratedItem item, int fromSeqId) {
            this(itemNo, item, fromSeqId, null, NOT_FROM_OBJECT);
        }

        public GroundItem(int itemNo, com.dylan.game.drop.GeneratedItem item, int fromSeqId,
                          com.dylan.store.blob.InvenItem original) {
            this(itemNo, item, fromSeqId, original, NOT_FROM_OBJECT);
        }

        public boolean fromPlayer() {
            return original != null;
        }
    }

    public static final int DROPPED_BY_PLAYER = -1;

    public static final int NO_KILLER = -1;

    public static final int NOT_FROM_OBJECT = -1;

    private DungeonRoom(List<RoomMonster> monsters) {
        fill(monsters);
    }

    private void fill(List<RoomMonster> monsters) {
        for (RoomMonster m : monsters) {
            RoomMonster old = living.put(m.seqId(), m);
            if (old != null) {
                throw new IllegalArgumentException("同一个房间里 seqId 撞了：" + m.seqId()
                        + "（" + old + " 和 " + m + "）");
            }
        }
    }

    public DungeonRoom respawn(List<RoomMonster> monsters) {
        living.clear();
        dead.clear();
        fill(monsters);
        return this;
    }

    public static DungeonRoom empty() {
        return new DungeonRoom(List.of());
    }

    public static DungeonRoom of(List<RoomMonster> monsters) {
        return new DungeonRoom(monsters);
    }

    public Collection<RoomMonster> living() {
        return living.values();
    }

    public int livingCount() {
        return living.size();
    }

    public int clearableLivingCount() {
        int n = 0;
        for (RoomMonster m : living.values()) {
            if (m.countsForRoomClear()) {
                n++;
            }
        }
        return n;
    }

    public boolean isEmpty() {
        return living.isEmpty() && dead.isEmpty();
    }

    public RoomMonster kill(int seqId) {
        RoomMonster monster = living.remove(seqId);
        if (monster != null) {
            dead.put(seqId, monster);
        }
        return monster;
    }

    public void resetHellGroups(Map<Integer, Integer> remaining) {
        hellGroupRemaining.clear();
        hellGroupRemaining.putAll(remaining);
    }

    public record HellKill(boolean known, boolean lastOfGroup, boolean allCleared) {
        public static final HellKill NONE = new HellKill(true, false, false);
    }

    public HellKill killHellPartyGroup(int groupId) {
        Integer remaining = hellGroupRemaining.get(groupId);
        if (remaining == null) {
            return new HellKill(false, false, false);
        }
        if (remaining != 0) {
            hellGroupRemaining.put(groupId, remaining - 1);
            return new HellKill(true, false, false);
        }
        hellGroupRemaining.remove(groupId);
        return new HellKill(true, true, hellGroupRemaining.isEmpty());
    }

    public boolean isCleared() {
        return living.isEmpty() && !dead.isEmpty();
    }

    public int drop(com.dylan.game.drop.GeneratedItem item, int fromSeqId) {
        return put(new GroundItem(itemNo(), item, fromSeqId));
    }

    public int dropByPlayer(com.dylan.game.drop.GeneratedItem item,
                            com.dylan.store.blob.InvenItem original) {
        return put(new GroundItem(itemNo(), item, DROPPED_BY_PLAYER, original));
    }

    public int dropFromObject(com.dylan.game.drop.GeneratedItem item, int objectIndex) {
        return put(new GroundItem(itemNo(), item, NO_KILLER, null, objectIndex));
    }

    public List<GroundItem> groundItems() {
        return List.copyOf(ground.values());
    }

    private int itemNo() {
        int itemNo = nextItemNo;
        nextItemNo = nextItemNo >= 0xFFFF ? 1 : nextItemNo + 1;
        return itemNo;
    }

    private int put(GroundItem item) {
        ground.put(item.itemNo(), item);
        return item.itemNo();
    }

    public GroundItem pickUp(int itemNo) {
        return ground.remove(itemNo);
    }

    public void restore(GroundItem item) {
        ground.put(item.itemNo(), item);
    }

    @Override
    public String toString() {
        return "房间：活 " + living.size() + " 只、死 " + dead.size() + " 只";
    }
}
