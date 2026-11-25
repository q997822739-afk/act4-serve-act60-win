package com.dylan.store;

final class MailSchema {

    private MailSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS postal (
              postal_id          INTEGER          NOT NULL PRIMARY KEY AUTOINCREMENT,
              occ_time           DATETIME         NOT NULL DEFAULT '1970-01-01 00:00:00',
              send_charac_no     INT              NOT NULL DEFAULT 0,
              send_charac_name   VARCHAR(20)      NOT NULL DEFAULT '',
              receive_charac_no  INT              NOT NULL DEFAULT 0,
              item_id            INT UNSIGNED     NOT NULL DEFAULT 0,
              add_info           INT              NOT NULL DEFAULT 0,
              endurance          SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              upgrade            TINYINT UNSIGNED NOT NULL DEFAULT 0,
              amplify_option     TINYINT UNSIGNED NOT NULL DEFAULT 0,
              amplify_value      MEDIUMINT UNSIGNED NOT NULL DEFAULT 0,
              gold               INT UNSIGNED     NOT NULL DEFAULT 0,
              receive_time       DATETIME         NOT NULL DEFAULT '1970-01-01 00:00:00',
              delete_flag        TINYINT UNSIGNED NOT NULL DEFAULT 0,
              avata_flag         TINYINT          NOT NULL DEFAULT 0,
              unlimit_flag       TINYINT          NOT NULL DEFAULT 0,
              seal_flag          TINYINT          NOT NULL DEFAULT 0,
              creature_flag      TINYINT          NOT NULL DEFAULT 0,
              postal             INT              NOT NULL DEFAULT 0,
              letter_id          INT              NOT NULL DEFAULT 0,
              extend_info        INT              NOT NULL DEFAULT 0,
              ipg_db_id          TINYINT          NOT NULL DEFAULT 0,
              ipg_transaction_id INT              NOT NULL DEFAULT 0,
              ipg_nexon_id       VARCHAR(32)      NOT NULL DEFAULT '',
              auction_id         BIGINT UNSIGNED  NOT NULL DEFAULT 0,
              random_option      VARBINARY(14)    NOT NULL DEFAULT '',
              seperate_upgrade   TINYINT UNSIGNED NOT NULL DEFAULT 0,
              type               TINYINT UNSIGNED NOT NULL DEFAULT 0,
              item_guid          VARBINARY(10)    NOT NULL DEFAULT ''
            )
            """,

            """
            CREATE TABLE IF NOT EXISTS letter (
              letter_id        INTEGER     NOT NULL PRIMARY KEY AUTOINCREMENT,
              charac_no        INT         NOT NULL DEFAULT 0,
              send_charac_no   INT         NOT NULL DEFAULT 0,
              send_charac_name VARCHAR(20) NOT NULL DEFAULT '',
              letter_text      VARCHAR(255) NOT NULL DEFAULT '',
              reg_date         DATETIME    NOT NULL DEFAULT '1970-01-01 00:00:00',
              stat             TINYINT     NOT NULL DEFAULT 0
            )
            """,
    };

    static final String[] INDEXES = {
            "CREATE INDEX IF NOT EXISTS postal_idx_send_charac_no ON postal (send_charac_no)",
            "CREATE INDEX IF NOT EXISTS postal_idx_receive_charac_no ON postal (receive_charac_no)",
            "CREATE INDEX IF NOT EXISTS postal_idx_occ_time ON postal (occ_time)",
            "CREATE INDEX IF NOT EXISTS letter_idx_charac_no ON letter (charac_no)",
            "CREATE INDEX IF NOT EXISTS letter_idx_reg_date ON letter (reg_date)",
    };
}
