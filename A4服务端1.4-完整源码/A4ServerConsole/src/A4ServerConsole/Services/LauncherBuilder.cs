using System;
using System.Collections.Generic;
using System.IO;
using System.Reflection;
using System.Text;
using A4ServerConsole.Models;

namespace A4ServerConsole.Services
{
    public sealed class LauncherBuildResult
    {
        public bool Success { get; set; }
        public string Message { get; set; } = "";
        public string OutputDir { get; set; } = "";
        public string IniPath { get; set; } = "";
        public string ExePath { get; set; } = "";
        public bool IniEncrypted { get; set; }
        public bool ExeIncluded { get; set; }
        public bool PatchDllIncluded { get; set; }
    }

    /// <summary>
    /// 使用内置成品模板将配套登录器写入单一目录。
    /// </summary>
    public static class LauncherBuilder
    {
        private const string IniName = "launcher.ini";
        private const string ExeName = "A4Launcher.exe";
        private static readonly string[] PackageFiles = { ExeName, "a4popuppatch.dll", ExeName + ".config", IniName };

        public static string BuildIniContent(ConsoleConfig cfg)
        {
            string host = cfg.EffectiveHost;
            var sb = new StringBuilder();
            sb.AppendLine("; 由 A4ServerConsole 自动生成于 " + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss"));
            sb.AppendLine("; 模式: " + (cfg.IsPublicMode ? "公网模式" : "本机单机模式"));
            sb.AppendLine();
            sb.AppendLine("[gateway]");
            sb.AppendLine("host = " + host);
            sb.AppendLine("port = " + cfg.GatewayPort);
            sb.AppendLine();
            sb.AppendLine("[game]");
            sb.AppendLine("; 留空 = 使用网关登录时下发的地址；此处同时写入兜底端口");
            sb.AppendLine("host = " + host);
            sb.AppendLine("port = " + cfg.EntrancePort);
            sb.AppendLine();
            sb.AppendLine("[login]");
            sb.AppendLine("region = " + cfg.LauncherRegion);
            sb.AppendLine("server = " + cfg.LauncherServerName);
            sb.AppendLine("server_state = " + cfg.LauncherServerState);
            sb.AppendLine();
            sb.AppendLine("[links]");
            sb.AppendLine("website = " + cfg.LinkWebsite);
            sb.AppendLine("helper = " + cfg.LinkHelper);
            sb.AppendLine("shop = " + cfg.LinkShop);
            sb.AppendLine("forum = " + cfg.LinkForum);
            sb.AppendLine("service = " + cfg.LinkService);
            sb.AppendLine();
            sb.AppendLine("[regions]");
            sb.AppendLine("telecom = " + cfg.LauncherTelecomRegions);
            sb.AppendLine("netcom = " + cfg.LauncherNetcomRegions);
            sb.AppendLine();
            sb.AppendLine("[servers]");
            sb.AppendLine("list = " + cfg.LauncherServerList);
            sb.AppendLine();
            sb.AppendLine("[debug]");
            sb.AppendLine("allow_multi = 0");
            return sb.ToString();
        }

        public static string BuildIniFileContent(ConsoleConfig cfg)
            => LauncherIniCipher.Encrypt(BuildIniContent(cfg)) + "\r\n";

        public static string DefaultOutputDirectory => Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "配套登录器");

        // 仅完整游戏目录可作为预填目标；服务端自带的 client 通常只有 Script.pvf。
        public static string GetPreferredClientDirectory(ConsoleConfig cfg)
        {
            if (!string.IsNullOrWhiteSpace(cfg.ClientDir))
            {
                string clientDir = Path.GetFullPath(cfg.ClientDir.Trim());
                if (File.Exists(Path.Combine(clientDir, "DNF.exe")) && File.Exists(Path.Combine(clientDir, "Script.pvf")))
                    return clientDir;
            }
            return "";
        }

        public static string ResolveOutputDirectory(string path)
            => string.IsNullOrWhiteSpace(path) ? DefaultOutputDirectory : Path.GetFullPath(path.Trim());

        public static string[] GetExistingOutputFiles(string outputDir) => FindExistingFiles(outputDir);

