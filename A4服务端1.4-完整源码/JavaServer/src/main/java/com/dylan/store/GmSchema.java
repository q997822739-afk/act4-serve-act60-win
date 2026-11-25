package com.dylan.store;

final class GmSchema {

    private GmSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS gm_manifest (
              m_id  INT              NOT NULL DEFAULT 0,
              level TINYINT UNSIGNED NOT NULL DEFAULT 0,
              PRIMARY KEY (m_id)
            )
            """,

            """
            CREATE TABLE IF NOT EXISTS gm_command_log (
              seq       INTEGER          NOT NULL PRIMARY KEY AUTOINCREMENT,
              m_id      INT              NOT NULL DEFAULT 0,
              gm_level  TINYINT UNSIGNED NOT NULL DEFAULT 0,
              charac_no INT              NOT NULL DEFAULT 0,
              charac_name VARCHAR(64)    NOT NULL DEFAULT '',
              command   VARCHAR(64)      NOT NULL DEFAULT '',
              detail    VARCHAR(255)     NOT NULL DEFAULT '',
              accepted  TINYINT UNSIGNED NOT NULL DEFAULT 0,
              result    VARCHAR(255)     NOT NULL DEFAULT '',
              reg_date  DATETIME         NOT NULL DEFAULT (datetime('now','localtime'))
            )
            """,

            // 服务端"角色正在内存里"的存在表（2026-10-10）。
            //
            // 为什么要有这张表：网页 GM 是**另一个进程**，它想直写某个角色的背包 / 技能 /
            // 数值，只有"服务端没有把这些整块数据拿在手里"时才安全 —— 否则服务端存档时整块
            // 回写会盖掉直写。但服务端原来**不向外界暴露任何"角色在不在游戏里"的信号**
            // （全部会话状态都在堆里，落盘的只有业务数据），外部只看得见端口在不在听。
            // 于是 GM 只能让用户手动点「已小退」。
            //
            // 现在服务端把这件事**主动记下来**：角色一进游戏（selectCharacter 加载完）
            // 写一行；小退（CMD 7）或断线时删掉这一行。语义很直白 ——
            //   **有行 = 服务端正持有这个角色（在游戏中，GM 不许直写）**；
            //   **没行 = 选择界面 / 没登录（GM 可以直写）**。
            // 服务端启动时先清空整表（旧进程可能是崩溃退出的，残留行会误判）；
            // 这张表对玩法零影响，纯粹是给外部工具读的。
            """
            CREATE TABLE IF NOT EXISTS server_presence (
              charac_no   INT             NOT NULL PRIMARY KEY,
              charac_name VARCHAR(20)     NOT NULL DEFAULT '',
              m_id        INT             NOT NULL DEFAULT 0,
              since       DATETIME        NOT NULL DEFAULT (datetime('now','localtime'))
            )
            """,
    };

    static final String[] INDEXES = {
            "CREATE INDEX IF NOT EXISTS gm_command_log_idx_m_id ON gm_command_log (m_id, reg_date)",
            "CREATE INDEX IF NOT EXISTS gm_command_log_idx_charac ON gm_command_log (charac_no, reg_date)",
    };
}
