package com.dylan.game;

import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyRegistry {
    public static final int FIRST_INDEX = 1;

    public static final int LAST_INDEX = 0xFFFE;

    private static final Map<GameSession, Party> PARTY_OF = new ConcurrentHashMap<>();

    private static final Map<Integer, Party> BY_INDEX = new ConcurrentHashMap<>();

    private static final Map<GameSession, Invite> PENDING = new ConcurrentHashMap<>();

    private static final BitSet USED_INDEXES = new BitSet(LAST_INDEX + 1);

    private static int cursor = FIRST_INDEX;

    private PartyRegistry() {
    }

    public static Party partyOf(GameSession s) {
        return s == null ? null : PARTY_OF.get(s);
    }

    public static Party byIndex(int index) {
        return BY_INDEX.get(index);
    }

    public static Party create(GameSession founder, String title, int capacity) {
        Party party = new Party(allocateIndex(), founder, title, capacity);
        HellPartyService.initHellPartyValue(party, founder);
        PARTY_OF.put(founder, party);
        BY_INDEX.put(party.index(), party);
        return party;
    }

    public static Party createSinglePlay(GameSession founder, String title) {
        Party party = new Party(allocateIndex(), founder, title, Party.MAX_MEMBERS, true);
        HellPartyService.initHellPartyValue(party, founder);
        PARTY_OF.put(founder, party);
        BY_INDEX.put(party.index(), party);
        return party;
    }

    public static void forget(GameSession s) {
        PARTY_OF.remove(s);
    }

    /** {@link #create} 只登记队长。城镇组队进本时，其余队员用这个挂到同一支 Party 上。 */
    public static void bind(GameSession s, Party party) {
        if (s != null && party != null) {
            PARTY_OF.put(s, party);
        }
    }

    public static void dissolve(Party party) {
        PARTY_OF.entrySet().removeIf(e -> e.getValue() == party);
        BY_INDEX.remove(party.index(), party);
        releaseIndex(party.index());
    }

    public static int count() {
        return (int) PARTY_OF.values().stream().distinct().count();
    }

    public record Invite(GameSession requester, int kind, int echo, long atNanos) {
    }

    public static void dropInvite(GameSession target) {
        PENDING.remove(target);
    }

    public static void dropInvitesFrom(GameSession requester) {
        PENDING.entrySet().removeIf(e -> e.getValue().requester() == requester);
    }

    private static synchronized int allocateIndex() {
        int id = findIndex();
        if (id < 0) {
            cursor = FIRST_INDEX;
            id = findIndex();
        }
        if (id < 0) {
            throw new IllegalStateException("队伍编号用光了：" + FIRST_INDEX + ".." + LAST_INDEX);
        }
        cursor = id + 1;
        return id;
    }

    private static int findIndex() {
        for (int i = cursor; i <= LAST_INDEX; i++) {
            if (!USED_INDEXES.get(i)) {
                USED_INDEXES.set(i);
                return i;
            }
        }
        return -1;
    }

    private static synchronized void releaseIndex(int index) {
        if (index >= FIRST_INDEX && index <= LAST_INDEX) {
            USED_INDEXES.clear(index);
        }
    }

    static synchronized void clear() {
        PARTY_OF.clear();
        BY_INDEX.clear();
        PENDING.clear();
        USED_INDEXES.clear();
        cursor = FIRST_INDEX;
    }
}
