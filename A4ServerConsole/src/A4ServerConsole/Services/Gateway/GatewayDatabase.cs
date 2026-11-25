using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using Microsoft.Data.Sqlite;

namespace A4ServerConsole.Services.Gateway
{
    public sealed class AccountRecord
    {
        public bool Found { get; set; }
        public long MId { get; set; }
        public string UserId { get; set; } = "";
        public string Passwd { get; set; } = "";
        public string Nickname { get; set; } = "";
        public int State { get; set; } = 1;
        public long Slot { get; set; } = 16;
        public string LastPlayTime { get; set; } = "";
    }

    public sealed class NoticeRecord
    {
        public long Id { get; set; }
        public string Title { get; set; } = "";
        public string Content { get; set; } = "";
        public string State { get; set; } = "";
        public int Sort { get; set; }
        public bool Enabled { get; set; } = true;
    }

    public sealed class CdkeyRecord
    {
        public string Code { get; set; } = "";
        public string Kind { get; set; } = "register";
        public string UsedBy { get; set; } = "";
        public long UsedAt { get; set; }
        public long CreatedAt { get; set; }
    }

    /// <summary>
    /// 完整移植自原 Linux C++ gateway/src/db.cpp。
    /// 直接读写 A5 服务端 s1a5.db（member_info / member_login / gw_login_token）与 gateway.db。
    /// </summary>
    public sealed class GatewayDatabase : IDisposable
    {
        private readonly object _a5Lock = new object();
        private readonly object _gwLock = new object();
        private SqliteConnection? _a5;
        private SqliteConnection? _gw;

        public string A5DbPath { get; private set; } = "";
        public string GatewayDbPath { get; private set; } = "";

        public bool Open(string a5DbPath, string gatewayDbPath, out string error)
        {
            error = "";
            A5DbPath = a5DbPath;
            GatewayDbPath = gatewayDbPath;

            if (!File.Exists(a5DbPath))
            {
                error = "找不到游戏数据库文件：" + a5DbPath + "（请先确认路径或至少启动一次服务端建库）";
                return false;
            }

            try
            {
                string? gwDir = Path.GetDirectoryName(gatewayDbPath);
                if (!string.IsNullOrEmpty(gwDir) && !Directory.Exists(gwDir))
                {
                    Directory.CreateDirectory(gwDir);
                }

                var a5Builder = new SqliteConnectionStringBuilder
                {
                    DataSource = a5DbPath,
                    Mode = SqliteOpenMode.ReadWrite,
                    Pooling = false
                };
                _a5 = new SqliteConnection(a5Builder.ToString());
                _a5.Open();
                Exec(_a5, "PRAGMA journal_mode=WAL;");
                Exec(_a5, "PRAGMA busy_timeout=5000;");
                Exec(_a5,
                    "CREATE TABLE IF NOT EXISTS gw_login_token (" +
                    " token      TEXT NOT NULL PRIMARY KEY," +
                    " user_id    TEXT NOT NULL," +
                    " m_id       INTEGER NOT NULL DEFAULT 0," +
                    " expire_at  INTEGER NOT NULL," +
                    " client_ip  TEXT NOT NULL DEFAULT ''," +
                    " created_at INTEGER NOT NULL);");
                Exec(_a5, "CREATE INDEX IF NOT EXISTS idx_gw_token_user ON gw_login_token(user_id);");

                var gwBuilder = new SqliteConnectionStringBuilder
                {
                    DataSource = gatewayDbPath,
                    Mode = SqliteOpenMode.ReadWriteCreate,
                    Pooling = false
                };
                _gw = new SqliteConnection(gwBuilder.ToString());
                _gw.Open();
                Exec(_gw, "PRAGMA journal_mode=WAL;");
                Exec(_gw, "PRAGMA busy_timeout=5000;");
                Exec(_gw,
                    "CREATE TABLE IF NOT EXISTS config (" +
                    " key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL DEFAULT '');");
                Exec(_gw,
                    "CREATE TABLE IF NOT EXISTS notice (" +
                    " id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT," +
                    " title TEXT NOT NULL DEFAULT ''," +
                    " content TEXT NOT NULL DEFAULT ''," +
                    " state TEXT NOT NULL DEFAULT ''," +
                    " sort INTEGER NOT NULL DEFAULT 0," +
                    " enabled INTEGER NOT NULL DEFAULT 1);");
                Exec(_gw,
                    "CREATE TABLE IF NOT EXISTS cdkey (" +
                    " code TEXT NOT NULL PRIMARY KEY," +
                    " kind TEXT NOT NULL DEFAULT 'register'," +
                    " used_by TEXT NOT NULL DEFAULT ''," +
                    " used_at INTEGER NOT NULL DEFAULT 0," +
                    " created_at INTEGER NOT NULL DEFAULT 0);");
                Exec(_gw,
                    "CREATE TABLE IF NOT EXISTS login_log (" +
                    " id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT," +
                    " user_id TEXT NOT NULL DEFAULT ''," +
                    " ip TEXT NOT NULL DEFAULT ''," +
                    " ok INTEGER NOT NULL DEFAULT 0," +
                    " note TEXT NOT NULL DEFAULT ''," +
                    " at INTEGER NOT NULL DEFAULT 0);");
                Exec(_gw,
                    "INSERT OR IGNORE INTO config(key, value) VALUES " +
                    "('cdk_required', '0'), ('notice', ''), ('news_text', '');");

                return true;
            }
            catch (Exception ex)
            {
                error = "打开 SQLite 数据库失败：" + ex.Message;
                for (Exception? cause = ex.InnerException; cause != null; cause = cause.InnerException)
                    error += "\n" + cause.GetType().Name + "：" + cause.Message;
                Dispose();
                return false;
            }
        }

