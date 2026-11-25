using System;
using System.Collections.Generic;
using System.IO;
using System.Net.Sockets;
using A4GmTool.Core.Data;
using A4GmTool.Core.Img;
using A4GmTool.Core.Pvf;

namespace A4GmTool.Core.Services
{
    /// <summary>运行环境：数据库路径、PVF、图标库、端口探测、在线状态、日志。</summary>
    public sealed class GmEnv
    {
        public const string DbName = "s1a5.db";
        public const string PvfName = "Script.pvf";
        public const string SettingsName = "gm-settings.json";
        public const string BackupDirName = "gm-backups";
        public const int LogKeep = 400;

        private readonly List<LogLine> _log = new List<LogLine>();
        private readonly object _logLock = new object();

        public string DbPath { get; private set; }
        public string PvfPath { get; private set; }
        public List<string> ImagePackDirs { get; private set; }
        public int GamePort { get; private set; }
        /// <summary>网页 GM 自己的监听端口（由 HttpServer 回填，只用于界面显示）。</summary>
        public int GmPort { get; set; }
        public string AccountName { get; private set; }
        public long AccountId { get; private set; }

        public bool ShutToSelect { get; set; }
        public bool PresenceSupported { get; set; }

        private bool _lastServerUp;

        public GameScripts Scripts { get; private set; }
        public Catalog Catalog { get; private set; }
        public ImagePackLibrary ImagePacks { get; private set; }
        /// <summary>技能名表（PVF）。</summary>
        public SkillTable Skills { get; private set; }
        /// <summary>任务名 / 任务材料表（PVF）。</summary>
        public QuestTable Quests { get; private set; }

        public GmEnv()
        {
            ImagePackDirs = new List<string>();
            GamePort = 10011;
            AccountName = "";
            AccountId = -1;
            PresenceSupported = true;
            Catalog = new Catalog(this);
        }

        public sealed class LogLine
        {
            public string Time;
            public string Level;
            public string Message;

            public LogLine(string time, string level, string message)
            {
                Time = time;
                Level = level;
                Message = message;
            }
        }

        // ---------------- 日志 ----------------

        public void Note(string level, string message)
        {
            lock (_logLock)
            {
                _log.Add(new LogLine(DateTime.Now.ToString("HH:mm:ss"), level, message));
                if (_log.Count > LogKeep) _log.RemoveRange(0, _log.Count - LogKeep);
            }
        }

        public List<LogLine> GetLog()
        {
            lock (_logLock) return new List<LogLine>(_log);
        }

        // ---------------- 配置 ----------------

        public void Configure(string dbPath, string pvfPath, List<string> imagePackDirs, int gamePort)
        {
            DbPath = string.IsNullOrEmpty(dbPath) ? null : System.IO.Path.GetFullPath(dbPath);
            PvfPath = string.IsNullOrEmpty(pvfPath) ? null : System.IO.Path.GetFullPath(pvfPath);
            ImagePackDirs = imagePackDirs ?? new List<string>();
            if (gamePort > 0) GamePort = gamePort;

            CloseScripts();
            CloseImagePacks();

            LoadScripts();
            LoadImagePacks();
        }

        public void LoadScripts()
        {
            CloseScripts();
            if (PvfPath == null || !File.Exists(PvfPath))
            {
                Note("warn", "PVF 文件不存在：" + (PvfPath ?? "<未设置>") + " —— 物品名字会显示成编号");
                return;
            }

            try
            {
                Note("info", "正在读 PVF（" + new FileInfo(PvfPath).Length / 1024 / 1024 + " MB）…");
                PvfArchive archive = PvfArchive.Open(PvfPath);
                Scripts = GameScripts.Load(archive, m => Note("warn", m));
                Note("info", "PVF 已装载：索引 " + archive.Count + " 条，物品 " + Scripts.ItemCount + " 个");

                // 目录放后台重建，别把第一个请求卡住
                Catalog.Rebuild(archive, Scripts);

                // 技能名 / 任务名（原版也是从 PVF 读的）
                try
                {
                    Skills = SkillTable.Load(archive, Scripts.StringTables, m => Note("warn", m));
                    Quests = QuestTable.Load(archive, Scripts.StringTables, m => Note("warn", m));
                    Quests.UseItemIndex(Scripts.ItemPaths);
                    Note("info", "技能表 " + SkillTable.JobNames.Length + " 个职业，任务表 " + Quests.Count + " 条");
                }
                catch (Exception ex2)
                {
                    Note("warn", "读技能/任务表失败：" + ex2.Message);
                }
            }
            catch (Exception ex)
            {
                Note("bad", "装载 PVF 失败：" + ex.Message);
                Scripts = null;
            }
        }

