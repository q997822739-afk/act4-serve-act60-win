using System;
using System.IO;
using System.Text;
using System.Web.Script.Serialization;

namespace A4ServerConsole.Models
{
    public sealed class ConsoleConfig
    {
        // 运行模式：false = 本机模式 (127.0.0.1)，true = 公网模式 (PublicIp)
        public bool IsPublicMode { get; set; } = false;
        public string PublicIp { get; set; } = "";

        // 路径配置（支持自动探测）
        public string ServerRootDir { get; set; } = "";
        public string JavaExePath { get; set; } = "java";
        public string ServerJarPath { get; set; } = "";
        public string ClientDir { get; set; } = "";
        public string DbPath { get; set; } = "";
        public string GatewayDbPath { get; set; } = "";
        public string BackupDir { get; set; } = "";

        // 游戏服端口与参数
        public int GamePort { get; set; } = 10011;
        public int EntrancePort { get; set; } = 7001;
        public string A4Channels { get; set; } = "1:7002,6:7003,51:7004";
        public string JvmArgs { get; set; } = "-Xms256m -Xmx1g -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8";
        public bool UseTokenLogin { get; set; } = true;
        public bool AllowPasswordFallback { get; set; } = true;
        public bool AutoCreateAccount { get; set; } = true;

        // 移植版登录器网关配置（原 Linux gateway）
        public bool EnableGateway { get; set; } = true;
        public int GatewayPort { get; set; } = 8188;
        public int TokenTtlSeconds { get; set; } = 60;
        public int GatewayQpsPerIp { get; set; } = 30;
        public string GatewayUpdateDir { get; set; } = "update";

        public bool EnableGm { get; set; }
        public int GmPort { get; set; } = 60694;
        public string GmPasswordProtected { get; set; } = "";

        public string ReadGmPassword()
        {
            if (string.IsNullOrEmpty(GmPasswordProtected)) return "";
            try
            {
                return Encoding.UTF8.GetString(System.Security.Cryptography.ProtectedData.Unprotect(
                    Convert.FromBase64String(GmPasswordProtected), null, System.Security.Cryptography.DataProtectionScope.CurrentUser));
            }
            catch (System.Security.Cryptography.CryptographicException) { return ""; }
            catch (FormatException) { return ""; }
        }

        public void SetGmPassword(string password)
        {
            GmPasswordProtected = string.IsNullOrWhiteSpace(password) ? "" : Convert.ToBase64String(
                System.Security.Cryptography.ProtectedData.Protect(Encoding.UTF8.GetBytes(password), null,
                    System.Security.Cryptography.DataProtectionScope.CurrentUser));
        }

        // 一键生成登录器配置
        public string LauncherTemplateExe { get; set; } = "";
        public string LauncherPopupDll { get; set; } = "";
        public string LauncherOutputExeName { get; set; } = "A4Launcher.exe";
        public string LauncherRegion { get; set; } = "华南区 / 广东一区";
        public string LauncherServerName { get; set; } = "广东一区";
        public string LauncherServerState { get; set; } = "空闲";
        public string LauncherTelecomRegions { get; set; } = "华南区";
        public string LauncherNetcomRegions { get; set; } = "华南区";
        public string LauncherServerList { get; set; } = "广东一区|空闲";
        public string LinkWebsite { get; set; } = "";
        public string LinkHelper { get; set; } = "";
        public string LinkShop { get; set; } = "";
        public string LinkForum { get; set; } = "";
        public string LinkService { get; set; } = "";
        // 生成的 launcher.ini 是否加密（玩家看不到明文 IP / 端口）
        public bool LauncherIniEncrypt { get; set; } = true;
        public bool LauncherSyncToClientDir { get; set; } = true;
        public bool LauncherAllowMulti { get; set; } = false;

        public string EffectiveHost =>
            IsPublicMode && !string.IsNullOrWhiteSpace(PublicIp)
                ? PublicIp.Trim()
                : "127.0.0.1";

        public static string ConfigFilePath =>
            Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "console-config.json");

        public static ConsoleConfig Load()
        {
            ConsoleConfig cfg = new ConsoleConfig();
            try
            {
                if (File.Exists(ConfigFilePath))
                {
                    var json = File.ReadAllText(ConfigFilePath, Encoding.UTF8);
                    var serializer = new JavaScriptSerializer { MaxJsonLength = int.MaxValue };
                    var loaded = serializer.Deserialize<ConsoleConfig>(json);
                    if (loaded != null)
                    {
                        cfg = loaded;
                    }
                }
            }
            catch
            {
                // 配置损坏时回退默认值
            }

            cfg.ApplyAutoDetectedDefaults();
            return cfg;
        }

