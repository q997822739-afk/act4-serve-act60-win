package com.dylan.store;

/** 师徒。一个徒弟只有一个师傅；师傅可以有多个徒弟。 */
final class MentorSchema {

    private MentorSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS mentor_link (
              apprentice_no INT NOT NULL PRIMARY KEY,
              master_no     INT NOT NULL
            )
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_mentor_master ON mentor_link (master_no)
            """,
    };
}
