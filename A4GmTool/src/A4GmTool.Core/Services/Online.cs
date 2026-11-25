using System;
using System.Collections.Generic;
using A4GmTool.Core.Data;

namespace A4GmTool.Core.Services
{
    /// <summary>邮箱里的一封邮件。</summary>
    public sealed class MailLine
    {
        public long PostalId;
        public long ItemId;
        public string Name = "";
        public long Count;
        public bool Quantity;
        public int Upgrade;
        public int Endurance;
        public string Sender = "";
        public bool Unlimited;
        public bool Avatar;
        public int Rarity;
    }

    /// <summary>
    /// 服务端在跑时也能用的操作：发物品走邮箱（postal 表），发时装直接进装扮栏。
    /// 这样不用停服、不用小退。
    /// </summary>
    public sealed class Online
    {
        public const string Sender = "GM";

        private readonly GmEnv _env;

        public Online(GmEnv env)
        {
            _env = env;
        }

        private SqliteConnection Open()
        {
            return SqliteConnection.OpenReadWrite(_env.DbPath);
        }

        private string RequireCharacter(SqliteConnection conn, int characNo)
        {
            if (characNo <= 0) return "没有选角色。";
            using (SqliteCommand cmd = conn.CreateCommand("SELECT 1 FROM charac_info WHERE charac_no = ? AND delete_flag = 0"))
            {
                cmd.Bind(1, characNo);
                if (!cmd.Read()) return "charac_info 里没有角色 " + characNo + "（或已删除）。";
            }
            return null;
        }

        /// <summary>塞一封到角色邮箱里。</summary>
        private long InsertItemMail(SqliteConnection conn, int characNo, int itemId, int count, int upgrade, int endurance, bool avatar)
        {
            // receive_time 是 NOT NULL，未领取时必须留数据库默认的 1970 时间戳，
            // 不能写 NULL（服务端就是靠 delete_flag=0 判断未领取的）。
            using (SqliteCommand cmd = conn.CreateCommand(
                "INSERT INTO postal (occ_time, send_charac_no, send_charac_name, receive_charac_no, item_id, add_info, endurance, "
                + "upgrade, amplify_option, amplify_value, gold, delete_flag, avata_flag, unlimit_flag, seal_flag, creature_flag, type) "
                + "VALUES (datetime('now','localtime'), 0, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0, ?, ?, 0, 0, 0)"))
            {
                cmd.Bind(1, Sender);
                cmd.Bind(2, characNo);
                cmd.Bind(3, itemId);
                cmd.Bind(4, count);
                cmd.Bind(5, endurance);
                cmd.Bind(6, upgrade);
                cmd.Bind(7, avatar ? 1 : 0);
                cmd.Bind(8, 1);
                cmd.ExecuteNonQuery();
            }

            using (SqliteCommand cmd = conn.CreateCommand("SELECT last_insert_rowid()"))
            {
                return cmd.Read() ? cmd.GetInt64(0) : 0;
            }
        }

        private int ItemTypeOf(int itemId)
        {
            if (_env.Scripts == null) return 0;
            Pvf.ItemScript s = _env.Scripts.Get(itemId);
            if (s == null) return 0;
            if (s.IsAvatarItem) return 8;
            if (s.Creature) return 6;
            if (s.Stackable) return 0;
            return 1;
        }

        private string ItemName(int itemId)
        {
            CatalogEntry e = _env.Catalog.Item(itemId);
            if (e != null && e.Name.Length > 0) return e.Name;
            return _env.Scripts != null ? _env.Scripts.NameOf(itemId) : "";
        }

        private string DeliveryHint(int characNo)
        {
            bool? inGame = _env.CharInGame(characNo);
            if (inGame == null)
                return "在邮箱里领取（若角色在游戏中，服务端会把新邮件直接推到邮箱；否则进游戏后可见）。";
            return inGame.Value
                ? "角色正在游戏中 —— 服务端会在几秒内把邮件直接推到他邮箱并弹提醒，不用小退。"
                : "角色此刻在选择界面 —— 邮件已在邮箱里，进游戏后即可领取。";
        }

