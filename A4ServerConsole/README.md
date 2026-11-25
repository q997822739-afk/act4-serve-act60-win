# A4ServerConsole — A4 游戏服务端 Windows 控制台

独立于原服务端源码的 **C# WPF (`.NET Framework 4.8`)** 服务器控制台，专为 Windows 服务器与个人单机/外网开服设计，原生兼容 **Win7 SP1 ~ Win11** 以及 **Windows Server 2012 / 2016 / 2019 / 2022**。

## 核心功能

1. **单机模式 / 公网模式一键切换**
   - **本机模式 (`127.0.0.1`)**：自动将游戏服 `-Ds1a5.peer.host` / `-Ds1a5.entrance.host`、登录器网关切回本机，适合单机调试。
   - **公网模式 (自定义 IP + 自动识别)**：支持一键自动识别服务器公网 IP，自动同步到游戏服启动参数（同时触发 `7001` 组队 UDP 中转）与登录器网关。
2. **登录器网关完整迁移到 Windows（端口 `8188`）**
   - 完整移植原 Linux C++ `登录器/gateway` 到 C#，100% 兼容 `<hz>...</hz>` 协议与 `data/s1a5.db`、`data/gateway.db`。
   - 支持注册、登录发一次性令牌、改密、CDK 校验、公告、版本接口与单 IP QPS 限流，**彻底不再需要 Linux 服务器**。
3. **生产配套登录器**
   - **生成为补丁**：把 `launcher.ini`、`A4Launcher.exe`、`A4Launcher.exe.config` 和 `a4popuppatch.dll` 输出到补丁文件夹，默认也同步到已配置的客户端目录。
   - **制作成一体客户端**：直接把这四个文件写入「路径与端口设置」中的客户端目录，不再另建补丁文件夹，也不复制整个游戏。覆盖客户端旧文件前会提示并备份到客户端的 `LauncherBackups`。
   - 始终使用内置本体和补丁，固定加密 `launcher.ini`，关闭调试多开。
4. **角色存档备份与恢复**
   - 首次启动若无备份自动创建初始备份；安全打包/恢复 `s1a5.db`（含 `-wal`/`-shm` 与 `gateway.db`），恢复前强制检查停服并自动留存快照。
5. **网页 GM（可选）**
   - 勾选「同时启动网页 GM 工具」并设置密码后，GM 才会随游戏服启动，也可单独点「启动 GM」。启动后需要先在浏览器输入密码，才能进入 GM 页面或访问 GM API。密码在配置文件中由当前 Windows 用户的 DPAPI 加密，换电脑或用户后需重新输入。
   - **本机模式**：访问 `http://127.0.0.1:60694/`（默认端口，可在「路径与端口设置」修改）。GM 仅监听本机，不开放给局域网或公网。
   - **公网模式**：显示 `https://<公网 IP>/`。请在同一台服务器配置 HTTPS 反向代理，把请求转到 `http://127.0.0.1:60694/`（按实际 GM 端口修改），保持请求的 Host 不变，使用浏览器信任且匹配公网地址的证书。只开放代理的 TCP 443，不直接对外转发 GM 端口；建议额外限制来源 IP 或 VPN。切换模式不会自动安装代理或申请证书。
   - GM 在后台运行，不自动开浏览器；停止服务或关闭控制台时一并停止。

## 编译方法

本机需安装 **Visual Studio 2022**（勾选「.NET 桌面开发」）或 **.NET SDK**（如 .NET 8 SDK），然后：

1. 直接双击 `build.cmd`，或用 Visual Studio 打开 `A4ServerConsole.sln` 生成 `Release` 版本；
2. **登录器本体**：`src\A4Launcher\A4Launcher.csproj` 是登录器源码（已带加密 launcher.ini 支持），编译产物需要复制到 `src\A4ServerConsole\EmbeddedTemplates\A4Launcher.exe` 才会被打进控制台；这一步 `package_release.ps1` 会自动做；
3. **发版**：在 PowerShell 里跑 `powershell -ExecutionPolicy Bypass -File package_release.ps1 -OutputDir ..\A4ServerRelease-New`，会依次编译登录器、控制台和网页 GM，再写入新发布目录；不会覆盖已有的发布目录或存档；
4. **如需在 Win7 上运行 Java 游戏服务端**：将原服务端 `pom.xml` 中的 `<maven.compiler.release>25</maven.compiler.release>` 改为 `17` 后用 JDK 17 打包即可（Win7 玩家端只需运行 `A4Launcher.exe` + `DNF.exe`，本来就不依赖 Java）。

> 注意：`package_release.ps1` / `release_guide.txt` 必须保存为 **UTF-8 带 BOM**，否则 Windows PowerShell 5.1 会把中文读成乱码导致路径解析失败。

## 公网开服防火墙 / 云安全组放行端口

- **TCP**：`7001`（频道入口）、`7002-7004`（A4 频道游戏服）、`10011`（默认游戏服）、`8188`（登录器网关）
- **UDP**：`7001`（组队中转）、`7002-7004` / `10011`（NAT 探测）
