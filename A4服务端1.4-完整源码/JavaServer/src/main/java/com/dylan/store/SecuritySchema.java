package com.dylan.store;

final class SecuritySchema {

    private SecuritySchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS hack_log (
              seq        INTEGER           NOT NULL PRIMARY KEY AUTOINCREMENT,
              m_id       INT               NOT NULL DEFAULT 0,
              charac_no  INT               NOT NULL DEFAULT 0,
              hack_type  SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              parameter1 INT               NOT NULL DEFAULT 0,
              parameter2 INT               NOT NULL DEFAULT 0,
              cnt        INT UNSIGNED      NOT NULL DEFAULT 0,
              detail     VARCHAR(255)      NOT NULL DEFAULT '',
              client_ip  VARCHAR(45)       NOT NULL DEFAULT '',
              occ_time   DATETIME          NOT NULL DEFAULT (datetime('now','localtime'))
            )
            """,

            """
            CREATE TABLE IF NOT EXISTS item_history (
              seq            INTEGER           NOT NULL PRIMARY KEY AUTOINCREMENT,
              occ_time       DATETIME(3)       NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now','localtime')),
              charac_no      INT               NOT NULL DEFAULT 0,
              charac_name    VARCHAR(64)       NOT NULL DEFAULT '',
              reason         SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              inven_type     TINYINT UNSIGNED  NOT NULL DEFAULT 0,
              slot_no        SMALLINT          NOT NULL DEFAULT -1,
              item_id        INT UNSIGNED      NOT NULL DEFAULT 0,
              item_count     INT               NOT NULL DEFAULT 0,
              add_info       INT UNSIGNED      NOT NULL DEFAULT 0,
              endurance      SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              upgrade        TINYINT UNSIGNED  NOT NULL DEFAULT 0,
              enchant        INT UNSIGNED      NOT NULL DEFAULT 0,
              amplify_option TINYINT UNSIGNED  NOT NULL DEFAULT 0,
              amplify_value  SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              seal_cnt       TINYINT UNSIGNED  NOT NULL DEFAULT 0,
              random_option  VARCHAR(32)       NOT NULL DEFAULT '',
              guid           VARCHAR(32)       NOT NULL DEFAULT '',
              item_x_pos     SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              item_y_pos     SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              gold_after     BIGINT UNSIGNED   NOT NULL DEFAULT 0,
              coin_col       TINYINT UNSIGNED  NOT NULL DEFAULT 0,
              coin_after     INT UNSIGNED      NOT NULL DEFAULT 0
            )
            """,

    };

    static final String[] INDEXES = {
            "CREATE INDEX IF NOT EXISTS hack_log_idx_m_id ON hack_log (m_id, occ_time)",
            "CREATE INDEX IF NOT EXISTS hack_log_idx_charac ON hack_log (charac_no, occ_time)",
            "CREATE INDEX IF NOT EXISTS hack_log_idx_type ON hack_log (hack_type, occ_time)",
            "CREATE INDEX IF NOT EXISTS hack_log_idx_ip ON hack_log (client_ip, occ_time)",
            "CREATE INDEX IF NOT EXISTS item_history_idx_charac ON item_history (charac_no, occ_time)",
            "CREATE INDEX IF NOT EXISTS item_history_idx_item ON item_history (item_id, occ_time)",
            "CREATE INDEX IF NOT EXISTS item_history_idx_reason ON item_history (reason, occ_time)",
    };
}
