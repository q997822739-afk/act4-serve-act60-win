package com.dylan.store;

final class AccountSchema {

    private AccountSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS member_info (
              m_id            INTEGER          NOT NULL PRIMARY KEY AUTOINCREMENT,
              user_id         VARCHAR(30)      DEFAULT NULL,
              user_name       VARCHAR(10)      NOT NULL DEFAULT '',
              first_ssn       VARCHAR(6)       NOT NULL DEFAULT '',
              second_ssn      VARCHAR(7)       NOT NULL DEFAULT '',
              passwd          VARCHAR(32)      NOT NULL DEFAULT '',
              mobile_no       VARCHAR(15)      NOT NULL DEFAULT '',
              reg_date        INT              NOT NULL DEFAULT 0,
              email           VARCHAR(50)      NOT NULL DEFAULT '',
              q_no            TINYINT          NOT NULL DEFAULT 0,
              q_answer        VARCHAR(30)      NOT NULL DEFAULT '',
              updt_date       TIMESTAMP        NOT NULL DEFAULT (datetime('now','localtime')),
              state           TINYINT          NOT NULL DEFAULT 1,
              nickname        VARCHAR(16)      NOT NULL DEFAULT '',
              email_yn        CHAR(1)          NOT NULL DEFAULT 'y',
              ssn_check       TINYINT UNSIGNED NOT NULL DEFAULT 0,
              slot            INT UNSIGNED     NOT NULL DEFAULT 16,
              last_play_time  DATETIME         NOT NULL DEFAULT '1970-01-01 00:00:00',
              hangame_flag    TINYINT          NOT NULL DEFAULT 0,
              hanmon_flag     TINYINT          NOT NULL DEFAULT 0,
              m_type          TINYINT          NOT NULL DEFAULT 0,
              cera            INT UNSIGNED     NOT NULL DEFAULT 0,
              black_diamond_end DATETIME       NOT NULL DEFAULT '1970-01-01 00:00:00'
            )
            """,

            """
            CREATE TABLE IF NOT EXISTS member_login (
              m_id               INT              NOT NULL DEFAULT 0,
              login_time         INT UNSIGNED     NOT NULL DEFAULT 0,
              expire_time        INT UNSIGNED     NOT NULL DEFAULT 0,
              last_play_time     INT UNSIGNED     NOT NULL DEFAULT 0,
              total_account_fail INT UNSIGNED     NOT NULL DEFAULT 0,
              account_fail       TINYINT          NOT NULL DEFAULT 0,
              report_cnt         INT              NOT NULL DEFAULT 0,
              reliable_flag      TINYINT          NOT NULL DEFAULT 0,
              trade_gold_daily   INT UNSIGNED     NOT NULL DEFAULT 0,
              last_gift_time     INT UNSIGNED     NOT NULL DEFAULT 0,
              gift_cnt           SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              login_ip           VARCHAR(15)      NOT NULL DEFAULT '',
              security_flag      TINYINT          NOT NULL DEFAULT 0,
              power_side         TINYINT          NOT NULL DEFAULT 0,
              dungeon_gain_gold  INT UNSIGNED     NOT NULL DEFAULT 0,
              school_id          INT              NOT NULL DEFAULT 0,
              rating             FLOAT            NOT NULL DEFAULT 0,
              cleanpad_point     INT UNSIGNED     NOT NULL DEFAULT 0,
              tutorial_skipable  CHAR(1)          NOT NULL DEFAULT '0',
              event_charac_flag  TINYINT UNSIGNED NOT NULL DEFAULT 0,
              PRIMARY KEY (m_id)
            )
            """,
    };

    static final String[] INDEXES = {
            "CREATE INDEX IF NOT EXISTS member_info_idx_reg_date ON member_info (reg_date)",
            "CREATE INDEX IF NOT EXISTS member_info_idx_ssn ON member_info (first_ssn, second_ssn)",
            "CREATE INDEX IF NOT EXISTS member_info_idx_nick ON member_info (nickname)",
            "CREATE INDEX IF NOT EXISTS member_info_idx_userid ON member_info (user_id)",
            "CREATE INDEX IF NOT EXISTS member_info_idx_user_name ON member_info (user_name)",
    };

}