        private void LoadImagePacks()
        {
            try
            {
                List<string> dirs = new List<string>();
                foreach (string d in ImagePackDirs)
                {
                    if (!string.IsNullOrEmpty(d) && Directory.Exists(d)) dirs.Add(d);
                }
                if (dirs.Count == 0) return;

                ImagePacks = ImagePackLibrary.Open(dirs, m => Note("warn", m));
                Note("info", "图标库就绪：" + dirs.Count + " 个目录，NPK " + (ImagePacks == null ? 0 : ImagePacks.PackCount) + " 个");
            }
            catch (Exception ex)
            {
                Note("warn", "图标库装载失败（不影响发放）：" + ex.Message);
                ImagePacks = null;
            }
        }

        public void CloseScripts()
        {
            Scripts = null;
            Skills = null;
            Quests = null;
        }

        public void CloseImagePacks()
        {
            if (ImagePacks != null)
            {
                ImagePacks.Dispose();
                ImagePacks = null;
            }
        }

        // ---------------- 服务器 / 在线状态 ----------------

        /// <summary>游戏服端口能不能连上 = 服务端在跑。</summary>
        public bool ServerUp
        {
            get { return PortOpen("127.0.0.1", GamePort, 500); }
        }

        public void ObserveServer()
        {
            bool up = ServerUp;
            if (!up)
            {
                if (ShutToSelect)
                {
                    ShutToSelect = false;
                    Note("warn", "服务端已停止 ——「小退写库」随之关闭");
                }
            }
            else if (!_lastServerUp && ShutToSelect)
            {
                ShutToSelect = false;
                Note("warn", "服务端刚重新启动 ——「小退写库」已自动关闭（要再写请重新小退）");
            }
            _lastServerUp = up;
        }

        /// <summary>服务端没在跑，或者用户确认了「已小退」才允许写数值/背包/技能/任务。</summary>
        public bool OnlineWriteAllowed
        {
            get { return !ServerUp || ShutToSelect; }
        }

        /// <summary>角色是否还在游戏里。null = 这份服务端没有 server_presence 表，判断不了。</summary>
        public bool? CharInGame(int characNo)
        {
            if (DbPath == null || !File.Exists(DbPath)) return null;
            try
            {
                using (SqliteConnection conn = SqliteConnection.OpenReadOnly(DbPath))
                {
                    if (!TableExists(conn, "server_presence"))
                    {
                        if (PresenceSupported) { PresenceSupported = false; Note("warn", "这份服务端没有 server_presence 表（旧版）—— 小退识别退回手动开关"); }
                        return null;
                    }
                    PresenceSupported = true;

                    using (SqliteCommand cmd = conn.CreateCommand("SELECT 1 FROM server_presence WHERE charac_no = ? LIMIT 1"))
                    {
                        cmd.Bind(1, characNo);
                        return cmd.Read();
                    }
                }
            }
            catch (Exception ex)
            {
                Note("warn", "查在线状态失败：" + ex.Message);
                return null;
            }
        }

        public int InGameCount()
        {
            if (DbPath == null || !File.Exists(DbPath)) return 0;
            try
            {
                using (SqliteConnection conn = SqliteConnection.OpenReadOnly(DbPath))
                {
                    if (!TableExists(conn, "server_presence")) return 0;
                    using (SqliteCommand cmd = conn.CreateCommand("SELECT COUNT(*) FROM server_presence"))
                    {
                        return cmd.Read() ? cmd.GetInt32(0) : 0;
                    }
                }
            }
            catch
            {
                return 0;
            }
        }

