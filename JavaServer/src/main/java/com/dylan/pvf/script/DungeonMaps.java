package com.dylan.pvf.script;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public final class DungeonMaps {

    public static final int TYPE_NORMAL = MapScript.MAP_TYPE_NORMAL;
    public static final int TYPE_BOSS = MapScript.MAP_TYPE_BOSS;
    public static final int TYPE_DUMMY = MapScript.MAP_TYPE_DUMMY;

    private static final int TYPES = 3;

    private static final int[] DIRECTIONS = {
            GridCode.DIR_EAST, GridCode.DIR_NORTH, GridCode.DIR_WEST, GridCode.DIR_SOUTH,
    };

    private final List<Integer>[][][] byDirection;

    private final List<Integer>[][] anyDirection;

    private final List<Integer>[] startMaps;

    private final java.util.Map<Integer, Integer> entranceOf = new java.util.HashMap<>();

    private final java.util.Map<Integer, MapScript> scriptOf = new java.util.HashMap<>();

    @SuppressWarnings("unchecked")
    private DungeonMaps() {
        byDirection = new List[TYPES][GridCode.CODES][DIRECTIONS.length];
        anyDirection = new List[TYPES][GridCode.CODES];
        startMaps = new List[GridCode.CODES];
        for (int t = 0; t < TYPES; t++) {
            for (int c = 0; c < GridCode.CODES; c++) {
                anyDirection[t][c] = new ArrayList<>();
                for (int d = 0; d < DIRECTIONS.length; d++) {
                    byDirection[t][c][d] = new ArrayList<>();
                }
            }
        }
        for (int c = 0; c < GridCode.CODES; c++) {
            startMaps[c] = new ArrayList<>();
        }
    }

    public static DungeonMaps empty() {
        return new DungeonMaps();
    }

    public void insert(int mapId, MapScript map) {
        int type = map.mapType();
        scriptOf.put(mapId, map);
        List<MapScript.Greed> greeds = map.greeds();
        if (greeds.isEmpty()) {
            insertOne(type, GridCode.ALL_DIRECTIONS, GridCode.ALL_DIRECTIONS, mapId, map);
            return;
        }
        for (MapScript.Greed greed : greeds) {
            insertOne(type, greed.gridCode(), greed.entrance(), mapId, map);
        }
    }

    private void insertOne(int type, int gridCode, int entrance, int mapId, MapScript map) {
        if (gridCode < 0 || gridCode >= GridCode.CODES || type < 0 || type >= TYPES) {
            return;
        }
        anyDirection[type][gridCode].add(mapId);
        entranceOf.merge(mapId, entrance, (a, b) -> a | b);
        for (int d = 0; d < DIRECTIONS.length; d++) {
            if ((entrance & DIRECTIONS[d]) != 0) {
                byDirection[type][gridCode][d].add(mapId);
            }
        }
        if (map.canBeStartMap() && !startMaps[gridCode].contains(mapId)) {
            startMaps[gridCode].add(mapId);
        }
    }

    public int chooseRandomMap(int type, int gridCode, int direction, RandomGenerator random) {
        if (type < 0 || type >= TYPES || gridCode < 0 || gridCode >= GridCode.CODES) {
            return -1;
        }
        List<Integer> pool = direction == 0
                ? anyDirection[type][gridCode]
                : byDirection[type][gridCode][directionIndex(direction)];
        if (pool == null || pool.isEmpty()) {
            return -1;
        }
        return pool.get(random.nextInt(pool.size()));
    }

    public List<Integer> candidates(int type, int gridCode, int direction) {
        if (type < 0 || type >= TYPES || gridCode < 0 || gridCode >= GridCode.CODES) {
            return List.of();
        }
        List<Integer> pool = direction == 0
                ? anyDirection[type][gridCode]
                : byDirection[type][gridCode][directionIndex(direction)];
        return pool == null ? List.of() : List.copyOf(pool);
    }

    public boolean checkEntrance(int mapId, int direction) {
        Integer mask = entranceOf.get(mapId);
        return mask != null && (direction & mask) != 0;
    }

    public MapScript map(int mapId) {
        return scriptOf.get(mapId);
    }

    public int startMap(int gridCode, RandomGenerator random) {
        if (gridCode < 0 || gridCode >= GridCode.CODES) {
            return -1;
        }
        List<Integer> pool = startMaps[gridCode];
        if (pool.isEmpty()) {
            return -1;
        }
        return pool.get(random.nextInt(pool.size()));
    }

    /** 起始房候选（{@link #startMap} 从这里随机取）；离线工具 DungeonDump 用。 */
    public List<Integer> startCandidates(int gridCode) {
        if (gridCode < 0 || gridCode >= GridCode.CODES) {
            return List.of();
        }
        return List.copyOf(startMaps[gridCode]);
    }

    public boolean hasDummyMaps() {
        for (int c = 0; c < GridCode.CODES; c++) {
            if (!anyDirection[TYPE_DUMMY][c].isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        int total = 0;
        for (int t = 0; t < TYPES; t++) {
            for (int c = 0; c < GridCode.CODES; c++) {
                total += anyDirection[t][c].size();
            }
        }
        return total;
    }

    private static int directionIndex(int direction) {
        for (int d = 0; d < DIRECTIONS.length; d++) {
            if ((direction & DIRECTIONS[d]) != 0) {
                return d;
            }
        }
        return 0;
    }
}
