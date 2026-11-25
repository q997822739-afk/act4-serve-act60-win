package com.dylan.game;

public final class DungeonRunSummary {
    public static final int MONSTER_TYPE_COUNT = 4;

    private long experienceBefore;

    private long experienceGained;

    private int monstersKilled;

    private final int[] killsByType = new int[MONSTER_TYPE_COUNT];

    private int monstersSpawned;

    private int roomsEntered;

    private long startedAtNanos;

    private long elapsedMillis;

    private boolean clockStopped;

    private boolean running;

    public void begin(long currentExperience) {
        experienceBefore = currentExperience;
        experienceGained = 0;
        monstersKilled = 0;
        java.util.Arrays.fill(killsByType, 0);
        monstersSpawned = 0;
        roomsEntered = 0;
        startedAtNanos = System.nanoTime();
        elapsedMillis = 0;
        clockStopped = false;
        running = true;
    }

    public void end() {
        running = false;
    }

    public boolean running() {
        return running;
    }

    public void addExperience(int amount) {
        if (running && amount > 0) {
            experienceGained += amount;
        }
    }

    public void countKill(int wireType) {
        if (!running) {
            return;
        }
        monstersKilled++;
        if (wireType >= 0 && wireType < MONSTER_TYPE_COUNT) {
            killsByType[wireType]++;
        }
    }

    public int killsOfType(int wireType) {
        return wireType >= 0 && wireType < MONSTER_TYPE_COUNT ? killsByType[wireType] : 0;
    }

    public void countRoom(int spawned) {
        if (running) {
            roomsEntered++;
            monstersSpawned += spawned;
        }
    }

    public void stopClock() {
        if (running && !clockStopped && startedAtNanos != 0) {
            elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L;
            clockStopped = true;
        }
    }

    public long experienceGained() {
        return experienceGained;
    }

    public long experienceAfter() {
        return experienceBefore + experienceGained;
    }

    public int monstersKilled() {
        return monstersKilled;
    }

    public int monstersSpawned() {
        return monstersSpawned;
    }

    public int roomsEntered() {
        return roomsEntered;
    }

    public long elapsedMillis() {
        if (clockStopped) {
            return elapsedMillis;
        }
        return startedAtNanos == 0 ? 0 : (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    public boolean clockStarted() {
        return startedAtNanos != 0;
    }

    @Override
    public String toString() {
        return "本局击杀 " + monstersKilled + "/" + monstersSpawned + " 只、走过 "
                + roomsEntered + " 个房间、用时 " + elapsedMillis() / 1000 + " 秒、获得经验 "
                + experienceGained + "（" + experienceBefore + " → " + experienceAfter() + "）";
    }
}
