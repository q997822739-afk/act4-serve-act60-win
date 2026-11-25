// 花枝 6 系网关的包格式：明文文本，<hz> ... </hz> 包裹，内部是 <键>值</键>。
// 本文件是 CMapObj::get 的等价实现（v2-cpp/Utils/MapObj.cpp），只读不加密。
#pragma once

#include <string>

namespace proto {

// 从 data 里取 <key>...</key> 之间的内容；没有返回空串（和 CMapObj 一样）
std::string field(const std::string& data, const std::string& key);

// 从按顺序读到的粘包缓冲里切出所有完整的 <hz>...</hz>，剩余部分留在 buffer
void drain_frames(std::string& buffer, std::string& out_payloads);

// 拼一条下发给登录器的包：<hz>命令|正文</hz>
std::string frame(const std::string& command, const std::string& body);

// 转义：正文里如果出现 '<' 会把客户端解析弄乱，统一替换成全角/实体
std::string escape(std::string text);

}  // namespace proto
