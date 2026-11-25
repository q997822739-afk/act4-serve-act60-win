package com.dylan.store;

final class DeathTowerSchema {

    private DeathTowerSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS charac_tower_record (
              charac_no     INT          NOT NULL DEFAULT 0,
              tower_index   INT          NOT NULL DEFAULT 0,
              member_info_1 VARCHAR(255) NOT NULL DEFAULT '',
              stage_1       INT UNSIGNED NOT NULL DEFAULT 0,
              play_time_1   INT UNSIGNED NOT NULL DEFAULT 0,
              occ_time_1    DATETIME     NULL,
              member_info_2 VARCHAR(255) NOT NULL DEFAULT '',
              stage_2       INT UNSIGNED NOT NULL DEFAULT 0,
              play_time_2   INT UNSIGNED NOT NULL DEFAULT 0,
              occ_time_2    DATETIME     NULL,
              member_info_3 VARCHAR(255) NOT NULL DEFAULT '',
              stage_3       INT UNSIGNED NOT NULL DEFAULT 0,
              play_time_3   INT UNSIGNED NOT NULL DEFAULT 0,
              occ_time_3    DATETIME     NULL,
              member_info_4 VARCHAR(255) NOT NULL DEFAULT '',
              stage_4       INT UNSIGNED NOT NULL DEFAULT 0,
              play_time_4   INT UNSIGNED NOT NULL DEFAULT 0,
              occ_time_4    DATETIME     NULL,
              PRIMARY KEY (charac_no, tower_index)
            )
            """,
    };
}