        private static long NowSeconds() => DateTimeOffset.UtcNow.ToUnixTimeSeconds();

        private static void Exec(SqliteConnection conn, string sql)
        {
            using var cmd = conn.CreateCommand();
            cmd.CommandText = sql;
            cmd.ExecuteNonQuery();
        }

        public AccountRecord FindAccount(string userId)
        {
            var result = new AccountRecord();
            lock (_a5Lock)
            {
                if (_a5 == null) return result;
                using var cmd = _a5.CreateCommand();
                cmd.CommandText = "SELECT m_id, user_id, passwd, nickname, state, slot FROM member_info WHERE user_id = $u";
                cmd.Parameters.AddWithValue("$u", userId);
                using var reader = cmd.ExecuteReader();
                if (reader.Read())
                {
                    result.Found = true;
                    result.MId = reader.GetInt64(0);
                    result.UserId = reader.IsDBNull(1) ? "" : reader.GetString(1);
                    result.Passwd = reader.IsDBNull(2) ? "" : reader.GetString(2);
                    result.Nickname = reader.IsDBNull(3) ? "" : reader.GetString(3);
                    result.State = reader.IsDBNull(4) ? 1 : reader.GetInt32(4);
                    result.Slot = reader.IsDBNull(5) ? 16 : reader.GetInt64(5);
                }
            }
            return result;
        }

        public List<AccountRecord> ListAccounts(int limit = 200)
        {
            var list = new List<AccountRecord>();
            lock (_a5Lock)
            {
                if (_a5 == null) return list;
                using var cmd = _a5.CreateCommand();
                cmd.CommandText =
                    "SELECT m_id, user_id, nickname, state, slot, COALESCE(last_play_time, '') " +
                    "FROM member_info ORDER BY m_id DESC LIMIT $lim";
                cmd.Parameters.AddWithValue("$lim", limit);
                using var reader = cmd.ExecuteReader();
                while (reader.Read())
                {
                    list.Add(new AccountRecord
                    {
                        Found = true,
                        MId = reader.GetInt64(0),
                        UserId = reader.IsDBNull(1) ? "" : reader.GetString(1),
                        Nickname = reader.IsDBNull(2) ? "" : reader.GetString(2),
                        State = reader.IsDBNull(3) ? 1 : reader.GetInt32(3),
                        Slot = reader.IsDBNull(4) ? 16 : reader.GetInt64(4),
                        LastPlayTime = reader.IsDBNull(5) ? "" : reader.GetString(5)
                    });
                }
            }
            return list;
        }

