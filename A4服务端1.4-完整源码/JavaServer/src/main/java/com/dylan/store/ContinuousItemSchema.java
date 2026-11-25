package com.dylan.store;

final class ContinuousItemSchema {

    private ContinuousItemSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS charac_item_stat (
              charac_no     INT NOT NULL DEFAULT 0,
              cooltime_item VARBINARY(404) NULL,
              effect_item   VARBINARY(404) NULL,
              check_flag    BLOB NULL,
              PRIMARY KEY (charac_no)
            )
            """,
    };
}
