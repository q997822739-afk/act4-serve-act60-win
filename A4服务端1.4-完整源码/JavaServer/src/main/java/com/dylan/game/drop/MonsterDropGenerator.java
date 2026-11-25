package com.dylan.game.drop;

import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.DropInfoTable;
import com.dylan.pvf.script.GoldDropTable;
import com.dylan.pvf.script.ItemDropType;
import com.dylan.pvf.script.ItemScript;
import com.dylan.pvf.script.MonsterScript;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public final class MonsterDropGenerator {
    private static final int MAX_ROLE_TYPE = 3;

    private static final int MAX_SPECIFIC_ROLE = 4;

    private static final int NORMAL_POOL = 0;

    private static final int BOURGEOIS_THRESHOLD = 50;

    private static final double BOURGEOIS_SLOPE = 0.002;

    private static final double BOURGEOIS_BASE = 0.01;

    private static final int SPECIFIC_ROLL_MAX = DropInfoTable.DENOMINATOR;

    private final DropInfoTable table;
    private final GoldDropTable gold;
    private final ItemGenRateTable pool;
    private final GameScripts scripts;

    private HellDropGenerator hell;

    public MonsterDropGenerator(DropInfoTable table, GoldDropTable gold,
                                ItemGenRateTable pool, GameScripts scripts) {
        this.table = table;
        this.gold = gold;
        this.pool = pool;
        this.scripts = scripts;
    }

    public void setHellDrop(HellDropGenerator hell) {
        this.hell = hell;
    }

    public GoldDropTable goldTable() {
        return gold;
    }

    public List<GeneratedItem> generate(DropContext ref, RandomGenerator random) {
        List<GeneratedItem> out = new ArrayList<>();
        generateRandomItem(ref, out, random);
        generateSpecificItem(ref, out, random);
        return out;
    }

    public void generateRandomItem(DropContext ref, List<GeneratedItem> out,
                                   RandomGenerator random) {
        if (ref.monsterRoleType() > MAX_ROLE_TYPE) {
            return;
        }
        for (int i = 0; i < ref.randomDropCount(); i++) {
            generateOnce(ref, out, random);
        }
    }

    private void generateOnce(DropContext ref, List<GeneratedItem> out, RandomGenerator random) {
        DropInfoTable.Band band = table.bandFor(ref.dungeonLevel());
        if (band == null) {
            return;
        }

        double goldRate = band.of(ItemDropType.GOLD) * bonusOf(ItemDropType.GOLD, ref);
        if (ref.bourgeoisRate() > BOURGEOIS_THRESHOLD) {
            double cut = (ref.bourgeoisRate() - BOURGEOIS_THRESHOLD) * BOURGEOIS_SLOPE
                    + BOURGEOIS_BASE;
            goldRate = (int) goldRate * (1.0 - cut);
        }
        goldRate = ref.dropRate() * (int) goldRate / 100.0;
        if (random.nextInt(DropInfoTable.DENOMINATOR) < (int) goldRate) {
            long amount = GoldDropTable.roll(gold.forLevel(ref.monsterLevel()), random);
            if (amount > 0) {
                out.add(GeneratedItem.money(amount));
            }
        }

        for (ItemDropType type : ItemDropType.ITEMS) {
            double rate = band.of(type) * bonusOf(type, ref);
            int value = (int) (ref.dropRate() * (int) rate / 100.0);
            if (value < random.nextInt(DropInfoTable.DENOMINATOR)) {
                continue;
            }
            int rarity = rollRarity(table.rarityThresholds(type), random);
            int itemId = pool.chooseItem(type, rarity, ref.monsterLevel(), random);
            if (itemId == ItemGenRateTable.NONE) {
                continue;
            }
            ItemScript script = scripts.item(itemId).orElse(null);
            if (script == null) {
                continue;
            }
            long addInfo = type == ItemDropType.EQUIPMENT
                    ? EquipGradeGenerator.addInfoFor(script, random,
                            ref.monsterLevel() - script.grade(), 1)
                    : 1;
            out.add(GeneratedItem.item(itemId, addInfo, script.freshDurability()));
        }
    }

    static int rollRarity(int[] thresholds, RandomGenerator random) {
        int roll = 1 + random.nextInt(DropInfoTable.DENOMINATOR);
        for (int k = 0; k < thresholds.length; k++) {
            if (thresholds[k] >= roll) {
                return k;
            }
        }
        return 0;
    }

    private double bonusOf(ItemDropType type, DropContext ref) {
        return table.difficultyBonus(type, ref.dungeonDiff())
                * table.monsterTypeBonus(type, ref.monsterRoleType())
                * table.partyBonus(type, ref.memberCount());
    }

    public void generateSpecificItem(DropContext ref, List<GeneratedItem> out,
                                     RandomGenerator random) {
        if (ref.hellMonster()) {
            if (hell == null) {
                return;
            }
            hell.hellPartySpecificItem(ref, this, out, random);
            return;
        }
        if (ref.roleType() > MAX_SPECIFIC_ROLE) {
            return;
        }
        MonsterScript mob = scripts.monster(ref.monsterId()).orElse(null);
        if (mob == null) {
            throw new IllegalStateException("指定掉落查不到怪 " + ref.monsterId() + " 的脚本");
        }
        generateIndependentItem(mob, ref, out, random);
        for (int i = 0; i < ref.specifyDropCount(); i++) {
            generateMonsterSpecificItem(mob, NORMAL_POOL, out, random);
        }
        if (ref.roleType() != NORMAL_POOL) {
            generateMonsterSpecificItem(mob, ref.roleType(), out, random);
        }
        // A4 没有 hellparty.etc，深渊装备只从封印柱放出的恶魔（.mob [hell monster] 1）掉。
        // 官方实抓 hell_whitenight 恶魔 1051 → `GROUND_DROP hell monster=8 item=10072`。
        if (com.dylan.net.ServerConfig.A4 && mob.hellMonster() && hell != null) {
            hell.hellDemonItems(ref, com.dylan.net.ServerConfig.HELL_DEMON_DROP_ROLLS,
                    out, random);
        }
    }

    public void generateIndependentItem(MonsterScript mob, DropContext ref,
                                        List<GeneratedItem> out, RandomGenerator random) {
        generateIndependentItem(mob.independentDrops(), mob.path(), ref, out, random);
    }

    public void generateIndependentItem(List<MonsterScript.IndependentDrop> drops, String owner,
                                        DropContext ref, List<GeneratedItem> out,
                                        RandomGenerator random) {
        for (MonsterScript.IndependentDrop drop : drops) {
            int count = drop.countFor(ref.memberCount());
            int prob = drop.probFor(ref.dungeonDiff());
            int success = 0;
            for (int i = 0; i < count; i++) {
                if (prob > random.nextInt(MonsterScript.INDEPENDENT_DENOMINATOR)) {
                    success++;
                }
            }
            if (success <= 0) {
                continue;
            }
            if (drop.subPool().isEmpty()) {
                addIndependent(drop.itemId(), success, owner, out, random);
            } else {
                int total = totalWeight(drop.subPool());
                for (int i = 0; i < success; i++) {
                    int itemId = pickByCumulative(drop.subPool(), random.nextInt(total));
                    addIndependent(itemId, 1, owner, out, random);
                }
            }
        }
    }

    private void addIndependent(int itemId, int count, String owner,
                                List<GeneratedItem> out, RandomGenerator random) {
        if (itemId <= 0) {
            return;
        }
        ItemScript script = scripts.item(itemId).orElse(null);
        if (script == null) {
            return;
        }
        for (int i = 0; i < count; i++) {
            out.add(GeneratedItem.item(itemId,
                    EquipGradeGenerator.addInfoFor(script, random, 0, 1),
                    script.freshDurability()));
        }
    }

    private static int totalWeight(List<MonsterScript.DropEntry> pool) {
        int total = 0;
        for (MonsterScript.DropEntry entry : pool) {
            total += entry.weight();
        }
        return total;
    }

    private void generateMonsterSpecificItem(MonsterScript mob, int role,
                                             List<GeneratedItem> out, RandomGenerator random) {
        int roll = random.nextInt(SPECIFIC_ROLL_MAX + 1);
        int itemId = pickByCumulative(mob.dropPool(role), roll);
        if (itemId < 0) {
            return;
        }
        ItemScript script = scripts.item(itemId).orElse(null);
        if (script == null) {
            return;
        }
        out.add(GeneratedItem.item(itemId,
                EquipGradeGenerator.addInfoFor(script, random, 0, 1),
                script.freshDurability()));
    }

    static int pickByCumulative(List<MonsterScript.DropEntry> pool, int roll) {
        int cumulative = 0;
        for (MonsterScript.DropEntry entry : pool) {
            cumulative += entry.weight();
            if (roll < cumulative) {
                return entry.itemId();
            }
        }
        return -1;
    }
}
