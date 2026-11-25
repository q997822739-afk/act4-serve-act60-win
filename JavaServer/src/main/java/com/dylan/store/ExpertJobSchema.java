package com.dylan.store;

final class ExpertJobSchema {

    private ExpertJobSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS charac_expert_job (
              charac_no             INT               NOT NULL DEFAULT 0,
              expert_job_giveup_cnt SMALLINT UNSIGNED NOT NULL DEFAULT 0,
              expert_job_info       INT               NOT NULL DEFAULT 0,
              expert_job_info_ex    INT               NOT NULL DEFAULT 0,
              recipe                TINYBLOB          NOT NULL,
              PRIMARY KEY (charac_no)
            )
            """,
    };
}
