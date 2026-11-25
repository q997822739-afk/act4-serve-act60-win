package com.dylan.store;

final class PvpSchema {

    private PvpSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS pvp_result (
              charac_no                 INT              NOT NULL DEFAULT 0,
              win                       INT              NOT NULL DEFAULT 0,
              lose                      INT              NOT NULL DEFAULT 0,
              pvp_point                 INT              NOT NULL DEFAULT 0,
              pvp_grade                 INT              NOT NULL DEFAULT 0,
              pvp_grade_ext             TINYINT UNSIGNED NOT NULL DEFAULT 0,
              avg_kill_count            INT              NOT NULL DEFAULT 0,
              avg_buf_count             INT              NOT NULL DEFAULT 0,
              avg_debuf_count           INT              NOT NULL DEFAULT 0,
              avg_heal_count            INT              NOT NULL DEFAULT 0,
              avg_counter_count         INT              NOT NULL DEFAULT 0,
              avg_back_atk_count        INT              NOT NULL DEFAULT 0,
              avg_union_hit_count       INT              NOT NULL DEFAULT 0,
              avg_overkill_count        INT              NOT NULL DEFAULT 0,
              avg_aerial_count          INT              NOT NULL DEFAULT 0,
              avg_combo_count           INT              NOT NULL DEFAULT 0,
              avg_attacked_count        INT              NOT NULL DEFAULT 0,
              avg_deal_damage           INT              NOT NULL DEFAULT 0,
              avg_technic               INT              NOT NULL DEFAULT 0,
              avg_style                 INT              NOT NULL DEFAULT 0,
              avg_hit_penalty           INT              NOT NULL DEFAULT 0,
              pvp_count                 INT              NOT NULL DEFAULT 0,
              win_point                 INT              NOT NULL DEFAULT 0,
              last_play_time            DATETIME         NOT NULL DEFAULT '1970-01-01 00:00:00',
              play_count                INT UNSIGNED     NOT NULL DEFAULT 0,
              play_time                 INT UNSIGNED     NOT NULL DEFAULT 0,
              pvp_grade_ext_update_time DATETIME         NOT NULL DEFAULT '1970-01-01 00:00:00',
              PRIMARY KEY (charac_no)
            )
            """,
    };
}
