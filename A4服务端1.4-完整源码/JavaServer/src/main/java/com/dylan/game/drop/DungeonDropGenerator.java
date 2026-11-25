package com.dylan.game.drop;

import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.ItemScript;
import com.dylan.pvf.script.MonsterScript;

import java.util.List;
import java.util.random.RandomGenerator;

public final class DungeonDropGenerator {

    private static final int ROLL_MAX = 10_000;

    private final GameScripts scripts;

    public DungeonDropGenerator(GameScripts scripts) {
        this.scripts = scripts;
    }

    public void generateSpecificItem(List<MonsterScript.DropEntry> pool,
                                     List<GeneratedItem> out, RandomGenerator random) {
        if (pool.isEmpty()) {
            return;
        }
        int roll = random.nextInt(ROLL_MAX + 1);
        int cumulative = 0;
        for (MonsterScript.DropEntry entry : pool) {
            cumulative += entry.weight();
            if (roll >= cumulative) {
                continue;
            }
            ItemScript script = scripts.item(entry.itemId()).orElse(null);
            if (script == null) {
                return;
            }
            out.add(GeneratedItem.item(entry.itemId(), 1, script.freshDurability()));
            return;
        }
    }
}
