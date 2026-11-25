package com.dylan.store;

/** 公会（A4）。列对照官方 DF60ACT4 库 guilds / guild_members（07_协议检修/社交/公会.md）。 */
final class GuildSchema {

    private GuildSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS guild (
              guild_id      INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
              name          VARCHAR(32)  NOT NULL UNIQUE,
              domain        VARCHAR(32)  NOT NULL UNIQUE,
              master_charac INT          NOT NULL DEFAULT 0,
              level         INT          NOT NULL DEFAULT 1,
              exp           INT          NOT NULL DEFAULT 0,
              notice        VARCHAR(256) NOT NULL DEFAULT ''
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS guild_member (
              charac_no INT NOT NULL PRIMARY KEY,
              guild_id  INT NOT NULL,
              grade     INT NOT NULL DEFAULT 0
            )
            """,
    };
}
