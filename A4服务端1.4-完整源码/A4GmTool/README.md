# A4GmTool — A4 网页 GM 工具（本地版）

本机 GM 网页服务。由 A4 服务端控制台启动时会在后台运行，也可以单独运行。

## 怎么用

1. 双击 `dist\A4GmTool\A4GmTool.exe`，或在终端运行它。不启动浏览器，也没有托盘或内嵌浏览器。
2. 看到启动地址后，手动打开 **http://127.0.0.1:60694/**。
3. 在服务窗口按 **Ctrl+C** 停止。关闭网页不会停止服务。

服务只监听 `127.0.0.1`，不要通过代理或端口转发暴露到公网。
程序会向上查找本目录及同级 `A4ServerRelease` 中的数据；找不到可以用启动参数或网页「环境」指定：

| 需要的文件 | 默认查找位置 |
|---|---|
| `s1a5.db` | exe 所在目录、`data\`、`server\data\` |
| `Script.pvf` | exe 所在目录、`client\`、`server\client\` |
| `ImagePacks2\`（可选，只影响图标） | 同上 |

## 启动参数

```
A4GmTool.exe [--port 60694] [--game-port 10011] [--db <s1a5.db>] [--pvf <Script.pvf>]
             [--imagepacks <目录;目录>] [--account <账号>]
```

- 默认固定为 `60694`，被占用就报错退出，不会悄悄改用别的端口。仅在显式指定 `--port 0` 时才使用随机空闲端口。
- `--game-port` 用来探测「服务端在不在跑」，决定发放走在线邮箱还是直接写库。
- `--account` 只显示这个账号的角色（不填就显示全部）。

## 技术栈

- **.NET Framework 3.5**，C# 7.3，x86
- 控制台进程只负责启动和停止 HTTP 服务；网页由你自己的浏览器访问
- 数据库：直接 P/Invoke 原生 `e_sqlite3.dll`（1.4 MB，x86），不依赖任何 SQLite 托管包
- JSON：`Newtonsoft.Json`（net35 版本）
- HTTP：自己写的裸 `TcpListener`（**不用 HttpListener** —— 那个要管理员权限或预先注册 urlacl，普通玩家双击会直接「拒绝访问」）

## 功能（与原版一致）

- 角色数值：等级 / 金币 / 点券 / SP / 疲劳 / 决斗胜点 / 职业与转职
- 物品目录：从 `Script.pvf` 解析出 **13,988 件**物品并按 ID 显示**中文名**
- 发放物品 / 批量发放 / 时装 / 一键配装
- 背包 / 穿戴 / 水晶库 / 宠物栏 / 邮箱查看与编辑
- 技能栏、任务（标记可交 / 通关 / 一键完成 / 补发材料）
- GM 名单、操作日志、环境诊断

## 写库安全（照搬原版语义）

- **发放物品 / 时装**：服务端在跑也能用 —— 走邮箱（`postal` 表）或直接写装扮栏，不用停服。
- **改数值 / 背包 / 技能 / 任务**：服务端在跑就拒写，除非确认角色已小退到选择界面。
  - 有 `server_presence` 表时**自动**判断角色在不在游戏里；
  - 老服务端没有这张表才退回手动「已小退」开关。
- 每次写库前**自动备份** `s1a5.db` 到 `gm-backups\`。

## 重新编译

```
build.cmd
```

需要 Visual Studio 2022（或 Build Tools）。产物在 `dist\A4GmTool\`。

## 目录结构

```
A4GmTool/
├─ build.cmd                       一键编译 + 打包
├─ ico.ico
├─ vendor/
│  ├─ net35/Newtonsoft.Json.dll
│  └─ sqlite/e_sqlite3.dll         x86 原生 SQLite
├─ web/                            网页前端
│  └─ index.html  app.js  style.css  icon.png  favicon.ico
├─ src/
│  ├─ A4GmTool.Core/               核心库（.NET 3.5）
│  │  ├─ Data/                     Sqlite P/Invoke、存档 blob 编解码、61 字节物品格
│  │  ├─ Pvf/                      PVF 包解析、字符串表、物品脚本
│  │  ├─ Img/                      ImagePacks2 图标（找不到就显示占位）
│  │  ├─ Services/                 环境、目录、离线写库、在线邮箱、一键配装、API 路由
│  │  └─ Web/                      HTTP 服务
│  └─ A4GmTool.Host/               控制台入口
└─ dist/A4GmTool/                  绿色发布目录
```

## 已知限制

- **图标**：本地 `ImagePacks2` 为空时显示 `?` 占位（不影响发放）。把客户端的 `ImagePacks2` 放上去就会出真图标。
- **技能名 / 任务名**：暂时显示编号，还没接 PVF 里的技能表与任务脚本。
- **一键配装**：按「同职业 + 等级不超限 + 品级最高」挑装备，不是原版的套装检索。
