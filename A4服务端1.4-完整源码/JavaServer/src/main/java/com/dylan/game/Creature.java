package com.dylan.game;

import com.dylan.pvf.script.CreatureScript;
import com.dylan.pvf.script.ExpTable;
import com.dylan.store.CreatureItemRow;

public final class Creature {
    private CreatureItemRow row;
    private final CreatureStomach stomach;
    private int state;
    private int level;
    private final CreatureScript script;

    private Creature(CreatureItemRow row, CreatureStomach stomach, int state, int level,
                     CreatureScript script) {
        this.row = row;
        this.stomach = stomach;
        this.state = state;
        this.level = level;
        this.script = script;
    }

    public static Creature load(CreatureItemRow row, CreatureScript script, ExpTable table,
                                long nowSecs) {
        CreatureStomach stomach = new CreatureStomach(row.stomach(), nowSecs);
        return new Creature(row, stomach, CreatureStomach.STATE_UNEQUIPPED,
                levelOf(script, table, row.exp()), script);
    }

    public CreatureItemRow row() {
        return row;
    }

    public void setRow(CreatureItemRow updated) {
        this.row = updated;
    }

    public CreatureStomach stomach() {
        return stomach;
    }

    public int state() {
        return state;
    }

    void setStateRaw(int newState) {
        this.state = newState;
    }

    public int level() {
        return level;
    }

    public CreatureScript script() {
        return script;
    }

    public long uiId() {
        return row.uiId();
    }

    public long exp() {
        return row.exp();
    }

    public int stomachNow(long nowSecs) {
        return (int) stomach.valueAt(state, nowSecs);
    }

    public boolean dead(long nowSecs) {
        return stomach.valueAt(state, nowSecs) < CreatureStomach.DEAD_BELOW;
    }

    public boolean deadPeek(long nowSecs) {
        return stomach.peek(state, nowSecs) < CreatureStomach.DEAD_BELOW;
    }

    public boolean gainExp(int base, double artifactRate, ExpTable table) {
        long gained = base + (long) (base * artifactRate / 100.0);
        long newExp = Math.min(row.exp() + gained, ExpTable.MAX_EXP);
        row = row.withExp(newExp);
        int newLevel = levelOf(script, table, newExp);
        boolean levelledUp = level < newLevel;
        level = newLevel;
        return levelledUp;
    }

    public boolean maxedOut() {
        return script != null && script.maxedOut(level);
    }

    public boolean artifactAvailable(int equipSlot) {
        return script != null && script.artifactAvailable(equipSlot);
    }

    private static int levelOf(CreatureScript script, ExpTable table, long exp) {
        if (script == null) {
            return 1;
        }
        return script.levelFor(table, exp);
    }

    @Override
    public String toString() {
        return "宠物 " + row.itemId() + "（ui_id " + row.uiId() + "）等级 " + level
                + "、经验 " + row.exp() + "、饱食度 " + stomach + "、状态 " + state;
    }
}
