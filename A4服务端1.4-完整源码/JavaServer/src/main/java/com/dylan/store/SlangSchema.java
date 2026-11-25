package com.dylan.store;

final class SlangSchema {

    private SlangSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS slang_list (
              slang VARCHAR(153) COLLATE BINARY NOT NULL DEFAULT '',
              PRIMARY KEY (slang)
            )
            """,

            """
            CREATE TABLE IF NOT EXISTS slang_list_name (
              slang VARCHAR(153) COLLATE BINARY NOT NULL DEFAULT '',
              PRIMARY KEY (slang)
            )
            """,
    };
}
