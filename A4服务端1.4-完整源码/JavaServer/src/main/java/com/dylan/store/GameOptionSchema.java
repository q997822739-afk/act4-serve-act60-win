package com.dylan.store;

final class GameOptionSchema {

    private GameOptionSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS member_game_option (
              m_id              INT   NOT NULL DEFAULT 0,
              option_1          BLOB  NOT NULL,
              option_2          BLOB  NOT NULL,
              option_3          BLOB  NOT NULL,
              shortcut_emoticon BLOB  NOT NULL,
              PRIMARY KEY (m_id)
            )
            """,
    };
}
