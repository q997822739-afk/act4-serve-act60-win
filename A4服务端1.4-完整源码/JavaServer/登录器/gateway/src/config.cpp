#include "config.h"

#include <cstdlib>
#include <fstream>

namespace {

std::string trim(const std::string& text) {
    std::string::size_type a = text.find_first_not_of(" \t\r\n");
    if (a == std::string::npos) {
        return "";
    }
    std::string::size_type b = text.find_last_not_of(" \t\r\n");
    return text.substr(a, b - a + 1);
}

std::string lower(std::string text) {
    for (char& ch : text) {
        if (ch >= 'A' && ch <= 'Z') {
            ch = (char)(ch - 'A' + 'a');
        }
    }
    return text;
}

}  // namespace

namespace cfg {

bool Ini::load(const std::string& path, std::string& error) {
    std::ifstream in(path.c_str());
    if (!in) {
        error = "打不开配置文件 " + path;
        return false;
    }
    std::string section;
    std::string line;
    while (std::getline(in, line)) {
        line = trim(line);
        if (line.empty() || line[0] == '#' || line[0] == ';') {
            continue;
        }
        if (line.front() == '[' && line.back() == ']') {
            section = lower(trim(line.substr(1, line.size() - 2)));
            continue;
        }
        std::string::size_type eq = line.find('=');
        if (eq == std::string::npos) {
            continue;
        }
        std::string key = lower(trim(line.substr(0, eq)));
        std::string value = trim(line.substr(eq + 1));
        if (!value.empty() && (value.front() == '"' || value.front() == '\'')) {
            value = value.substr(1, value.size() - 2);
        }
        values_[section + "." + key] = value;
    }
    if (!in.eof() && in.fail()) {
        error = "读配置文件 " + path + " 出错";
        return false;
    }
    return true;
}

std::string Ini::get(const std::string& section, const std::string& key,
                     const std::string& fallback) const {
    auto it = values_.find(lower(section) + "." + lower(key));
    return it == values_.end() ? fallback : it->second;
}

int Ini::getInt(const std::string& section, const std::string& key, int fallback) const {
    std::string text = get(section, key);
    if (text.empty()) {
        return fallback;
    }
    return std::atoi(text.c_str());
}

bool Gateway::load(const std::string& path, std::string& error) {
    Ini ini;
    if (!ini.load(path, error)) {
        return false;
    }
    port = ini.getInt("gate", "gateprot", port);   // 花枝的键名就是 gateprot
    workerThreads = ini.getInt("server", "server_num", workerThreads);
    qpsPerIp = ini.getInt("server", "server_qps", qpsPerIp);
    tokenTtl = ini.getInt("gate", "token_ttl", tokenTtl);
    logPath = ini.get("gate", "log_path", logPath);
    updateDir = ini.get("gate", "update_dir", updateDir);
    gameHost = ini.get("game", "host", gameHost);
    gamePort = ini.getInt("game", "port", gamePort);
    debug = ini.getInt("license", "debug", debug ? 1 : 0) != 0;

    a5Db = ini.get("db", "a5_db");
    gatewayDb = ini.get("db", "gateway_db");
    if (a5Db.empty()) {
        error = "配置 [db] a5_db 没写（A5 的 s1a5.db 路径）";
        return false;
    }
    if (gatewayDb.empty()) {
        gatewayDb = a5Db + ".gateway.db";
    }
    if (workerThreads < 1) {
        workerThreads = 1;
    }
    if (tokenTtl < 10) {
        tokenTtl = 10;
    }
    return true;
}

}  // namespace cfg
