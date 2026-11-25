package com.dylan.game;

import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DeathTowerRegistry {

    public static final int MAX_TOWERS = 600;

    public static final int NOT_IN_TOWER = -1;

    private static final Map<Integer, DeathTower> BY_INDEX = new ConcurrentHashMap<>();

    private static final BitSet USED = new BitSet(MAX_TOWERS);

    private DeathTowerRegistry() {
    }

    public static DeathTower create(DeathTower tower, Party party) {
        int index = allocateIndex();
        if (index < 0) {
            return null;
        }
        tower.index(index);
        tower.attachParty(party);
        BY_INDEX.put(index, tower);
        for (GameSession member : party.view().members()) {
            member.deathTowerIndex(index);
        }
        return tower;
    }

    public static DeathTower byIndex(int index) {
        return index < 0 ? null : BY_INDEX.get(index);
    }

    public static void destroy(DeathTower tower) {
        if (tower == null) {
            return;
        }
        int index = tower.index();
        Party party = tower.party();
        if (party != null) {
            for (GameSession member : party.view().members()) {
                if (member.deathTowerIndex() == index) {
                    member.deathTowerIndex(NOT_IN_TOWER);
                }
            }
        }
        BY_INDEX.remove(index, tower);
        tower.index(NOT_IN_TOWER);
        tower.attachParty(null);
        releaseIndex(index);
    }

    public static void destroyIfEmpty(DeathTower tower) {
        if (tower == null || tower.index() == NOT_IN_TOWER) {
            return;
        }
        Party party = tower.party();
        if (party != null) {
            for (GameSession member : party.view().members()) {
                if (member.deathTowerIndex() == tower.index()) {
                    return;
                }
            }
        }
        destroy(tower);
    }

    public static int count() {
        return BY_INDEX.size();
    }

    private static synchronized int allocateIndex() {
        int index = USED.nextClearBit(0);
        if (index >= MAX_TOWERS) {
            return -1;
        }
        USED.set(index);
        return index;
    }

    private static synchronized void releaseIndex(int index) {
        if (index >= 0 && index < MAX_TOWERS) {
            USED.clear(index);
        }
    }

    static synchronized void clear() {
        BY_INDEX.clear();
        USED.clear();
    }
}
