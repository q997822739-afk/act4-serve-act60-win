package com.dylan.game.drop;

import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.DropInfoTable;
import com.dylan.pvf.script.DungeonScript;
import com.dylan.pvf.script.ItemScript;

import java.util.List;
import java.util.random.RandomGenerator;

public final class ObjectDropGenerator {
    private static final int OBJECT_ROLE_TYPE = 0;

    private static final int OBJECT_MEMBER_COUNT = 1;

    private static final int OBJECT_BOURGEOIS_RATE = 0;

    private final MonsterDropGenerator randomDrop;

    private final GameScripts scripts;

    public ObjectDropGenerator(MonsterDropGenerator randomDrop, GameScripts scripts) {
        this.randomDrop = randomDrop;
        this.scripts = scripts;
    }

    public void generateRandomItem(int level, int dungeonDiff, int count,
                                   List<GeneratedItem> out, RandomGenerator random) {
        if (count <= 0) {
            return;
        }
        DropContext ref = new DropContext(
                DropContext.NO_PREMIUM,
                level,
                OBJECT_ROLE_TYPE,
                level,
                dungeonDiff,
                OBJECT_MEMBER_COUNT,
                OBJECT_BOURGEOIS_RATE,
                count,
                0, 0);
        randomDrop.generateRandomItem(ref, out, random);
    }

    public void generateSpecificItem(DungeonScript.AssignItem assign, int count,
                                     List<GeneratedItem> out, RandomGenerator random) {
        if (assign == null || count <= 0 || assign.items().isEmpty()) {
            return;
        }
        for (int i = 0; i < count; i++) {
            int roll = random.nextInt(DropInfoTable.DENOMINATOR + 1);
            int itemId = MonsterDropGenerator.pickByCumulative(assign.items(), roll);
            if (itemId < 0) {
                continue;
            }
            ItemScript script = scripts.item(itemId).orElse(null);
            if (script == null) {
                continue;
            }
            out.add(GeneratedItem.item(itemId,
                    EquipGradeGenerator.addInfoFor(script, random, 0, 1),
                    script.freshDurability()));
        }
    }
}
