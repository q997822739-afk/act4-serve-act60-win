// 网关配置。格式沿用花枝的 gateway.ini（[Gate] / [sever] / [server] / [db]），
// 只保留用得到的键；不认识的键忽略，老配置文件能直接用。
#pragma once

#include <map>
#include <string>

namespace cfg {

class Ini {
public:
    bool load(const std::string& path, std::string& error);
    std::string get(const std::string& section, const std::string& key,
                    const std::string& fallback = "") const;
    int getInt(const std::string& section, const std::string& key, int fallback) const;

private:
    std::map<std::string, std::string> values_;   // 键 = section + "." + key
};

// 网关运行时需要的全部配置
struct Gateway {
    int port = 8188;
    int workerThreads = 2;          // 花枝的 server_num，登录网关用不到多线程，2 足够
    int qpsPerIp = 30;              // 每个 IP 每秒最多几个请求（花枝的 server_qps）
    int tokenTtl = 60;              // 令牌有效期（秒），登录器拿到后立刻启动游戏
    std::string a5Db;               // A5 的 s1a5.db 路径
    std::string gatewayDb;          // 网关自己的 gateway.db 路径
    std::string logPath = "logs/gateway.log";
    std::string updateDir = "update";   // 自动更新用的静态目录（HTTP 由 gm_proxy 一样的转发提供）
    std::string gameHost = "";      // 下发给登录器的游戏地址，空则用本机公网 IP
    int gamePort = 7001;
    bool debug = true;              // true = 不检查授权（调试期）

    bool load(const std::string& path, std::string& error);
};

}  // namespace cfg
