package com.dylan.game;

import com.dylan.protocol.body.Cmd74SelectCardReply;

public final class ClearState {
    public static final int NONE = 0;

    public static final int PLAY_RESULT = 1;

    public static final int REWARD_SENT = 2;

    public static final int SCORE_SCROLL = 3;

    public static final int CARD_SELECT = 4;

    public static final int DONE = 5;

    public static final int SLOT_COUNT = Cmd74SelectCardReply.SLOT_COUNT;

    public static final int CARD_TYPE_BOTH = 2;

    private int state = NONE;

    private int reportedRank;

    private ClearRank rank = ClearRank.F;

    private final ClearRewardRules.Cards[] cards = new ClearRewardRules.Cards[SLOT_COUNT];

    private final int[] freeOwners = new int[SLOT_COUNT];

    private final int[] goldOwners = new int[SLOT_COUNT];

    public ClearState() {
        resetOwners();
    }

    public void reset() {
        state = NONE;
        reportedRank = 0;
        rank = ClearRank.F;
        java.util.Arrays.fill(cards, null);
        resetOwners();
    }

    private void resetOwners() {
        java.util.Arrays.fill(freeOwners, Cmd74SelectCardReply.NOBODY);
        java.util.Arrays.fill(goldOwners, Cmd74SelectCardReply.NOBODY);
    }

    public int state() {
        return state;
    }

    public boolean advance(int next) {
        if (state >= next) {
            return false;
        }
        state = next;
        return true;
    }

    public int reportedRank() {
        return reportedRank;
    }

    public void reportedRank(int value) {
        this.reportedRank = value;
    }

    public ClearRank rank() {
        return rank;
    }

    public void rank(ClearRank value) {
        this.rank = value;
    }

    public void cardsOf(int slot, ClearRewardRules.Cards value) {
        if (slot >= 0 && slot < SLOT_COUNT) {
            cards[slot] = value;
        }
    }

    public ClearRewardRules.Cards cardsOf(int slot) {
        return slot >= 0 && slot < SLOT_COUNT ? cards[slot] : null;
    }

    public int[] freeOwners() {
        return freeOwners.clone();
    }

    public int[] goldOwners() {
        return goldOwners.clone();
    }

    public boolean takeCard(int cardType, int cardNo, int owner) {
        if (cardNo < 0 || cardNo >= SLOT_COUNT) {
            return false;
        }
        int[] owners = cardType == Cmd74SelectCardReply.CARD_TYPE_GOLD ? goldOwners : freeOwners;
        if (owners[cardNo] != Cmd74SelectCardReply.NOBODY) {
            return false;
        }
        owners[cardNo] = owner;
        return true;
    }

    public boolean haveAlreadySelectedCard(int owner, int cardType) {
        if (cardType != Cmd74SelectCardReply.CARD_TYPE_FREE
                && cardType != Cmd74SelectCardReply.CARD_TYPE_GOLD) {
            return false;
        }
        return contains(cardType == Cmd74SelectCardReply.CARD_TYPE_GOLD ? goldOwners : freeOwners,
                owner);
    }

    public boolean isCardFree(int cardType, int cardNo) {
        if (cardNo < 0 || cardNo >= SLOT_COUNT) {
            return false;
        }
        int[] owners = cardType == Cmd74SelectCardReply.CARD_TYPE_GOLD ? goldOwners : freeOwners;
        return owners[cardNo] == Cmd74SelectCardReply.NOBODY;
    }

    public boolean tookGoldCard(int owner) {
        return contains(goldOwners, owner);
    }

    public boolean isCompleteCardSelectOfMembers(int cardType,
                                                 java.util.function.IntPredicate validMember) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!validMember.test(i)) {
                continue;
            }
            boolean free = cardType == CARD_TYPE_BOTH
                    || cardType == Cmd74SelectCardReply.CARD_TYPE_FREE;
            boolean gold = cardType == CARD_TYPE_BOTH
                    || cardType == Cmd74SelectCardReply.CARD_TYPE_GOLD;
            if (free && freeOwners[i] == Cmd74SelectCardReply.NOBODY) {
                return false;
            }
            if (gold && goldOwners[i] == Cmd74SelectCardReply.NOBODY) {
                return false;
            }
        }
        return true;
    }

    public boolean completeCardSelect(java.util.function.IntPredicate validMember) {
        boolean filled = false;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (freeOwners[i] != Cmd74SelectCardReply.NOBODY) {
                continue;
            }
            for (int j = 0; j < SLOT_COUNT; j++) {
                if (validMember.test(j) && !contains(freeOwners, j)) {
                    freeOwners[i] = j;
                    filled = true;
                    break;
                }
            }
        }
        return filled;
    }

    private static boolean contains(int[] owners, int owner) {
        for (int o : owners) {
            if (o == owner) {
                return true;
            }
        }
        return false;
    }

    public ClearRewardRules.Cards[] takeCards() {
        var out = cards.clone();
        java.util.Arrays.fill(cards, null);
        return out;
    }
}
