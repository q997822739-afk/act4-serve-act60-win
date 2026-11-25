package com.dylan.store;

final class CreatureSchema {

    private CreatureSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS creature_items (
              ui_id         INTEGER          NOT NULL PRIMARY KEY AUTOINCREMENT,
              charac_no     INT              NOT NULL DEFAULT 0,
              slot          TINYINT UNSIGNED NOT NULL DEFAULT 0,
              it_id         INT UNSIGNED     NOT NULL DEFAULT 0,
              reg_date      DATETIME         NOT NULL DEFAULT '1970-01-01 00:00:00',
              name          VARCHAR(12)      NOT NULL DEFAULT '',
              stomach       INT UNSIGNED     NOT NULL DEFAULT 0,
              exp           INT UNSIGNED     NOT NULL DEFAULT 0,
              endurance     TINYINT UNSIGNED NOT NULL DEFAULT 0,
              creature_type TINYINT          NOT NULL DEFAULT 0,
              no_charge     TINYINT          NOT NULL DEFAULT 0,
              stat          TINYINT          NOT NULL DEFAULT 0,
              item_lock_key TINYINT UNSIGNED NOT NULL DEFAULT 0,
              ipg_agency_no VARCHAR(32)      NOT NULL DEFAULT '',
              expire_date   DATETIME         NOT NULL DEFAULT '9999-12-31 23:59:59',
              delete_date   DATETIME         NOT NULL DEFAULT '9999-12-31 23:59:59'
            )
            """,
    };

    static final String[] INDEXES = {
            "CREATE INDEX IF NOT EXISTS creature_items_idx_charac_no ON creature_items (charac_no)",
    };
}
