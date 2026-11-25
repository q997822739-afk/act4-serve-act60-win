#include "db.h"

#include <ctime>
#include <sstream>

namespace {

// 简单语句执行，失败把 sqlite 的错误信息带出来
bool exec(sqlite3* db, const char* sql, std::string& error) {
    char* msg = nullptr;
    if (sqlite3_exec(db, sql, nullptr, nullptr, &msg) != SQLITE_OK) {
        error = msg ? msg : "sqlite 执行失败";
        if (msg) {
            sqlite3_free(msg);
        }
        return false;
    }
    return true;
}

bool prepare(sqlite3* db, const char* sql, sqlite3_stmt** stmt, std::string& error) {
    if (sqlite3_prepare_v2(db, sql, -1, stmt, nullptr) != SQLITE_OK) {
        error = sqlite3_errmsg(db);
        *stmt = nullptr;
        return false;
    }
    return true;
}

void bindText(sqlite3_stmt* stmt, int index, const std::string& value) {
    sqlite3_bind_text(stmt, index, value.c_str(), (int)value.size(), SQLITE_TRANSIENT);
}

long long nowSeconds() {
    return (long long)::time(nullptr);
}

}  // namespace

namespace db {

Database::~Database() {
    if (a5_) {
        sqlite3_close(a5_);
    }
    if (gw_) {
        sqlite3_close(gw_);
    }
}

bool Database::openA5(const std::string& path, std::string& error) {
    if (sqlite3_open_v2(path.c_str(), &a5_, SQLITE_OPEN_READWRITE, nullptr) != SQLITE_OK) {
        error = "打不开 A5 库 " + path + "：" + (a5_ ? sqlite3_errmsg(a5_) : "未知");
        return false;
    }
    // A5 自己也是 WAL + 5 秒忙等，这里照抄，避免两边互相锁死
    if (!exec(a5_, "PRAGMA journal_mode=WAL;", error)
        || !exec(a5_, "PRAGMA busy_timeout=5000;", error)
        || !exec(a5_,
                 "CREATE TABLE IF NOT EXISTS gw_login_token ("
                 " token      TEXT NOT NULL PRIMARY KEY,"
                 " user_id    TEXT NOT NULL,"
                 " m_id       INTEGER NOT NULL DEFAULT 0,"
                 " expire_at  INTEGER NOT NULL,"
                 " client_ip  TEXT NOT NULL DEFAULT '',"
                 " created_at INTEGER NOT NULL);",
                 error)) {
        return false;
    }
    return exec(a5_, "CREATE INDEX IF NOT EXISTS idx_gw_token_user ON gw_login_token(user_id);",
                error);
}

bool Database::openGateway(const std::string& path, std::string& error) {
    if (sqlite3_open_v2(path.c_str(), &gw_, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE,
                        nullptr) != SQLITE_OK) {
        error = "打不开网关库 " + path + "：" + (gw_ ? sqlite3_errmsg(gw_) : "未知");
        return false;
    }
    if (!exec(gw_, "PRAGMA journal_mode=WAL;", error)
        || !exec(gw_, "PRAGMA busy_timeout=5000;", error)
        || !exec(gw_,
                 "CREATE TABLE IF NOT EXISTS config ("
                 " key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL DEFAULT '');", error)
        || !exec(gw_,
                 "CREATE TABLE IF NOT EXISTS notice ("
                 " id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,"
                 " title TEXT NOT NULL DEFAULT '',"
                 " content TEXT NOT NULL DEFAULT '',"
                 " state TEXT NOT NULL DEFAULT '',"
                 " sort INTEGER NOT NULL DEFAULT 0,"
                 " enabled INTEGER NOT NULL DEFAULT 1);", error)
        || !exec(gw_,
                 "CREATE TABLE IF NOT EXISTS cdkey ("
                 " code TEXT NOT NULL PRIMARY KEY,"
                 " kind TEXT NOT NULL DEFAULT 'register',"
                 " used_by TEXT NOT NULL DEFAULT '',"
                 " used_at INTEGER NOT NULL DEFAULT 0,"
                 " created_at INTEGER NOT NULL DEFAULT 0);", error)
        || !exec(gw_,
                 "CREATE TABLE IF NOT EXISTS login_log ("
                 " id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,"
                 " user_id TEXT NOT NULL DEFAULT '',"
                 " ip TEXT NOT NULL DEFAULT '',"
                 " ok INTEGER NOT NULL DEFAULT 0,"
                 " note TEXT NOT NULL DEFAULT '',"
                 " at INTEGER NOT NULL DEFAULT 0);", error)) {
        return false;
    }
    // 默认值只在第一次建库时写
    return exec(gw_,
                "INSERT OR IGNORE INTO config(key, value) VALUES "
                "('cdk_required', '0'),"
                "('notice', ''),"
                "('news_text', '');", error);
}

Account Database::findAccount(const std::string& userId) {
    Account out;
    std::string error;
    sqlite3_stmt* stmt = nullptr;
    if (!prepare(a5_,
                 "SELECT m_id, user_id, passwd, nickname, state, slot "
                 "FROM member_info WHERE user_id = ?", &stmt, error)) {
        return out;
    }
    bindText(stmt, 1, userId);
    if (sqlite3_step(stmt) == SQLITE_ROW) {
        out.found = true;
        out.mId = sqlite3_column_int64(stmt, 0);
        out.userId = (const char*)sqlite3_column_text(stmt, 1);
        const unsigned char* pass = sqlite3_column_text(stmt, 2);
        out.passwd = pass ? (const char*)pass : "";
        const unsigned char* nick = sqlite3_column_text(stmt, 3);
        out.nickname = nick ? (const char*)nick : "";
        out.state = sqlite3_column_int(stmt, 4);
        out.slot = sqlite3_column_int64(stmt, 5);
    }
    sqlite3_finalize(stmt);
    return out;
}

long long Database::createAccount(const std::string& userId, const std::string& passwd,
                                  std::string& error) {
    // SQL 与 A5 AccountStore.createAccount 一致：user_name = 账号，昵称同账号，默认 16 个角色位
    sqlite3_stmt* stmt = nullptr;
    if (!prepare(a5_,
                 "INSERT INTO member_info (user_id, user_name, passwd, nickname, slot, reg_date) "
                 "VALUES (?, ?, ?, ?, 16, ?)", &stmt, error)) {
        return 0;
    }
    bindText(stmt, 1, userId);
    bindText(stmt, 2, userId);
    bindText(stmt, 3, passwd);
    bindText(stmt, 4, userId);
    sqlite3_bind_int64(stmt, 5, nowSeconds());
    bool ok = sqlite3_step(stmt) == SQLITE_DONE;
    if (!ok) {
        error = sqlite3_errmsg(a5_);
    }
    sqlite3_finalize(stmt);
    if (!ok) {
        return 0;
    }
    long long mId = sqlite3_last_insert_rowid(a5_);
    // A5 的 GameStore 会补 member_login，这里照做，免得第一次进游戏少一行
    sqlite3_stmt* login = nullptr;
    if (prepare(a5_, "INSERT INTO member_login (m_id, login_ip) VALUES (?, '')", &login, error)) {
        sqlite3_bind_int64(login, 1, mId);
        sqlite3_step(login);
        sqlite3_finalize(login);
    }
    return mId;
}

bool Database::updatePassword(const std::string& userId, const std::string& passwd,
                              std::string& error) {
    sqlite3_stmt* stmt = nullptr;
    if (!prepare(a5_, "UPDATE member_info SET passwd = ? WHERE user_id = ?", &stmt, error)) {
        return false;
    }
    bindText(stmt, 1, passwd);
    bindText(stmt, 2, userId);
    bool ok = sqlite3_step(stmt) == SQLITE_DONE && sqlite3_changes(a5_) > 0;
    if (!ok) {
        error = sqlite3_changes(a5_) > 0 ? sqlite3_errmsg(a5_) : "账号不存在";
    }
    sqlite3_finalize(stmt);
    return ok;
}

bool Database::touchLastPlay(const std::string& userId) {
    std::string error;
    return exec(a5_, ("UPDATE member_info SET last_play_time = datetime('now','localtime') "
                      "WHERE user_id = '" + userId + "'").c_str(), error);
}

bool Database::issueToken(const std::string& userId, long long mId, const std::string& token,
                          int ttlSeconds, const std::string& clientIp, std::string& error) {
    // 同一账号之前没用的令牌作废，避免旧令牌被重放
    sqlite3_stmt* del = nullptr;
    if (prepare(a5_, "DELETE FROM gw_login_token WHERE user_id = ?", &del, error)) {
        bindText(del, 1, userId);
        sqlite3_step(del);
        sqlite3_finalize(del);
    }
    sqlite3_stmt* stmt = nullptr;
    if (!prepare(a5_,
                 "INSERT INTO gw_login_token (token, user_id, m_id, expire_at, client_ip, created_at) "
                 "VALUES (?, ?, ?, ?, ?, ?)", &stmt, error)) {
        return false;
    }
    bindText(stmt, 1, token);
    bindText(stmt, 2, userId);
    sqlite3_bind_int64(stmt, 3, mId);
    sqlite3_bind_int64(stmt, 4, nowSeconds() + ttlSeconds);
    bindText(stmt, 5, clientIp);
    sqlite3_bind_int64(stmt, 6, nowSeconds());
    bool ok = sqlite3_step(stmt) == SQLITE_DONE;
    if (!ok) {
        error = sqlite3_errmsg(a5_);
    }
    sqlite3_finalize(stmt);
    return ok;
}

void Database::purgeExpiredTokens() {
    std::string error;
    std::ostringstream sql;
    sql << "DELETE FROM gw_login_token WHERE expire_at < " << nowSeconds() - 3600;
    exec(a5_, sql.str().c_str(), error);
}

bool Database::cdkRequired() {
    std::string error;
    sqlite3_stmt* stmt = nullptr;
    if (!prepare(gw_, "SELECT value FROM config WHERE key = 'cdk_required'", &stmt, error)) {
        return false;
    }
    bool required = false;
    if (sqlite3_step(stmt) == SQLITE_ROW) {
        const unsigned char* v = sqlite3_column_text(stmt, 0);
        required = v && std::string((const char*)v) == "1";
    }
    sqlite3_finalize(stmt);
    return required;
}

bool Database::consumeCdkey(const std::string& code, const std::string& userId,
                            std::string& error) {
    sqlite3_stmt* stmt = nullptr;
    if (!prepare(gw_,
                 "UPDATE cdkey SET used_by = ?, used_at = ? "
                 "WHERE code = ? AND used_by = ''", &stmt, error)) {
        return false;
    }
    bindText(stmt, 1, userId);
    sqlite3_bind_int64(stmt, 2, nowSeconds());
    bindText(stmt, 3, code);
    bool ok = sqlite3_step(stmt) == SQLITE_DONE && sqlite3_changes(gw_) == 1;
    if (!ok) {
        error = "CDK 不存在或已被使用";
    }
    sqlite3_finalize(stmt);
    return ok;
}

std::vector<Notice> Database::notices(int limit) {
    std::vector<Notice> out;
    std::string error;
    sqlite3_stmt* stmt = nullptr;
    if (!prepare(gw_,
                 "SELECT id, title, content, state FROM notice "
                 "WHERE enabled = 1 ORDER BY sort DESC, id DESC LIMIT ?", &stmt, error)) {
        return out;
    }
    sqlite3_bind_int(stmt, 1, limit);
    while (sqlite3_step(stmt) == SQLITE_ROW) {
        Notice n;
        n.id = sqlite3_column_int64(stmt, 0);
        n.title = (const char*)sqlite3_column_text(stmt, 1);
        n.content = (const char*)sqlite3_column_text(stmt, 2);
        n.state = (const char*)sqlite3_column_text(stmt, 3);
        out.push_back(n);
    }
    sqlite3_finalize(stmt);
    return out;
}

void Database::logLogin(const std::string& userId, const std::string& ip, int ok,
                        const std::string& note) {
    std::string error;
    sqlite3_stmt* stmt = nullptr;
    if (!prepare(gw_, "INSERT INTO login_log (user_id, ip, ok, note, at) VALUES (?,?,?,?,?)",
                 &stmt, error)) {
        return;
    }
    bindText(stmt, 1, userId);
    bindText(stmt, 2, ip);
    sqlite3_bind_int(stmt, 3, ok);
    bindText(stmt, 4, note);
    sqlite3_bind_int64(stmt, 5, nowSeconds());
    sqlite3_step(stmt);
    sqlite3_finalize(stmt);
}

}  // namespace db