        public long CreateAccount(string userId, string passwd, out string error)
        {
            error = "";
            lock (_a5Lock)
            {
                if (_a5 == null)
                {
                    error = "数据库未打开";
                    return 0;
                }
                try
                {
                    using (var cmd = _a5.CreateCommand())
                    {
                        cmd.CommandText =
                            "INSERT INTO member_info (user_id, user_name, passwd, nickname, slot, reg_date) " +
                            "VALUES ($u, $un, $p, $n, 16, $t)";
                        cmd.Parameters.AddWithValue("$u", userId);
                        cmd.Parameters.AddWithValue("$un", userId);
                        cmd.Parameters.AddWithValue("$p", passwd);
                        cmd.Parameters.AddWithValue("$n", userId);
                        cmd.Parameters.AddWithValue("$t", NowSeconds());
                        cmd.ExecuteNonQuery();
                    }

                    long mId;
                    using (var idCmd = _a5.CreateCommand())
                    {
                        idCmd.CommandText = "SELECT last_insert_rowid()";
                        mId = Convert.ToInt64(idCmd.ExecuteScalar());
                    }

                    try
                    {
                        using var loginCmd = _a5.CreateCommand();
                        loginCmd.CommandText = "INSERT INTO member_login (m_id, login_ip) VALUES ($id, '')";
                        loginCmd.Parameters.AddWithValue("$id", mId);
                        loginCmd.ExecuteNonQuery();
                    }
                    catch
                    {
                        // 某些定制表结构可能已存在触发器或不同列，忽略辅助插入错误
                    }

                    return mId;
                }
                catch (Exception ex)
                {
                    error = ex.Message;
                    return 0;
                }
            }
        }

        public bool UpdatePassword(string userId, string passwd, out string error)
        {
            error = "";
            lock (_a5Lock)
            {
                if (_a5 == null)
                {
                    error = "数据库未打开";
                    return false;
                }
                try
                {
                    using var cmd = _a5.CreateCommand();
                    cmd.CommandText = "UPDATE member_info SET passwd = $p WHERE user_id = $u";
                    cmd.Parameters.AddWithValue("$p", passwd);
                    cmd.Parameters.AddWithValue("$u", userId);
                    int changed = cmd.ExecuteNonQuery();
                    if (changed <= 0)
                    {
                        error = "账号不存在";
                        return false;
                    }
                    return true;
                }
                catch (Exception ex)
                {
                    error = ex.Message;
                    return false;
                }
            }
        }

        public bool SetAccountState(string userId, int state)
        {
            lock (_a5Lock)
            {
                if (_a5 == null) return false;
                using var cmd = _a5.CreateCommand();
                cmd.CommandText = "UPDATE member_info SET state = $s WHERE user_id = $u";
                cmd.Parameters.AddWithValue("$s", state);
                cmd.Parameters.AddWithValue("$u", userId);
                return cmd.ExecuteNonQuery() > 0;
            }
        }

        public void TouchLastPlay(string userId)
        {
            lock (_a5Lock)
            {
                if (_a5 == null) return;
                try
                {
                    using var cmd = _a5.CreateCommand();
                    cmd.CommandText = "UPDATE member_info SET last_play_time = datetime('now','localtime') WHERE user_id = $u";
                    cmd.Parameters.AddWithValue("$u", userId);
                    cmd.ExecuteNonQuery();
                }
                catch
                {
                    // 忽略非关键时间戳更新失败
                }
            }
        }

        public bool IssueToken(string userId, long mId, string token, int ttlSeconds, string clientIp, out string error)
        {
            error = "";
            lock (_a5Lock)
            {
                if (_a5 == null)
                {
                    error = "数据库未打开";
                    return false;
                }
                try
                {
                    using (var del = _a5.CreateCommand())
                    {
                        del.CommandText = "DELETE FROM gw_login_token WHERE user_id = $u";
                        del.Parameters.AddWithValue("$u", userId);
                        del.ExecuteNonQuery();
                    }

                    long now = NowSeconds();
                    using (var ins = _a5.CreateCommand())
                    {
                        ins.CommandText =
                            "INSERT INTO gw_login_token (token, user_id, m_id, expire_at, client_ip, created_at) " +
                            "VALUES ($t, $u, $m, $e, $ip, $c)";
                        ins.Parameters.AddWithValue("$t", token);
                        ins.Parameters.AddWithValue("$u", userId);
                        ins.Parameters.AddWithValue("$m", mId);
                        ins.Parameters.AddWithValue("$e", now + ttlSeconds);
                        ins.Parameters.AddWithValue("$ip", clientIp);
                        ins.Parameters.AddWithValue("$c", now);
                        ins.ExecuteNonQuery();
                    }
                    return true;
                }
                catch (Exception ex)
                {
                    error = ex.Message;
                    return false;
                }
            }
        }

