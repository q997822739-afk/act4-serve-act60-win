package com.dylan.store;

final class PremiumSchema {

    private PremiumSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS member_premium (
              event_id      INT              NOT NULL DEFAULT 0,
              pre_type      TINYINT UNSIGNED NOT NULL DEFAULT 0,
              server_id     TINYINT UNSIGNED NOT NULL DEFAULT 0,
              m_id          INT              NOT NULL DEFAULT 0,
              service_start DATETIME         NOT NULL DEFAULT '1970-01-01 00:00:00',
              service_end   DATETIME         NOT NULL DEFAULT '1970-01-01 00:00:00',
              PRIMARY KEY (event_id, pre_type, server_id, m_id, service_start)
            )
            """,
    };

    static final String[] INDEXES = {
            "CREATE INDEX IF NOT EXISTS member_premium_idx_m_id ON member_premium (m_id, service_end)",
    };

}
