package com.dylan.game;

import com.dylan.pvf.script.ItemScript;
import com.dylan.pvf.script.QuestGrade;
import com.dylan.pvf.script.QuestScript;
import com.dylan.store.CharacterQuests;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public final class QuestDropService {

    private QuestDropService() {
    }

    public static void onMonsterKill(GameSession s, RoomMonster monster, int enemyType,
                                     int inDungeon, int atDifficulty, RandomGenerator random) {
        if (!com.dylan.net.ServerConfig.QUEST_DROPS) {
            return;
        }
        CharacterQuests quests = s.quests();
        if (quests == null || s.scripts() == null || quests.runningIds().isEmpty()) {
            return;
        }
        List<Candidate> candidates = new ArrayList<>();
        for (var slot : quests.running().entrySet()) {
            int questId = slot.getKey();
            QuestScript quest = s.scripts().quest(questId).orElse(null);
            if (quest == null) {
                continue;
            }
            if (!stillDropsFor(quest.grade(), slot.getValue())) {
                continue;
            }
            for (var entry : quest.monsterRewardItems()) {
                if (entry.matchesKill(monster.monsterId(), enemyType, inDungeon, atDifficulty)) {
                    candidates.add(new Candidate(questId, entry));
                }
            }
        }
        while (!candidates.isEmpty()) {
            Candidate picked = candidates.get(random.nextInt(candidates.size()));
            var entry = picked.entry();
            int got = roll(entry, random);
            if (got <= 0) {
                return;
            }
            long held = heldOf(s, entry.itemId());
            int limit = entry.maxStack();
            if (limit > 0 && held >= limit) {
                candidates.remove(picked);
                continue;
            }
            if (limit > 0 && held + got > limit) {
                got = (int) (limit - held);
            }
            grant(s, picked, got, "打死怪 " + monster.monsterId());
            return;
        }
    }

    public static int enemyTypeOf(RoomMonster monster) {
        return monster.type() > 4 && monster.type() <= 8
                ? ENEMY_TYPE_APC
                : QuestScript.QuestDropEntry.DEFAULT_ENEMY_TYPE;
    }

    public static final int ENEMY_TYPE_APC = 2;

    static boolean stillDropsFor(QuestGrade grade, long trigger) {
        return grade == QuestGrade.NORMALY_REPEAT || grade == QuestGrade.DAILY || trigger != 0;
    }

    private record Candidate(int questId, QuestScript.QuestDropEntry entry) {}

    private static void grant(GameSession s, Candidate picked, int count, String why) {
        var entry = picked.entry();
        ItemScript script = s.scripts().item(entry.itemId()).orElse(null);
        if (script == null) {
            return;
        }
        var landing = s.inventory().addItem(script, count);
        if (!landing.ok()) {
            return;
        }
        s.inventory().save();
        s.inventory().sendItemUpdate(landing.itemSpace(),
                "任务 " + picked.questId() + " 的材料入账", landing.slot());
    }

    public static List<InventoryService.Stack> onDungeonClear(GameSession s,
                                                              RandomGenerator random) {
        CharacterQuests quests = s.quests();
        if (!com.dylan.net.ServerConfig.QUEST_DROPS
                || quests == null || s.scripts() == null || quests.runningIds().isEmpty()) {
            return List.of();
        }
        DungeonRun run = s.dungeon();
        int inDungeon = run == null ? QuestScript.ANY_DUNGEON : run.dungeonIndex();

        List<InventoryService.Stack> rolled = new ArrayList<>();
        for (var slot : quests.running().entrySet()) {
            int questId = slot.getKey();
            if (slot.getValue() == 0) {
                continue;
            }
            QuestScript quest = s.scripts().quest(questId).orElse(null);
            if (quest == null) {
                continue;
            }
            for (var entry : quest.clearRewardItems()) {
                if (entry.dungeonId() != QuestScript.ANY_DUNGEON
                        && entry.dungeonId() != inDungeon) {
                    continue;
                }
                if (entry.maxStack() > 0 && heldOf(s, entry.itemId()) >= entry.maxStack()) {
                    continue;
                }
                int got = roll(entry, random);
                if (got > 0) {
                    rolled.add(new InventoryService.Stack(entry.itemId(), got));
                }
            }
        }
        if (rolled.isEmpty()) {
            return List.of();
        }
        return grantAll(s, rolled);
    }

    private static List<InventoryService.Stack> grantAll(GameSession s,
                                                         List<InventoryService.Stack> rolled) {
        if (s.store() == null || s.characNo() < 0) {
            return List.of();
        }
        var inv = s.inventory().inventory();
        if (inv == null) {
            s.fail("角色 " + s.characNo() + " 在 inventory 表里没有行，任务的通关奖励发不下去");
            return List.of();
        }
        List<InventoryService.Stack> granted = new ArrayList<>();
        for (var stack : rolled) {
            var script = s.scripts().item(stack.itemId()).orElse(null);
            if (script == null) {
                continue;
            }
            if (!InventoryService.addItem(s, s.store(), s.characNo(), inv, script, stack.count())
                    .ok()) {
                continue;
            }
            granted.add(stack);
        }
        if (granted.isEmpty()) {
            return List.of();
        }
        s.store().inventory().saveInventory(inv);
        CharacterService.resendItemLists(s, "任务的通关奖励入账");
        return granted;
    }

    private static int roll(QuestScript.QuestDropEntry entry, RandomGenerator random) {
        int got = 0;
        for (int i = 0; i < entry.count(); i++) {
            if (random.nextInt(100) < entry.dropRate()) {
                got++;
            }
        }
        return got;
    }

    private static long heldOf(GameSession s, int itemId) {
        if (s.store() == null || s.characNo() < 0) {
            return 0;
        }
        var inv = s.inventory().inventory();
        return inv == null ? 0 : InventoryService.countOf(inv.inventory(), itemId);
    }
}
