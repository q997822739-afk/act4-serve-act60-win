package com.dylan.game;

import java.util.Random;

import com.dylan.game.drop.ItemGenRateTable;
import com.dylan.net.ServerConfig;
import com.dylan.pvf.script.ClearRewardTable;
import com.dylan.pvf.script.GoldDropTable;
import com.dylan.pvf.script.ItemDropType;
import com.dylan.pvf.script.ItemScript;

public final class ClearRewardRules {
    private ClearRewardRules() {
    }

    public static final int[] KILL_WEIGHT_BY_TYPE = {1, 2, 3, 4};

    public record Context(int dungeonLevel, int difficulty, int partyMembers,
                          int[] killsByType) {
        public Context {
            dungeonLevel = Math.max(1, dungeonLevel);
            difficulty = Math.clamp(difficulty, 0, ClearRewardTable.DIFFICULTY_COUNT - 1);
            partyMembers = Math.clamp(partyMembers, 1, ClearRewardTable.MAX_PARTY_MEMBERS);
            killsByType = killsByType.clone();
        }

        public static Context of(int dungeonLevel, int difficulty, int partyMembers,
                                 DungeonRunSummary run) {
            int[] kills = new int[DungeonRunSummary.MONSTER_TYPE_COUNT];
            for (int type = 0; type < kills.length; type++) {
                kills[type] = run.killsOfType(type);
            }
            return new Context(dungeonLevel, difficulty, partyMembers, kills);
        }

        public long weightedKills() {
            long total = 0;
            for (int type = 0; type < killsByType.length
                    && type < KILL_WEIGHT_BY_TYPE.length; type++) {
                total += (long) killsByType[type] * KILL_WEIGHT_BY_TYPE[type];
            }
            return total;
        }
    }

    public record CardItem(int itemId, int count, int durability) {}

    public record Cards(long freeGold, CardItem freeItem, CardItem goldCardItem,
                        int goldCardCost) {
        public static Cards none() {
            return new Cards(0, null, null, 0);
        }

        public Cards withoutGoldCardItem() {
            return new Cards(freeGold, freeItem, null, goldCardCost);
        }

        public Cards withoutFreeItem() {
            return new Cards(freeGold, null, goldCardItem, goldCardCost);
        }

        @Override
        public String toString() {
            return freeGold + " 金币"
                    + (freeItem == null ? "" : " + 物品 " + freeItem.itemId())
                    + "，金卡 "
                    + (goldCardItem == null ? "空牌" : String.valueOf(goldCardItem.itemId()))
                    + "（手续费 " + goldCardCost + (goldCardCost == 0 ? "，免费翻" : "") + "）";
        }
    }

    public static Cards roll(Context context, ClearRewardTable table, GoldDropTable gold,
                             ItemGenRateTable pool,
                             java.util.function.IntFunction<ItemScript> items,
                             Random random) {
        return roll(context, table, gold, pool, items, random, false);
    }

    public static Cards roll(Context context, ClearRewardTable table, GoldDropTable gold,
                             ItemGenRateTable pool,
                             java.util.function.IntFunction<ItemScript> items,
                             Random random, boolean freeGoldCardEvent) {
        if (table == null) {
            return Cards.none();
        }
        long freeGold = freeCardGold(context, table, gold, random);
        CardItem freeItem = rollItem(context, table, pool, items, random,
                ClearRewardTable.PROFILE_DEFAULT, 1.0, "免费牌");
        CardItem goldItem = rollItem(context, table, pool, items, random,
                ClearRewardTable.PROFILE_GOLD_CARD, table.goldCardCreateRate(), "金卡");
        if (freeGoldCardEvent) {
            return new Cards(freeGold, freeItem, goldItem, 0);
        }
        int cost = table.hasGoldCardCost() ? table.goldCardCost(context.dungeonLevel()) : 0;
        return new Cards(freeGold, freeItem, goldItem, cost);
    }

    public static long freeCardGold(Context context, ClearRewardTable table,
                                    GoldDropTable gold, Random random) {
        if (gold == null) {
            return 0;
        }
        GoldDropTable.GoldGen basis = gold.forLevel(context.dungeonLevel());
        long weighted = context.weightedKills();
        if (basis.amount() <= 0 || weighted <= 0) {
            return 0;
        }
        double amount = (double) basis.amount() * weighted
                * (ServerConfig.CLEAR_CARD_GOLD_SHARE / (double) ClearRewardTable.DENOMINATOR)
                * table.partyGoldRate(context.partyMembers())
                * table.difficultyGoldRate(context.difficulty())
                / context.partyMembers();
        if (basis.variancePercent() > 0 && random != null) {
            int swing = random.nextInt(2 * basis.variancePercent() + 1) - basis.variancePercent();
            amount += amount * swing / 100.0;
        }
        return clampToNonNegative(amount);
    }

    private static long clampToNonNegative(double value) {
        if (!(value > 0)) {
            return 0;
        }
        return (long) Math.min(value, Integer.MAX_VALUE);
    }

    public static CardItem rollItem(Context context, ClearRewardTable table,
                                    ItemGenRateTable pool,
                                    java.util.function.IntFunction<ItemScript> items,
                                    Random random, String profile, double extraRate,
                                    String what) {
        if (pool == null || random == null) {
            return null;
        }
        if (!table.hasProfile(profile)) {
            return null;
        }
        double threshold = table.itemProbability(profile, context.dungeonLevel())
                * Math.max(0.0, extraRate)
                + table.difficultyItemBonus(context.difficulty());
        threshold = Math.clamp(threshold, 0.0, ClearRewardTable.DENOMINATOR);
        int roll = random.nextInt(ClearRewardTable.DENOMINATOR);
        if (roll >= threshold) {
            return null;
        }
        return drawItem(context, table, pool, items, random, what);
    }

    public static CardItem drawItem(Context context, ClearRewardTable table,
                                    ItemGenRateTable pool,
                                    java.util.function.IntFunction<ItemScript> items,
                                    Random random, String what) {
        if (pool == null || random == null || table == null) {
            return null;
        }
        ItemDropType type = rollCategory(table, random);
        if (type == null) {
            return null;
        }
        int rarity = rollRarity(table.rarityThresholds(), random);
        int itemId = pool.chooseItem(type, rarity, context.dungeonLevel(), random);
        if (itemId == ItemGenRateTable.NONE) {
            return null;
        }
        ItemScript script = items == null ? null : items.apply(itemId);
        if (script == null) {
            return null;
        }
        if (script.isAvatar() || script.isCreature()) {
            return null;
        }
        return new CardItem(itemId, 1, script.freshDurability());
    }

    static ItemDropType rollCategory(ClearRewardTable table, Random random) {
        int[] weights = table.itemTypeWeights();
        int total = 0;
        for (int weight : weights) {
            total += Math.max(0, weight);
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        int cumulative = 0;
        var categories = ItemDropType.ITEMS;
        for (int i = 0; i < weights.length && i < categories.length; i++) {
            cumulative += Math.max(0, weights[i]);
            if (roll < cumulative) {
                return categories[i];
            }
        }
        return categories[categories.length - 1];
    }

    static int rollRarity(int[] thresholds, Random random) {
        int point = random.nextInt(ClearRewardTable.DENOMINATOR);
        for (int i = 0; i < thresholds.length; i++) {
            if (point < thresholds[i]) {
                return i;
            }
        }
        return Math.max(0, thresholds.length - 1);
    }
}