        public void Save()
        {
            var serializer = new JavaScriptSerializer { MaxJsonLength = int.MaxValue };
            var json = serializer.Serialize(this);
            File.WriteAllText(ConfigFilePath, FormatJson(json), new UTF8Encoding(false));
        }

        public void ApplyAutoDetectedDefaults()
        {
            string baseDir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd('\\', '/');

            // 优先识别“绿色成品便携布局”：同目录下自带 server\s1a5-0.1.0-SNAPSHOT-all.jar
            bool isPortableLayout = File.Exists(Path.Combine(baseDir, "server", "s1a5-0.1.0-SNAPSHOT-all.jar"));

            // 自动查找服务端根目录（绿色成品目录优先，其次上一级、或开发源码目录）
            if (isPortableLayout)
            {
                if (string.IsNullOrWhiteSpace(ServerRootDir) || !Directory.Exists(ServerRootDir) ||
                    !File.Exists(Path.Combine(ServerRootDir, "server", "s1a5-0.1.0-SNAPSHOT-all.jar")))
                {
                    ServerRootDir = baseDir;
                }
            }
            else if (string.IsNullOrWhiteSpace(ServerRootDir) || !Directory.Exists(ServerRootDir))
            {
                string[] candidates =
                {
                    baseDir,
                    Path.GetFullPath(Path.Combine(baseDir, "..")),
                    Path.GetFullPath(Path.Combine(baseDir, "..", "server")),
                    @"H:\60ACT4项目\唐泽雪穗狐2K神力A4修复外网服务端源码_20261118"
                };
                foreach (var dir in candidates)
                {
                    if (Directory.Exists(dir) &&
                        (File.Exists(Path.Combine(dir, "pom.xml")) ||
                         File.Exists(Path.Combine(dir, "target", "s1a5-0.1.0-SNAPSHOT-all.jar")) ||
                         File.Exists(Path.Combine(dir, "server", "s1a5-0.1.0-SNAPSHOT-all.jar"))))
                    {
                        ServerRootDir = dir;
                        break;
                    }
                }
                if (string.IsNullOrWhiteSpace(ServerRootDir))
                {
                    ServerRootDir = baseDir;
                }
            }

            if (string.IsNullOrWhiteSpace(ClientDir) || !File.Exists(Path.Combine(ClientDir, "Script.pvf")))
            {
                string c0 = Path.Combine(baseDir, "client");
                string c1 = Path.Combine(ServerRootDir, "client");
                string c2 = Path.GetFullPath(Path.Combine(ServerRootDir, "..", "ACT5"));
                if (File.Exists(Path.Combine(c0, "Script.pvf")))
                {
                    ClientDir = c0;
                }
                else if (File.Exists(Path.Combine(c1, "Script.pvf")))
                {
                    ClientDir = c1;
                }
                else if (File.Exists(Path.Combine(c2, "Script.pvf")))
                {
                    ClientDir = c2;
                }
                else
                {
                    // 自动扫描上级目录的同级客户端文件夹（例如 DNF60A4_纯净客户端）
                    string? parent = Directory.GetParent(ServerRootDir)?.FullName;
                    string? foundClient = null;
                    if (!string.IsNullOrEmpty(parent) && Directory.Exists(parent))
                    {
                        foreach (var sub in Directory.GetDirectories(parent))
                        {
                            if (File.Exists(Path.Combine(sub, "Script.pvf")))
                            {
                                foundClient = sub;
                                break;
                            }
                        }
                    }
                    ClientDir = foundClient ?? c1;
                }
            }

            if (string.IsNullOrWhiteSpace(ServerJarPath) || !File.Exists(ServerJarPath))
            {
                string portableJar = Path.Combine(baseDir, "server", "s1a5-0.1.0-SNAPSHOT-all.jar");
                string serverJar = Path.Combine(ServerRootDir, "server", "s1a5-0.1.0-SNAPSHOT-all.jar");
                string targetJar = Path.Combine(ServerRootDir, "target", "s1a5-0.1.0-SNAPSHOT-all.jar");
                ServerJarPath = File.Exists(portableJar) ? portableJar : (File.Exists(serverJar) ? serverJar : targetJar);
            }

            if (string.IsNullOrWhiteSpace(DbPath) || !Directory.Exists(Path.GetDirectoryName(DbPath) ?? ""))
            {
                DbPath = Path.Combine(ServerRootDir, "data", "s1a5.db");
            }

            if (string.IsNullOrWhiteSpace(GatewayDbPath) || !Directory.Exists(Path.GetDirectoryName(GatewayDbPath) ?? ""))
            {
                GatewayDbPath = Path.Combine(ServerRootDir, "data", "gateway.db");
            }

            if (string.IsNullOrWhiteSpace(BackupDir) || !Directory.Exists(Path.GetDirectoryName(BackupDir) ?? ""))
            {
                BackupDir = Path.Combine(ServerRootDir, "data", "backups");
            }

            if (string.IsNullOrWhiteSpace(JavaExePath) || JavaExePath == "java" || !File.Exists(JavaExePath))
            {
                string? detectedJava = DetectJava21Plus(baseDir, ServerRootDir);
                if (!string.IsNullOrEmpty(detectedJava))
                {
                    JavaExePath = detectedJava!;
                }
                else if (string.IsNullOrWhiteSpace(JavaExePath))
                {
                    JavaExePath = "java";
                }
            }

            // 登录器模板已内置到控制台 exe 资源中，留空即可直接生成完整成品；此处仅清理失效的旧路径
            if (!string.IsNullOrWhiteSpace(LauncherTemplateExe) && !File.Exists(LauncherTemplateExe))
            {
                LauncherTemplateExe = "";
            }
            if (!string.IsNullOrWhiteSpace(LauncherPopupDll) && !File.Exists(LauncherPopupDll))
            {
                LauncherPopupDll = "";
            }
        }

