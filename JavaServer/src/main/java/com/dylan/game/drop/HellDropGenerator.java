package com.dylan.game.drop;

import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.AiCharacterScript;
import com.dylan.pvf.script.HellDropTable;
import com.dylan.pvf.script.ItemDropType;
import com.dylan.pvf.script.ItemScript;
import com.dylan.pvf.script.MonsterScript;

import java.util.List;
import java.util.random.RandomGenerator;

public final class HellDropGenerator {

    private static final ItemDropType HELL_POOL = ItemDropType.EQUIPMENT;

    private final HellDropTable table;
    private final ItemGenRateTable pool;
    private final GameScripts scripts;

    public HellDropGenerator(HellDropTable table, ItemGenRateTable pool, GameScripts scripts) {
        this.table = table;
        this.pool = pool;
        this.scripts = scripts;
    }

    public void hellPartySpecificItem(DropContext ref, MonsterDropGenerator independent,
                                      List<GeneratedItem> out, RandomGenerator random) {
        int routineCount = 0;

        AiCharacterScript aic = scripts.aiCharacter(ref.monsterId()).orElse(null);
        if (aic != null) {
            routineCount = aic.hellMonster() ? 0 : ref.specifyDropCount();
            independent.generateIndependentItem(
                    aic.independentDrops(), aic.path(), ref, out, random);
        }

        MonsterScript mob = scripts.monster(ref.monsterId()).orElse(null);
        if (mob != null) {
            routineCount = mob.hellMonster() ? 0 : ref.specifyDropCount();
            independent.generateIndependentItem(mob, ref, out, random);
        }

        if (aic == null && mob == null) {
            return;
        }

        if (!ref.lastOfHellGroup()) {
            return;
        }
        for (int i = 0; i < routineCount; i++) {
            generateSpecificItem(ref, out, random);
        }
    }

    /** A4 itemdropinfo_monster_hell.etc 的 [basis of rarity dicision] 只有一行，三档共用，取第 1 档。 */
    private static final int A4_DEMON_GRADE = 1;

    /**
     * A4 深渊恶魔：照官方成品服 hell_drop_roll_times（默认 3）掷 {@link #generateSpecificItem}，
     * 每次按 [dungeon difficulty drop prob] 判中不中（普通难度 500/1000），中了按稀有度挑一件装备。
     */
    public void hellDemonItems(DropContext ref, int rolls, List<GeneratedItem> out,
                               RandomGenerator random) {
        DropContext demon = new DropContext(ref.dropRate(), ref.monsterLevel(), ref.roleType(),
                ref.dungeonLevel(), ref.dungeonDiff(), ref.memberCount(), ref.bourgeoisRate(),
                ref.randomDropCount(), ref.specifyDropCount(), ref.monsterId(),
                true, A4_DEMON_GRADE, true);
        for (int i = 0; i < rolls; i++) {
            generateSpecificItem(demon, out, random);
        }
    }

    public void generateSpecificItem(DropContext ref, List<GeneratedItem> out,
                                     RandomGenerator random) {
        HellDropTable.Band band = table.bandFor(ref.dungeonLevel());
        if (band == null) {
            return;
        }
        int rate = ref.dropRate() * band.of(ref.dungeonDiff()) / 100;
        if (random.nextInt(HellDropTable.PROB_DENOMINATOR + 1) > rate) {
            return;
        }
        int[] thresholds = table.rarityThresholds(ref.hellDifficulty());
        if (thresholds == null) {
            return;
        }
        int rarity = MonsterDropGenerator.rollRarity(thresholds, random);
        int itemId = pool.chooseItem(HELL_POOL, rarity, ref.dungeonLevel(), random);
        if (itemId == ItemGenRateTable.NONE) {
            return;
        }
        ItemScript script = scripts.item(itemId).orElse(null);
        if (script == null) {
            return;
        }
        out.add(GeneratedItem.item(itemId, 1, script.freshDurability()));
    }
}
