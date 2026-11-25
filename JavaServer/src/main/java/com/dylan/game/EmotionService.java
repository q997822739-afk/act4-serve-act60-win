package com.dylan.game;

import com.dylan.protocol.body.Cmd273ChangeEmotionRequest;
import com.dylan.protocol.body.Cmd273ReplyBody;
import com.dylan.pvf.script.EmotionTable;
import com.dylan.store.GameStore;

public final class EmotionService {

    public static final int NONE = 0;

    private EmotionService() {
    }

    public static void load(GameSession s, int characNo) {
        s.emotion(NONE);
        GameStore store = s.store();
        if (store == null) {
            return;
        }
        int emotion = store.emotion().loadEmotion(characNo);
        if (emotion == NONE) {
            return;
        }
        EmotionTable table = s.scripts() == null ? null : s.scripts().emotions();
        if (table != null && !table.isValid(emotion)) {
            return;
        }
        s.emotion(emotion);
    }

    public static Cmd273ReplyBody change(GameSession s, Cmd273ChangeEmotionRequest req) {
        if (SessionRegistry.presenceOf(s) == null) {
            return null;
        }
        if (!s.hasCharacter()) {
            return null;
        }

        EmotionTable table = s.scripts() == null ? null : s.scripts().emotions();
        if (table == null) {
            return Cmd273ReplyBody.rejected(Cmd273ReplyBody.ERROR_INVALID);
        }
        if (!table.isValid(req.emotion())) {
            return Cmd273ReplyBody.rejected(Cmd273ReplyBody.ERROR_INVALID);
        }

        s.emotion();
        s.emotion(req.emotion());
        persist(s, req.emotion());
        return Cmd273ReplyBody.changed(req.emotion());
    }

    private static void persist(GameSession s, int emotion) {
        GameStore store = s.store();
        if (store == null || s.characNo() < 0) {
            return;
        }
        store.emotion().saveEmotion(s.characNo(), emotion);
    }

    public static String describe(GameSession s, int emotion) {
        EmotionTable table = s.scripts() == null ? null : s.scripts().emotions();
        EmotionTable.Emotion one = table == null ? null : table.get(emotion);
        if (one == null) {
            return String.valueOf(emotion);
        }
        return emotion + "（" + one.name() + "）";
    }
}