        public static LauncherBuildResult Build(ConsoleConfig cfg, string outDir, bool allowOverwrite = false)
        {
            var result = new LauncherBuildResult();
            string? backupDir = null;
            try
            {
                string[] existingFiles = FindExistingFiles(outDir);
                if (existingFiles.Length > 0 && !allowOverwrite)
                    throw new InvalidOperationException("生成位置已有登录器文件，请确认覆盖后重试。");

                // 先读取并校验完整的内置成品，避免模板缺失时写出不完整的登录器。
                var contents = new Dictionary<string, byte[]>(StringComparer.OrdinalIgnoreCase)
                {
                    [IniName] = Encoding.UTF8.GetBytes(BuildIniFileContent(cfg))
                };
                foreach (string name in PackageFiles)
                {
                    if (name != IniName)
                        contents.Add(name, ReadEmbedded(name));
                }
                // 程序字符串中的 UTF-16 标记用于避免把加密配置交给不支持解密的模板。
                byte[] exeBytes = contents[ExeName];
                if (exeBytes.Length < LauncherIniCipher.Magic.Length * 2 ||
                    (!Encoding.Unicode.GetString(exeBytes).Contains(LauncherIniCipher.Magic) &&
                     !Encoding.Unicode.GetString(exeBytes, 1, exeBytes.Length - 1).Contains(LauncherIniCipher.Magic)))
                    throw new InvalidOperationException("内置登录器模板不支持加密配置，请更新控制台中的登录器模板。");

                if (existingFiles.Length > 0)
                {
                    string backupName = DateTime.Now.ToString("yyyyMMdd-HHmmss") + "-" + Guid.NewGuid().ToString("N").Substring(0, 8);
                    backupDir = Path.Combine(outDir, "LauncherBackups", backupName);
                    Directory.CreateDirectory(backupDir);
                    foreach (string oldFile in existingFiles)
                        File.Copy(oldFile, Path.Combine(backupDir, Path.GetFileName(oldFile)));
                }

                WritePackage(outDir, contents, new HashSet<string>(existingFiles, StringComparer.OrdinalIgnoreCase));

                result.OutputDir = outDir;
                result.IniPath = Path.Combine(outDir, IniName);
                result.ExePath = Path.Combine(outDir, ExeName);
                result.IniEncrypted = result.ExeIncluded = result.PatchDllIncluded = result.Success = true;
                var msg = new StringBuilder();
                msg.AppendLine("配套登录器制作完成。");
                msg.AppendLine($"连接地址：{cfg.EffectiveHost}:{cfg.GatewayPort}（游戏入口 {cfg.EntrancePort}）");
                msg.AppendLine("生成位置：" + outDir);
                if (backupDir != null)
                    msg.AppendLine("原文件备份：" + backupDir);
                result.Message = msg.ToString().TrimEnd();
            }
            catch (Exception ex)
            {
                result.Message = "生产配套登录器失败：" + ex.Message;
                if (backupDir != null)
                    result.Message += "\n客户端文件备份目录：" + backupDir;
            }
            return result;
        }

        private static string[] FindExistingFiles(string dir)
        {
            var existing = new List<string>();
            foreach (string name in PackageFiles)
            {
                string path = Path.Combine(dir, name);
                if (File.Exists(path)) existing.Add(path);
            }
            return existing.ToArray();
        }

        private static byte[] ReadEmbedded(string name)
        {
            using var resource = Assembly.GetExecutingAssembly().GetManifestResourceStream("EmbeddedTemplates." + name)
                ?? throw new InvalidOperationException("控制台缺少内置模板：" + name);
            using var buffer = new MemoryStream();
            resource.CopyTo(buffer);
            if (buffer.Length == 0)
                throw new InvalidOperationException("控制台内置模板为空：" + name);
            return buffer.ToArray();
        }

        private static void WritePackage(string dir, Dictionary<string, byte[]> contents, HashSet<string>? backedUp = null)
        {
            Directory.CreateDirectory(dir);
            foreach (string name in PackageFiles)
            {
                string file = Path.Combine(dir, name);
                string temp = file + "." + Guid.NewGuid().ToString("N") + ".tmp";
                try
                {
                    File.WriteAllBytes(temp, contents[name]);
                    if (File.Exists(file) && (backedUp == null || backedUp.Contains(file)))
                        File.Replace(temp, file, null);
                    else
                        File.Move(temp, file); // 不覆盖确认之后才出现的新客户端文件。
                }
                finally
                {
                    if (File.Exists(temp)) File.Delete(temp);
                }
            }
        }
    }
}
