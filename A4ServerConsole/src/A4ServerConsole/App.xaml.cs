using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Windows;
using A4ServerConsole.Models;
using A4ServerConsole.Services;
using A4ServerConsole.Services.Gateway;

namespace A4ServerConsole
{
    public partial class App : Application
    {
        // 单实例：控制台是「启动服务端 + 端口占用」的那一方，多开会导致端口冲突 / 指向不同数据库。
        // 拿到锁就不释放（进程退出自动失效），第二个实例直接退出并提示。
        private static Mutex? _singleInstance;

        protected override void OnStartup(StartupEventArgs e)
        {
            bool smokeTest = e.Args.Length > 0 && string.Equals(e.Args[0], "--smoke-test", StringComparison.OrdinalIgnoreCase);
            if (!smokeTest)
            {
                _singleInstance = new Mutex(false, "Global\\A4ServerConsole.SingleInstance");
                bool acquired;
                try { acquired = _singleInstance.WaitOne(0); }
                catch (AbandonedMutexException) { acquired = true; }
                if (!acquired)
                {
                    System.Windows.MessageBox.Show("A4 服务端控制台已经在运行了（任务栏里找一下）。\n\n" +
                        "同一时间只允许开一个控制台，否则会出现端口占用、两个控制台指向不同数据库的问题。",
                        "A4 服务端控制台", MessageBoxButton.OK, MessageBoxImage.Information);
                    Shutdown(1);
                    return;
                }
            }

            // Win7 .NET 4.7.2/4.8 默认可能禁用 TLS 1.2，这里强制开启，确保公网 IP 接口 HTTPS 正常
            ServicePointManager.SecurityProtocol =
                SecurityProtocolType.Tls12 | SecurityProtocolType.Tls11 | SecurityProtocolType.Tls;

            // 初始化 SQLitePCLRaw 原生库
            SQLitePCL.Batteries_V2.Init();

            if (smokeTest)
            {
                int code = RunSmokeTest();
                Shutdown(code);
                return;
            }

            base.OnStartup(e);
        }

        private static int RunSmokeTest()
        {
            var sb = new StringBuilder();
            void Log(string msg) => sb.AppendLine($"[{DateTime.Now:HH:mm:ss}] {msg}");

            string reportPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "smoke-test-report.txt");
            var cfg = ConsoleConfig.Load();
            Log("=== 1. Config Auto-Detection ===");
            Log("ServerRootDir : " + cfg.ServerRootDir);
            Log("ServerJarPath : " + cfg.ServerJarPath + " (Exists=" + File.Exists(cfg.ServerJarPath) + ")");
            Log("ClientDir     : " + cfg.ClientDir + " (PVF=" + File.Exists(Path.Combine(cfg.ClientDir, "Script.pvf")) + ")");
            Log("JavaExePath   : " + cfg.JavaExePath + " (Exists=" + File.Exists(cfg.JavaExePath) + ")");
            Log("DbPath        : " + cfg.DbPath);

            using var game = new GameServerManager(Log);
            using var gw = new GatewayServer(Log);

