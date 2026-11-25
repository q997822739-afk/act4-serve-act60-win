package com.dylan.store;

final class InventorySchema {

    private InventorySchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS inventory (
              charac_no          INT          NOT NULL DEFAULT 0,
              money              INT UNSIGNED NOT NULL DEFAULT 0,
              coin               INT UNSIGNED NOT NULL DEFAULT 0,
              inventory          BLOB         NOT NULL,
              equipslot          BLOB         NOT NULL,
              pay_coin           INT UNSIGNED NOT NULL DEFAULT 0,
              event_coin         INT UNSIGNED NOT NULL DEFAULT 0,
              creature           BLOB         NOT NULL,
              creature_flag      TINYINT      NOT NULL DEFAULT 0,
              katagaki           BLOB         NOT NULL,
              inventory_capacity INT UNSIGNED NOT NULL DEFAULT 0,
              avatar_coin        INT UNSIGNED NOT NULL DEFAULT 0,
              PRIMARY KEY (charac_no)
            )
            """,

            """
            CREATE TABLE IF NOT EXISTS charac_inven_expand (
              charac_no         INT          NOT NULL DEFAULT 0,
              cargo             BLOB         NOT NULL,
              cargo_capacity    INT UNSIGNED NOT NULL DEFAULT 0,
              jewel             BLOB         NOT NULL,
              current_equipslot CHAR(1)      NOT NULL DEFAULT '',
              switch_equipslot  BLOB         NOT NULL,
              expand_equipslot  BLOB         NOT NULL,
              redeem_info       BLOB         NOT NULL,
              PRIMARY KEY (charac_no)
            )
            """,
    };
}