        private static string? DetectJava21Plus(string baseDir, string serverRootDir)
        {
            string[] exactCandidates =
            {
                Path.Combine(baseDir, "jre", "bin", "java.exe"),
                Path.Combine(baseDir, "jdk25", "bin", "java.exe"),
                Path.Combine(baseDir, "jdk21", "bin", "java.exe"),
                Path.Combine(serverRootDir, "jre", "bin", "java.exe"),
                Path.Combine(serverRootDir, "jdk25", "bin", "java.exe"),
                Path.Combine(serverRootDir, "tools", "jdk25", "bin", "java.exe"),
                Path.Combine(serverRootDir, "jdk21", "bin", "java.exe")
            };
            foreach (var p in exactCandidates)
            {
                if (File.Exists(p)) return p;
            }

            string[] scanRoots =
            {
                @"C:\Dev\java",
                @"C:\Program Files\Java",
                @"C:\Program Files\Eclipse Adoptium",
                @"C:\Program Files\Microsoft"
            };
            foreach (var root in scanRoots)
            {
                if (!Directory.Exists(root)) continue;
                try
                {
                    foreach (var dir in Directory.GetDirectories(root))
                    {
                        string name = Path.GetFileName(dir);
                        if (name.IndexOf("25", StringComparison.OrdinalIgnoreCase) >= 0 ||
                            name.IndexOf("21", StringComparison.OrdinalIgnoreCase) >= 0)
                        {
                            string exe = Path.Combine(dir, "bin", "java.exe");
                            if (File.Exists(exe)) return exe;
                        }
                    }
                }
                catch { }
            }
            return null;
        }

        private static string FormatJson(string raw)
        {
            var sb = new StringBuilder(raw.Length * 2);
            int indent = 0;
            bool inString = false;
            bool escaped = false;
            foreach (char c in raw)
            {
                if (escaped)
                {
                    sb.Append(c);
                    escaped = false;
                    continue;
                }
                if (c == '\\' && inString)
                {
                    sb.Append(c);
                    escaped = true;
                    continue;
                }
                if (c == '"')
                {
                    inString = !inString;
                    sb.Append(c);
                    continue;
                }
                if (!inString)
                {
                    if (c == '{' || c == '[')
                    {
                        sb.Append(c).AppendLine().Append(new string(' ', ++indent * 2));
                        continue;
                    }
                    if (c == '}' || c == ']')
                    {
                        sb.AppendLine().Append(new string(' ', Math.Max(0, --indent) * 2)).Append(c);
                        continue;
                    }
                    if (c == ',')
                    {
                        sb.Append(c).AppendLine().Append(new string(' ', indent * 2));
                        continue;
                    }
                    if (c == ':')
                    {
                        sb.Append(": ");
                        continue;
                    }
                }
                sb.Append(c);
            }
            return sb.ToString();
        }
    }
}
