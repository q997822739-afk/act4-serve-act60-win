package com.dylan.game;

import com.dylan.pvf.script.DungeonScript;

import java.util.ArrayList;
import java.util.List;

public final class ClearConditions {
    public static final int TYPE_COUNT = 5;

    private final List<DungeonScript.ClearCondition> conditions;

    private final int[] progress;

    private final int required;

    private int done;

    private ClearConditions(List<DungeonScript.ClearCondition> conditions) {
        this.conditions = List.copyOf(conditions);
        this.progress = new int[this.conditions.size()];
        int sum = 0;
        for (DungeonScript.ClearCondition c : this.conditions) {
            sum += c.count();
        }
        this.required = sum;
    }

    public static ClearConditions of(DungeonScript.Maze maze) {
        return of(maze == null ? List.of() : maze.clearConditions());
    }

    public static ClearConditions of(List<DungeonScript.ClearCondition> conditions) {
        return new ClearConditions(conditions);
    }

    public boolean isEmpty() {
        return conditions.isEmpty();
    }

    public boolean satisfied() {
        return required <= done;
    }

    public int required() {
        return required;
    }

    public int done() {
        return done;
    }

    public boolean report(int type, int id) {
        if (isEmpty() || type < 0 || type >= TYPE_COUNT) {
            return false;
        }
        for (int i = 0; i < conditions.size(); i++) {
            DungeonScript.ClearCondition c = conditions.get(i);
            if (c.type() != type || c.id() != id) {
                continue;
            }
            if (c.count() <= progress[i]) {
                return false;
            }
            progress[i]++;
            done++;
        }
        return satisfied();
    }

    public static String typeName(int type) {
        return switch (type) {
            case DungeonScript.TYPE_DESTROY_OBJECT -> "[destroy object] 打碎物件";
            case DungeonScript.TYPE_CLEAR_MAP -> "[clear map] 清图";
            case DungeonScript.TYPE_HUNT_MONSTER -> "[hunt monster] 打怪";
            case DungeonScript.TYPE_HUNT_APC -> "[hunt apc] 打 APC";
            case DungeonScript.TYPE_HUNT_BOSS -> "[hunt boss] 打 BOSS";
            default -> "条件类型 " + type;
        };
    }

    public static int conditionTypeOf(int wireType) {
        if (wireType == com.dylan.pvf.script.MapScript.TYPE_BOSS_MONSTER) {
            return DungeonScript.TYPE_HUNT_BOSS;
        }
        if (wireType < RoomMonster.TYPE_APC || wireType > RoomMonster.TYPE_APC + 3) {
            return DungeonScript.TYPE_HUNT_MONSTER;
        }
        return DungeonScript.TYPE_HUNT_APC;
    }

    @Override
    public String toString() {
        List<String> parts = new ArrayList<>(conditions.size());
        for (int i = 0; i < conditions.size(); i++) {
            DungeonScript.ClearCondition c = conditions.get(i);
            parts.add(typeName(c.type()) + " " + c.id() + " " + progress[i] + "/" + c.count());
        }
        return parts + "（合计 " + done + "/" + required + "）";
    }
}
