using System;
using A4GmTool.Core.Services;
using Newtonsoft.Json.Linq;

namespace RouterSmoke
{
    internal static class Program
    {
        private static int Main(string[] args)
        {
            string db = @"H:\60ACT4项目\A4ServerRelease\data\s1a5.db";
            string pvf = @"H:\60ACT4项目\A4ServerRelease\client\Script.pvf";

            GmEnv env = new GmEnv();
            env.Note("info", "=== 冒烟测试开始 ===");
            env.Configure(db, pvf, null, 10011);
            env.SetAccountName("");

            GmApiRouter router = new GmApiRouter(env);

            // 等目录建完
            for (int i = 0; i < 120 && !env.Catalog.Built; i++) System.Threading.Thread.Sleep(500);
            Console.WriteLine("catalog built = " + env.Catalog.Built + " count=" + env.Catalog.Count);

            Console.WriteLine("--- /api/status ---");
            Console.WriteLine(router.Handle("/api/status", new JObject()));

            Console.WriteLine("--- /api/characters ---");
            Console.WriteLine(router.Handle("/api/characters", new JObject()));

            Console.WriteLine("--- /api/character ---");
            Console.WriteLine(router.Handle("/api/character", new JObject { { "characNo", 1 } }));

            Console.WriteLine("--- /api/catalog (query=衬衫) ---");
            string cat = router.Handle("/api/catalog", new JObject { { "query", "布衬衫" }, { "size", 5 } });
            Console.WriteLine(cat.Length > 900 ? cat.Substring(0, 900) + "..." : cat);

            Console.WriteLine("--- /api/catalog/kinds ---");
            string kinds = router.Handle("/api/catalog/kinds", new JObject());
            Console.WriteLine(kinds.Length > 500 ? kinds.Substring(0, 500) + "..." : kinds);

            Console.WriteLine("--- /api/inventory ---");
            Console.WriteLine(router.Handle("/api/inventory", new JObject { { "characNo", 1 }, { "container", 0 } }));

            Console.WriteLine("--- /api/skills ---");
            string sk = router.Handle("/api/skills", new JObject { { "characNo", 1 } });
            Console.WriteLine(sk.Length > 400 ? sk.Substring(0, 400) + "..." : sk);

            Console.WriteLine("--- /api/quests ---");
            string q = router.Handle("/api/quests", new JObject { { "characNo", 1 } });
            Console.WriteLine(q.Length > 400 ? q.Substring(0, 400) + "..." : q);

            Console.WriteLine("--- /api/mailbox ---");
            Console.WriteLine(router.Handle("/api/mailbox", new JObject { { "characNo", 1 } }));

            Console.WriteLine("--- /api/avatars ---");
            Console.WriteLine(router.Handle("/api/avatars", new JObject { { "characNo", 1 } }));

            Console.WriteLine("--- /api/gmlist ---");
            string gl = router.Handle("/api/gmlist", new JObject());
            Console.WriteLine(gl.Length > 400 ? gl.Substring(0, 400) + "..." : gl);

            Console.WriteLine("--- /api/log ---");
            string lg = router.Handle("/api/log", new JObject());
            Console.WriteLine(lg.Length > 600 ? lg.Substring(0, 600) + "..." : lg);

            return 0;
        }
    }
}
