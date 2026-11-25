package com.dylan.game;

import com.dylan.protocol.body.ItemListType;
import com.dylan.protocol.body.Noti157DeathTowerRewardBody;
import com.dylan.pvf.script.DeathTowerTable;
import com.dylan.pvf.script.GoldDropTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class DeathTowerRewardRules {

    private DeathTowerRewardRules() {
    }

    public static final int MAX_REWARD_ITEMS = 10;

    public static final int PROB_STAGE_CAP = 10;

    public static final int GOLD_ITEM_ID = 0;

    static final Noti157DeathTowerRewardBody.RewardItem EMPTY_SLOT =
            new Noti157DeathTowerRewardBody.RewardItem(
                    Noti157DeathTowerRewardBody.EMPTY_ITEM_ID, 0);

    public static void generateOne(GameSession member, DeathTower tower, int slot,
                                   int recordBonus, long playTimeMs) {
        int stage = tower.currentStage();
        var scripts = member.scripts();
        DeathTowerTable table = scripts == null
                ? DeathTowerTable.empty() : scripts.deathTower();
        boolean illusion = !tower.useDeathTowerRewardTable();

        int cards = table.cardNum(stage, illusion) + recordBonus;
        int count = Math.min(MAX_REWARD_ITEMS,
                Math.min(tower.dungeon().tower().maxClearItemNum(), cards));
        int weight = Math.min(PROB_STAGE_CAP, stage);
        Random random = ThreadLocalRandom.current();
        tower.rewardItems(slot, rollItems(member, tower, table, count, weight, random));
        tower.rewardExp(slot, rollExp(member, table, stage, illusion));
    }

    private static List<Noti157DeathTowerRewardBody.RewardItem> rollItems(
            GameSession member, DeathTower tower, DeathTowerTable table,
            int count, int weight, Random random) {
        List<Noti157DeathTowerRewardBody.RewardItem> out = new ArrayList<>(Math.max(0, count));
        if (count <= 0) {
            return out;
        }
        var scripts = member.scripts();
        int goldThreshold = weight * table.rewardItemProb(DeathTowerTable.PROB_GOLD);
        int itemThreshold = weight * table.rewardItemProb(DeathTowerTable.PROB_ITEM);
        int level = tower.dungeon().basisLevel();
        var context = new ClearRewardRules.Context(level, 0,
                Math.max(1, tower.startMemberCount()), new int[]{0, 0, 0, 0});

        int filled = 0;
        for (int i = 0; i < count; i++) {
            Noti157DeathTowerRewardBody.RewardItem drawn = null;
            int roll = random.nextInt(DeathTowerTable.PROB_DENOMINATOR);
            if (roll < goldThreshold) {
                long gold = rollGold(scripts, table, level, random);
                if (gold > 0) {
                    drawn = new Noti157DeathTowerRewardBody.RewardItem(GOLD_ITEM_ID,
                            (int) Math.min(gold, Integer.MAX_VALUE));
                }
            } else if (roll < itemThreshold) {
                var item = scripts == null ? null : ClearRewardRules.drawItem(context,
                        scripts.clearReward(), scripts.clearRewardPool(),
                        id -> scripts.item(id).orElse(null), random,
                        "塔第 " + tower.currentStage() + " 层第 " + (i + 1) + " 件");
                if (item != null) {
                    drawn = new Noti157DeathTowerRewardBody.RewardItem(
                            item.itemId(), item.count());
                }
            }
            if (drawn == null) {
                out.add(EMPTY_SLOT);
            } else {
                out.add(drawn);
                filled++;
            }
        }
        return out;
    }

    private static long rollGold(com.dylan.pvf.GameScripts scripts, DeathTowerTable table,
                                 int level, Random random) {
        GoldDropTable gold = scripts == null ? null : scripts.goldDropTable();
        if (gold == null) {
            return 0;
        }
        GoldDropTable.GoldGen basis = gold.forLevel(level);
        long base = GoldDropTable.roll(basis, random);
        return Math.round(base * table.rewardGoldWeight());
    }

    private static int rollExp(GameSession member, DeathTowerTable table,
                              int stage, boolean illusion) {
        var store = member.store();
        if (store == null) {
            return 0;
        }
        int base = store.growthTable().monsterRewardExp(Math.max(1, member.level()));
        double weight = table.expWeight(stage, illusion);
        long exp = Math.round(weight * (double) base * stage);
        if (exp > OVERFLOW_CAP) {
            exp = OVERFLOW_CAP;
        }
        return (int) Math.clamp(exp, 0, Integer.MAX_VALUE);
    }

    public static final long OVERFLOW_CAP = 1_100_000;

    public static void grant(Party party, DeathTower tower) {
        for (int slot = 0; slot < Party.MAX_MEMBERS; slot++) {
            GameSession member = party.view().at(slot);
            if (member == null) {
                continue;
            }
            int mySlot = slot;
            member.run(() -> grantOne(member, tower, mySlot));
        }
    }

    private static void grantOne(GameSession member, DeathTower tower, int slot) {
        var inv = member.inventory();
        var bag = inv == null || inv.inventory() == null ? null : inv.inventory().inventory();
        if (bag == null) {
            return;
        }
        long gold = 0;
        List<Integer> touched = new ArrayList<>();
        StringBuilder granted = new StringBuilder();
        for (var reward : tower.rewardItems(slot)) {
            if (reward.itemId() == Noti157DeathTowerRewardBody.EMPTY_ITEM_ID) {
                continue;
            }
            if (reward.itemId() == GOLD_ITEM_ID) {
                gold += reward.addInfo();
                continue;
            }
            var script = member.scripts() == null ? null
                    : member.scripts().item(reward.itemId()).orElse(null);
            if (script == null) {
                continue;
            }
            int at = InventoryService.grant(bag, script, Math.max(1, reward.addInfo()));
            if (at < 0) {
                continue;
            }
            touched.add(at);
            granted.append(granted.isEmpty() ? "" : " + ")
                    .append(script.name().isEmpty() ? String.valueOf(reward.itemId())
                            : script.name())
                    .append(" 进第 ").append(at).append(" 格");
        }
        if (gold > 0) {
            long before = inv.money();
            long after = Math.min(0xFFFF_FFFFL, before + gold);
            inv.setMoney(after);
            granted.append(granted.isEmpty() ? "" : " + ")
                    .append(gold).append(" 金币（").append(before).append(" → ")
                    .append(after).append('）');
        }
        inv.save();
        if (!touched.isEmpty()) {
            inv.sendItemUpdate(ItemListType.LIST_MAIN, "死亡之塔通关奖励",
                    touched.stream().mapToInt(Integer::intValue).toArray());
        }
        int exp = tower.rewardExp(slot);
        if (exp > 0) {
            ExperienceService.grant(member, exp);
        }
        member.flush();
    }
}