        public void PurgeExpiredTokens()
        {
            lock (_a5Lock)
            {
                if (_a5 == null) return;
                try
                {
                    using var cmd = _a5.CreateCommand();
                    cmd.CommandText = "DELETE FROM gw_login_token WHERE expire_at < $t";
                    cmd.Parameters.AddWithValue("$t", NowSeconds() - 3600);
                    cmd.ExecuteNonQuery();
                }
                catch
                {
                }
            }
        }

        public bool IsCdkRequired()
        {
            lock (_gwLock)
            {
                if (_gw == null) return false;
                using var cmd = _gw.CreateCommand();
                cmd.CommandText = "SELECT value FROM config WHERE key = 'cdk_required'";
                var val = cmd.ExecuteScalar()?.ToString();
                return val == "1";
            }
        }

        public void SetCdkRequired(bool required)
        {
            lock (_gwLock)
            {
                if (_gw == null) return;
                using var cmd = _gw.CreateCommand();
                cmd.CommandText = "INSERT OR REPLACE INTO config(key, value) VALUES ('cdk_required', $v)";
                cmd.Parameters.AddWithValue("$v", required ? "1" : "0");
                cmd.ExecuteNonQuery();
            }
        }

        public bool ConsumeCdkey(string code, string userId, out string error)
        {
            error = "";
            lock (_gwLock)
            {
                if (_gw == null)
                {
                    error = "网关数据库未打开";
                    return false;
                }
                using var cmd = _gw.CreateCommand();
                cmd.CommandText = "UPDATE cdkey SET used_by = $u, used_at = $t WHERE code = $c AND used_by = ''";
                cmd.Parameters.AddWithValue("$u", userId);
                cmd.Parameters.AddWithValue("$t", NowSeconds());
                cmd.Parameters.AddWithValue("$c", code);
                int changed = cmd.ExecuteNonQuery();
                if (changed != 1)
                {
                    error = "CDK 不存在或已被使用";
                    return false;
                }
                return true;
            }
        }

        public List<string> GenerateCdkeys(int count, string prefix = "A4")
        {
            var created = new List<string>();
            const string alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
            lock (_gwLock)
            {
                if (_gw == null) return created;
                long now = NowSeconds();
                using var rng = RandomNumberGenerator.Create();
                var raw = new byte[12];
                for (int i = 0; i < count; i++)
                {
                    rng.GetBytes(raw);
                    var chars = new char[12];
                    for (int j = 0; j < 12; j++)
                    {
                        chars[j] = alphabet[raw[j] % alphabet.Length];
                    }
                    string code = $"{prefix}-{new string(chars, 0, 4)}-{new string(chars, 4, 4)}-{new string(chars, 8, 4)}";
                    using var cmd = _gw.CreateCommand();
                    cmd.CommandText =
                        "INSERT OR IGNORE INTO cdkey (code, kind, used_by, used_at, created_at) " +
                        "VALUES ($c, 'register', '', 0, $t)";
                    cmd.Parameters.AddWithValue("$c", code);
                    cmd.Parameters.AddWithValue("$t", now);
                    if (cmd.ExecuteNonQuery() > 0)
                    {
                        created.Add(code);
                    }
                }
            }
            return created;
        }

