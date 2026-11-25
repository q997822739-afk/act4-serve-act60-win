// 两个库：
//   a5  —— A5 服务端的 s1a5.db（只碰 member_info 和新加的 gw_login_token）
//   gw  —— 网关自己的 gateway.db（CDK、公告、登录日志）
#pragma once

#include <sqlite3.h>
#include <string>
#include <vector>

namespace db {

struct Account {
    bool found = false;
    long long mId = 0;
    std::string userId;
    std::string passwd;
    std::string nickname;
    int state = 0;
    long long slot = 0;
};

struct Notice {
    long long id = 0;
    std::string title;
    std::string content;
    std::string state;   // "banner" / "scroll" / ""
};

class Database {
public:
    Database() = default;
    ~Database();
    Database(const Database&) = delete;
    Database& operator=(const Database&) = delete;

    bool openA5(const std::string& path, std::string& error);
    bool openGateway(const std::string& path, std::string& error);

    // ---- 账号（s1a5.db member_info）----
    Account findAccount(const std::string& userId);
    long long createAccount(const std::string& userId, const std::string& passwd,
                            std::string& error);
    bool updatePassword(const std::string& userId, const std::string& passwd,
                        std::string& error);
    bool touchLastPlay(const std::string& userId);

    // ---- 登录令牌（s1a5.db gw_login_token）----
    bool issueToken(const std::string& userId, long long mId, const std::string& token,
                    int ttlSeconds, const std::string& clientIp, std::string& error);
    void purgeExpiredTokens();

    // ---- 网关库 ----
    bool consumeCdkey(const std::string& code, const std::string& userId, std::string& error);
    bool cdkRequired();
    std::vector<Notice> notices(int limit);
    void logLogin(const std::string& userId, const std::string& ip, int ok,
                  const std::string& note);

private:
    sqlite3* a5_ = nullptr;
    sqlite3* gw_ = nullptr;
};

}  // namespace db
