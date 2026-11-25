package com.dylan.game;

import java.util.concurrent.ThreadLocalRandom;

import com.dylan.protocol.body.EquipmentSlot;
import com.dylan.protocol.body.Noti39Body;
import com.dylan.pvf.script.ItemScript;

public final class ItemRoutingService {

    private ItemRoutingService() {
    }

    private static final int SCORE_ELIGIBLE = 1;

    private static final int SCORE_TURN = 40;

    private static final int SCORE_JOB_WEAPON = 30;

    private static final int SCORE_CAP = 100;

    public record Roll(int[] dice, GameSession winner, boolean routed) {

        public Roll {
            dice = dice == null ? Noti39Body.NO_DICE.clone() : dice.clone();
        }

        @Override
        public int[] dice() {
            return dice.clone();
        }

        static Roll toPicker(GameSession picker) {
            return new Roll(Noti39Body.NO_DICE.clone(), picker, false);
        }
    }

    public static Roll decide(Party party, GameSession picker, DungeonRoom.GroundItem ground,
                              ItemScript script, int count, boolean routingFlag) {
        int memberCount = party.memberCount();
        if (memberCount <= 1) {
            return Roll.toPicker(picker);
        }
        if (!shouldRoll(!ground.fromPlayer(), script.rarity(), script.epicRouting(),
                script.isAvatar(), routingFlag)) {
            return Roll.toPicker(picker);
        }
        return roll(party, memberCount, script, count);
    }

    static boolean shouldRoll(boolean monsterDrop, int rarity, boolean epicRouting,
                              boolean avatar, boolean routingFlag) {
        boolean routing = true;
        if (!monsterDrop) {
            routing = false;
        }
        if (rarity > ItemScript.RARITY_UNIQUE) {
            routing = routingFlag;
        }
        if (monsterDrop && epicRouting) {
            routing = routingFlag;
        }
        if (!routing) {
            return false;
        }
        boolean checkItemRouting = epicRouting || rarity > ItemScript.RARITY_COMMON;
        return checkItemRouting || avatar;
    }

    private static Roll roll(Party party, int memberCount, ItemScript script, int count) {
        boolean[] alive = new boolean[Noti39Body.PARTY_SIZE];
        boolean[] canTake = new boolean[Noti39Body.PARTY_SIZE];
        boolean[] jobWeapon = new boolean[Noti39Body.PARTY_SIZE];
        for (int slot = 0; slot < Noti39Body.PARTY_SIZE; slot++) {
            GameSession member = party.aliveAt(slot) ? party.memberAt(slot) : null;
            if (member == null) {
                continue;
            }
            alive[slot] = true;
            canTake[slot] = canTake(member, script, count);
            jobWeapon[slot] = !script.isStackableItem()
                    && script.equipmentSlot() == EquipmentSlot.WEAPON
                    && script.usableByJob(jobOf(member));
        }

        int[] dice = new int[Noti39Body.PARTY_SIZE];
        int turnBase = party.nextRoutingTurn(memberCount);
        int turnTaken = rollScores(dice, alive, canTake, jobWeapon, turnBase,
                ThreadLocalRandom.current());
        if (turnTaken >= 0) {
            party.setRoutingTurn(turnTaken);
        }
        int winnerSlot = pickWinner(dice, alive);
        GameSession winner = winnerSlot < 0 ? null : party.memberAt(winnerSlot);
        return new Roll(dice, winner, true);
    }

    static int rollScores(int[] dice, boolean[] alive, boolean[] canTake, boolean[] jobWeapon,
                          int turnBase, java.util.random.RandomGenerator random) {
        int turnTaken = -1;
        int seq = 0;
        for (int slot = 0; slot < dice.length; slot++) {
            if (!alive[slot]) {
                continue;
            }
            if (canTake[slot]) {
                dice[slot] += SCORE_ELIGIBLE;
                if (seq == turnBase) {
                    dice[slot] += SCORE_TURN;
                    turnTaken = seq;
                }
                if (jobWeapon[slot]) {
                    dice[slot] += SCORE_JOB_WEAPON;
                }
                dice[slot] += random.nextInt(SCORE_CAP - dice[slot]);
            }
            seq++;
        }
        return turnTaken;
    }

    static int pickWinner(int[] dice, boolean[] alive) {
        int best = 0;
        int winnerSlot = -1;
        for (int slot = 0; slot < dice.length; slot++) {
            if (!alive[slot]) {
                continue;
            }
            if (dice[slot] <= best) {
                if (dice[slot] == best && dice[slot] > 0) {
                    dice[slot]--;
                }
            } else {
                best = dice[slot];
                winnerSlot = slot;
            }
        }
        return winnerSlot;
    }

    private static boolean canTake(GameSession member, ItemScript script, int count) {
        if (!member.isActive()) {
            return false;
        }
        var inv = member.inventory() == null ? null : member.inventory().inventory();
        if (inv == null) {
            return true;
        }
        return InventoryService.tryAddItem(inv, script, count).ok();
    }

    private static int jobOf(GameSession member) {
        var charac = member.character();
        return charac == null ? -1 : charac.job();
    }
}
