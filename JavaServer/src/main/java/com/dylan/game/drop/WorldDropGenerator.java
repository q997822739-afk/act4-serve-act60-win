package com.dylan.game.drop;

import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.ItemScript;
import com.dylan.pvf.script.WorldDropScript;

import java.util.List;
import java.util.random.RandomGenerator;

public final class WorldDropGenerator {
    private static final int MAX_MONSTER_LEVEL = 199;

    private final WorldDropScript table;
    private final GameScripts scripts;

    public WorldDropGenerator(WorldDropScript table, GameScripts scripts) {
        this.table = table;
        this.scripts = scripts;
    }

    public void generateRandomItem(DropContext ref, List<GeneratedItem> out,
                                   RandomGenerator random) {
        if (ref.monsterLevel() > MAX_MONSTER_LEVEL) {
            return;
        }
        WorldDropScript.Level level = table.forLevel(ref.monsterLevel());
        if (level.totalWeight() <= 0) {
            return;
        }
        int roll = random.nextInt(WorldDropScript.DENOMINATOR + 1);
        int threshold = ref.dropRate() * level.totalWeight() / 100;
        if (roll > threshold) {
            return;
        }
        int itemId = level.pick(random.nextInt(level.totalWeight() + 1));
        if (itemId < 0) {
            return;
        }
        ItemScript script = scripts.item(itemId).orElse(null);
        if (script == null) {
            return;
        }
        out.add(GeneratedItem.item(itemId, 1, script.freshDurability()));
    }
}
