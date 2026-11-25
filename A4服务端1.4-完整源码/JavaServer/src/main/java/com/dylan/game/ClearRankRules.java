package com.dylan.game;

import com.dylan.net.ServerConfig;

public final class ClearRankRules {
    public static final int KILL_WEIGHT = 70;

    public static final int SPEED_WEIGHT = 70;

    private ClearRankRules() {
    }

    public static ClearRank evaluate(DungeonRunSummary run) {
        return fromScore(score(run));
    }

    public static int score(DungeonRunSummary run) {
        return killScore(run) + speedScore(run);
    }

    public static int killScore(DungeonRunSummary run) {
        int spawned = run.monstersSpawned();
        if (spawned <= 0) {
            return KILL_WEIGHT;
        }
        double ratio = Math.min(1.0, (double) run.monstersKilled() / spawned);
        return (int) Math.round(ratio * KILL_WEIGHT);
    }

    public static int speedScore(DungeonRunSummary run) {
        if (!run.clockStarted()) {
            return 0;
        }
        long elapsedMs = run.elapsedMillis();
        if (elapsedMs <= 0) {
            return SPEED_WEIGHT;
        }
        long baselineMs = (long) Math.max(1, run.roomsEntered())
                * ServerConfig.RANK_ROOM_SECONDS * 1000L;
        double ratio = Math.min(1.0, (double) baselineMs / elapsedMs);
        return (int) Math.round(ratio * SPEED_WEIGHT);
    }

    public static ClearRank fromScore(int score) {
        int[] thresholds = ServerConfig.RANK_THRESHOLDS;
        for (int i = 0; i < RANKS_HIGH_TO_LOW.length && i < thresholds.length; i++) {
            if (score >= thresholds[i]) {
                return RANKS_HIGH_TO_LOW[i];
            }
        }
        return ClearRank.F;
    }

    public static final ClearRank[] RANKS_HIGH_TO_LOW = {
            ClearRank.SSS, ClearRank.SS, ClearRank.S, ClearRank.A,
            ClearRank.B, ClearRank.C, ClearRank.D, ClearRank.E,
    };

    public static String explain(DungeonRunSummary run) {
        return "评级 " + evaluate(run) + "（总分 " + score(run)
                + " = 清怪 " + killScore(run) + "/" + KILL_WEIGHT
                + " [" + run.monstersKilled() + "/" + run.monstersSpawned() + " 只]"
                + " + 速度 " + speedScore(run) + "/" + SPEED_WEIGHT
                + " [" + run.elapsedMillis() / 1000 + " 秒 / 基准 "
                + Math.max(1, run.roomsEntered()) * ServerConfig.RANK_ROOM_SECONDS + " 秒]）";
    }
}
