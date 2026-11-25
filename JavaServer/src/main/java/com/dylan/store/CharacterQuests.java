package com.dylan.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CharacterQuests {
    public static final int SLOTS = 20;

    public static final int EMPTY = 0;

    public static final int NO_URGENT_QUEST = -1;

    private final int characNo;
    private final QuestClearFlags cleared;
    private final int[] questIds = new int[SLOTS];
    private final long[] triggers = new long[SLOTS];
    private int urgentQuestIndex = NO_URGENT_QUEST;

    private CharacterQuests(int characNo, QuestClearFlags cleared) {
        this.characNo = characNo;
        this.cleared = cleared;
    }

    public static CharacterQuests empty(int characNo) {
        return new CharacterQuests(characNo, QuestClearFlags.empty());
    }

    public static CharacterQuests of(int characNo, QuestClearFlags cleared,
                                     int[] questIds, long[] triggers, int urgentQuestIndex) {
        CharacterQuests quests = new CharacterQuests(characNo, cleared);
        int n = Math.min(SLOTS, Math.min(questIds.length, triggers.length));
        System.arraycopy(questIds, 0, quests.questIds, 0, n);
        System.arraycopy(triggers, 0, quests.triggers, 0, n);
        quests.urgentQuestIndex = urgentQuestIndex;
        return quests;
    }

    public int characNo() {
        return characNo;
    }

    public QuestClearFlags cleared() {
        return cleared;
    }

    public int questIdAt(int slot) {
        return questIds[slot];
    }

    public long triggerAt(int slot) {
        return triggers[slot];
    }

    public int urgentQuestIndex() {
        return urgentQuestIndex;
    }

    public void urgentQuestIndex(int value) {
        this.urgentQuestIndex = value;
    }

    public int slotOf(int questId) {
        for (int i = 0; i < SLOTS; i++) {
            if (questIds[i] == questId) {
                return i;
            }
        }
        return -1;
    }

    public boolean isRunning(int questId) {
        return questId != EMPTY && slotOf(questId) >= 0;
    }

    public int firstFreeSlot() {
        for (int i = 0; i < SLOTS; i++) {
            if (questIds[i] == EMPTY) {
                return i;
            }
        }
        return -1;
    }

    public void occupy(int slot, int questId, long trigger) {
        questIds[slot] = questId;
        triggers[slot] = trigger;
    }

    public void release(int slot) {
        questIds[slot] = EMPTY;
        triggers[slot] = 0;
    }

    public void trigger(int slot, long value) {
        triggers[slot] = value;
    }

    public Map<Integer, Long> running() {
        Map<Integer, Long> out = new LinkedHashMap<>();
        for (int i = 0; i < SLOTS; i++) {
            if (questIds[i] != EMPTY) {
                out.put(questIds[i], triggers[i]);
            }
        }
        return out;
    }

    public List<Integer> runningIds() {
        List<Integer> out = new ArrayList<>();
        for (int id : questIds) {
            if (id != EMPTY) {
                out.add(id);
            }
        }
        return out;
    }

    public int runningCount() {
        int n = 0;
        for (int id : questIds) {
            if (id != EMPTY) {
                n++;
            }
        }
        return n;
    }

    @Override
    public String toString() {
        return "角色 " + characNo + "：进行中 " + runningCount() + " 个，" + cleared;
    }
}
