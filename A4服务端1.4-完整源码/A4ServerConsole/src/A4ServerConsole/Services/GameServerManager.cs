using System;
using System.Diagnostics;
using System.IO;
using System.Text;
using A4ServerConsole.Models;

namespace A4ServerConsole.Services
{
    public sealed class GameServerManager : IDisposable
    {
        private readonly Action<string> _log;
        private Process? _process;

        public event Action<int?>? StateChanged;

        public bool IsRunning
        {
            get
            {
                try { return _process != null && !_process.HasExited; }
                catch { return false; }
            }
        }

        public int? ProcessId
        {
            get
            {
                try { return IsRunning ? _process!.Id : (int?)null; }
                catch { return null; }
            }
        }

        public GameServerManager(Action<string> log)
        {
            _log = log;
        }

        public static string BuildArguments(ConsoleConfig cfg)
        {
            string host = cfg.EffectiveHost;
            string dbUrl = "jdbc:sqlite:" + cfg.DbPath.Replace('\\', '/');
            string logDir = Path.Combine(cfg.ServerRootDir, "logs");
            string loginMode = cfg.UseTokenLogin ? "token" : "password";

            var sb = new StringBuilder();
            if (!string.IsNullOrWhiteSpace(cfg.JvmArgs))
            {
                sb.Append(cfg.JvmArgs.Trim()).Append(' ');
            }
            sb.Append("-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 ");
            sb.Append($"\"-Ds1a5.port={cfg.GamePort}\" ");
            sb.Append($"\"-Ds1a5.entrance.port={cfg.EntrancePort}\" ");
            sb.Append($"\"-Ds1a5.a4.channels={cfg.A4Channels}\" ");
            sb.Append($"\"-Ds1a5.peer.host={host}\" ");
            sb.Append($"\"-Ds1a5.entrance.host={host}\" ");
            sb.Append($"\"-Ds1a5.clientDir={cfg.ClientDir}\" ");
            sb.Append($"\"-Ds1a5.dbUrl={dbUrl}\" ");
            sb.Append($"\"-Ds1a5.logDir={logDir}\" ");
            sb.Append($"\"-Ds1a5.loginMode={loginMode}\" ");
            sb.Append($"-Ds1a5.loginPasswordFallback={(cfg.AllowPasswordFallback ? "1" : "0")} ");
            sb.Append($"-Ds1a5.dbAutoCreateAccount={(cfg.AutoCreateAccount ? "1" : "0")} ");
            sb.Append($"-jar \"{cfg.ServerJarPath}\" {cfg.GamePort}");
            return sb.ToString();
        }

        public static string BuildPreviewLine(ConsoleConfig cfg)
        {
            string mode = cfg.IsPublicMode ? "公网模式" : "本机模式";
            string host = cfg.EffectiveHost;
            string gw = cfg.EnableGateway ? $" | gateway:{cfg.GatewayPort}" : "";
            return $"当前模式: {mode} → -Ds1a5.peer.host={host} -Ds1a5.entrance.host={host} -port {cfg.EntrancePort}/{cfg.GamePort}{gw}";
        }

        public bool Start(ConsoleConfig cfg, out string error)
        {
            error = "";
            if (IsRunning)
            {
                error = "游戏服务端已在运行中";
                return false;
            }

            if (string.IsNullOrWhiteSpace(cfg.ServerJarPath) || !File.Exists(cfg.ServerJarPath))
            {
                error = "找不到服务端 JAR 文件：\n" + cfg.ServerJarPath + "\n\n请先在「路径与端口设置」中指定正确的 s1a5-0.1.0-SNAPSHOT-all.jar。";
                return false;
            }

            string pvfPath = Path.Combine(cfg.ClientDir, "Script.pvf");
            if (!File.Exists(pvfPath))
            {
                error = "找不到客户端 Script.pvf：\n" + pvfPath + "\n\n请在「路径与端口设置」中指向包含 Script.pvf 的客户端目录。";
                return false;
            }

            string? dbDir = Path.GetDirectoryName(cfg.DbPath);
            if (!string.IsNullOrEmpty(dbDir) && !Directory.Exists(dbDir))
            {
                Directory.CreateDirectory(dbDir);
            }

            string logDir = Path.Combine(cfg.ServerRootDir, "logs");
            if (!Directory.Exists(logDir))
            {
                Directory.CreateDirectory(logDir);
            }

            string args = BuildArguments(cfg);
            string javaExe = string.IsNullOrWhiteSpace(cfg.JavaExePath) ? "java" : cfg.JavaExePath.Trim();

            try
            {
                var psi = new ProcessStartInfo
                {
                    FileName = javaExe,
                    Arguments = args,
                    WorkingDirectory = Directory.Exists(cfg.ServerRootDir) ? cfg.ServerRootDir : AppDomain.CurrentDomain.BaseDirectory,
                    UseShellExecute = false,
                    CreateNoWindow = true,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    StandardOutputEncoding = Encoding.UTF8,
                    StandardErrorEncoding = Encoding.UTF8
                };

                var proc = new Process { StartInfo = psi, EnableRaisingEvents = true };
                proc.OutputDataReceived += (_, e) =>
                {
                    if (e.Data != null) _log("[游戏服] " + e.Data);
                };
                proc.ErrorDataReceived += (_, e) =>
                {
                    if (e.Data != null) _log("[游戏服/ERR] " + e.Data);
                };
                proc.Exited += (_, __) =>
                {
                    int code = -1;
                    try { code = proc.ExitCode; } catch { }
                    _log($"[游戏服] 进程已退出 (退出码: {code})");
                    _process = null;
                    StateChanged?.Invoke(null);
                };

                if (!proc.Start())
                {
                    error = "启动 Java 进程失败";
                    return false;
                }

                proc.BeginOutputReadLine();
                proc.BeginErrorReadLine();
                _process = proc;
                _log($"[游戏服] 已启动 (PID: {proc.Id})");
                _log($"[游戏服] 命令: \"{javaExe}\" {args}");
                StateChanged?.Invoke(proc.Id);
                return true;
            }
            catch (Exception ex)
            {
                error = $"无法启动 Java ({javaExe})：{ex.Message}\n请确认已安装 JDK 并配置好 PATH 或指定 java.exe 路径。";
                return false;
            }
        }

        public void Stop()
        {
            var proc = _process;
            if (proc == null) return;

            try
            {
                if (!proc.HasExited)
                {
                    _log($"[游戏服] 正在停止进程 (PID: {proc.Id})...");
                    proc.Kill();
                    proc.WaitForExit(5000);
                }
            }
            catch (Exception ex)
            {
                _log("[游戏服] 停止进程时发生异常：" + ex.Message);
            }
            finally
            {
                _process = null;
                StateChanged?.Invoke(null);
            }
        }

        public void Dispose()
        {
            Stop();
        }
    }
}