        public OpResult GiveItem(int characNo, int itemId, int count, int enhance, int endurance)
        {
            if (_env.DbPath == null) return OpResult.Fail("数据库还没打开。");
            if (_env.Scripts == null) return OpResult.Fail("PVF 还没装载，无法校验物品。");
            if (!_env.Scripts.Has(itemId)) return OpResult.Fail("物品 " + itemId + " 不在 PVF 里。");

            Pvf.ItemScript script = _env.Scripts.Get(itemId);
            if (script != null && script.IsAvatarItem) return OpResult.Fail("这是时装，去「时装」页发。");

            int want = Math.Max(1, count);
            int enh = Math.Max(0, Math.Min(31, enhance));
            int endur = endurance > 0 ? Math.Min(endurance, 65535) : Math.Max(0, Math.Min(script == null ? 0 : script.Durability, 65535));

            try
            {
                long postalId;
                using (SqliteConnection conn = Open())
                {
                    string err = RequireCharacter(conn, characNo);
                    if (err != null) return OpResult.Fail(err);

                    _env.BackupDb("在线发放物品 " + itemId);
                    postalId = InsertItemMail(conn, characNo, itemId, want, enh, endur, script != null && script.IsAvatarItem);
                }

                string msg = "已投递到邮箱（邮件 " + postalId + "）：" + itemId + " " + ItemName(itemId)
                             + " ×" + want + "（角色 " + characNo + "）。" + DeliveryHint(characNo);
                _env.Note("info", msg);
                return OpResult.Success(msg);
            }
            catch (Exception ex)
            {
                _env.Note("bad", "在线发放失败：" + ex.Message);
                return OpResult.Fail("投递到邮箱失败：" + ex.Message);
            }
        }

        public OpResult GiveMany(int characNo, List<int[]> items)
        {
            if (_env.DbPath == null) return OpResult.Fail("数据库还没打开。");
            if (_env.Scripts == null) return OpResult.Fail("PVF 还没装载，无法校验物品。");

            try
            {
                List<string> names = new List<string>();
                int sent = 0;
                using (SqliteConnection conn = Open())
                {
                    string err = RequireCharacter(conn, characNo);
                    if (err != null) return OpResult.Fail(err);

                    _env.BackupDb("在线整批发放");
                    foreach (int[] row in items)
                    {
                        if (row == null || row.Length < 2) continue;
                        int id = row[0];
                        if (!_env.Scripts.Has(id)) continue;

                        Pvf.ItemScript s = _env.Scripts.Get(id);
                        if (s != null && s.IsAvatarItem) continue;

                        int n = Math.Max(1, row[1]);
                        int enh = Math.Max(0, Math.Min(31, row.Length > 2 ? row[2] : 0));
                        int endur = Math.Max(0, Math.Min(s == null ? 0 : s.Durability, 65535));
                        InsertItemMail(conn, characNo, id, n, enh, endur, false);
                        sent++;
                        names.Add((ItemName(id).Length > 0 ? ItemName(id) : id.ToString()) + " ×" + n);
                    }
                }

                if (sent == 0) return OpResult.Fail("清单里没有可发的物品（时装请去时装页）。");

                string msg = "已投递到邮箱 " + sent + " 封（" + string.Join("、", names.ToArray()) + "，角色 " + characNo + "）。" + DeliveryHint(characNo);
                _env.Note("info", msg);
                return OpResult.Success(msg);
            }
            catch (Exception ex)
            {
                _env.Note("bad", "在线整批发放失败：" + ex.Message);
                return OpResult.Fail("投递到邮箱失败：" + ex.Message);
            }
        }

