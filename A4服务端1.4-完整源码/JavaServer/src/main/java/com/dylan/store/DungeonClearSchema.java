package com.dylan.store;

final class DungeonClearSchema {

    private DungeonClearSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS member_dungeon (
              m_id    INT UNSIGNED NOT NULL DEFAULT 0,
              dungeon TEXT         NOT NULL,
              PRIMARY KEY (m_id)
            )
            """,
    };
}
