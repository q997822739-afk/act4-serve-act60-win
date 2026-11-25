package com.dylan.game;

public enum ChState {
    NONE(0),

    SELECT(2),

    TOWN(3),

    DUNGEON(5),

    PVP_ROOM(6),

    PVP_FIGHT(7),

    DEATH_TOWER(10);

    private final int code;

    ChState(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public boolean atLeast(ChState floor) {
        return code >= floor.code;
    }

    @Override
    public String toString() {
        return name() + "(" + code + ")";
    }
}
