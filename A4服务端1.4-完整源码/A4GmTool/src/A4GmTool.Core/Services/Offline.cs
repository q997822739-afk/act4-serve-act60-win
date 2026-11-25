using System;
using System.Collections.Generic;
using A4GmTool.Core.Data;

namespace A4GmTool.Core.Services
{
    /// <summary>所有写操作的统一返回。</summary>
    public sealed class OpResult
    {
        public bool Ok;
        public string Message;

        public static OpResult Success(string message)
        {
            return new OpResult { Ok = true, Message = message ?? "" };
        }

        public static OpResult Fail(string message)
        {
            return new OpResult { Ok = false, Message = message ?? "" };
        }
    }

    /// <summary>
    /// 离线（服务端没在跑 / 角色已小退）时的直写库操作。
    ///
    /// 安全模型照搬远程版：
    ///  * 数值 / 背包 / 技能 / 任务：服务端在跑就拒写，除非用户确认「已小退」；
    ///  * 有 server_presence 表就自动判断角色在不在游戏里，没有就退回手动开关；
    ///  * 每次写之前先备份数据库。
    /// </summary>
    public sealed class Offline
    {
        private readonly GmEnv _env;

        public Offline(GmEnv env)
        {
            _env = env;
        }

        private delegate string WriteOp(SqliteConnection conn);

        private SqliteConnection OpenWrite()
        {
            return SqliteConnection.OpenReadWrite(_env.DbPath);
        }

        // ---------------- 安全门 ----------------

        private OpResult StoreReady()
        {
            return _env.DbPath != null && System.IO.File.Exists(_env.DbPath)
                ? OpResult.Success("")
                : OpResult.Fail("数据库还没打开。");
        }

        private OpResult ManualFallback()
        {
            return !_env.ShutToSelect
                ? OpResult.Fail("这份服务端没有「角色在线」记录表，无法自动判断是否小退 —— "
                                + "请按老办法：游戏里小退到【角色选择界面】后，点开顶栏「我已小退」开关，再改。")
                : StoreReady();
        }

        public OpResult GateCharacter(int characNo)
        {
            _env.ObserveServer();
            if (!_env.ServerUp) return StoreReady();

            bool? inGame = _env.CharInGame(characNo);
            if (inGame == null) return ManualFallback();

            if (inGame.Value)
            {
                return OpResult.Fail("角色 " + characNo + " 正在游戏中 —— 服务端正把它（背包 / 技能 / 数值 / 任务）整块拿在内存里，"
                                     + "现在直写会被它存档时覆盖。\n做法：游戏里小退到【角色选择界面】（不要进游戏），回到本页直接再点一次即可 —— "
                                     + "新服务端会自动识别你已经小退，不用再点任何开关。");
            }
            return StoreReady();
        }

        public OpResult GateAccount(long mId)
        {
            _env.ObserveServer();
            if (!_env.ServerUp) return StoreReady();

            bool? inGame = _env.AccountInGame(mId);
            if (inGame == null) return ManualFallback();

            return inGame.Value
                ? OpResult.Fail("账号 " + mId + " 名下已有角色在游戏中 —— 服务端正把账号数据拿在内存里。"
                                + "请先在游戏里小退到【角色选择界面】，再回本页操作。")
                : StoreReady();
        }

        public OpResult GateFree()
        {
            _env.ObserveServer();
            return StoreReady();
        }

        private OpResult Write(int characNo, string desc, WriteOp op)
        {
            OpResult gate = GateCharacter(characNo);
            if (!gate.Ok) return gate;
            return WriteRaw(desc, op);
        }

        private OpResult WriteAccount(long mId, string desc, WriteOp op)
        {
            OpResult gate = GateAccount(mId);
            if (!gate.Ok) return gate;
            return WriteRaw(desc, op);
        }

        private OpResult WriteRaw(string desc, WriteOp op)
        {
            _env.BackupDb(desc);
            try
            {
                using (SqliteConnection conn = OpenWrite())
                {
                    string err = op(conn);
                    if (err != null) return OpResult.Fail(err);
                }
                _env.Note("info", desc);
                return OpResult.Success(desc);
            }
            catch (Exception ex)
            {
                _env.Note("bad", desc + " 失败：" + ex.Message);
                return OpResult.Fail("写库失败：" + ex.Message);
            }
        }

