package com.dylan.game;

import com.dylan.protocol.body.Noti157DeathTowerRewardBody;
import com.dylan.pvf.script.DungeonScript;

import java.util.ArrayList;
import java.util.List;

public final class DeathTower {
    public static final int STATE_NONE = 0;

    public static final int STATE_PLAYING = 2;

    public static final int STATE_CLEARING = 3;

    public static final int STATE_REWARDED = 4;

    public static final int STATE_EPLP = 5;

    public static final int STAGE_READY = 0;

    public static final int STAGE_PLAYING = 1;

    public static final int STAGE_CLEARED = 2;

    private final DungeonScript dungeon;
    private final int endStage;

    private int currentStage = 1;
    private int stageMapIndex;
    private DungeonRoom room = DungeonRoom.empty();
    private int stageState = STAGE_READY;
    private long stageStartedAt;
    private long stageFinishedAt;

    private final List<MemberInfo> startMembers = new ArrayList<>(Party.MAX_MEMBERS);
    private int startMemberCount;
    private long playTimeMs;
    private long lastStageTimeMs;
    private final List<List<Noti157DeathTowerRewardBody.RewardItem>> rewardItems =
            new ArrayList<>(Party.MAX_MEMBERS);
    private final int[] rewardExp = new int[Party.MAX_MEMBERS];

    private int towerState = STATE_NONE;

    private int index = DeathTowerRegistry.NOT_IN_TOWER;

    private Party party;

    public synchronized int index() {
        return index;
    }

    synchronized void index(int value) {
        this.index = value;
    }

    public synchronized Party party() {
        return party;
    }

    synchronized void attachParty(Party value) {
        this.party = value;
    }

    public record MemberInfo(int characNo, String name, int level, int job, int growType) {}

    DeathTower(DungeonScript dungeon) {
        this.dungeon = dungeon;
        this.endStage = dungeon.tower().maxStage();
        for (int i = 0; i < Party.MAX_MEMBERS; i++) {
            rewardItems.add(List.of());
        }
    }

    public DungeonScript dungeon() {
        return dungeon;
    }

    public int dungeonIndex() {
        return dungeon.id();
    }

    public int endStage() {
        return endStage;
    }

    public int stageMap(int stage) {
        return dungeon.tower().mapIndex(stage);
    }

    public boolean useDeathTowerRewardTable() {
        return dungeon.tower().limitOfStackableItem();
    }

    public synchronized int currentStage() {
        return currentStage;
    }

    public synchronized int stageMapIndex() {
        return stageMapIndex;
    }

    public synchronized DungeonRoom room() {
        return room;
    }

    public synchronized int stageState() {
        return stageState;
    }

    public synchronized boolean stageState(int next) {
        if (next == STAGE_PLAYING) {
            if (stageState != STAGE_READY) {
                return false;
            }
        } else if (next == STAGE_CLEARED) {
            if (stageState != STAGE_PLAYING) {
                return false;
            }
        } else {
            return false;
        }
        stageState = next;
        return true;
    }

    public synchronized void resetStage(int mapIndex, List<RoomMonster> monsters) {
        this.stageMapIndex = mapIndex;
        this.room = DungeonRoom.of(monsters);
        this.stageState = STAGE_READY;
        this.stageStartedAt = 0;
        this.stageFinishedAt = 0;
    }

    public synchronized boolean moveNextStage() {
        if (room.livingCount() > 0 || currentStage >= endStage) {
            return false;
        }
        currentStage++;
        return true;
    }

    public synchronized boolean checkClearStage() {
        return room.livingCount() == 0;
    }

    public synchronized void onStartStage(long nowMs) {
        this.stageStartedAt = nowMs;
    }

    public synchronized long onFinishStage(long nowMs) {
        this.stageFinishedAt = nowMs;
        long spent = stageStartedAt == 0 ? 0 : Math.max(0, stageFinishedAt - stageStartedAt);
        this.lastStageTimeMs = spent;
        this.playTimeMs += spent;
        return spent;
    }

    public synchronized void makeStartMemberInfo(List<MemberInfo> members) {
        startMembers.clear();
        startMembers.addAll(members);
        startMemberCount = members.size();
    }

    public synchronized List<MemberInfo> startMembers() {
        return List.copyOf(startMembers);
    }

    public synchronized int startMemberCount() {
        return startMemberCount;
    }

    public synchronized long playTimeMs() {
        return playTimeMs;
    }

    public synchronized long lastStageTimeMs() {
        return lastStageTimeMs;
    }

    public synchronized List<Noti157DeathTowerRewardBody.RewardItem> rewardItems(int slot) {
        return slot >= 0 && slot < rewardItems.size() ? rewardItems.get(slot) : List.of();
    }

    public synchronized void rewardItems(int slot,
            List<Noti157DeathTowerRewardBody.RewardItem> items) {
        if (slot >= 0 && slot < rewardItems.size()) {
            rewardItems.set(slot, List.copyOf(items));
        }
    }

    public synchronized int rewardExp(int slot) {
        return slot >= 0 && slot < rewardExp.length ? rewardExp[slot] : 0;
    }

    public synchronized void rewardExp(int slot, int value) {
        if (slot >= 0 && slot < rewardExp.length) {
            rewardExp[slot] = value;
        }
    }

    public synchronized List<List<Noti157DeathTowerRewardBody.RewardItem>> allRewardItems() {
        return List.copyOf(rewardItems);
    }

    public synchronized int towerState() {
        return towerState;
    }

    public synchronized void towerState(int value) {
        this.towerState = value;
    }

    @Override
    public String toString() {
        return "死亡之塔 " + dungeonIndex() + " 第 " + currentStage() + "/" + endStage
                + " 层（图 " + stageMapIndex() + "，层状态 " + stageState()
                + "，塔状态 " + towerState() + "）";
    }
}
