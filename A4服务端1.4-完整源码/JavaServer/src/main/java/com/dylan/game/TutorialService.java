package com.dylan.game;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Cmd153ChangeTutorialFlagRequest;
import com.dylan.protocol.body.Cmd153ReplyBody;
import com.dylan.store.CharacterStore;
import com.dylan.store.GameStore;

public final class TutorialService {
    public static final int TUTORIAL_COMPLETE = 31;

    public static final int SKIPABLE_LEVEL = 9;

    private TutorialService() {
    }

    public static void changeFlag(GameSession s, byte[] plain) {
        Cmd153ChangeTutorialFlagRequest req = Cmd153ChangeTutorialFlagRequest.parse(plain);

        boolean fresh = s.tutorials().set(req.bit());
        persist(s, fresh);

        if (req.bit() == TUTORIAL_COMPLETE && s.inDungeon()) {
            TownService.returnToTown(s, "教学通关（客户端报了位 " + TUTORIAL_COMPLETE + "）");
            // A4 官方教程打完自动接任务（session_replay：A5 不接 → 后面交任务 1016 被拒 NOT_CLEARABLE）
            if (fresh && ServerConfig.A4 && ServerConfig.A4_TUTORIAL_QUEST > 0) {
                activateTutorialQuest(s, ServerConfig.A4_TUTORIAL_QUEST);
            }
        }

        if (ServerConfig.A4) {
            // A4 官方每次 CMD 153 都回 1 字节 01（replay_off 15 次、scen_off 13 次，全是 1 字节）。
            // 不能复用 Cmd153ReplyBody.empty()：那是 ACT5 的 01 00，A4 客户端只读结果字节，多一个字节会被算成 under。
            s.replyNow(Cmd153ReplyBody.PACKET_ID, new byte[]{Cmd153ReplyBody.RESULT_OK});
            return;
        }
        if (!ServerConfig.TUTORIAL_FLAG_REPLY) {
            return;
        }
        byte[] body = Cmd153ReplyBody.empty().toBytes();
        s.replyNow(Cmd153ReplyBody.PACKET_ID, body);
    }

    /**
     * A4 官方教程打通后替客户端走一遍「接任务 → 推进到可交付」，客户端接着自己发 CMD 35 / 36。
     * 用的是 A5 原有的 {@link QuestService#accept} / {@link QuestService#setTrigger}（等同客户端发了
     * CMD 33 {@code questId} 和 CMD 35 {@code questId, 0}），可接检查、存档、回包都照 A5；
     * 最后照官方补一份 NOTI 21 可接表。中途教程退出（没报位 31）官方也不触发。
     */
    private static void activateTutorialQuest(GameSession s, int questId) {
        byte[] id = {(byte) questId, (byte) (questId >>> 8)};
        QuestService.accept(s, id);
        if (s.quests() == null || !s.quests().isRunning(questId)) {
            return;
        }
        QuestService.setTrigger(s, new byte[]{id[0], id[1],
                (byte) com.dylan.protocol.body.Cmd35SetQuestTriggerRequest.TRIGGER_TYPE_WHOLE_DECREMENT});
        QuestService.sendAcceptableQuests(s, "教程完成任务 " + questId + " 已激活");
        s.flush();
    }

    public static void load(GameSession s, int characNo) {
        s.tutorials().reset();
        if (!ServerConfig.TUTORIAL_PERSIST || s.store() == null) {
            return;
        }
        CharacterStore.TutorialFlagRow row =
                s.store().character().characterTutorialFlag(characNo).orElse(null);
        if (row == null) {
            return;
        }
        s.tutorials().load(row.lowFlag(), row.highBytes());
    }

    public static void loadSkipable(GameSession s) {
        if (s.store() == null || s.account() == null) {
            return;
        }
        boolean skipable = s.store().account().tutorialSkipable(s.account().mId());
        s.tutorialSkipable(skipable);
    }

    public static void markSkipableOnLevelUp(GameSession s, int level) {
        if (level <= SKIPABLE_LEVEL || s.tutorialSkipable()) {
            return;
        }
        s.tutorialSkipable(true);
        if (s.store() == null || s.account() == null) {
            return;
        }
        s.store().account().markTutorialSkipable(s.account().mId());
    }

    private static void persist(GameSession s, boolean changed) {
        GameStore store = s.store();
        if (!ServerConfig.TUTORIAL_PERSIST || store == null || s.characNo() < 0) {
            return;
        }
        if (!changed) {
            return;
        }
        int low = s.tutorials().lowFlag();
        byte[] high = s.tutorials().highBytes();
        store.character().saveTutorialFlag(s.characNo(), low, high);
    }
}