        // ---------------- 数值 ----------------

        public OpResult SetLevel(int characNo, int level)
        {
            if (level < 1 || level > 100) return OpResult.Fail("等级 " + level + " 超出 1..100 的范围。");

            int oldLevel = 0;
            long oldExp = 0;
            try
            {
                using (SqliteConnection conn = SqliteConnection.OpenReadOnly(_env.DbPath))
                using (SqliteCommand cmd = conn.CreateCommand("SELECT lev, exp FROM charac_info WHERE charac_no = ?"))
                {
                    cmd.Bind(1, characNo);
                    if (!cmd.Read()) return OpResult.Fail("角色 " + characNo + " 在 charac_info 里没有行。");
                    oldLevel = cmd.GetInt32(0);
                    oldExp = cmd.GetInt64(1);
                }
            }
            catch (Exception ex)
            {
                return OpResult.Fail("读角色失败：" + ex.Message);
            }

            long newExp = oldExp;
            if (oldLevel != level) newExp = 0;

            string desc = "等级 " + oldLevel + " → " + level + "（角色 " + characNo + "）";
            return Write(characNo, desc, conn =>
            {
                using (SqliteCommand cmd = conn.CreateCommand("UPDATE charac_info SET lev = ?, exp = ? WHERE charac_no = ?"))
                {
                    cmd.Bind(1, level);
                    cmd.Bind(2, newExp);
                    cmd.Bind(3, characNo);
                    cmd.ExecuteNonQuery();
                    if (conn.Changes() == 0) return "写 charac_info 失败。";
                }
                return null;
            });
        }

        public OpResult SetSp(int characNo, long sp)
        {
            if (sp < 0 || sp > int.MaxValue) return OpResult.Fail("SP 超出 0.." + int.MaxValue + "。");
            string desc = "SP 余量设为 " + sp + "（角色 " + characNo + "）";
            return Write(characNo, desc, conn =>
            {
                using (SqliteCommand cmd = conn.CreateCommand("UPDATE skill SET remain_sp = ? WHERE charac_no = ?"))
                {
                    cmd.Bind(1, sp);
                    cmd.Bind(2, characNo);
                    cmd.ExecuteNonQuery();
                    if (conn.Changes() == 0) return "skill 表没有该角色的行。";
                }
                return null;
            });
        }

        public OpResult SetMoney(int characNo, long money)
        {
            if (money < 0 || money > 4294967295L) return OpResult.Fail("金币超出 0..4294967295。");
            string desc = "金币设为 " + money + "（角色 " + characNo + "）";
            return Write(characNo, desc, conn =>
            {
                using (SqliteCommand cmd = conn.CreateCommand("UPDATE inventory SET money = ? WHERE charac_no = ?"))
                {
                    cmd.Bind(1, money);
                    cmd.Bind(2, characNo);
                    cmd.ExecuteNonQuery();
                    if (conn.Changes() == 0) return "inventory 表没有该角色的行。";
                }
                return null;
            });
        }

        public OpResult SetWinPoint(int characNo, long winPoint)
        {
            if (winPoint < 0 || winPoint > 4294967295L) return OpResult.Fail("决斗胜点超出 0..4294967295。");
            string desc = "决斗胜点设为 " + winPoint + "（角色 " + characNo + "）";
            return Write(characNo, desc, conn =>
            {
                using (SqliteCommand cmd = conn.CreateCommand("UPDATE pvp_result SET win_point = ? WHERE charac_no = ?"))
                {
                    cmd.Bind(1, winPoint);
                    cmd.Bind(2, characNo);
                    cmd.ExecuteNonQuery();
                    if (conn.Changes() == 0) return "pvp_result 表没有该角色的行（建角色流程该插的）。";
                }
                return null;
            });
        }