        public OpResult GiveAvatar(int characNo, int itemId, int days, int abilityNo)
        {
            if (_env.DbPath == null) return OpResult.Fail("数据库还没打开。");
            if (_env.Scripts == null) return OpResult.Fail("PVF 还没装载，无法校验物品。");

            Pvf.ItemScript s = _env.Scripts.Get(itemId);
            if (s == null || !s.IsAvatarItem) return OpResult.Fail("物品 " + itemId + " 不是时装。");

            try
            {
                int slot;
                using (SqliteConnection conn = Open())
                {
                    string err = RequireCharacter(conn, characNo);
                    if (err != null) return OpResult.Fail(err);

                    HashSet<int> used = new HashSet<int>();
                    using (SqliteCommand cmd = conn.CreateCommand("SELECT slot FROM user_items WHERE charac_no = ?"))
                    {
                        cmd.Bind(1, characNo);
                        while (cmd.Read()) used.Add(cmd.GetInt32(0));
                    }

                    slot = -1;
                    for (int i = 104; i >= 0; i--)
                    {
                        if (!used.Contains(i + 10)) { slot = i; break; }
                    }
                    if (slot < 0) return OpResult.Fail("发不出去：装扮栏满了。");

                    _env.BackupDb("在线发放时装 " + itemId);
                    string expire = days > 0 ? DateTime.Now.AddDays(days).ToString("yyyy-MM-dd HH:mm:ss") : null;
                    using (SqliteCommand cmd = conn.CreateCommand(
                        "INSERT INTO user_items (charac_no, slot, it_id, expire_date, ability_no, reg_date) "
                        + "VALUES (?, ?, ?, ?, ?, datetime('now','localtime'))"))
                    {
                        cmd.Bind(1, characNo);
                        cmd.Bind(2, slot + 10);
                        cmd.Bind(3, itemId);
                        cmd.Bind(4, expire);
                        cmd.Bind(5, Math.Max(0, abilityNo));
                        cmd.ExecuteNonQuery();
                    }
                }

                string msg = "已发放时装 " + itemId + " " + ItemName(itemId) + " → 装扮栏槽 " + slot
                             + "（角色 " + characNo + "，期限 " + (days > 0 ? days + " 天" : "永久") + "）。"
                             + "重新选角就能看到 —— 在线时别同时在游戏里买/合成时装（挑的空槽可能撞上）。";
                _env.Note("info", msg);
                return OpResult.Success(msg);
            }
            catch (Exception ex)
            {
                _env.Note("bad", "在线发时装失败：" + ex.Message);
                return OpResult.Fail("写 user_items 失败：" + ex.Message);
            }
        }

        /// <summary>读某角色的邮箱（未删除的邮件）。</summary>
        public List<MailLine> PendingMails(int characNo)
        {
            List<MailLine> list = new List<MailLine>();
            if (_env.DbPath == null) return list;

            try
            {
                using (SqliteConnection conn = SqliteConnection.OpenReadOnly(_env.DbPath))
                using (SqliteCommand cmd = conn.CreateCommand(
                    "SELECT postal_id, item_id, add_info, upgrade, endurance, send_charac_name, unlimit_flag, avata_flag "
                    + "FROM postal WHERE receive_charac_no = ? AND delete_flag = 0 ORDER BY postal_id"))
                {
                    cmd.Bind(1, characNo);
                    while (cmd.Read())
                    {
                        long itemId = cmd.GetInt64(1);
                        long addInfo = cmd.GetInt64(2);

                        bool stackable = false;
                        if (_env.Scripts != null)
                        {
                            Pvf.ItemScript s = _env.Scripts.Get((int)itemId);
                            stackable = s != null && !s.IsAvatarItem && !s.Creature && s.Stackable;
                        }

                        CatalogEntry e = _env.Catalog.Item((int)itemId);
                        list.Add(new MailLine
                        {
                            PostalId = cmd.GetInt64(0),
                            ItemId = itemId,
                            Name = e != null ? e.Name : (_env.Scripts != null ? _env.Scripts.NameOf((int)itemId) : ""),
                            Count = stackable ? Math.Max(1L, addInfo) : 1L,
                            Quantity = stackable,
                            Upgrade = cmd.GetInt32(3),
                            Endurance = cmd.GetInt32(4),
                            Sender = cmd.GetString(5) ?? "",
                            Unlimited = cmd.GetInt32(6) == 1,
                            Avatar = cmd.GetInt32(7) == 1,
                            Rarity = e != null ? e.Rarity : 0
                        });
                    }
                }
            }
            catch (Exception ex)
            {
                _env.Note("warn", "读邮箱失败：" + ex.Message);
            }
            return list;
        }
    }
}
