// 商业登录器网关：花枝 6 系的线协议（明文 <hz>命令|正文</hz>），
// 业务只保留注册 / 登录 / 改密 / CDK / 公告 / 版本。
//
// 编译：make（测试服 CentOS 7 + gcc 4.8 直接能编）

#include <arpa/inet.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <signal.h>
#include <sys/socket.h>
#include <unistd.h>

#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <iostream>
#include <map>
#include <mutex>
#include <string>
#include <thread>

#include "config.h"
#include "db.h"
#include "handlers.h"
#include "proto.h"

namespace {

cfg::Gateway g_config;
db::Database* g_db = nullptr;
handlers::Service* g_service = nullptr;

std::mutex g_logMutex;
std::ofstream g_logFile;

std::mutex g_rateMutex;
std::map<std::string, std::pair<long long, int>> g_rate;   // IP -> (当前秒, 本秒次数)

std::atomic<bool> g_running(true);
std::atomic<long long> g_connectionSeq(0);

void logLine(const std::string& text) {
    std::lock_guard<std::mutex> guard(g_logMutex);
    if (g_logFile.is_open()) {
        g_logFile << text << std::endl;
    }
    std::cout << text << std::endl;
}

std::string stamp() {
    time_t now = ::time(nullptr);
    struct tm tmNow;
    localtime_r(&now, &tmNow);
    char buf[32] = {0};
    strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", &tmNow);
    return buf;
}

// 每个 IP 每秒最多 qpsPerIp 个请求，超过直接回 busy
bool allowRequest(const std::string& ip) {
    long long now = (long long)::time(nullptr);
    std::lock_guard<std::mutex> guard(g_rateMutex);
    auto& entry = g_rate[ip];
    if (entry.first != now) {
        entry.first = now;
        entry.second = 0;
    }
    ++entry.second;
    return entry.second <= g_config.qpsPerIp;
}

void handleClient(int fd, std::string ip) {
    long long seq = ++g_connectionSeq;
    struct timeval tv = {0, 0};
    tv.tv_sec = 120;   // 空闲 120 秒断开，登录器不会长连接
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
    int one = 1;
    setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &one, sizeof(one));

    std::string buffer;
    char chunk[4096];
    for (;;) {
        ssize_t got = recv(fd, chunk, sizeof(chunk), 0);
        if (got <= 0) {
            break;
        }
        buffer.append(chunk, (size_t)got);

        std::string payloads;
        proto::drain_frames(buffer, payloads);
        if (payloads.empty()) {
            if (buffer.size() > 64 * 1024) {   // 一直不闭合的垃圾数据，断开
                logLine("[" + stamp() + "] [连接 " + std::to_string(seq) + " " + ip
                        + "] 数据过长且没有完整包，断开");
                break;
            }
            continue;
        }

        std::string::size_type start = 0;
        while (start < payloads.size()) {
            std::string::size_type end = payloads.find('\n', start);
            if (end == std::string::npos) {
                end = payloads.size();
            }
            std::string payload = payloads.substr(start, end - start);
            start = end + 1;

            std::string command = proto::field(payload, "c");
            if (command.empty()) {
                continue;
            }
            std::string body;
            if (!allowRequest(ip)) {
                body = "{\"code\":8,\"msg\":\"请求过于频繁\"}";
            } else {
                body = g_service->dispatch(command, payload, ip,
                                           std::to_string(seq));
            }
            std::string out = proto::frame(command, body);
            size_t sent = 0;
            while (sent < out.size()) {
                ssize_t wrote = send(fd, out.data() + sent, out.size() - sent, 0);
                if (wrote <= 0) {
                    break;
                }
                sent += (size_t)wrote;
            }
            // 登录 / 注册 / 改密才打日志，心跳不刷屏
            if (command == "login" || command == "reg" || command == "reSetPassword") {
                logLine("[" + stamp() + "] [连接 " + std::to_string(seq) + " " + ip + "] "
                        + command + " -> " + body);
            }
        }
    }
    close(fd);
    logLine("[" + stamp() + "] [连接 " + std::to_string(seq) + " " + ip + "] 断开");
}

void onSignal(int) {
    g_running = false;
}

}  // namespace

int main(int argc, char** argv) {
    std::string configPath = argc > 1 ? argv[1] : "conf/gateway.ini";
    std::string error;
    if (!g_config.load(configPath, error)) {
        std::cerr << "[配置] " << error << std::endl;
        return 1;
    }

    if (!g_config.logPath.empty()) {
        g_logFile.open(g_config.logPath.c_str(), std::ios::app);
    }

    db::Database database;
    if (!database.openA5(g_config.a5Db, error)) {
        std::cerr << "[数据库] " << error << std::endl;
        return 1;
    }
    if (!database.openGateway(g_config.gatewayDb, error)) {
        std::cerr << "[数据库] " << error << std::endl;
        return 1;
    }
    g_db = &database;

    handlers::Service service(database, g_config);
    g_service = &service;

    // 调试期不查授权（花枝原版这里会 curl 授权服务器，还要比对写死的到期时间）
    if (!g_config.debug) {
        std::cerr << "[授权] 正式版授权校验还没接，请先用 debug=1 启动" << std::endl;
        return 1;
    }

    signal(SIGPIPE, SIG_IGN);
    signal(SIGINT, onSignal);
    signal(SIGTERM, onSignal);

    int listener = socket(AF_INET, SOCK_STREAM, 0);
    if (listener < 0) {
        std::cerr << "[网络] 建 socket 失败" << std::endl;
        return 1;
    }
    int reuse = 1;
    setsockopt(listener, SOL_SOCKET, SO_REUSEADDR, &reuse, sizeof(reuse));
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_ANY);
    addr.sin_port = htons((uint16_t)g_config.port);
    if (bind(listener, (struct sockaddr*)&addr, sizeof(addr)) != 0) {
        std::cerr << "[网络] 绑定端口 " << g_config.port << " 失败：" << strerror(errno)
                  << std::endl;
        return 1;
    }
    if (listen(listener, 128) != 0) {
        std::cerr << "[网络] listen 失败" << std::endl;
        return 1;
    }

    logLine("[" + stamp() + "] 网关监听 0.0.0.0:" + std::to_string(g_config.port)
            + "，A5 库 " + g_config.a5Db + "，网关库 " + g_config.gatewayDb
            + "，令牌 " + std::to_string(g_config.tokenTtl) + " 秒，调试授权="
            + (g_config.debug ? "关" : "开"));
    logLine("[" + stamp() + "] 对外游戏地址 "
            + (g_config.gameHost.empty() ? handlers::detectPublicIp() : g_config.gameHost)
            + ":" + std::to_string(g_config.gamePort));

    std::thread cleaner([&database]() {
        while (g_running) {
            struct timespec ts = {600, 0};
            nanosleep(&ts, nullptr);
            database.purgeExpiredTokens();
        }
    });
    cleaner.detach();

    while (g_running) {
        struct sockaddr_in peer;
        socklen_t peerLen = sizeof(peer);
        int fd = accept(listener, (struct sockaddr*)&peer, &peerLen);
        if (fd < 0) {
            if (errno == EINTR) {
                continue;
            }
            if (!g_running) {
                break;
            }
            std::cerr << "[网络] accept 失败：" << strerror(errno) << std::endl;
            continue;
        }
        char ip[INET_ADDRSTRLEN] = {0};
        inet_ntop(AF_INET, &peer.sin_addr, ip, sizeof(ip));
        std::thread(handleClient, fd, std::string(ip)).detach();
    }

    close(listener);
    logLine("[" + stamp() + "] 网关已停止");
    return 0;
}
