package com.dylan.game;

import com.dylan.net.ServerConfig;

public record ClearExpBonus(long base, long rank, long party, long avatar, int kind,
                            long event, long expItem, long premium, long pcRoom,
                            long channel, long member, long creature) {

    private static final double AVATAR_RATE_PARTY = 0.05;

    private static final double AVATAR_RATE_SOLO = 0.02;

    private static final double CREATURE_RATE = 0.05;

    public static final int KIND_NONE = 0;

    public static final int KIND_EVENT = 1;

    public static final int KIND_PC_ROOM = 2;

    public static ClearExpBonus compute(GameSession s, long base, ClearRank rank) {
        if (base <= 0) {
            return zero();
        }
        var scripts = s.scripts();
        var params = scripts == null ? null : scripts.serverParameter();
        int members = memberCount(s);

        double clearRate = params == null ? 1.0 : params.clearExpRate();
        double rankRate = params == null ? 0.0 : params.clearRankExpRate(rank);
        long rankBonus = Math.round(base * clearRate * rankRate);

        long avatar = 0;
        if (hasAvatarInParty(s)) {
            avatar = Math.round(base * avatarRate(members));
            avatar = Math.max(1, avatar);
        }

        long creature = 0;
        if (s.inventory() != null && s.inventory().equippedCreature() != null) {
            creature = Math.max(1, Math.round(base * CREATURE_RATE));
        }

        long eventMain;
        int kind;
        double pcRoomFactor = pcRoomBurningFactor(s);
        if (pcRoomFactor > 0) {
            eventMain = Math.round(base * pcRoomFactor);
            kind = KIND_PC_ROOM;
        } else {
            eventMain = Math.round(base * ExperienceService.expDoubleRate(s));
            kind = eventMain > 0 ? KIND_EVENT : KIND_NONE;
        }
        int burningPercent = ExperienceService.burningExpBonusPercent(s);
        long burning = base * burningPercent / 100;
        if (burning > 0 && kind == KIND_NONE) {
            kind = KIND_EVENT;
        }
        long event = eventMain + burning;

        long expItem = Math.round(base * ExperienceService.expItemBonusRate(s));

        long premium = base * premiumExpPercent(s) / 100;

        long pcRoom = existSamePcRoomUser(s) ? Math.round(base * pcRoomPartyRate(params)) : 0;

        long channel = Math.round(base * channelBonusRate(s));

        long member = upperMemberBonus(s, base);

        long party = partyDisplay(base, members, params,
                rankBonus + avatar + creature + event + premium + channel);

        var bonus = new ClearExpBonus(base, rankBonus, party, avatar, kind, event, expItem,
                premium, pcRoom, channel, member, creature);
        return bonus;
    }

    private static double pcRoomBurningFactor(GameSession s) {
        int no = com.dylan.game.event.EventNo.PC_ROOM_BURNING;
        if (!com.dylan.game.event.EventService.isRunning(no, s)) {
            return 0.0;
        }
        return com.dylan.game.event.EventService.param1(no) / 100;
    }

    private static boolean existSamePcRoomUser(GameSession s) {
        int mine = PremiumService.pcRoomNoFor(ServerConfig.PC_ROOM_NO);
        if (mine == 0) {
            return false;
        }
        Party party = PartyRegistry.partyOf(s);
        return party != null && party.memberCount() > 1;
    }

    private static double channelBonusRate(GameSession s) {
        return 0.0;
    }

    private static long upperMemberBonus(GameSession s, long base) {
        return 0;
    }

    public static ClearExpBonus zero() {
        return new ClearExpBonus(0, 0, 0, 0, KIND_NONE, 0, 0, 0, 0, 0, 0, 0);
    }

    public long extraTotal() {
        return rank + avatar + event + expItem + premium + pcRoom + channel + member + creature;
    }

    static double avatarRate(int members) {
        return members > 1 ? AVATAR_RATE_PARTY : AVATAR_RATE_SOLO;
    }

    static double pcRoomPartyRate(com.dylan.pvf.script.ServerParameter params) {
        int override = ServerConfig.PC_ROOM_PARTY_EXP_RATE;
        if (override >= 0) {
            return override / 100.0;
        }
        return params == null ? 0.0 : params.pcRoomPartyExpRate();
    }

    private static boolean hasAvatarInParty(GameSession s) {
        Party party = PartyRegistry.partyOf(s);
        if (party == null) {
            return s.inventory() != null && s.inventory().hasEquippedAvatar();
        }
        for (GameSession member : party.view().members()) {
            if (member.inventory() != null && member.inventory().hasEquippedAvatar()) {
                return true;
            }
        }
        return false;
    }

    static long partyDisplay(long base, int members,
                             com.dylan.pvf.script.ServerParameter params,
                             long bonusSum) {
        if (params == null || base <= 0) {
            return 0;
        }
        double weight = params.partySizeExpRate(members);
        if (weight <= 0) {
            return 0;
        }
        long fromParty = base - (long) (base / weight);
        if (fromParty <= 0) {
            return 0;
        }
        return fromParty * (bonusSum / base + 1);
    }

    private static int premiumExpPercent(GameSession s) {
        return s.premium() == null ? 0 : Math.max(0, s.premium().advantageExpRate());
    }

    private static int memberCount(GameSession s) {
        Party party = PartyRegistry.partyOf(s);
        return party == null ? 1 : Math.max(1, party.memberCount());
    }
}
