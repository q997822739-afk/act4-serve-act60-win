package com.dylan.store;

final class ItemLockSchema {

    private ItemLockSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS charac_item_lock_info (
              charac_no      INT  NOT NULL DEFAULT 0,
              item_lock_info BLOB NOT NULL,
              PRIMARY KEY (charac_no)
            )
            """,
    };
}
