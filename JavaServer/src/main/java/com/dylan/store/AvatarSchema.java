package com.dylan.store;

final class AvatarSchema {

    private AvatarSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS user_items (
              ui_id            INTEGER          NOT NULL PRIMARY KEY AUTOINCREMENT,
              charac_no        INT              NOT NULL DEFAULT 0,
              slot             INT              NOT NULL DEFAULT 0,
              it_id            INT              NOT NULL DEFAULT 0,
              expire_date      DATETIME         NOT NULL DEFAULT '1970-01-01 00:00:00',
              obtain_from      TINYINT          DEFAULT NULL,
              reg_date         DATETIME         NOT NULL DEFAULT '1970-01-01 00:00:00',
              ipg_agency_no    VARCHAR(32)      NOT NULL DEFAULT '',
              ability_no       TINYINT          NOT NULL DEFAULT 0,
              stat             TINYINT UNSIGNED NOT NULL DEFAULT 0,
              clear_avatar_id  INT              NOT NULL DEFAULT 0,
              jewel_socket     BLOB             NOT NULL,
              item_lock_key    TINYINT UNSIGNED NOT NULL DEFAULT 0,
              to_ipg_agency_no VARCHAR(32)      NOT NULL DEFAULT '',
              m_time           DATETIME         NOT NULL DEFAULT '1970-01-01 00:00:00',
              hidden_option    SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              emblem_endurance SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              color1           INT              DEFAULT 0,
              color2           INT              DEFAULT 0,
              trade_restrict   INT              DEFAULT 0
            )
            """,
    };

    static final String[] INDEXES = {
            "CREATE INDEX IF NOT EXISTS user_items_idxm_id ON user_items (charac_no)",
    };
}
