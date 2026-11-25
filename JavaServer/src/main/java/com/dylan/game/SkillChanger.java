package com.dylan.game;

import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.QuestScript;
import com.dylan.store.GameStore;
import com.dylan.store.QuestClearFlags;

public final class SkillChanger {

    public static final int BOOK_SP5 = 1031;

    public static final int BOOK_SP20 = 1038;

    public static final int SP_PER_BOOK5 = 5;

    public static final int SP_PER_BOOK20 = 20;

    private static final long SP_MAX = Integer.MAX_VALUE;

    private SkillChanger() {
    }

    public static TotalSp totalSp(GameStore store, GameScripts scripts,
                                  QuestClearFlags cleared, int level) {
        return new TotalSp(baseSkillPoint(store, level), questSkillPoint(scripts, cleared));
    }

    public record TotalSp(int base, int quest) {

        public long total() {
            return Math.min(SP_MAX, (long) base + quest);
        }
    }

    @FunctionalInterface
    public interface QuestFinder {

        QuestScript find(int questId);
    }

    public static QuestFinder finder(GameScripts scripts) {
        return scripts == null ? id -> null : id -> scripts.quest(id).orElse(null);
    }

    public static int baseSkillPoint(GameStore store, int level) {
        if (store == null) {
            return 0;
        }
        long sp = 0;
        for (int grade = 1; grade <= level; grade++) {
            sp = Math.min(SP_MAX, sp + store.growthTable().spAtLevelUp(grade));
        }
        return (int) sp;
    }

    public static int questSkillPoint(GameScripts scripts, QuestClearFlags cleared) {
        return questSkillPoint(finder(scripts), cleared);
    }

    public static int questSkillPoint(QuestFinder findQuest, QuestClearFlags cleared) {
        if (findQuest == null || cleared == null) {
            return 0;
        }
        int[] books = countQuestSpBooks(findQuest, cleared);
        return books[0] * SP_PER_BOOK5 + books[1] * SP_PER_BOOK20;
    }

    private static int[] countQuestSpBooks(QuestFinder findQuest, QuestClearFlags cleared) {
        int n1031 = 0;
        int n1038 = 0;
        for (int questId : cleared.clearedIds()) {
            QuestScript quest = findQuest.find(questId);
            if (quest == null) {
                continue;
            }
            if (!quest.rewardsAreItems()) {
                continue;
            }
            for (var reward : quest.userRewardItems(cleared::isCleared).items()) {
                if (reward.itemId() == BOOK_SP5) {
                    n1031 += reward.count();
                } else if (reward.itemId() == BOOK_SP20) {
                    n1038 += reward.count();
                }
            }
        }
        return new int[] {n1031, n1038};
    }
}
