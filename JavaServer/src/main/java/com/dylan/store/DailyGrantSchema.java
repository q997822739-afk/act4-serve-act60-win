package com.dylan.store;

final class DailyGrantSchema {

    private DailyGrantSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS charac_daily_grant (
              charac_no  INT    NOT NULL DEFAULT 0,
              item_id    INT    NOT NULL DEFAULT 0,
              granted_at BIGINT NOT NULL DEFAULT 0,
              PRIMARY KEY (charac_no, item_id)
            )
            """,
    };
}
