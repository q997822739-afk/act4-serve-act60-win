# A4 服务端 1.4 · Windows 完整源码

- `JavaServer/`：游戏服务端 Java 源码（包含 1.4 角斗场切频道修复和回归测试）、旧版网关及客户端辅助组件源码。
- `A4ServerConsole/`：Windows WPF 控制台、内置登录网关和 Windows 登录器源码。
- `A4GmTool/`：GM 工具源码和 Web 界面。

本包不含 PVF、游戏客户端、运行数据库、真实部署配置、日志、JRE 或成品服务端。保留工程直接引用的构建依赖和嵌入式模板文件。需要运行时，请自行提供兼容的 Script.pvf。

## 构建

- Java 服务端：在 `JavaServer` 下用 JDK 21 和 Maven 执行 `mvn -Dmaven.compiler.release=21 clean package`。输出位于 `target`，部署用带 `-all.jar` 后缀的文件。
- Windows 登录器、控制台：使用 Visual Studio 2022 MSBuild，先以 Release/x86 构建 `A4ServerConsole/src/A4Launcher/A4Launcher.csproj`，再以 Release 构建 `A4ServerConsole/A4ServerConsole.sln`。修改登录器后，需要用新登录器替换控制台项目 `EmbeddedTemplates` 中的模板再编译控制台。原有 `package_release.ps1` 是成品打包脚本，依赖本机路径和 PVF，不是源码编译的必需步骤。
- GM 工具：参阅 `A4GmTool/README.md`，按该工程的 `build.cmd` 构建。