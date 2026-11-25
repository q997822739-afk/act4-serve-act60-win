using System;
using System.Collections.Generic;
using System.Text;
using A4GmTool.Core.Data;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace A4GmTool.Core.Services
{
    /// <summary>
    /// 网页 GM 的本地 HTTP API 分发。
    /// </summary>
    public sealed class GmApiRouter
    {
        private readonly GmEnv _env;
        private readonly Offline _offline;
        private readonly Online _online;

        public GmApiRouter(GmEnv env)
        {
            _env = env;
            _offline = new Offline(env);
            _online = new Online(env);
        }

        public GmEnv Env { get { return _env; } }

        // Only these routes may be dispatched by HTTP GET. New routes are POST-only by default.
        internal static bool IsReadOnly(string path)
        {
            switch (path)
            {
                case "/api/status":
                case "/api/probe-gm":
                case "/api/log":
                case "/api/characters":
                case "/api/character":
                case "/api/catalog":
                case "/api/catalog/kinds":
                case "/api/icon":
                case "/api/mailbox":
                case "/api/inventory":
                case "/api/avatars":
                case "/api/skills":
                case "/api/quests":
                case "/api/loadout":
                case "/api/gmlist":
                    return true;
                default:
                    return false;
            }
        }

        /// <summary>前端传进来的 path + 参数对象，返回 JSON 字符串。</summary>
        public string Handle(string path, JObject body)
        {
            path = (path ?? "").Trim();
            while (path.Length > 1 && path.EndsWith("/", StringComparison.Ordinal)) path = path.Substring(0, path.Length - 1);
            if (!path.StartsWith("/api/", StringComparison.Ordinal)) path = "/api/" + path.TrimStart('/');

            JObject b = body ?? new JObject();

            // 账号守卫：角色不属于本工具认的账号就拒
            int guardCharacNo = Int(b, "characNo");
            if (guardCharacNo > 0)
            {
                string reason = AccountGuard(guardCharacNo);
                if (reason != null) return Fail(reason);
            }

            switch (path)
            {
                case "/api/status":
                case "/api/probe-gm":
                    return Status();

                case "/api/log":
                    return LogJson();

                case "/api/shut-to-select":
                    {
                        JToken on = b["on"];
                        string s = on == null ? "" : on.ToString();
                        bool enabled = string.Equals(s, "true", StringComparison.OrdinalIgnoreCase)
                                       || s == "1" || string.Equals(s, "on", StringComparison.OrdinalIgnoreCase);
                        _env.ShutToSelect = enabled;
                        _env.Note("info", enabled ? "已开启「小退写库」" : "已关闭「小退写库」");
                        return Json(new JObject
                        {
                            { "ok", true },
                            { "shutToSelect", enabled },
                            { "message", enabled
                                ? "已允许在服务端运行时写库 —— 请确保角色停在【选择界面】（不要进游戏）。"
                                : "已关闭 —— 服务端在跑时恢复拒写。" }
                        });
                    }

                case "/api/configure":
                    {
                        List<string> dirs = new List<string>();
                        string packs = Str(b, "imagepacks");
                        foreach (string d in packs.Split(';'))
                        {
                            string t = d.Trim();
                            if (t.Length > 0) dirs.Add(t);
                        }
                        _env.Configure(Str(b, "db"), Str(b, "pvf"), dirs, Int(b, "gamePort"));
                        return Ok("已应用（重读 PVF / 图标库）");
                    }

                case "/api/reload-pvf":
                    _env.LoadScripts();
                    return Ok("PVF 已重读（物品目录在后台重建）");

                case "/api/characters":
                    return Characters();

                case "/api/character":
                    return CharacterDetail(Int(b, "characNo"));

                case "/api/character/level":
                    return Result(_offline.SetLevel(Int(b, "characNo"), Int(b, "level")));

                case "/api/character/sp":
                case "/api/skills/sp":
                    return Result(_offline.SetSp(Int(b, "characNo"), Long(b, "sp")));

                case "/api/character/money":
                    return Result(_offline.SetMoney(Int(b, "characNo"), Long(b, "money")));

                case "/api/character/winpoint":
                    return Result(_offline.SetWinPoint(Int(b, "characNo"), Long(b, "winPoint")));

                case "/api/character/cera":
                    {
                        long mId = Long(b, "mId");
                        long mine = _env.ResolveAccountId();
                        if (mId > 0 && mine > 0 && mId != mine)
                        {
                            return Fail("账号 " + mId + " 不是本工具认的账号（「" + _env.AccountName + "」，m_id " + mine + "）—— 点券只能改自己的。");
                        }
                        return Result(_offline.SetCera(mId, Long(b, "cera")));
                    }

                case "/api/character/fatigue":
                    return Result(_offline.RecoverFatigue(Int(b, "characNo")));

                case "/api/character/class":
                    {
                        int characNo = Int(b, "characNo");
                        int job = b["job"] != null ? Int(b, "job") : _offline.CurrentJob(characNo);
                        return Result(_offline.SetClass(characNo, job, Int(b, "firstGrow"), Int(b, "secondGrow")));
                    }

                case "/api/character/grow":
                    return Result(_offline.SetGrowType(Int(b, "characNo"), Int(b, "firstGrow"), Int(b, "secondGrow")));

                case "/api/character/job":
                    return Result(_offline.SetJobClass(Int(b, "characNo"), Int(b, "job")));

                case "/api/character/delete":
                    return Result(_offline.DeleteCharacter(Int(b, "characNo"), Str(b, "name")));

                case "/api/catalog":
                    return CatalogJson(b);

                case "/api/catalog/kinds":
                    return KindsJson();

                case "/api/icon":
                    return IconJson(b);

                case "/api/give":
                    return Result(GiveItem(Int(b, "characNo"), Int(b, "itemId"), Int(b, "count"), Int(b, "enhance"), Int(b, "endurance")));

                case "/api/give/many":
                    return Result(GiveMany(Int(b, "characNo"), Items(b)));

                case "/api/mailbox":
                    return MailboxJson(Int(b, "characNo"));

                case "/api/mailbox/delete":
                    return Result(_offline.DeleteMail(Int(b, "characNo"), Long(b, "postalId")));

                case "/api/inventory":
                    return InventoryJson(b);

                case "/api/inventory/edit":
                    return Result(_offline.EditItem(Int(b, "characNo"), Int(b, "container"), Int(b, "slot"), Str(b, "op"), Long(b, "value")));

                case "/api/inventory/clear":
                    return Result(_offline.ClearInventory(Int(b, "characNo")));

                case "/api/avatars":
                    return AvatarsJson(Int(b, "characNo"));

                case "/api/avatars/give":
                    return Result(GiveAvatar(Int(b, "characNo"), Int(b, "itemId"), Int(b, "days"), Int(b, "abilityNo")));

                case "/api/avatars/delete":
                    return Result(_offline.DeleteAvatarItem(Int(b, "characNo"), Long(b, "uiId")));

                case "/api/avatars/option":
                    return Result(_offline.SetAvatarOption(Int(b, "characNo"), Long(b, "uiId"), Int(b, "option")));

                case "/api/skills":
                    return SkillsJson(Int(b, "characNo"));

                case "/api/skills/set":
                    return Result(_offline.SetSkillLevel(Int(b, "characNo"), Int(b, "slot"), Int(b, "skillIndex"), Int(b, "level")));

                case "/api/quests":
                    return QuestsJson(Int(b, "characNo"));

                case "/api/quests/clear":
                    return Result(_offline.QuestClear(Int(b, "characNo"), IntList(b, "ids"), Bool(b, "all")));

                case "/api/loadout":
                    return LoadoutJson(Int(b, "characNo"));

                case "/api/gmlist":
                    return GmListJson();

                case "/api/gmlist/add":
                    {
                        string acct = Str(b, "account");
                        if (acct.Length == 0) acct = Str(b, "mId");
                        return Result(_offline.GmAdd(acct, Int(b, "level")));
                    }

                case "/api/gmlist/remove":
                    return Result(_offline.GmRemove(Long(b, "mId")));

                case "/api/shutdown":
                    return Json(new JObject { { "message", "网页 GM 已关闭，可以关掉这个窗口了" } });

                default:
                    return Fail("没有这个接口：" + path);
            }
        }

        // ---------------- 发放分流 ----------------

        private OpResult GiveItem(int characNo, int itemId, int count, int enhance, int endurance)
        {
            return _env.ServerUp
                ? _online.GiveItem(characNo, itemId, count, enhance, endurance)
                : _offline.GiveItem(characNo, itemId, count, enhance, endurance);
        }

        private OpResult GiveMany(int characNo, List<int[]> items)
        {
            return _env.ServerUp
                ? _online.GiveMany(characNo, items)
                : _offline.GiveMany(characNo, items);
        }

        private OpResult GiveAvatar(int characNo, int itemId, int days, int abilityNo)
        {
            return _env.ServerUp
                ? _online.GiveAvatar(characNo, itemId, days, abilityNo)
                : _offline.GiveAvatar(characNo, itemId, days, abilityNo);
        }

        // ---------------- 查询 ----------------

        private string AccountGuard(int characNo)
        {
            long mine = _env.ResolveAccountId();
            if (mine <= 0 || characNo <= 0) return null;
            try
            {
                using (SqliteConnection conn = SqliteConnection.OpenReadOnly(_env.DbPath))
                using (SqliteCommand cmd = conn.CreateCommand("SELECT m_id FROM charac_info WHERE charac_no = ?"))
                {
                    cmd.Bind(1, characNo);
                    if (!cmd.Read()) return null;
                    long owner = cmd.GetInt64(0);
                    if (owner != mine)
                    {
                        return "角色 " + characNo + " 属于别的账号（m_id " + owner + "），本工具只允许操作账号「"
                               + _env.AccountName + "」（m_id " + mine + "）的角色。";
                    }
                }
                return null;
            }
            catch (Exception ex)
            {
                return "校验角色归属失败：" + ex.Message;
            }
        }

        private string Status()
        {
            _env.ObserveServer();
            bool up = _env.ServerUp;

            JObject o = new JObject();
            o["db"] = _env.DbPath ?? "";
            o["dbName"] = _env.DbPath == null ? "未打开" : System.IO.Path.GetFileName(_env.DbPath);
            o["dbReady"] = _env.DbPath != null && System.IO.File.Exists(_env.DbPath);
            o["serverUp"] = up;
            o["giveMode"] = up ? "online" : "offline";
            o["presenceSupported"] = _env.PresenceSupported;
            o["inGameCount"] = up ? _env.InGameCount() : 0;
            o["autoDetect"] = _env.PresenceSupported;
            o["shutToSelect"] = _env.ShutToSelect;
            o["writeAllowed"] = _env.OnlineWriteAllowed;
            o["writeMode"] = !up ? "offline" : (_env.PresenceSupported ? "auto" : (_env.ShutToSelect ? "shutdown" : "blocked"));
            o["account"] = _env.AccountName ?? "";
            o["accountId"] = _env.ResolveAccountId();
            o["gamePort"] = _env.GamePort;
            o["gmPort"] = _env.GmPort;
            o["pvf"] = _env.PvfPath ?? "";
            o["pvfReady"] = _env.Scripts != null;
            o["itemCount"] = _env.Scripts == null ? 0 : _env.Scripts.ItemCount;
            o["catalogReady"] = _env.Catalog.Built;
            o["catalogCount"] = _env.Catalog.Count;
            o["catalogError"] = _env.Catalog.BuildError ?? "";
            o["imagepacksReady"] = _env.ImagePacks != null && _env.ImagePacks.Ready;
            o["imagepacks"] = string.Join(";", _env.ImagePackDirs.ToArray());
            o["characterCount"] = CountCharacters();
            return Json(o);
        }

        private int CountCharacters()
        {
            if (_env.DbPath == null) return 0;
            try
            {
                using (SqliteConnection conn = SqliteConnection.OpenReadOnly(_env.DbPath))
                using (SqliteCommand cmd = conn.CreateCommand("SELECT COUNT(*) FROM charac_info WHERE delete_flag = 0"))
                {
                    return cmd.Read() ? cmd.GetInt32(0) : 0;
                }
            }
            catch
            {
                return 0;
            }
        }

        private string LogJson()
        {
            JArray arr = new JArray();
            foreach (GmEnv.LogLine line in _env.GetLog())
            {
                arr.Add(new JObject { { "time", line.Time }, { "level", line.Level }, { "message", line.Message } });
            }
            return Json(new JObject { { "log", arr } });
        }

        private string Characters()
        {
            if (_env.DbPath == null) return Fail("数据库还没打开。");

            long mId = _env.ResolveAccountId();
            JArray arr = new JArray();
            try
            {
                using (SqliteConnection conn = SqliteConnection.OpenReadOnly(_env.DbPath))
                {
                    string sql = "SELECT c.charac_no, c.m_id, c.charac_name, c.job, c.grow_type, c.lev, c.exp,"
                                 + " c.fatigue, c.max_fatigue, c.sex, c.expert_job, IFNULL(i.money,0), IFNULL(m.cera,0)"
                                 + " FROM charac_info c"
                                 + " LEFT JOIN inventory i ON i.charac_no = c.charac_no"
                                 + " LEFT JOIN member_info m ON m.m_id = c.m_id"
                                 + " WHERE c.delete_flag = 0"
                                 + (mId > 0 ? " AND c.m_id = ?" : "")
                                 + " ORDER BY c.m_id, c.charac_no";

                    using (SqliteCommand cmd = conn.CreateCommand(sql))
                    {
                        if (mId > 0) cmd.Bind(1, mId);
                        while (cmd.Read())
                        {
                            arr.Add(new JObject
                            {
                                { "characNo", cmd.GetInt64(0) },
                                { "mId", cmd.GetInt64(1) },
                                { "name", cmd.GetString(2) ?? "" },
                                { "job", cmd.GetInt32(3) },
                                { "growType", cmd.GetInt32(4) },
                                { "lev", cmd.GetInt32(5) },
                                { "exp", cmd.GetInt64(6) },
                                { "fatigue", cmd.GetInt32(7) },
                                { "maxFatigue", cmd.GetInt32(8) },
                                { "sex", cmd.GetInt32(9) },
                                { "expertJob", cmd.GetInt32(10) },
                                { "money", cmd.GetInt64(11) },
                                { "cera", cmd.GetInt64(12) }
                            });
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                return Fail("读角色列表失败：" + ex.Message);
            }

            return Json(new JObject
            {
                { "account", _env.AccountName ?? "" },
                { "accountId", mId },
                { "characters", arr }
            });
        }

        private string CharacterDetail(int characNo)
        {
            if (characNo <= 0) return Fail("没有选角色");
            if (_env.DbPath == null) return Fail("数据库还没打开");

            JObject o = new JObject();
            try
            {
                using (SqliteConnection conn = SqliteConnection.OpenReadOnly(_env.DbPath))
                using (SqliteCommand cmd = conn.CreateCommand(
                    "SELECT c.charac_no, c.m_id, c.charac_name, c.job, c.grow_type, c.lev, c.exp, c.fatigue,"
                    + " c.max_fatigue, c.sex, c.expert_job, c.village, IFNULL(i.money,0), IFNULL(i.coin,0),"
                    + " IFNULL(m.cera,0), IFNULL(s.used_fatigue,0), IFNULL(k.remain_sp,0), IFNULL(k.used_sp,0),"
                    + " IFNULL(p.win_point,0)"
                    + " FROM charac_info c"
                    + " LEFT JOIN inventory i ON i.charac_no = c.charac_no"
                    + " LEFT JOIN member_info m ON m.m_id = c.m_id"
                    + " LEFT JOIN charac_stat s ON s.charac_no = c.charac_no"
                    + " LEFT JOIN skill k ON k.charac_no = c.charac_no"
                    + " LEFT JOIN pvp_result p ON p.charac_no = c.charac_no"
                    + " WHERE c.charac_no = ?"))
                {
                    cmd.Bind(1, characNo);
                    if (!cmd.Read()) return Fail("角色 " + characNo + " 不存在");

                    o["characNo"] = cmd.GetInt64(0);
                    o["mId"] = cmd.GetInt64(1);
                    o["name"] = cmd.GetString(2) ?? "";
                    o["job"] = cmd.GetInt32(3);
                    o["growType"] = cmd.GetInt32(4);
                    o["lev"] = cmd.GetInt32(5);
                    o["exp"] = cmd.GetInt64(6);
                    o["fatigue"] = cmd.GetInt32(7);
                    o["maxFatigue"] = cmd.GetInt32(8);
                    o["sex"] = cmd.GetInt32(9);
                    o["expertJob"] = cmd.GetInt32(10);
                    o["village"] = cmd.GetInt32(11);
                    o["money"] = cmd.GetInt64(12);
                    o["coin"] = cmd.GetInt64(13);
                    o["cera"] = cmd.GetInt64(14);
                    o["usedFatigue"] = cmd.GetInt32(15);
                    o["remainSp"] = cmd.GetInt64(16);
                    o["usedSp"] = cmd.GetInt64(17);
                    o["winPoint"] = cmd.GetInt64(18);
                }
            }
            catch (Exception ex)
            {
                return Fail("读角色失败：" + ex.Message);
            }

            return Json(o);
        }

        // ---------------- 物品目录 ----------------

        private string CatalogJson(JObject b)
        {
            if (!_env.Catalog.Built)
            {
                return Fail("物品目录还在构建中（或构建失败：" + (_env.Catalog.BuildError ?? "") + "），稍等几秒再刷新");
            }

            string query = Str(b, "query").Trim().ToLowerInvariant();
            string kind = Str(b, "kind");
            string part = Str(b, "part");
            string subtype = Str(b, "subtype");
            string job = Str(b, "job");
            int rarity = IntOr(b, "rarity", -1);
            int min = IntOr(b, "min", -1);
            int max = IntOr(b, "max", -1);
            bool named = Str(b, "named") == "1" || string.Equals(Str(b, "named"), "true", StringComparison.OrdinalIgnoreCase);
            int page = Math.Max(1, IntOr(b, "page", 1));
            int size = Math.Max(1, Math.Min(200, IntOr(b, "size", 60)));

            List<CatalogEntry> hits = new List<CatalogEntry>();
            foreach (CatalogEntry e in _env.Catalog.Items())
            {
                if (kind.Length > 0 && !string.Equals(kind, e.Kind, StringComparison.OrdinalIgnoreCase)) continue;
                if (part.Length > 0 && !string.Equals(part, e.Part, StringComparison.OrdinalIgnoreCase)) continue;
                if (subtype.Length > 0 && !string.Equals(subtype, e.Subtype, StringComparison.OrdinalIgnoreCase)) continue;
                if (job.Length > 0 && !string.Equals(job, e.Job, StringComparison.OrdinalIgnoreCase)) continue;
                if (rarity >= 0 && e.Rarity != rarity) continue;
                if (min >= 0 && e.MinLevel < min) continue;
                if (max >= 0 && e.MinLevel > max) continue;
                if (named && string.IsNullOrEmpty(e.Name)) continue;

                if (query.Length > 0)
                {
                    string hay = (e.Id + " " + e.Name + " " + e.Path).ToLowerInvariant();
                    if (hay.IndexOf(query, StringComparison.Ordinal) < 0) continue;
                }
                hits.Add(e);
            }

            int total = hits.Count;
            int totalPages = Math.Max(1, (total + size - 1) / size);
            if (page > totalPages) page = totalPages;
            int start = Math.Min((page - 1) * size, total);
            int end = Math.Min(start + size, total);

            JArray items = new JArray();
            for (int i = start; i < end; i++) items.Add(EntryJson(hits[i]));

            return Json(new JObject
            {
                { "total", total },
                { "page", page },
                { "size", size },
                { "totalPages", totalPages },
                { "items", items }
            });
        }

        private static JObject EntryJson(CatalogEntry e)
        {
            return new JObject
            {
                { "id", e.Id },
                { "path", e.Path },
                { "name", e.Name },
                { "kind", e.Kind },
                { "kindName", e.KindName },
                { "part", e.Part },
                { "partName", e.PartName },
                { "subtype", e.Subtype },
                { "subtypeName", e.SubtypeName },
                { "job", e.Job },
                { "jobName", e.JobName },
                { "rarity", e.Rarity },
                { "minLevel", e.MinLevel },
                { "stackLimit", e.StackLimit },
                { "stackable", e.Stackable },
                { "stackableItem", e.StackableItem },
                { "avatar", e.Avatar },
                { "price", e.Price },
                { "subtypeAxis", e.SubtypeAxis },
                { "iconPath", e.IconPath },
                { "iconFrame", e.IconFrame },
                { "avatarOptions", ToArray(e.AvatarOptions) },
                { "avatarPeriods", ToArray(e.AvatarPeriods) },
                { "stats", ToArray(e.Stats) },
                { "effects", ToArray(e.Effects) },
                { "explain", e.Explain },
                { "detailExplain", e.DetailExplain },
                { "flavorText", e.FlavorText },
                { "sets", ToArray(e.Sets) }
            };
        }

        private string KindsJson()
        {
            if (!_env.Catalog.Built) return Fail("物品目录还在构建中");

            Dictionary<string, int> counts = _env.Catalog.KindCounts();
            Dictionary<string, string> kindNames = new Dictionary<string, string>();
            Dictionary<string, Dictionary<string, int>> jobs = new Dictionary<string, Dictionary<string, int>>();
            Dictionary<string, Dictionary<string, int>> parts = new Dictionary<string, Dictionary<string, int>>();
            Dictionary<string, string> jobNames = new Dictionary<string, string>();
            Dictionary<string, string> partNames = new Dictionary<string, string>();

            foreach (CatalogEntry e in _env.Catalog.Items())
            {
                if (!kindNames.ContainsKey(e.Kind)) kindNames[e.Kind] = e.KindName;

                if (e.Job.Length > 0)
                {
                    if (!jobs.ContainsKey(e.Kind)) jobs[e.Kind] = new Dictionary<string, int>();
                    int n;
                    jobs[e.Kind].TryGetValue(e.Job, out n);
                    jobs[e.Kind][e.Job] = n + 1;
                    if (!jobNames.ContainsKey(e.Job)) jobNames[e.Job] = e.JobName;
                }

                if (e.Part.Length > 0)
                {
                    if (!parts.ContainsKey(e.Kind)) parts[e.Kind] = new Dictionary<string, int>();
                    int n;
                    parts[e.Kind].TryGetValue(e.Part, out n);
                    parts[e.Kind][e.Part] = n + 1;
                    if (!partNames.ContainsKey(e.Part)) partNames[e.Part] = e.PartName;
                }
            }

            JArray kinds = new JArray();
            foreach (KeyValuePair<string, string> kv in kindNames)
            {
                JArray jobArr = new JArray();
                Dictionary<string, int> js;
                if (jobs.TryGetValue(kv.Key, out js))
                {
                    foreach (KeyValuePair<string, int> j in js)
                    {
                        jobArr.Add(new JObject { { "id", j.Key }, { "name", jobNames.ContainsKey(j.Key) ? jobNames[j.Key] : j.Key }, { "count", j.Value } });
                    }
                }

                JArray partArr = new JArray();
                Dictionary<string, int> ps;
                if (parts.TryGetValue(kv.Key, out ps))
                {
                    foreach (KeyValuePair<string, int> p in ps)
                    {
                        partArr.Add(new JObject { { "id", p.Key }, { "name", partNames.ContainsKey(p.Key) ? partNames[p.Key] : p.Key }, { "count", p.Value } });
                    }
                }

                kinds.Add(new JObject
                {
                    { "id", kv.Key },
                    { "name", kv.Value },
                    { "count", counts.ContainsKey(kv.Key) ? counts[kv.Key] : 0 },
                    { "jobs", jobArr },
                    { "parts", partArr }
                });
            }

            return Json(new JObject { { "kinds", kinds } });
        }

        private string IconJson(JObject b)
        {
            int id = Int(b, "id");
            CatalogEntry e = _env.Catalog.Item(id);
            if (e == null) return Fail("目录里没有物品 " + id);

            // 前端现在用 <img src="/api/icon?id=">，改走桥以后取的是 base64 PNG
            if (_env.ImagePacks == null) return Json(new JObject { { "ok", false }, { "reason", "图标库未就绪（未找到 ImagePacks2）" } });

            byte[] png = _env.ImagePacks.TryRenderPng(e.IconPath, e.IconFrame);
            if (png == null) return Json(new JObject { { "ok", false }, { "reason", "图标解码失败：" + e.IconPath } });

            return Json(new JObject { { "ok", true }, { "png", Convert.ToBase64String(png) } });
        }

        // ---------------- 背包 / 时装 / 邮箱 ----------------

        private string InventoryJson(JObject b)
        {
            int characNo = Int(b, "characNo");
            int container = IntOr(b, "container", 0);
            if (_env.DbPath == null || characNo <= 0) return Fail("没有打开数据库或没选角色");

            string column = Offline.ContainerColumn(container);
            if (column == null) return Fail("容器 " + container + " 读不出来");
            int slots = Offline.ContainerSlots(container);

            ItemContainer inv;
            try
            {
                using (SqliteConnection conn = SqliteConnection.OpenReadOnly(_env.DbPath))
                using (SqliteCommand cmd = conn.CreateCommand("SELECT " + column + " FROM inventory WHERE charac_no = ?"))
                {
                    cmd.Bind(1, characNo);
                    if (!cmd.Read()) return Fail("容器 " + container + " 读不出来");
                    inv = ItemContainer.Decode(cmd.GetBlob(0), slots);
                }
            }
            catch (Exception ex)
            {
                return Fail("容器 " + container + " 读不出来：" + ex.Message);
            }

            JArray rows = new JArray();
            for (int i = 0; i < inv.Slots; i++)
            {
                InvenItem it = inv[i];
                if (it.IsEmpty) continue;

                CatalogEntry e = _env.Catalog.Item((int)it.ItemId);
                JObject o = new JObject
                {
                    { "slot", i },
                    { "itemId", it.ItemId },
                    { "count", it.StackCount },
                    { "upgrade", it.Upgrade },
                    { "endurance", it.Endurance },
                    { "enchant", it.Enchant },
                    { "addInfo", it.AddInfo },
                    { "itemType", it.ItemType }
                };

                if (e != null)
                {
                    o["name"] = e.Name;
                    o["kindName"] = e.KindName;
                    o["partName"] = e.PartName;
                    o["rarity"] = e.Rarity;
                    o["stackable"] = e.Stackable;
                    o["stackableItem"] = e.StackableItem;
                }
                else
                {
                    o["name"] = "";
                    o["kindName"] = "目录外";
                    o["partName"] = "";
                    o["rarity"] = 0;
                    o["stackable"] = false;
                    o["stackableItem"] = false;
                }
                rows.Add(o);
            }

            return Json(new JObject { { "container", container }, { "slots", inv.Slots }, { "rows", rows } });
        }

        private string AvatarsJson(int characNo)
        {
            if (_env.DbPath == null || characNo <= 0) return Fail("没有打开数据库或没选角色");

            JArray rows = new JArray();
            try
            {
                using (SqliteConnection conn = SqliteConnection.OpenReadOnly(_env.DbPath))
                using (SqliteCommand cmd = conn.CreateCommand(
                    "SELECT ui_id, slot, it_id, ability_no, expire_date FROM user_items WHERE charac_no = ? ORDER BY slot"))
                {
                    cmd.Bind(1, characNo);
                    while (cmd.Read())
                    {
                        int itemId = cmd.GetInt32(2);
                        CatalogEntry e = _env.Catalog.Item(itemId);

                        JObject o = new JObject
                        {
                            { "uiId", cmd.GetInt64(0) },
                            { "slot", cmd.GetInt32(1) },
                            { "itemId", itemId },
                            { "abilityNo", cmd.GetInt32(3) },
                            { "expire", cmd.GetString(4) ?? "永久" }
                        };
                        o["name"] = e != null ? e.Name : (_env.Scripts != null ? _env.Scripts.NameOf(itemId) : "");
                        o["partName"] = e != null ? e.PartName : "";
                        o["jobName"] = e != null ? e.JobName : "";
                        o["rarity"] = e != null ? e.Rarity : 0;
                        o["avatarOptions"] = e != null ? ToArray(e.AvatarOptions) : new JArray();
                        rows.Add(o);
                    }
                }
            }
            catch (Exception ex)
            {
                return Fail("读装扮栏失败：" + ex.Message);
            }

            return Json(new JObject { { "rows", rows } });
        }

        private string MailboxJson(int characNo)
        {
            if (characNo <= 0) return Fail("没有选角色");

            JArray rows = new JArray();
            foreach (MailLine m in _online.PendingMails(characNo))
            {
                rows.Add(new JObject
                {
                    { "postalId", m.PostalId },
                    { "itemId", m.ItemId },
                    { "name", m.Name },
                    { "count", m.Count },
                    { "quantity", m.Quantity },
                    { "upgrade", m.Upgrade },
                    { "endurance", m.Endurance },
                    { "sender", m.Sender },
                    { "unlimited", m.Unlimited },
                    { "avatar", m.Avatar },
                    { "rarity", m.Rarity }
                });
            }
            return Json(new JObject { { "characNo", characNo }, { "rows", rows } });
        }

        // ---------------- 技能 / 任务 ----------------

        private string SkillsJson(int characNo)
        {
            if (_env.DbPath == null || characNo <= 0) return Fail("没有打开数据库或没选角色");

            try
            {
                using (SqliteConnection conn = SqliteConnection.OpenReadOnly(_env.DbPath))
                using (SqliteCommand cmd = conn.CreateCommand("SELECT remain_sp, skill_slot FROM skill WHERE charac_no = ?"))
                {
                    cmd.Bind(1, characNo);
                    if (!cmd.Read()) return Fail("skill 表没有该角色");

                    long remainSp = cmd.GetInt64(0);
                    SkillSlots slots = SkillSlots.Decode(cmd.GetBlob(1));

                    JArray rows = new JArray();
                    int job = _offline.CurrentJob(characNo);
                    for (int i = 0; i < SkillSlots.Slots; i++)
                    {
                        if (slots.IsEmptyAt(i)) continue;
                        int idx = slots.SkillIndexAt(i);

                        string name = "";
                        if (_env.Skills != null) name = _env.Skills.ByJob(JobIndexOf(job), idx);

                        rows.Add(new JObject
                        {
                            { "slot", i },
                            { "skillIndex", idx },
                            { "name", name },
                            { "level", slots.LevelAt(i) }
                        });
                    }

                    return Json(new JObject
                    {
                        { "remainSp", remainSp },
                        { "slots", SkillSlots.Slots },
                        { "job", job },
                        { "rows", rows }
                    });
                }
            }
            catch (Exception ex)
            {
                return Fail("读技能失败：" + ex.Message);
            }
        }

        private string QuestsJson(int characNo)
        {
            if (_env.DbPath == null || characNo <= 0) return Fail("没有打开数据库或没选角色");

            JArray running = new JArray();
            int cleared = 0;
            try
            {
                using (SqliteConnection conn = SqliteConnection.OpenReadOnly(_env.DbPath))
                {
                    // play_N 是任务号，play_N_trigger 是进度；两列都要读，否则 trigger 会一直是 0
                    string sql = "SELECT clear_quest";
                    for (int n = 1; n <= QuestRunning.Slots; n++) sql += ", play_" + n + ", play_" + n + "_trigger";
                    sql += " FROM new_charac_quest WHERE charac_no = ?";

                    using (SqliteCommand cmd = conn.CreateCommand(sql))
                    {
                        cmd.Bind(1, characNo);
                        if (!cmd.Read()) return Fail("new_charac_quest 表没有该角色");

                        cleared = QuestClearFlags.Decode(cmd.GetBlob(0)).Count();
                        for (int i = 0; i < QuestRunning.Slots; i++)
                        {
                            int q = cmd.GetInt32(1 + i * 2);
                            long trigger = cmd.GetInt64(2 + i * 2);
                            if (q == 0) continue;

                            string qname = _env.Quests != null ? _env.Quests.NameOf(q) : ("任务 " + q);

                            JArray mats = new JArray();
                            if (_env.Quests != null)
                            {
                                foreach (Pvf.QuestTable.QuestMaterial m in _env.Quests.MaterialsOf(q))
                                {
                                    long have = CountItem(conn, characNo, m.ItemId);
                                    CatalogEntry ce = _env.Catalog.Item(m.ItemId);
                                    mats.Add(new JObject
                                    {
                                        { "itemId", m.ItemId },
                                        { "name", ce != null ? ce.Name : (_env.Scripts != null ? _env.Scripts.NameOf(m.ItemId) : "") },
                                        { "need", m.Need },
                                        { "have", have },
                                        { "gap", Math.Max(0, m.Need - have) }
                                    });
                                }
                            }

                            running.Add(new JObject
                            {
                                { "questId", q },
                                { "trigger", trigger },
                                { "name", qname },
                                { "materials", mats }
                            });
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                return Fail("读任务失败：" + ex.Message);
            }

            return Json(new JObject { { "running", running }, { "clearedCount", cleared } });
        }

        // ---------------- 一键配装 ----------------

        private string LoadoutJson(int characNo)
        {
            if (characNo <= 0) return Fail("没有选角色");
            if (!_env.Catalog.Built) return Fail("物品目录还在构建中，稍等几秒再点");

            int job, growType, lev;
            try
            {
                using (SqliteConnection conn = SqliteConnection.OpenReadOnly(_env.DbPath))
                using (SqliteCommand cmd = conn.CreateCommand("SELECT job, grow_type, lev FROM charac_info WHERE charac_no = ?"))
                {
                    cmd.Bind(1, characNo);
                    if (!cmd.Read()) return Fail("角色 " + characNo + " 不存在");
                    job = cmd.GetInt32(0);
                    growType = cmd.GetInt32(1);
                    lev = cmd.GetInt32(2);
                }
            }
            catch (Exception ex)
            {
                return Fail("读角色失败：" + ex.Message);
            }

            Loadout lo = Loadout.Build(_env, characNo, job, growType, lev);

            JArray picks = new JArray();
            foreach (Loadout.Pick p in lo.Picks)
            {
                JObject o = new JObject
                {
                    { "part", p.Part },
                    { "partName", p.PartName },
                    { "inSet", p.InSet },
                    { "found", p.Found }
                };
                if (p.Found)
                {
                    o["id"] = p.Id;
                    o["name"] = p.Name;
                    o["subtype"] = p.Subtype;
                    o["subtypeName"] = p.SubtypeName;
                    o["jobName"] = p.JobName;
                    o["rarity"] = p.Rarity;
                    o["minLevel"] = p.MinLevel;
                    o["icon"] = p.Icon;
                }
                picks.Add(o);
            }

            return Json(new JObject
            {
                { "characNo", characNo },
                { "job", lo.Job },
                { "growType", lo.GrowType },
                { "branch", lo.Branch },
                { "lev", lo.Level },
                { "title", lo.Title },
                { "setName", lo.SetName },
                { "setSlots", ToArray(lo.SetSlots) },
                { "foundCount", lo.FoundCount },
                { "picks", picks }
            });
        }

        private string GmListJson()
        {
            if (_env.DbPath == null) return Fail("数据库还没打开");

            JArray rows = new JArray();
            JArray accounts = new JArray();
            try
            {
                using (SqliteConnection conn = SqliteConnection.OpenReadOnly(_env.DbPath))
                {
                    using (SqliteCommand cmd = conn.CreateCommand("SELECT m_id, IFNULL(NULLIF(user_id,''), user_name) FROM member_info"))
                    {
                        Dictionary<long, string> names = new Dictionary<long, string>();
                        while (cmd.Read()) names[cmd.GetInt64(0)] = cmd.GetString(1) ?? "";

                        using (SqliteCommand g = conn.CreateCommand("SELECT m_id, level FROM gm_manifest ORDER BY level DESC, m_id"))
                        {
                            while (g.Read())
                            {
                                long id = g.GetInt64(0);
                                rows.Add(new JObject
                                {
                                    { "mId", id },
                                    { "level", g.GetInt32(1) },
                                    { "userName", names.ContainsKey(id) ? names[id] : "" }
                                });
                            }
                        }
                    }

                    using (SqliteCommand cmd = conn.CreateCommand("SELECT m_id, user_id, user_name FROM member_info ORDER BY m_id"))
                    {
                        while (cmd.Read())
                        {
                            accounts.Add(new JObject
                            {
                                { "mId", cmd.GetInt64(0) },
                                { "userId", cmd.GetString(1) ?? "" },
                                { "userName", cmd.GetString(2) ?? "" }
                            });
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                return Fail("读 GM 名单失败：" + ex.Message);
            }

            return Json(new JObject { { "rows", rows }, { "accounts", accounts } });
        }

        // ---------------- 小工具 ----------------

        /// <summary>DB 里的 job 数字 → 技能表职业下标（0 剑士 1 格斗 2 枪手 3 法师 4 圣职）。</summary>
        private static int JobIndexOf(int job)
        {
            switch (job)
            {
                case 0: return 0;
                case 1: return 1;
                case 2: return 2;
                case 3: return 3;
                case 4: return 4;
                default: return 0;
            }
        }

        /// <summary>背包 + 仓库里某物品的数量（任务材料缺口用）。</summary>
        private long CountItem(SqliteConnection conn, int characNo, int itemId)
        {
            long total = 0;
            try
            {
                using (SqliteCommand cmd = conn.CreateCommand("SELECT inventory, equipslot, creature FROM inventory WHERE charac_no = ?"))
                {
                    cmd.Bind(1, characNo);
                    if (!cmd.Read()) return 0;

                    total += CountInBlob(cmd.GetBlob(0), ItemContainer.InventorySlots, itemId);
                    total += CountInBlob(cmd.GetBlob(1), ItemContainer.EquipSlotSlots, itemId);
                    total += CountInBlob(cmd.GetBlob(2), ItemContainer.CreatureSlots, itemId);
                }

                using (SqliteCommand cmd = conn.CreateCommand("SELECT cargo FROM charac_inven_expand WHERE charac_no = ?"))
                {
                    cmd.Bind(1, characNo);
                    if (cmd.Read()) total += CountInBlob(cmd.GetBlob(0), ItemContainer.CargoSlots, itemId);
                }
            }
            catch { }
            return total;
        }

        private static long CountInBlob(byte[] blob, int slots, int itemId)
        {
            if (blob == null || blob.Length == 0) return 0;
            try
            {
                ItemContainer inv = ItemContainer.Decode(blob, slots);
                long n = 0;
                for (int i = 0; i < inv.Slots; i++)
                {
                    InvenItem it = inv[i];
                    if (it.IsEmpty || it.ItemId != itemId) continue;
                    n += it.StackCount;
                }
                return n;
            }
            catch { return 0; }
        }

        private static string Json(JObject o)
        {
            return o.ToString(Formatting.None);
        }

        private static JArray ToArray(List<string> list)
        {
            JArray a = new JArray();
            if (list != null) foreach (string s in list) a.Add(s);
            return a;
        }

        private static JArray ToArray(List<int> list)
        {
            JArray a = new JArray();
            if (list != null) foreach (int s in list) a.Add(s);
            return a;
        }

        private static string Ok(string message)
        {
            return new JObject { { "ok", true }, { "message", message } }.ToString(Formatting.None);
        }

        private static string Fail(string message)
        {
            return new JObject { { "ok", false }, { "error", message } }.ToString(Formatting.None);
        }

        private static string Result(OpResult r)
        {
            return r.Ok ? Ok(r.Message) : Fail(r.Message);
        }

        private static int Int(JObject b, string key)
        {
            JToken t = b[key];
            if (t == null || t.Type == JTokenType.Null) return 0;
            try { return t.Type == JTokenType.String ? (int.TryParse((string)t, out int v) ? v : 0) : t.Value<int>(); }
            catch { return 0; }
        }

        private static int IntOr(JObject b, string key, int fallback)
        {
            JToken t = b[key];
            if (t == null || t.Type == JTokenType.Null) return fallback;
            try { return t.Type == JTokenType.String ? (int.TryParse((string)t, out int v) ? v : fallback) : t.Value<int>(); }
            catch { return fallback; }
        }

        private static long Long(JObject b, string key)
        {
            JToken t = b[key];
            if (t == null || t.Type == JTokenType.Null) return 0;
            try { return t.Type == JTokenType.String ? (long.TryParse((string)t, out long v) ? v : 0) : t.Value<long>(); }
            catch { return 0; }
        }

        private static string Str(JObject b, string key)
        {
            JToken t = b[key];
            if (t == null || t.Type == JTokenType.Null) return "";
            return t.ToString();
        }

        private static bool Bool(JObject b, string key)
        {
            JToken t = b[key];
            if (t == null || t.Type == JTokenType.Null) return false;
            if (t.Type == JTokenType.Boolean) return (bool)t;
            string s = t.ToString();
            return string.Equals(s, "true", StringComparison.OrdinalIgnoreCase) || s == "1";
        }

        private static List<int> IntList(JObject b, string key)
        {
            List<int> list = new List<int>();
            JArray a = b[key] as JArray;
            if (a != null)
            {
                foreach (JToken t in a)
                {
                    try { list.Add(t.Value<int>()); } catch { }
                }
            }
            return list;
        }

        /// <summary>[[itemId, count, enhance], ...]</summary>
        private static List<int[]> Items(JObject b)
        {
            List<int[]> list = new List<int[]>();
            JArray a = b["items"] as JArray;
            if (a == null) return list;

            foreach (JToken t in a)
            {
                JArray row = t as JArray;
                if (row == null || row.Count < 2) continue;
                int[] vals = new int[row.Count];
                for (int i = 0; i < row.Count; i++)
                {
                    try { vals[i] = row[i].Value<int>(); } catch { vals[i] = 0; }
                }
                list.Add(vals);
            }
            return list;
        }
    }
}
