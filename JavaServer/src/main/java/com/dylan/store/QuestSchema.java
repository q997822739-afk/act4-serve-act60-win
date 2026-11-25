package com.dylan.store;

final class QuestSchema {

    private QuestSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS new_charac_quest (
              charac_no        INT UNSIGNED      NOT NULL DEFAULT 0,
              clear_quest      BLOB              NOT NULL,
              quest_notify     BLOB              NOT NULL,
              play_1           SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_1_trigger   INT UNSIGNED      NOT NULL DEFAULT 0,
              play_2           SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_2_trigger   INT UNSIGNED      NOT NULL DEFAULT 0,
              play_3           SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_3_trigger   INT UNSIGNED      NOT NULL DEFAULT 0,
              play_4           SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_4_trigger   INT UNSIGNED      NOT NULL DEFAULT 0,
              play_5           SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_5_trigger   INT UNSIGNED      NOT NULL DEFAULT 0,
              play_6           SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_6_trigger   INT UNSIGNED      NOT NULL DEFAULT 0,
              play_7           SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_7_trigger   INT UNSIGNED      NOT NULL DEFAULT 0,
              play_8           SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_8_trigger   INT UNSIGNED      NOT NULL DEFAULT 0,
              play_9           SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_9_trigger   INT UNSIGNED      NOT NULL DEFAULT 0,
              play_10          SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_10_trigger  INT UNSIGNED      NOT NULL DEFAULT 0,
              auto_clear       TINYINT UNSIGNED  NOT NULL DEFAULT 0,
              play_11          SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_11_trigger  INT UNSIGNED      NOT NULL DEFAULT 0,
              play_12          SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_12_trigger  INT UNSIGNED      NOT NULL DEFAULT 0,
              play_13          SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_13_trigger  INT UNSIGNED      NOT NULL DEFAULT 0,
              play_14          SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_14_trigger  INT UNSIGNED      NOT NULL DEFAULT 0,
              play_15          SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_15_trigger  INT UNSIGNED      NOT NULL DEFAULT 0,
              play_16          SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_16_trigger  INT UNSIGNED      NOT NULL DEFAULT 0,
              play_17          SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_17_trigger  INT UNSIGNED      NOT NULL DEFAULT 0,
              play_18          SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_18_trigger  INT UNSIGNED      NOT NULL DEFAULT 0,
              play_19          SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_19_trigger  INT UNSIGNED      NOT NULL DEFAULT 0,
              play_20          SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              play_20_trigger  INT UNSIGNED      NOT NULL DEFAULT 0,
              urgentQuestIndex SMALLINT          NOT NULL DEFAULT -1,
              PRIMARY KEY (charac_no)
            )
            """,
    };
}
