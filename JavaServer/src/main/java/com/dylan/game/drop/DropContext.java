package com.dylan.game.drop;

public record DropContext(int dropRate, int monsterLevel, int roleType,
                          int dungeonLevel, int dungeonDiff, int memberCount,
                          int bourgeoisRate,
                          int randomDropCount, int specifyDropCount, int monsterId,
                          boolean hellMonster, int hellDifficulty, boolean lastOfHellGroup) {

    public DropContext(int dropRate, int monsterLevel, int roleType,
                       int dungeonLevel, int dungeonDiff, int memberCount,
                       int bourgeoisRate,
                       int randomDropCount, int specifyDropCount, int monsterId) {
        this(dropRate, monsterLevel, roleType, dungeonLevel, dungeonDiff, memberCount,
                bourgeoisRate, randomDropCount, specifyDropCount, monsterId,
                false, 0, false);
    }

    public static final int NO_PREMIUM = 100;

    public static final int MAX_BOURGEOIS_RATE = 100;

    public static final int MAX_MEMBERS = 4;

    public DropContext {
        memberCount = Math.clamp(memberCount, 1, MAX_MEMBERS);
        bourgeoisRate = Math.clamp(bourgeoisRate, 0, MAX_BOURGEOIS_RATE);
    }

    public int monsterRoleType() {
        return switch (roleType) {
            case 5 -> 0;
            case 6 -> 1;
            case 7 -> 2;
            case 8 -> 3;
            default -> roleType;
        };
    }
}
