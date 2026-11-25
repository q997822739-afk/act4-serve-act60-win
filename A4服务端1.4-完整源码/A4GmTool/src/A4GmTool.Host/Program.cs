using System;
using System.Collections.Generic;
using System.IO;
using System.Threading;
using A4GmTool.Core.Services;
using A4GmTool.Core.Web;

namespace A4GmTool.Host
{
    internal sealed class Options
    {
        public int Port = 60694;
        public int GamePort = 10011;
        public string Db;
        public string Pvf;
        public List<string> ImagePacks = new List<string>();
        public string Account = "";
        public bool StopOnStdinEof;
        public bool SecureCookie;
        public bool RequirePassword;

        public static Options Parse(string[] args)
        {
            Options options = new Options();
            for (int i = 0; i < args.Length; i++)
            {
                string flag = args[i];
                if (flag == "--no-browser") continue; // 兼容旧启动脚本；现在从不自动开网页。
                if (flag == "--stop-on-stdin-eof") { options.StopOnStdinEof = true; continue; }
                if (flag == "--secure-cookie") { options.SecureCookie = true; continue; }
                if (flag == "--require-password") { options.RequirePassword = true; continue; }
                if (i + 1 >= args.Length || args[i + 1].StartsWith("--", StringComparison.Ordinal))
                    throw new ArgumentException("参数缺少值：" + flag);
                string value = args[++i];
                switch (flag)
                {
                    case "--port": options.Port = ParsePort(flag, value, 0); break;
                    case "--game-port": options.GamePort = ParsePort(flag, value, 1); break;
                    case "--db": options.Db = value; break;
                    case "--pvf": options.Pvf = value; break;
                    case "--account": options.Account = value; break;
                    case "--imagepacks":
                        foreach (string dir in value.Split(';'))
                            if (dir.Trim().Length > 0) options.ImagePacks.Add(dir.Trim());
                        break;
                    default: throw new ArgumentException("未知参数：" + flag);
                }
            }
            return options;
        }

        private static int ParsePort(string flag, string value, int min)
        {
            int port;
            if (!int.TryParse(value, out port) || port < min || port > 65535)
                throw new ArgumentException(flag + " 必须是 " + min + " 至 65535 的整数。");
            return port;
        }
    }

    internal static class Program
    {
        private static int Main(string[] args)
        {
            if (args.Length == 1 && (args[0] == "--help" || args[0] == "-h"))
            {
                Console.WriteLine("A4GmTool.exe [--port 60694] [--game-port 10011] [--db <path>] [--pvf <path>] [--imagepacks <dirs>] [--account <name>] [--require-password] [--stop-on-stdin-eof] [--secure-cookie]");
                Console.WriteLine("A4_GM_PASSWORD 启用网页登录；--require-password、--stop-on-stdin-eof 和 --secure-cookie 必须设置此变量。");
                Console.WriteLine("HTTPS 反向代理请启用 --secure-cookie，GM 后端仍只监听 127.0.0.1。无密码时仅限本机独立使用，勿反代。");
                return 0;
            }

            GmEnv env = new GmEnv();
            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            string webRoot = Path.Combine(baseDir, "web");
            try
            {
                Options options = Options.Parse(args);
                string password = Environment.GetEnvironmentVariable("A4_GM_PASSWORD");
                if (!string.IsNullOrEmpty(password) && password.Trim().Length == 0)
                    throw new ArgumentException("A4_GM_PASSWORD 不能只包含空白字符。");
                if ((options.RequirePassword || options.StopOnStdinEof || options.SecureCookie) && string.IsNullOrEmpty(password))
                    throw new ArgumentException("--require-password、--stop-on-stdin-eof 和 --secure-cookie 需要设置 A4_GM_PASSWORD。");
                if (!File.Exists(Path.Combine(baseDir, "e_sqlite3.dll")))
                    throw new FileNotFoundException("缺少 e_sqlite3.dll，请使用完整的发布目录。");
                if (!File.Exists(Path.Combine(webRoot, "index.html")))
                    throw new FileNotFoundException("缺少 web/index.html，请使用完整的发布目录。");

                env.SetAccountName(options.Account);
                ConfigureEnvironment(env, options, baseDir);

                using (ManualResetEvent stop = new ManualResetEvent(false))
                using (GmHttpServer server = new GmHttpServer(new GmApiRouter(env), webRoot, message => env.Note("info", message),
                    password, options.SecureCookie))
                {
                    ConsoleCancelEventHandler cancel = (sender, e) => { e.Cancel = true; stop.Set(); };
                    Console.CancelKeyPress += cancel;
                    try
                    {
                        string error;
                        if (!server.Start(options.Port, out error)) throw new InvalidOperationException(error);
                        env.GmPort = server.Port;
                        Console.WriteLine(server.Url);
                        Console.WriteLine("数据库: " + (env.DbPath ?? "未找到"));
                        Console.WriteLine("PVF: " + (env.PvfPath ?? "未找到"));
                        if (options.StopOnStdinEof)
                        {
                            // 控制台退出或停止服务时关闭管道，后台 GM 随之退出。
                            while (Console.ReadLine() != null) { }
                        }
                        else
                        {
                            Console.WriteLine("服务已启动。按 Ctrl+C 停止。");
                            stop.WaitOne();
                        }
                    }
                    finally
                    {
                        Console.CancelKeyPress -= cancel;
                    }
                }
                return 0;
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine("GM 启动失败：" + ex.Message);
                return 1;
            }
            finally
            {
                env.Close();
            }
        }

        private static void ConfigureEnvironment(GmEnv env, Options options, string baseDir)
        {
            List<string> roots = new List<string>();
            DirectoryInfo dir = new DirectoryInfo(baseDir);
            for (int i = 0; i < 5 && dir != null; i++, dir = dir.Parent)
            {
                roots.Add(dir.FullName);
                string release = Path.Combine(dir.FullName, "A4ServerRelease");
                if (Directory.Exists(release)) roots.Add(release);
            }

            string db = options.Db ?? Find(roots, GmEnv.DbName, File.Exists, "", "data", @"server\data", "server");
            string pvf = options.Pvf ?? Find(roots, GmEnv.PvfName, File.Exists, "", "client", @"server\client");
            if (options.ImagePacks.Count == 0)
            {
                string packs = Find(roots, "ImagePacks2", Directory.Exists, "", "client", @"server\client");
                if (packs != null) options.ImagePacks.Add(packs);
            }
            env.Configure(db, pvf, options.ImagePacks, options.GamePort);
        }

        private static string Find(IEnumerable<string> roots, string name, Predicate<string> exists, params string[] subdirs)
        {
            foreach (string root in roots)
                foreach (string subdir in subdirs)
                {
                    string path = Path.Combine(Path.Combine(root, subdir), name);
                    if (exists(path)) return path;
                }
            return null;
        }
    }
}
