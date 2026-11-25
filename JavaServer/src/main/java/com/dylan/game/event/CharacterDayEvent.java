package com.dylan.game.event;

import com.dylan.game.GameSession;
import com.dylan.game.MailService;
import com.dylan.store.GameStore;

public final class CharacterDayEvent {
    private CharacterDayEvent() {
    }

    private static final int STEP = 30;

    private static final int FULL_DAY = 156;

    public static final int ITEM_DRINK = 7516;

    public static final int ITEM_TICKET = 7517;

    public static final int ITEM_RECOVERY = 7518;

    public static final String SENDER_KEY = "game_server_msg_70";

    record Gift(int itemId, int addInfo, String messageKey) {
    }

    static Gift giftFor(int usedBefore, int spend) {
        int after = usedBefore + spend;
        if (usedBefore == 0 || after == 0 || usedBefore > FULL_DAY) {
            return null;
        }
        int stepBefore = usedBefore / STEP;
        int stepAfter = after / STEP;
        if (stepBefore == stepAfter) {
            if (usedBefore / FULL_DAY == after / FULL_DAY) {
                return null;
            }
            return new Gift(ITEM_RECOVERY, 1, "game_server_msg_75");
        }
        if (stepBefore == 0 && stepAfter == 1) {
            return new Gift(ITEM_DRINK, 2, "game_server_msg_71");
        }
        if (stepBefore == 1 && stepAfter == 2) {
            return new Gift(ITEM_DRINK, 4, "game_server_msg_72");
        }
        if (stepBefore == 2 && stepAfter == 3) {
            return new Gift(ITEM_DRINK, 6, "game_server_msg_73");
        }
        if (stepBefore == 3 && stepAfter == 4) {
            return new Gift(ITEM_TICKET, 1, "game_server_msg_74");
        }
        return null;
    }

    public static void onFatigueUp(GameSession session, int usedBefore, int spend) {
        if (!EventService.isRunning(EventNo.CHARACTER_DAY, session)) {
            return;
        }
        var row = session.character();
        if (row == null) {
            return;
        }
        if (row.job() != EventService.param1(EventNo.CHARACTER_DAY)) {
            return;
        }
        Gift gift = giftFor(usedBefore, spend);
        if (gift == null) {
            return;
        }
        GameStore store = session.store();
        if (store == null) {
            return;
        }
        String sender = scriptString(session, SENDER_KEY, "角色节日");
        String text = scriptString(session, gift.messageKey(), "");
        MailService.deliver(store, session.characNo(), sender,
                gift.itemId(), gift.addInfo(), 0, text, true);
    }

    private static String scriptString(GameSession session, String key, String fallback) {
        var scripts = session.scripts();
        if (scripts == null) {
            return fallback;
        }
        return scripts.etcString(key).orElse(fallback);
    }
}
