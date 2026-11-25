#include "handlers.h"

#include <ctime>
#include <sstream>

#include <arpa/inet.h>
#include <ifaddrs.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>

#include "proto.h"

namespace {

// 账号规则照抄 A5 AccountRules：1~10 位，字母数字下划线
const int kUserIdMin = 1;
const int kUserIdMax = 10;
const int kPasswordMax = 16;

bool isValidUserId(const std::string& userId, std::string& why) {
    if (userId.empty()) {
        why = "请输入账号";
        return false;
    }
    if ((int)userId.size() < kUserIdMin || (int)userId.size() > kUserIdMax) {
        why = "账号长度需要在 1 到 10 位之间";
        return false;
    }
    for (char ch : userId) {
        bool ok = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                  || (ch >= '0' && ch <= '9') || ch == '_';
        if (!ok) {
            why = "账号只能包含字母、数字和下划线";
            return false;
        }
    }
    return true;
}

// 客户端的启动行用 ? 分隔参数，密码里不能有 ? 或 "；长度上限是登录器的 16 位
bool isValidPassword(const std::string& pass, std::string& why) {
    if (pass.empty() || (int)pass.size() > kPasswordMax) {
        why = "密码长度需要在 1 到 16 位之间";
        return false;
    }
    for (char ch : pass) {
        if (ch == '?' || ch == '"') {
            why = "密码不能包含问号或双引号";
            return false;
        }
        if (ch < '!' || ch > '~') {
            why = "密码只能是可见的英文、数字或符号";
            return false;
        }
    }
    return true;
}

std::string jsonEscape(const std::string& text) {
    std::string out;
    out.reserve(text.size());
    for (char ch : text) {
        switch (ch) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if ((unsigned char)ch < 0x20) {
                    out += ' ';
                } else {
                    out += ch;
                }
        }
    }
    return out;
}

// 失败统一格式：{"code":N,"msg":"..."}；0 = 成功
std::string fail(int code, const std::string& message) {
    std::ostringstream out;
    out << "{\"code\":" << code << ",\"msg\":\"" << jsonEscape(message) << "\"}";
    return out.str();
}

const char kBase64[] =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

}  // namespace

