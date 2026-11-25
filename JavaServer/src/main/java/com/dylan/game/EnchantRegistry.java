package com.dylan.game;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EnchantRegistry {
    private static final Map<GameSession, EnchantMission> BY_MEMBER = new ConcurrentHashMap<>();

    private EnchantRegistry() {
    }

    public static EnchantMission openSolo(GameSession user) {
        if (user == null) {
            return null;
        }
        EnchantMission mission = new EnchantMission(user, user, false);
        BY_MEMBER.put(user, mission);
        return mission;
    }

    public static EnchantMission open(GameSession enchanter, GameSession beneficiary,
                                      boolean useOtherUserMonsterCard) {
        if (enchanter == null || beneficiary == null || enchanter == beneficiary) {
            return null;
        }
        EnchantMission mission =
                new EnchantMission(enchanter, beneficiary, useOtherUserMonsterCard);
        if (BY_MEMBER.putIfAbsent(enchanter, mission) != null) {
            return null;
        }
        if (BY_MEMBER.putIfAbsent(beneficiary, mission) != null) {
            BY_MEMBER.remove(enchanter, mission);
            return null;
        }
        return mission;
    }

    public static EnchantMission of(GameSession side) {
        return side == null ? null : BY_MEMBER.get(side);
    }

    public static boolean busy(GameSession side) {
        return of(side) != null;
    }

    public static void close(EnchantMission mission) {
        if (mission == null) {
            return;
        }
        BY_MEMBER.remove(mission.enchanter(), mission);
        BY_MEMBER.remove(mission.beneficiary(), mission);
    }
}
