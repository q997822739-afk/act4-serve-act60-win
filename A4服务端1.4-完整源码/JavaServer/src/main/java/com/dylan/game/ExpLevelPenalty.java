package com.dylan.game;

import com.dylan.net.ServerConfig;

public final class ExpLevelPenalty {
    public static final int MIN_GAP = -7;

    static final double[] DEFAULT_TABLE = {
            0.05, 0.20, 0.50, 0.79, 0.88, 0.95, 0.98, 1.00,
            1.01, 1.04, 1.07, 1.12, 1.20, 0.70, 0.70, 0.60, 0.50, 0.05,
    };

    private static final double[] TABLE = resolve();

    private ExpLevelPenalty() {
    }

    public static double rateOf(int characterLevel, int monsterLevel) {
        return rateOfGap(monsterLevel - characterLevel);
    }

    public static double rateOfGap(int gap) {
        int index = Math.max(0, Math.min(TABLE.length - 1, gap - MIN_GAP));
        return TABLE[index];
    }

    public static double[] table() {
        return TABLE.clone();
    }

    private static double[] resolve() {
        String raw = ServerConfig.EXP_PENALTY_TABLE;
        if (raw == null || raw.isBlank()) {
            return DEFAULT_TABLE.clone();
        }
        String[] parts = raw.split(",");
        if (parts.length < 2) {
            return DEFAULT_TABLE.clone();
        }
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException e) {
                return DEFAULT_TABLE.clone();
            }
        }
        return out;
    }
}
