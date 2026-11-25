package com.dylan.game;

public final class DungeonInstance {

    public static final int STATE_TOWN = 1;

    public static final int STATE_IN_DUNGEON = 2;

    private final DungeonRun run;

    private final BattleField field;

    private volatile DungeonRoom room;

    DungeonInstance(DungeonRun run, BattleField field, DungeonRoom startRoom) {
        this.run = run;
        this.field = field;
        this.room = startRoom == null ? DungeonRoom.empty() : startRoom;
    }

    public DungeonRun run() {
        return run;
    }

    public BattleField field() {
        return field;
    }

    public DungeonRoom room() {
        return room;
    }

    void room(DungeonRoom value) {
        this.room = value == null ? DungeonRoom.empty() : value;
    }

    @Override
    public String toString() {
        if (field == null) {
            return "塔 " + run.dungeonIndex() + " 难度 " + run.difficulty() + "（没有场地网格）";
        }
        return "副本 " + run.dungeonIndex() + " 难度 " + run.difficulty()
                + " 迷宫 " + run.mazeIndex()
                + "，人在 (" + field.curX() + "," + field.curY() + ")";
    }
}
