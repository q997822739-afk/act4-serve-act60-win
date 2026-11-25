#include "proto.h"

namespace proto {

std::string field(const std::string& data, const std::string& key) {
    const std::string open = "<" + key + ">";
    const std::string close = "</" + key + ">";
    std::string::size_type a = data.find(open);
    if (a == std::string::npos) {
        return "";
    }
    a += open.size();
    std::string::size_type b = data.find(close, a);
    if (b == std::string::npos) {
        return "";
    }
    return data.substr(a, b - a);
}

void drain_frames(std::string& buffer, std::string& out_payloads) {
    static const std::string kOpen = "<hz>";
    static const std::string kClose = "</hz>";
    for (;;) {
        std::string::size_type a = buffer.find(kOpen);
        if (a == std::string::npos) {
            // 没有包头：只留可能是半个包头的尾巴，其余丢掉，防止内存无限涨
            if (buffer.size() > kOpen.size()) {
                buffer.erase(0, buffer.size() - (kOpen.size() - 1));
            }
            return;
        }
        std::string::size_type b = buffer.find(kClose, a + kOpen.size());
        if (b == std::string::npos) {
            // 半包：从包头开始留下，等下次读完
            if (a > 0) {
                buffer.erase(0, a);
            }
            return;
        }
        out_payloads += buffer.substr(a + kOpen.size(), b - a - kOpen.size());
        out_payloads += '\n';  // 分隔，正文里不会有换行（入库前会过滤）
        buffer.erase(0, b + kClose.size());
    }
}

std::string frame(const std::string& command, const std::string& body) {
    return "<hz>" + command + "|" + body + "</hz>";
}

std::string escape(std::string text) {
    std::string out;
    out.reserve(text.size());
    for (char ch : text) {
        switch (ch) {
            case '<': out += "&lt;"; break;
            case '>': out += "&gt;"; break;
            case '|': out += "&#124;"; break;
            case '\r': case '\n': case '\t': out += ' '; break;
            default: out += ch;
        }
    }
    return out;
}

}  // namespace proto