        public List<CdkeyRecord> ListCdkeys(int limit = 100)
        {
            var list = new List<CdkeyRecord>();
            lock (_gwLock)
            {
                if (_gw == null) return list;
                using var cmd = _gw.CreateCommand();
                cmd.CommandText =
                    "SELECT code, kind, used_by, used_at, created_at FROM cdkey ORDER BY created_at DESC LIMIT $lim";
                cmd.Parameters.AddWithValue("$lim", limit);
                using var reader = cmd.ExecuteReader();
                while (reader.Read())
                {
                    list.Add(new CdkeyRecord
                    {
                        Code = reader.GetString(0),
                        Kind = reader.GetString(1),
                        UsedBy = reader.GetString(2),
                        UsedAt = reader.GetInt64(3),
                        CreatedAt = reader.GetInt64(4)
                    });
                }
            }
            return list;
        }

        public List<NoticeRecord> GetNotices(int limit = 20, bool onlyEnabled = true)
        {
            var list = new List<NoticeRecord>();
            lock (_gwLock)
            {
                if (_gw == null) return list;
                using var cmd = _gw.CreateCommand();
                cmd.CommandText = onlyEnabled
                    ? "SELECT id, title, content, state, sort, enabled FROM notice WHERE enabled = 1 ORDER BY sort DESC, id DESC LIMIT $lim"
                    : "SELECT id, title, content, state, sort, enabled FROM notice ORDER BY sort DESC, id DESC LIMIT $lim";
                cmd.Parameters.AddWithValue("$lim", limit);
                using var reader = cmd.ExecuteReader();
                while (reader.Read())
                {
                    list.Add(new NoticeRecord
                    {
                        Id = reader.GetInt64(0),
                        Title = reader.IsDBNull(1) ? "" : reader.GetString(1),
                        Content = reader.IsDBNull(2) ? "" : reader.GetString(2),
                        State = reader.IsDBNull(3) ? "" : reader.GetString(3),
                        Sort = reader.IsDBNull(4) ? 0 : reader.GetInt32(4),
                        Enabled = !reader.IsDBNull(5) && reader.GetInt32(5) != 0
                    });
                }
            }
            return list;
        }

        public void AddNotice(string title, string content, string state = "公告")
        {
            lock (_gwLock)
            {
                if (_gw == null) return;
                using var cmd = _gw.CreateCommand();
                cmd.CommandText = "INSERT INTO notice (title, content, state, sort, enabled) VALUES ($t, $c, $s, 0, 1)";
                cmd.Parameters.AddWithValue("$t", title);
                cmd.Parameters.AddWithValue("$c", content);
                cmd.Parameters.AddWithValue("$s", state);
                cmd.ExecuteNonQuery();
            }
        }

        public void DeleteNotice(long id)
        {
            lock (_gwLock)
            {
                if (_gw == null) return;
                using var cmd = _gw.CreateCommand();
                cmd.CommandText = "DELETE FROM notice WHERE id = $id";
                cmd.Parameters.AddWithValue("$id", id);
                cmd.ExecuteNonQuery();
            }
        }

        public void DeleteCdkey(string code)
        {
            lock (_gwLock)
            {
                if (_gw == null) return;
                using var cmd = _gw.CreateCommand();
                cmd.CommandText = "DELETE FROM cdkey WHERE code = $c AND used_by = ''";
                cmd.Parameters.AddWithValue("$c", code);
                cmd.ExecuteNonQuery();
            }
        }

        public void LogLogin(string userId, string ip, int ok, string note)
        {
            lock (_gwLock)
            {
                if (_gw == null) return;
                try
                {
                    using var cmd = _gw.CreateCommand();
                    cmd.CommandText = "INSERT INTO login_log (user_id, ip, ok, note, at) VALUES ($u, $ip, $ok, $n, $t)";
                    cmd.Parameters.AddWithValue("$u", userId);
                    cmd.Parameters.AddWithValue("$ip", ip);
                    cmd.Parameters.AddWithValue("$ok", ok);
                    cmd.Parameters.AddWithValue("$n", note);
                    cmd.Parameters.AddWithValue("$t", NowSeconds());
                    cmd.ExecuteNonQuery();
                }
                catch
                {
                }
            }
        }

        public void Dispose()
        {
            lock (_a5Lock)
            {
                _a5?.Close();
                _a5?.Dispose();
                _a5 = null;
            }
            lock (_gwLock)
            {
                _gw?.Close();
                _gw?.Dispose();
                _gw = null;
            }
        }
    }
}
