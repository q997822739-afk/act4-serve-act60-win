package com.dylan.store;

final class SkillSchema {

    private SkillSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS skill (
              charac_no            INT          NOT NULL DEFAULT 0,
              remain_sp            INT UNSIGNED NOT NULL DEFAULT 0,
              skill_slot           BLOB         NOT NULL,
              request_sp           BLOB         NOT NULL,
              sp_garbage           INT UNSIGNED NOT NULL DEFAULT 0,
              used_sp              INT UNSIGNED NOT NULL DEFAULT 0,
              remain_sp_2nd        INT UNSIGNED NOT NULL DEFAULT 0,
              skill_slot_2nd       BLOB         NOT NULL,
              request_sp_2nd       BLOB         NOT NULL,
              skill_slot_lethe     BLOB         NOT NULL,
              lethe_flag           TINYINT      NOT NULL DEFAULT 0,
              skill_slot_lethe_2nd BLOB         NOT NULL,
              lethe_flag_2nd       TINYINT      NOT NULL DEFAULT 0,
              remain_sfp_2nd       INT UNSIGNED NOT NULL DEFAULT 0,
              remain_sfp_1st       INT UNSIGNED NOT NULL DEFAULT 0,
              skill_command        BLOB         NOT NULL,
              script_version       TINYINT      NOT NULL DEFAULT 0,
              PRIMARY KEY (charac_no)
            )
            """,
    };
}