        /// <summary>某个账号名下是否有角色在游戏里。null = 判断不了。</summary>
        public bool? AccountInGame(long mId)
        {
            if (DbPath == null || !File.Exists(DbPath)) return null;
            try
            {
                using (SqliteConnection conn = SqliteConnection.OpenReadOnly(DbPath))
                {
                    if (!TableExists(conn, "server_presence")) return null;
                    using (SqliteCommand cmd = conn.CreateCommand("SELECT COUNT(*) FROM server_presence WHERE m_id = ?"))
                    {
                        cmd.Bind(1, mId);
                        if (!cmd.Read()) return false;
                        return cmd.GetInt32(0) > 0;
                    }
                }
            }
            catch
            {
                return null;
            }
        }

        public static bool TableExists(SqliteConnection conn, string table)
        {
            using (SqliteCommand cmd = conn.CreateCommand("SELECT 1 FROM sqlite_master WHERE type='table' AND name = ? LIMIT 1"))
            {
                cmd.Bind(1, table);
                return cmd.Read();
            }
        }

        public static bool PortOpen(string host, int port, int timeoutMs)
        {
            TcpClient c = new TcpClient();
            try
            {
                IAsyncResult ar = c.BeginConnect(host, port, null, null);
                if (!ar.AsyncWaitHandle.WaitOne(timeoutMs)) return false;
                c.EndConnect(ar);
                return true;
            }
            catch
            {
                return false;
            }
            finally
            {
                try { c.Close(); } catch { }
            }
        }

        // ---------------- 备份 ----------------

        /// <summary>写库前留一份备份。服务端在跑时用 VACUUM INTO，否则连 -wal/-shm 一起复制。</summary>
        public string BackupDb(string reason)
        {
            if (DbPath == null || !File.Exists(DbPath)) return null;
            try
            {
                string dir = System.IO.Path.Combine(System.IO.Path.GetDirectoryName(DbPath), BackupDirName);
                Directory.CreateDirectory(dir);
                string stamp = DateTime.Now.ToString("yyyyMMdd-HHmmss");
                string target = System.IO.Path.Combine(dir, "s1a5-" + stamp + ".db");
                int n = 1;
                while (File.Exists(target))
                {
                    target = System.IO.Path.Combine(dir, "s1a5-" + stamp + "-" + n + ".db");
                    n++;
                }

                if (ServerUp)
                {
                    using (SqliteConnection conn = SqliteConnection.OpenReadOnly(DbPath))
                    using (SqliteCommand cmd = conn.CreateCommand("VACUUM INTO ?"))
                    {
                        cmd.Bind(1, target);
                        cmd.Read();
                    }
                }
                else
                {
                    File.Copy(DbPath, target, true);
                }

                Note("info", "已备份数据库 → " + System.IO.Path.GetFileName(target));
                return target;
            }
            catch (Exception ex)
            {
                Note("warn", "写库前备份失败（继续执行）：" + ex.Message);
                return null;
            }
        }

        // ---------------- 账号 ----------------

        public void SetAccountName(string name)
        {
            AccountName = (name ?? "").Trim();
            AccountId = -1;
        }

        public long ResolveAccountId()
        {
            if (AccountId >= 0) return AccountId;
            if (string.IsNullOrEmpty(AccountName) || DbPath == null || !File.Exists(DbPath)) return -1;

            try
            {
                using (SqliteConnection conn = SqliteConnection.OpenReadOnly(DbPath))
                using (SqliteCommand cmd = conn.CreateCommand(
                    "SELECT m_id FROM member_info WHERE user_id = ? OR user_name = ? ORDER BY m_id LIMIT 1"))
                {
                    cmd.Bind(1, AccountName);
                    cmd.Bind(2, AccountName);
                    AccountId = cmd.Read() ? cmd.GetInt64(0) : -1;
                }
            }
            catch (Exception ex)
            {
                Note("warn", "查账号 " + AccountName + " 失败（本次不过滤账号）：" + ex.Message);
                AccountId = -1;
            }
            return AccountId;
        }

        public void Close()
        {
            CloseScripts();
            CloseImagePacks();
        }
    }
}
