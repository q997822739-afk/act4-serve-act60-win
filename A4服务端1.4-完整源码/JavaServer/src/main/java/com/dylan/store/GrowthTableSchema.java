package com.dylan.store;

final class GrowthTableSchema {

    private GrowthTableSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS monster_reward_ref (
              level TINYINT UNSIGNED NOT NULL DEFAULT 0,
              exp   INT UNSIGNED     NOT NULL DEFAULT 0,
              PRIMARY KEY (level)
            )
            """,

            """
            CREATE TABLE IF NOT EXISTS sp_reward (
              grade SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              sp    INT UNSIGNED      NOT NULL DEFAULT 0,
              PRIMARY KEY (grade)
            )
            """,
    };

    static final int[] MONSTER_REWARD_EXP = {
            30, 40, 50, 60, 70, 80, 90, 100, 110, 120,
            130, 140, 150, 160, 170, 185, 201, 218, 235, 253,
            271, 290, 310, 330, 351, 372, 394, 417, 440, 464,
            488, 513, 539, 565, 592, 619, 647, 676, 705, 735,
            765, 796, 828, 860, 893, 926, 960, 995, 1030, 1066,
            1102, 1139, 1177, 1215, 1254, 1293, 1333, 1374, 1415, 1457,
    };

    static final int MAX_SEEDED_LEVEL = MONSTER_REWARD_EXP.length;

    static final int SP_REWARD_AT_LEVEL_2 = 30;

    static int spRewardFor(int grade) {
        return grade < 2 ? 0 : SP_REWARD_AT_LEVEL_2 + (grade - 2);
    }
}