            try
            {
                Log("=== 2. Start Java Game Server ===");
                if (!game.Start(cfg, out string err)) throw new Exception("GameServer start failed: " + err);

                bool ready = false;
                for (int i = 0; i < 40; i++)
                {
                    Thread.Sleep(500);
                    if (!game.IsRunning) throw new Exception("GameServer exited prematurely");
                    if (File.Exists(cfg.DbPath) && CanConnectTcp("127.0.0.1", cfg.EntrancePort))
                    {
                        ready = true;
                        break;
                    }
                }
                if (!ready) throw new Exception("Timed out waiting for GameServer port " + cfg.EntrancePort + " & s1a5.db");
                Log($"GameServer READY (PID={game.ProcessId}, port {cfg.EntrancePort} listening, s1a5.db exists)");

                Log("=== 3. Start C# Gateway Server & Test Protocol ===");
                if (!gw.Start(cfg, out err)) throw new Exception("Gateway start failed: " + err);

                string rPing = SendGw("127.0.0.1", cfg.GatewayPort, "<hz><c>ping</c></hz>");
                Log("GW ping  -> " + rPing);
                if (!rPing.Contains("\"code\":0")) throw new Exception("GW ping failed: " + rPing);

                string rReg = SendGw("127.0.0.1", cfg.GatewayPort, "<hz><c>reg</c><u>smoke1</u><p>123456</p></hz>");
                Log("GW reg   -> " + rReg);

                string rLogin = SendGw("127.0.0.1", cfg.GatewayPort, "<hz><c>login</c><u>smoke1</u><p>123456</p></hz>");
                Log("GW login -> " + rLogin);
                if (!rLogin.Contains("\"code\":0") || !rLogin.Contains("\"token\":\""))
                    throw new Exception("GW login failed: " + rLogin);

                Log("=== 4. Test Launcher Builder (folder output + encrypted ini) ===");
                string launcherTestDir = Path.Combine(Path.GetTempPath(), "A4LauncherSmoke-" + Guid.NewGuid().ToString("N"));
                var buildRes = LauncherBuilder.Build(cfg, launcherTestDir);
                Log($"LauncherBuilder: Success={buildRes.Success}, Ini={buildRes.IniPath}, Encrypted={buildRes.IniEncrypted}, Exe={buildRes.ExePath}");
                if (!buildRes.Success || !File.Exists(buildRes.IniPath))
                    throw new Exception("LauncherBuilder failed: " + buildRes.Message);
                string iniText = File.ReadAllText(buildRes.IniPath, Encoding.UTF8);
                if (!iniText.StartsWith(LauncherIniCipher.Magic)) throw new Exception("launcher.ini 不是密文");
                string plain = LauncherIniCipher.Decrypt(iniText);
                if (!plain.Contains("[gateway]")) throw new Exception("launcher.ini 解密失败");
                Log("LauncherBuilder: ini 加密 / 解密往返 OK（玩家看不到明文）");
                foreach (var z in Directory.GetFiles(buildRes.OutputDir, "A4登录器_*.zip"))
                {
                    // 只清掉以前版本留下的旧压缩包，确认现在不再生成
                    if (File.GetLastWriteTime(z) > DateTime.Now.AddMinutes(-3))
                        throw new Exception("输出目录里不应再生成 zip：" + z);
                }

                Log("=== 5. Test Backup Manager ===");
                if (!BackupManager.CreateBackup(cfg, "smoke", out string zipPath, out err))
                    throw new Exception("Backup failed: " + err);
                Log($"BackupManager OK: {zipPath} ({new FileInfo(zipPath).Length} bytes)");

                Log("=== ALL SMOKE TESTS PASSED ===");
                File.WriteAllText(reportPath, sb.ToString(), new UTF8Encoding(false));
                return 0;
            }
            catch (Exception ex)
            {
                Log("SMOKE TEST FAILED: " + ex);
                File.WriteAllText(reportPath, sb.ToString(), new UTF8Encoding(false));
                return 1;
            }
            finally
            {
                gw.Stop();
                game.Stop();
            }
        }

        private static bool CanConnectTcp(string host, int port)
        {
            try
            {
                using var tcp = new TcpClient();
                var ar = tcp.BeginConnect(host, port, null, null);
                if (!ar.AsyncWaitHandle.WaitOne(300)) return false;
                tcp.EndConnect(ar);
                return true;
            }
            catch
            {
                return false;
            }
        }

        private static string SendGw(string host, int port, string packet)
        {
            using var tcp = new TcpClient(host, port) { ReceiveTimeout = 5000, SendTimeout = 5000 };
            using var stream = tcp.GetStream();
            byte[] data = Encoding.UTF8.GetBytes(packet);
            stream.Write(data, 0, data.Length);
            var buf = new byte[4096];
            int n = stream.Read(buf, 0, buf.Length);
            return Encoding.UTF8.GetString(buf, 0, n);
        }
    }
}
