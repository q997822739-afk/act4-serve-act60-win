// 网关的业务入口：收到一条 <hz>c|...的内容</hz>，按 <c> 分发。
// 关键字沿用花枝：login / reg / reSetPassword / cdk / news，另外加了 version / ping。
#pragma once

#include <string>

#include "config.h"
#include "db.h"

namespace handlers {

class Service {
public:
    Service(db::Database& database, const cfg::Gateway& config);

    // 返回正文（不含 <hz>c| 前缀和 </hz> 尾）。正文统一是 JSON。
    std::string dispatch(const std::string& command, const std::string& payload,
                         const std::string& clientIp, const std::string& peerText);

private:
    std::string login(const std::string& payload, const std::string& clientIp);
    std::string reg(const std::string& payload, const std::string& clientIp);
    std::string resetPassword(const std::string& payload, const std::string& clientIp);
    std::string useCdkey(const std::string& payload, const std::string& clientIp);
    std::string news();
    std::string version();
    std::string ping();

    db::Database& db_;
    const cfg::Gateway& config_;
};

// 生成 16 位一次性令牌（大小写字母 + 数字）
std::string makeToken();

// 服务器自己的对外地址（配置没写时用第一条非回环 IPv4）
std::string detectPublicIp();

}  // namespace handlers
