package com.dylan.store;

final class ElixirSchema {

    private ElixirSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS charac_elixir (
              charac_no INT          NOT NULL DEFAULT 0,
              item_id   INT          NOT NULL DEFAULT 0,
              used      INT UNSIGNED NOT NULL DEFAULT 0,
              PRIMARY KEY (charac_no, item_id)
            )
            """,
    };
}