        public OpResult SetCera(long mId, long cera)
        {
            if (cera < 0 || cera > 4294967295L) return OpResult.Fail("点券超出 0..4294967295。");
            string desc = "点券设为 " + cera + "（账号 " + mId + "）";
            return WriteAccount(mId, desc, conn =>
            {
                using (SqliteCommand cmd = conn.CreateCommand("UPDATE member_info SET cera = ? WHERE m_id = ?"))
                {
                    cmd.Bind(1, cera);
                    cmd.Bind(2, mId);
                    cmd.ExecuteNonQuery();
                    if (conn.Changes() == 0) return "member_info 里没有账号 " + mId + "。";
                }
                return null;
            });
        }

        public OpResult RecoverFatigue(int characNo)
        {
            string desc = "疲劳已恢复（fatigue/used_fatigue 清零，角色 " + characNo + "）";
            return Write(characNo, desc, conn =>
            {
                using (SqliteCommand cmd = conn.CreateCommand("UPDATE charac_stat SET fatigue = 0, used_fatigue = 0 WHERE charac_no = ?"))
                {
                    cmd.Bind(1, characNo);
                    cmd.ExecuteNonQuery();
                    if (conn.Changes() == 0) return "charac_stat 表没有该角色的行。";
                }
                return null;
            });
        }

        public int CurrentJob(int characNo)
        {
            try
            {
                using (SqliteConnection conn = SqliteConnection.OpenReadOnly(_env.DbPath))
                using (SqliteCommand cmd = conn.CreateCommand("SELECT job FROM charac_info WHERE charac_no = ?"))
                {
                    cmd.Bind(1, characNo);
                    return cmd.Read() ? cmd.GetInt32(0) : 0;
                }
            }
            catch
            {
                return 0;
            }
        }

        public OpResult SetJobClass(int characNo, int job)
        {
            if (job < 0 || job > 15) return OpResult.Fail("职业编号超出 0..15：" + job);
            int old = CurrentJob(characNo);
            string desc = "职业 " + old + " → " + job + "（角色 " + characNo + "）";
            return Write(characNo, desc, conn =>
            {
                using (SqliteCommand cmd = conn.CreateCommand("UPDATE charac_info SET job = ? WHERE charac_no = ?"))
                {
                    cmd.Bind(1, job);
                    cmd.Bind(2, characNo);
                    cmd.ExecuteNonQuery();
                    if (conn.Changes() == 0) return "charac_info 表没有该角色的行。";
                }
                return null;
            });
        }

        public OpResult SetGrowType(int characNo, int firstGrow, int secondGrow)
        {
            int growType = firstGrow * 10 + secondGrow;
            if (growType < 0 || growType > 255) return OpResult.Fail("成长类型超出范围：firstGrow=" + firstGrow + " secondGrow=" + secondGrow);
            string desc = "成长类型设为 " + growType + "（角色 " + characNo + "）";
            return Write(characNo, desc, conn =>
            {
                using (SqliteCommand cmd = conn.CreateCommand("UPDATE charac_info SET grow_type = ? WHERE charac_no = ?"))
                {
                    cmd.Bind(1, growType);
                    cmd.Bind(2, characNo);
                    cmd.ExecuteNonQuery();
                    if (conn.Changes() == 0) return "charac_info 表没有该角色的行。";
                }
                return null;
            });
        }

        public OpResult SetClass(int characNo, int job, int firstGrow, int secondGrow)
        {
            OpResult j = SetJobClass(characNo, job);
            if (!j.Ok) return j;
            if (firstGrow > 0 || secondGrow > 0) return SetGrowType(characNo, firstGrow, secondGrow);
            return j;
        }

        // ---------------- 发放物品（离线写背包） ----------------