namespace handlers {

std::string makeToken() {
    // 用系统随机数（/dev/urandom 走 std::random_device 也行，但 4.8 下不一定真随机）
    std::string token;
    token.reserve(16);
    unsigned char raw[16] = {0};
    FILE* f = fopen("/dev/urandom", "rb");
    if (f) {
        size_t got = fread(raw, 1, sizeof(raw), f);
        (void)got;
        fclose(f);
    } else {
        std::srand((unsigned)time(nullptr) ^ (unsigned)getpid());
        for (int i = 0; i < 16; i++) {
            raw[i] = (unsigned char)(std::rand() & 0xFF);
        }
    }
    for (int i = 0; i < 16; i++) {
        token += kBase64[raw[i] % 62];
    }
    return token;
}

std::string detectPublicIp() {
    struct ifaddrs* list = nullptr;
    if (getifaddrs(&list) != 0) {
        return "127.0.0.1";
    }
    std::string fallback = "127.0.0.1";
    for (struct ifaddrs* it = list; it; it = it->ifa_next) {
        if (!it->ifa_addr || it->ifa_addr->sa_family != AF_INET) {
            continue;
        }
        char text[INET_ADDRSTRLEN] = {0};
        struct sockaddr_in* addr = (struct sockaddr_in*)it->ifa_addr;
        if (!inet_ntop(AF_INET, &addr->sin_addr, text, sizeof(text))) {
            continue;
        }
        std::string ip = text;
        if (ip == "127.0.0.1") {
            continue;
        }
        // 优先挑公网口，没有就随便一个非回环（测试服的网卡是 10.2.0.16，公网在 EIP 上）
        if (ip.rfind("10.", 0) != 0 && ip.rfind("192.168.", 0) != 0
            && ip.rfind("172.", 0) != 0) {
            freeifaddrs(list);
            return ip;
        }
        fallback = ip;
    }
    freeifaddrs(list);
    return fallback;
}

Service::Service(db::Database& database, const cfg::Gateway& config)
        : db_(database), config_(config) {}

std::string Service::dispatch(const std::string& command, const std::string& payload,
                              const std::string& clientIp, const std::string& peerText) {
    (void)peerText;
    if (command == "login") {
        return login(payload, clientIp);
    }
    if (command == "reg") {
        return reg(payload, clientIp);
    }
    if (command == "reSetPassword") {
        return resetPassword(payload, clientIp);
    }
    if (command == "cdk") {
        return useCdkey(payload, clientIp);
    }
    if (command == "news") {
        return news();
    }
    if (command == "version") {
        return version();
    }
    if (command == "ping" || command == "05") {
        return "{\"code\":0,\"msg\":\"pong\"}";
    }
    return fail(9, "未知命令 " + command);
}

std::string Service::login(const std::string& payload, const std::string& clientIp) {
    std::string account = proto::field(payload, "u");
    std::string password = proto::field(payload, "p");
    std::string why;
    if (!isValidUserId(account, why) || !isValidPassword(password, why)) {
        db_.logLogin(account, clientIp, 0, why);
        return fail(3, why);
    }

    db::Account found = db_.findAccount(account);
    if (!found.found || found.passwd != password) {
        db_.logLogin(account, clientIp, 0, "账号或密码错误");
        return fail(1, "账号或密码不正确");
    }
    if (found.state != 1) {
        db_.logLogin(account, clientIp, 0, "账号已封停");
        return fail(2, "该账号已被封停");
    }

    std::string token = makeToken();
    std::string error;
    if (!db_.issueToken(account, found.mId, token, config_.tokenTtl, clientIp, error)) {
        db_.logLogin(account, clientIp, 0, "发令牌失败：" + error);
        return fail(4, "服务器繁忙，请稍后再试");
    }
    db_.touchLastPlay(account);
    db_.logLogin(account, clientIp, 1, "登录成功");

    std::ostringstream out;
    out << "{\"code\":0,\"msg\":\"ok\""
        << ",\"token\":\"" << token << "\""
        << ",\"uid\":" << found.mId
        << ",\"name\":\"" << jsonEscape(found.nickname.empty() ? found.userId : found.nickname)
        << "\",\"ttl\":" << config_.tokenTtl
        << ",\"host\":\"" << jsonEscape(config_.gameHost.empty() ? detectPublicIp()
                                                                 : config_.gameHost)
        << "\",\"port\":" << config_.gamePort << "}";
    return out.str();
}

std::string Service::reg(const std::string& payload, const std::string& clientIp) {
    std::string account = proto::field(payload, "u");
    std::string password = proto::field(payload, "p");
    std::string cdkey = proto::field(payload, "k");   // 花枝里 <k> 是验证码位，这里借来放 CDK

    std::string why;
    if (!isValidUserId(account, why) || !isValidPassword(password, why)) {
        return fail(3, why);
    }
    if (db_.cdkRequired()) {
        std::string error;
        if (cdkey.empty()) {
            return fail(5, "需要邀请码");
        }
        if (!db_.consumeCdkey(cdkey, account, error)) {
            return fail(6, error);
        }
    }
    if (db_.findAccount(account).found) {
        return fail(7, "账号已存在");
    }
    std::string error;
    long long mId = db_.createAccount(account, password, error);
    if (mId <= 0) {
        return fail(4, "注册失败：" + error);
    }
    db_.logLogin(account, clientIp, 1, "注册成功");
    std::ostringstream out;
    out << "{\"code\":0,\"msg\":\"注册成功\",\"uid\":" << mId << "}";
    return out.str();
}

std::string Service::resetPassword(const std::string& payload, const std::string& clientIp) {
    std::string account = proto::field(payload, "u");
    std::string oldPass = proto::field(payload, "p");
    std::string newPass = proto::field(payload, "n");

    std::string why;
    if (!isValidUserId(account, why) || !isValidPassword(newPass, why)) {
        return fail(3, why);
    }
    db::Account found = db_.findAccount(account);
    if (!found.found || found.passwd != oldPass) {
        db_.logLogin(account, clientIp, 0, "改密：原密码错误");
        return fail(1, "账号或原密码不正确");
    }
    std::string error;
    if (!db_.updatePassword(account, newPass, error)) {
        return fail(4, "改密失败：" + error);
    }
    db_.logLogin(account, clientIp, 1, "改密成功");
    return "{\"code\":0,\"msg\":\"密码已修改\"}";
}

std::string Service::useCdkey(const std::string& payload, const std::string& clientIp) {
    (void)clientIp;
    std::string account = proto::field(payload, "u");
    std::string code = proto::field(payload, "k");
    if (account.empty() || code.empty()) {
        return fail(3, "参数不全");
    }
    // 只检查，不消耗：真正消耗在注册时
    std::string error;
    if (!db_.consumeCdkey(code, account, error)) {
        return fail(6, error);
    }
    return "{\"code\":0,\"msg\":\"邀请码可用\"}";
}

std::string Service::news() {
    std::vector<db::Notice> list = db_.notices(20);
    std::ostringstream out;
    out << "{\"code\":0,\"count\":" << list.size() << ",\"list\":[";
    for (size_t i = 0; i < list.size(); i++) {
        if (i) {
            out << ",";
        }
        out << "{\"id\":" << list[i].id << ",\"title\":\""
            << jsonEscape(list[i].title) << "\",\"content\":\""
            << jsonEscape(list[i].content) << "\",\"state\":\""
            << jsonEscape(list[i].state) << "\"}";
    }
    out << "]}";
    return out.str();
}

std::string Service::version() {
    std::ostringstream out;
    out << "{\"code\":0,\"host\":\""
        << jsonEscape(config_.gameHost.empty() ? detectPublicIp() : config_.gameHost)
        << "\",\"port\":" << config_.gamePort
        << ",\"update\":\"" << jsonEscape(config_.updateDir) << "\"}";
    return out.str();
}

std::string Service::ping() {
    return "{\"code\":0,\"msg\":\"pong\"}";
}

}  // namespace handlers