        /// <summary>
        /// 往背包里塞物品。返回被放入的槽号，-1 表示背包满。
        /// </summary>
        private static int Grant(ItemContainer inv, int itemId, int itemType, int count)
        {
            bool stackable = !(itemType == 1 || itemType == 5 || itemType == 6 || itemType == 8);
            if (stackable)
            {
                long remain = Math.Max(1, count);
                int lastSlot = -1;
                for (int i = 0; i < inv.Slots && remain > 0; i++)
                {
                    InvenItem it = inv[i];
                    if (it.IsEmpty || it.ItemId != itemId) continue;
                    long room = 9999 - it.AddInfo;
                    if (room <= 0) continue;
                    long add = Math.Min(room, remain);
                    inv[i] = it.WithAddInfo(it.AddInfo + add);
                    remain -= add;
                    lastSlot = i;
                }

                while (remain > 0)
                {
                    int slot = inv.FirstEmptySlot();
                    if (slot < 0) return lastSlot;
                    long add = Math.Min(9999, remain);
                    inv[slot] = InvenItem.Of(itemId, itemType, add);
                    remain -= add;
                    lastSlot = slot;
                }
                return lastSlot;
            }

            int free = inv.FirstEmptySlot();
            if (free < 0) return -1;
            inv[free] = InvenItem.Of(itemId, itemType, 1);
            return free;
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

        public OpResult GiveItem(int characNo, int itemId, int count, int enhance, int endurance)
        {
            if (_env.DbPath == null) return OpResult.Fail("数据库还没打开。");
            if (_env.Scripts == null) return OpResult.Fail("PVF 还没装载，无法校验物品。");
            if (!_env.Scripts.Has(itemId)) return OpResult.Fail("物品 " + itemId + " 不在 PVF 里。");

            Pvf.ItemScript script = _env.Scripts.Get(itemId);
            if (script != null && script.IsAvatarItem) return OpResult.Fail("这是时装，去「时装」页发。");

            int want = Math.Max(1, count);
            int itemType = ItemTypeOf(itemId);
            CatalogEntry entry = _env.Catalog.Item(itemId);
            string name = entry == null ? "" : entry.Name;
            string desc = "发放 " + itemId + " " + name + " ×" + want + " → 背包（角色 " + characNo + "）";

            return Write(characNo, desc, conn =>
            {
                byte[] blob = ReadInventoryBlob(conn, characNo, "inventory");
                if (blob == null) return "inventory 表没有该角色的行。";

                ItemContainer inv = ItemContainer.Decode(blob, ItemContainer.InventorySlots);
                int slot = Grant(inv, itemId, itemType, want);
                if (slot < 0) return "发不出去：背包满了。";

                if (enhance > 0 || endurance > 0)
                {
                    InvenItem it = inv[slot];
                    if (enhance > 0) it = it.WithUpgrade(Math.Min(enhance, 31));
                    if (endurance > 0) it = it.WithEndurance(Math.Min(endurance, 65535));
                    inv[slot] = it;
                }

                SaveInventoryBlob(conn, characNo, "inventory", inv.Encode());
                return null;
            });
        }

        public OpResult GiveMany(int characNo, List<int[]> items)
        {
            if (_env.DbPath == null) return OpResult.Fail("数据库还没打开。");
            if (_env.Scripts == null) return OpResult.Fail("PVF 还没装载，无法校验物品。");

            List<int[]> todo = new List<int[]>();
            List<string> names = new List<string>();
            foreach (int[] row in items)
            {
                if (row == null || row.Length < 2) continue;
                int id = row[0];
                if (!_env.Scripts.Has(id)) continue;

                Pvf.ItemScript s = _env.Scripts.Get(id);
                if (s != null && s.IsAvatarItem) continue;

                int n = Math.Max(1, row[1]);
                int enh = row.Length > 2 ? row[2] : 0;
                todo.Add(new[] { id, n, enh });
                CatalogEntry e = _env.Catalog.Item(id);
                names.Add((e == null ? id.ToString() : e.Name) + " ×" + n);
            }

            if (todo.Count == 0) return OpResult.Fail("清单里没有可发的物品（时装请去时装页）。");

            string desc = "整批发放 " + todo.Count + " 件（" + string.Join("、", names.ToArray()) + "，角色 " + characNo + "）";
            return Write(characNo, desc, conn =>
            {
                byte[] blob = ReadInventoryBlob(conn, characNo, "inventory");
                if (blob == null) return "inventory 表没有该角色的行。";

                ItemContainer inv = ItemContainer.Decode(blob, ItemContainer.InventorySlots);
                int ok = 0;
                foreach (int[] row in todo)
                {
                    int slot = Grant(inv, row[0], ItemTypeOf(row[0]), row[1]);
                    if (slot >= 0)
                    {
                        if (row[2] > 0) inv[slot] = inv[slot].WithUpgrade(Math.Min(row[2], 31));
                        ok++;
                    }
                }

                if (ok == 0) return "一件都没放进去：背包满了。";
                SaveInventoryBlob(conn, characNo, "inventory", inv.Encode());
                return null;
            });
        }

        public OpResult GiveAvatar(int characNo, int itemId, int days, int abilityNo)
        {
            if (_env.DbPath == null) return OpResult.Fail("数据库还没打开。");
            if (_env.Scripts == null) return OpResult.Fail("PVF 还没装载，无法校验物品。");

            Pvf.ItemScript s = _env.Scripts.Get(itemId);
            if (s == null || !s.IsAvatarItem) return OpResult.Fail("物品 " + itemId + " 不是时装。");

            CatalogEntry e = _env.Catalog.Item(itemId);
            string name = e == null ? "" : e.Name;
            string desc = "发放时装 " + itemId + " " + name + " → 装扮栏（角色 " + characNo + "，"
                          + (days > 0 ? days + " 天" : "永久") + "）";

            return Write(characNo, desc, conn =>
            {
                int slot = FirstFreeAvatarSlot(conn, characNo);
                if (slot < 0) return "发不出去：装扮栏满了。";

                string expire = days > 0
                    ? DateTime.Now.AddDays(days).ToString("yyyy-MM-dd HH:mm:ss")
                    : null;

                using (SqliteCommand cmd = conn.CreateCommand(
                    "INSERT INTO user_items (charac_no, slot, it_id, expire_date, ability_no, reg_date) VALUES (?, ?, ?, ?, ?, datetime('now','localtime'))"))
                {
                    cmd.Bind(1, characNo);
                    cmd.Bind(2, slot + 10);
                    cmd.Bind(3, itemId);
                    cmd.Bind(4, expire);
                    cmd.Bind(5, Math.Max(0, abilityNo));
                    cmd.ExecuteNonQuery();
                }
                return null;
            });
        }

        private static int FirstFreeAvatarSlot(SqliteConnection conn, int characNo)
        {
            HashSet<int> used = new HashSet<int>();
            using (SqliteCommand cmd = conn.CreateCommand("SELECT slot FROM user_items WHERE charac_no = ?"))
            {
                cmd.Bind(1, characNo);
                while (cmd.Read()) used.Add(cmd.GetInt32(0));
            }
            for (int i = 104; i >= 0; i--)
            {
                if (!used.Contains(i + 10)) return i;
            }
            return -1;
        }

        // ---------------- 背包 / 时装 ----------------

        private static byte[] ReadInventoryBlob(SqliteConnection conn, int characNo, string column)
        {
            using (SqliteCommand cmd = conn.CreateCommand("SELECT " + column + " FROM inventory WHERE charac_no = ?"))
            {
                cmd.Bind(1, characNo);
                if (!cmd.Read()) return null;
                return cmd.GetBlob(0) ?? new byte[0];
            }
        }

        private static void SaveInventoryBlob(SqliteConnection conn, int characNo, string column, byte[] blob)
        {
            using (SqliteCommand cmd = conn.CreateCommand("UPDATE inventory SET " + column + " = ? WHERE charac_no = ?"))
            {
                cmd.Bind(1, blob);
                cmd.Bind(2, characNo);
                cmd.ExecuteNonQuery();
            }
        }

        public OpResult ClearInventory(int characNo)
        {
            string desc = "清空背包（角色 " + characNo + "）";
            return Write(characNo, desc, conn =>
            {
                byte[] blob = ReadInventoryBlob(conn, characNo, "inventory");
                if (blob == null) return "inventory 表没有该角色的行。";
                ItemContainer inv = new ItemContainer(ItemContainer.InventorySlots);
                SaveInventoryBlob(conn, characNo, "inventory", inv.Encode());
                return null;
            });
        }

        public OpResult EditItem(int characNo, int container, int slot, string op, long value)
        {
            string column = ContainerColumn(container);
            if (column == null) return OpResult.Fail("未知容器：" + container);
            int slots = ContainerSlots(container);
            if (slot < 0 || slot >= slots) return OpResult.Fail("槽号超出 0.." + (slots - 1) + "：" + slot);

            string desc = "改背包（角色 " + characNo + "，容器 " + container + "，槽 " + slot + "，" + op + "=" + value + "）";
            return Write(characNo, desc, conn =>
            {
                byte[] blob = ReadInventoryBlob(conn, characNo, column);
                if (blob == null) return "inventory 表没有该角色的行。";

                ItemContainer inv = ItemContainer.Decode(blob, slots);
                InvenItem it = inv[slot];
                if (it.IsEmpty) return "这一格是空的。";

                switch ((op ?? "").ToLowerInvariant())
                {
                    case "upgrade":
                        inv[slot] = it.WithUpgrade((int)Math.Max(0, Math.Min(31, value)));
                        break;
                    case "count":
                        inv[slot] = it.WithAddInfo(Math.Max(1, Math.Min(9999, value)));
                        break;
                    case "endurance":
                        inv[slot] = it.WithEndurance((int)Math.Max(0, Math.Min(65535, value)));
                        break;
                    case "enchant":
                        inv[slot] = it.WithEnchant(value);
                        break;
                    case "amplify":
                        inv[slot] = it.Clone();
                        inv[slot].AmplifyOption = (int)Math.Max(0, Math.Min(255, value));
                        break;
                    case "upgradeseparate":
                        inv[slot] = it.WithUpgradeSeparate((int)Math.Max(0, Math.Min(31, value)));
                        break;
                    case "delete":
                        inv[slot] = InvenItem.Empty();
                        break;
                    default:
                        return "不认识的编辑操作：" + op;
                }

                SaveInventoryBlob(conn, characNo, column, inv.Encode());
                return null;
            });
        }

        public static string ContainerColumn(int container)
        {
            switch (container)
            {
                case 0: return "inventory";
                case 2: return "cargo";
                case 3: return "equipslot";
                case 7: return "creature";
                default: return null;
            }
        }

        public static int ContainerSlots(int container)
        {
            switch (container)
            {
                case 0: return ItemContainer.InventorySlots;
                case 2: return ItemContainer.CargoSlots;
                case 3: return ItemContainer.EquipSlotSlots;
                case 7: return ItemContainer.CreatureSlots;
                default: return 0;
            }
        }

        public OpResult DeleteAvatarItem(int characNo, long uiId)
        {
            string desc = "删时装 uiId=" + uiId + "（角色 " + characNo + "）";
            return Write(characNo, desc, conn =>
            {
                using (SqliteCommand cmd = conn.CreateCommand("DELETE FROM user_items WHERE ui_id = ? AND charac_no = ?"))
                {
                    cmd.Bind(1, uiId);
                    cmd.Bind(2, characNo);
                    cmd.ExecuteNonQuery();
                    if (conn.Changes() == 0) return "user_items 里没有这件时装。";
                }
                return null;
            });
        }

        public OpResult SetAvatarOption(int characNo, long uiId, int option)
        {
            string desc = "改时装词条 uiId=" + uiId + " option=" + option + "（角色 " + characNo + "）";
            return Write(characNo, desc, conn =>
            {
                using (SqliteCommand cmd = conn.CreateCommand("UPDATE user_items SET ability_no = ? WHERE ui_id = ? AND charac_no = ?"))
                {
                    cmd.Bind(1, option);
                    cmd.Bind(2, uiId);
                    cmd.Bind(3, characNo);
                    cmd.ExecuteNonQuery();
                    if (conn.Changes() == 0) return "user_items 里没有这件时装（或值没变）。";
                }
                return null;
            });
        }

        public OpResult DeleteMail(int characNo, long postalId)
        {
            if (characNo <= 0) return OpResult.Fail("没有选角色。");
            if (postalId <= 0) return OpResult.Fail("邮件号不合法：" + postalId + "。");

            string desc = "强行删除邮件 " + postalId + "（角色 " + characNo + "）";
            return Write(characNo, desc, conn =>
            {
                using (SqliteCommand cmd = conn.CreateCommand("SELECT receive_charac_no, delete_flag FROM postal WHERE postal_id = ?"))
                {
                    cmd.Bind(1, postalId);
                    if (!cmd.Read()) return "postal 里没有邮件 " + postalId + "（可能已经领走/过期/被删）。";
                    long owner = cmd.GetInt64(0);
                    int deleted = cmd.GetInt32(1);
                    if (owner != characNo) return "邮件 " + postalId + " 不属于角色 " + characNo + "（收件人是 " + owner + "），拒绝删除。";
                    if (deleted != 0) return "邮件 " + postalId + " 已经是删除状态，不用再删。";
                }

                using (SqliteCommand cmd = conn.CreateCommand(
                    "UPDATE postal SET delete_flag = 2, receive_time = datetime('now','localtime') "
                    + "WHERE postal_id = ? AND receive_charac_no = ? AND delete_flag = 0"))
                {
                    cmd.Bind(1, postalId);
                    cmd.Bind(2, characNo);
                    cmd.ExecuteNonQuery();
                    if (conn.Changes() != 1) return "删除邮件 " + postalId + " 没有生效（可能刚被改动），请刷新后重试。";
                }
                return null;
            });
        }

        // ---------------- 技能 ----------------

        public OpResult SetSkillLevel(int characNo, int slot, int skillIndex, int level)
        {
            if (slot < 0 || slot >= Data.SkillSlots.Slots) return OpResult.Fail("技能槽超出 0..221：" + slot);
            if (skillIndex < 0 || skillIndex > 255) return OpResult.Fail("技能索引超出 0..255：" + skillIndex);

            string desc = "技能槽 " + slot + " 设为 " + skillIndex + " 级 " + level + "（角色 " + characNo + "）";
            return Write(characNo, desc, conn =>
            {
                byte[] blob;
                using (SqliteCommand cmd = conn.CreateCommand("SELECT skill_slot FROM skill WHERE charac_no = ?"))
                {
                    cmd.Bind(1, characNo);
                    if (!cmd.Read()) return "skill 表没有该角色的行。";
                    blob = cmd.GetBlob(0);
                }

                Data.SkillSlots slots = Data.SkillSlots.Decode(blob);
                if (skillIndex == 0) slots.Clear(slot);
                else slots.Set(slot, skillIndex, level);

                using (SqliteCommand cmd = conn.CreateCommand("UPDATE skill SET skill_slot = ? WHERE charac_no = ?"))
                {
                    cmd.Bind(1, slots.Encode());
                    cmd.Bind(2, characNo);
                    cmd.ExecuteNonQuery();
                }
                return null;
            });
        }

        // ---------------- 任务 ----------------

        public OpResult QuestClear(int characNo, List<int> ids, bool all)
        {
            string desc = all ? "一键完成全部进行中任务（角色 " + characNo + "）"
                              : "完成任务 " + string.Join(",", ids.ConvertAll(i => i.ToString()).ToArray()) + "（角色 " + characNo + "）";

            return Write(characNo, desc, conn =>
            {
                long[] plays = new long[Data.QuestRunning.Slots];
                int[] questIds = new int[Data.QuestRunning.Slots];
                byte[] flagBlob;

                using (SqliteCommand cmd = conn.CreateCommand(
                    "SELECT clear_quest, play_1, play_2, play_3, play_4, play_5, play_6, play_7, play_8, play_9, play_10,"
                    + " play_11, play_12, play_13, play_14, play_15, play_16, play_17, play_18, play_19, play_20"
                    + " FROM new_charac_quest WHERE charac_no = ?"))
                {
                    cmd.Bind(1, characNo);
                    if (!cmd.Read()) return "new_charac_quest 表没有该角色的行。";
                    flagBlob = cmd.GetBlob(0);
                    for (int i = 0; i < Data.QuestRunning.Slots; i++) questIds[i] = cmd.GetInt32(1 + i);
                }

                Data.QuestClearFlags flags = Data.QuestClearFlags.Decode(flagBlob);
                List<int> targets = all ? new List<int>() : new List<int>(ids);
                if (all)
                {
                    foreach (int q in questIds) if (q != 0) targets.Add(q);
                }
                if (targets.Count == 0) return "没有要完成的任务。";

                int done = 0;
                foreach (int q in targets)
                {
                    if (q <= 0) continue;
                    if (flags.SetCleared(q)) done++;
                }

                using (SqliteCommand cmd = conn.CreateCommand("UPDATE new_charac_quest SET clear_quest = ? WHERE charac_no = ?"))
                {
                    cmd.Bind(1, flags.Encode());
                    cmd.Bind(2, characNo);
                    cmd.ExecuteNonQuery();
                }

                // 已完成的从「进行中」里清掉
                for (int i = 0; i < questIds.Length; i++)
                {
                    if (targets.Contains(questIds[i]))
                    {
                        using (SqliteCommand cmd = conn.CreateCommand(
                            "UPDATE new_charac_quest SET play_" + (i + 1) + " = 0, play_" + (i + 1) + "_trigger = 0 WHERE charac_no = ?"))
                        {
                            cmd.Bind(1, characNo);
                            cmd.ExecuteNonQuery();
                        }
                    }
                }

                return done == 0 ? "这些任务本来就已经完成了。" : null;
            });
        }

        // ---------------- GM 名单 ----------------

        public OpResult GmAdd(string accountOrId, int level)
        {
            long mId;
            if (!long.TryParse(accountOrId, out mId))
            {
                try
                {
                    using (SqliteConnection conn = SqliteConnection.OpenReadOnly(_env.DbPath))
                    using (SqliteCommand cmd = conn.CreateCommand("SELECT m_id FROM member_info WHERE user_id = ? OR user_name = ? LIMIT 1"))
                    {
                        cmd.Bind(1, accountOrId);
                        cmd.Bind(2, accountOrId);
                        if (!cmd.Read()) return OpResult.Fail("找不到账号：" + accountOrId);
                        mId = cmd.GetInt64(0);
                    }
                }
                catch (Exception ex)
                {
                    return OpResult.Fail("查账号失败：" + ex.Message);
                }
            }

            string desc = "账号 " + mId + " 加入 GM 名单，等级 " + level;
            return WriteAccount(mId, desc, conn =>
            {
                using (SqliteCommand cmd = conn.CreateCommand(
                    "INSERT INTO gm_manifest (m_id, level) VALUES (?, ?) ON CONFLICT(m_id) DO UPDATE SET level = excluded.level"))
                {
                    cmd.Bind(1, mId);
                    cmd.Bind(2, Math.Max(1, Math.Min(255, level)));
                    cmd.ExecuteNonQuery();
                }
                return null;
            });
        }

        public OpResult GmRemove(long mId)
        {
            if (mId <= 0) return OpResult.Fail("账号号不合法：" + mId);
            string desc = "账号 " + mId + " 移出 GM 名单";
            return WriteAccount(mId, desc, conn =>
            {
                using (SqliteCommand cmd = conn.CreateCommand("DELETE FROM gm_manifest WHERE m_id = ?"))
                {
                    cmd.Bind(1, mId);
                    cmd.ExecuteNonQuery();
                    if (conn.Changes() == 0) return "gm_manifest 里没有账号 " + mId;
                }
                return null;
            });
        }

        public OpResult DeleteCharacter(int characNo, string name)
        {
            string desc = "删除角色 " + characNo + "（" + name + "）";
            return Write(characNo, desc, conn =>
            {
                using (SqliteCommand cmd = conn.CreateCommand("UPDATE charac_info SET delete_flag = 1, delete_time = datetime('now','localtime') WHERE charac_no = ?"))
                {
                    cmd.Bind(1, characNo);
                    cmd.ExecuteNonQuery();
                    if (conn.Changes() == 0) return "charac_info 里没有角色 " + characNo + "。";
                }
                return null;
            });
        }
    }
}
