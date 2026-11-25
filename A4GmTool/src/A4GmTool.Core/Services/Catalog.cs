using System;
using System.Collections.Generic;
using System.Threading;
using A4GmTool.Core.Pvf;

namespace A4GmTool.Core.Services
{
    /// <summary>物品目录里的一条。字段名与远程网页 GM 的 entryJson 一一对应。</summary>
    public sealed class CatalogEntry
    {
        public int Id;
        public string Path = "";
        public string Name = "";
        public string Kind = "other";
        public string KindName = "其他";
        public string Part = "";
        public string PartName = "";
        public string Subtype = "";
        public string SubtypeName = "";
        public string Job = "";
        public string JobName = "";
        public int Rarity;
        public int MinLevel;
        public int StackLimit;
        public bool Stackable;
        public bool StackableItem;
        public bool Avatar;
        public long Price;
        public string SubtypeAxis = "类型";
        public string IconPath = "";
        public int IconFrame;
        public string Explain = "";
        public string DetailExplain = "";
        public string FlavorText = "";

        public List<string> AvatarOptions = new List<string>();
        public List<int> AvatarPeriods = new List<int>();
        public List<string> Stats = new List<string>();
        public List<string> Effects = new List<string>();
        public List<string> Sets = new List<string>();
    }

    /// <summary>
    /// 物品目录。分类规则严格照搬原版 com.dylan.tools.webgm.Catalog#toEntry：
    /// 按 PVF 路径切分判断 kind / part，而不是靠脚本里的 [equipment type]。
    /// </summary>
    public sealed class Catalog
    {
        public const string KindWeapon = "weapon";
        public const string KindArmor = "armor";
        public const string KindJewel = "jewel";
        public const string KindTitle = "title";
        public const string KindAvatar = "avatar";
        public const string KindCreature = "creature";
        public const string KindStackable = "stackable";
        public const string KindOther = "other";

        private static readonly Dictionary<string, string> Jobs = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            { "swordman", "鬼剑士" }, { "fighter", "格斗家" }, { "gunner", "神枪手" },
            { "mage", "魔法师" }, { "priest", "圣职者" }, { "common", "通用" }, { "atgunner", "女枪手" }
        };

        private static readonly Dictionary<string, string> ArmorParts = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            { "jacket", "上衣" }, { "shoulder", "头肩" }, { "pants", "下装" }, { "shoes", "鞋" }, { "belt", "腰带" }
        };

        private static readonly Dictionary<string, string> JewelParts = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            { "amulet", "项链" }, { "wrist", "手镯" }, { "ring", "戒指" }
        };

        private static readonly Dictionary<string, string> AvatarParts = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            { "cap", "帽子" }, { "hat", "头发" }, { "hair", "头发" }, { "face", "脸部" },
            { "coat", "上衣" }, { "pants", "下装" }, { "shoes", "鞋" }, { "belt", "腰带" },
            { "skin", "皮肤" }, { "neck", "颈部" }
        };

        private static readonly Dictionary<string, string> WeaponTypes = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            { "sword", "短剑" }, { "dagger", "匕首" }, { "ssword", "短剑" }, { "hsword", "巨剑" },
            { "katana", "太刀" }, { "beamsword", "光剑" }, { "blunt", "钝器" }, { "club", "钝器" },
            { "spear", "长矛" }, { "axe", "战斧" }, { "scythe", "镰刀" }, { "tonfa", "东方棍" },
            { "rod", "东方棍" }, { "pole", "棍棒" }, { "staff", "法杖" }, { "wand", "魔杖" },
            { "broom", "扫把" }, { "cross", "十字架" }, { "rosary", "念珠" }, { "totem", "图腾" },
            { "knuckle", "指虎" }, { "boxglove", "拳套" }, { "claw", "爪" }, { "gauntlet", "臂铠" },
            { "revolver", "左轮枪" }, { "automatic", "自动手枪" }, { "bowgun", "手弩" },
            { "musket", "火枪" }, { "hcannon", "手炮" }, { "handcannon", "手炮" }
        };

        private static readonly Dictionary<string, string> Materials = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            { "cloth", "布甲" }, { "leather", "皮甲" }, { "light", "轻甲" }, { "heavy", "重甲" },
            { "larmor", "轻甲" }, { "harmor", "重甲" }, { "plate", "板甲" }
        };

        private static readonly Dictionary<string, string> StackableDirs = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            { "recipe", "设计图纸" }, { "emblem", "徽章" }, { "event", "活动" }, { "cash", "商城" },
            { "quest", "任务道具" }, { "professional", "副职业" }, { "material", "材料" },
            { "monsterCard", "怪物卡" }, { "fp", "疲劳点数" }, { "throw", "投掷" },
            { "temp", "临时" }, { "extention", "扩展" }, { "ect", "其他" }, { "(root)", "消耗品" }
        };

        private readonly GmEnv _env;
        private readonly List<CatalogEntry> _all = new List<CatalogEntry>();
        private readonly Dictionary<int, CatalogEntry> _byId = new Dictionary<int, CatalogEntry>();
        private readonly Dictionary<string, int> _kindCounts = new Dictionary<string, int>();
        private readonly object _lock = new object();

        public volatile bool Built;
        public string BuildError { get; private set; }
        public int Count { get { lock (_lock) return _all.Count; } }

        public Catalog(GmEnv env)
        {
            _env = env;
        }

        public CatalogEntry Item(int id)
        {
            lock (_lock)
            {
                CatalogEntry e;
                return _byId.TryGetValue(id, out e) ? e : null;
            }
        }

        public List<CatalogEntry> Items()
        {
            lock (_lock) return new List<CatalogEntry>(_all);
        }

        public Dictionary<string, int> KindCounts()
        {
            lock (_lock) return new Dictionary<string, int>(_kindCounts);
        }

        public void Rebuild(PvfArchive archive, GameScripts scripts)
        {
            ThreadPool.QueueUserWorkItem(_ =>
            {
                try { Build(archive, scripts); }
                catch (Exception ex)
                {
                    BuildError = ex.Message;
                    Built = false;
                    _env.Note("bad", "建物品目录失败：" + ex.Message);
                }
            });
        }

        private void Build(PvfArchive archive, GameScripts scripts)
        {
            List<CatalogEntry> list = new List<CatalogEntry>();

            foreach (int id in scripts.ItemIds)
            {
                ItemScript script;
                try { script = scripts.Get(id); }
                catch { script = null; }
                if (script == null) continue;

                // 原版：脚本读出来了就一定进目录（名字为空也进）
                CatalogEntry e = ToEntry(id, script, scripts);
                if (e != null) list.Add(e);
            }

            list.Sort((a, b) => a.Id.CompareTo(b.Id));

            lock (_lock)
            {
                _all.Clear();
                _byId.Clear();
                _kindCounts.Clear();
                foreach (CatalogEntry e in list)
                {
                    // 原版按 id 排序后逐条放进以 id 为键的 Map，重复 id 会被覆盖成一条。
                    // 本地 Script.pvf 里 equipment.lst 的 30211 被列了两次（第二行路径不存在），
                    // 原版的表现就是这个 id 在目录里查不到 —— 这里保持相同的去重口径。
                    if (_byId.ContainsKey(e.Id)) continue;

                    _all.Add(e);
                    _byId[e.Id] = e;
                    int n;
                    _kindCounts.TryGetValue(e.Kind, out n);
                    _kindCounts[e.Kind] = n + 1;
                }
            }

            Built = true;
            BuildError = null;
            _env.Note("info", "物品目录已建好：" + list.Count + " 件");
        }

        /// <summary>严格照搬原版 toEntry 的路径判定。</summary>
        private CatalogEntry ToEntry(int id, ItemScript s, GameScripts scripts)
        {
            CatalogEntry e = new CatalogEntry();
            e.Id = id;
            e.Path = s.Path ?? "";

            string[] seg = e.Path.Split('/');

            string kind = KindOther;
            string kindName = "其他";
            string part = "";
            string partName = "";
            string subtype = "";
            string subtypeName = "";
            string job = "";
            string jobName = "";

            if (seg.Length >= 2 && seg[0] == "equipment")
            {
                if (seg.Length >= 5 && seg[1] == "character")
                {
                    job = seg[2];
                    jobName = Lookup(Jobs, job);
                    string slot = seg[3];

                    if (slot == KindWeapon)
                    {
                        kind = KindWeapon; kindName = "武器";
                        part = KindWeapon; partName = "武器";
                        if (seg.Length >= 5)
                        {
                            subtype = seg[4];
                            subtypeName = Lookup(WeaponTypes, subtype);
                        }
                    }
                    else if (ArmorParts.ContainsKey(slot))
                    {
                        kind = KindArmor; kindName = "防具";
                        part = slot; partName = ArmorParts[slot];
                        if (seg.Length >= 6)
                        {
                            subtype = seg[4];
                            subtypeName = Lookup(Materials, subtype);
                        }
                    }
                    else if (JewelParts.ContainsKey(slot))
                    {
                        kind = KindJewel; kindName = "首饰";
                        part = slot; partName = JewelParts[slot];
                    }
                    else if (slot == KindTitle)
                    {
                        kind = KindTitle; kindName = "称号";
                        part = KindTitle; partName = "称号";
                    }
                    else if (slot == KindAvatar || slot == "at_avatar")
                    {
                        kind = KindAvatar;
                        kindName = slot == "at_avatar" ? "时装（女枪手）" : "时装";
                        part = seg.Length >= 5 ? seg[4] : "";
                        partName = Lookup(AvatarParts, part);
                        if (slot == "at_avatar") { job = "atgunner"; jobName = "女枪手"; }
                    }
                    else
                    {
                        kindName = "其他装备";
                    }
                }
                else if (seg.Length >= 3 && seg[1] == KindCreature)
                {
                    kind = KindCreature; kindName = "宠物";
                    part = seg[2]; partName = "宠物";
                }
                else
                {
                    kindName = "其他装备";
                }
            }
            else if (seg.Length >= 1 && seg[0] == KindStackable)
            {
                kind = KindStackable;
                string dir = seg.Length >= 3 ? seg[1] : "(root)";
                kindName = StackableDirs.ContainsKey(dir) ? StackableDirs[dir] : "其他";
                part = dir;
                partName = kindName;
            }
            else
            {
                // stackable/coin.stk 这种：seg[0] == "stackable" 走上面；
                // 其它散装路径算「其他装备」
                kindName = "其他装备";
            }

            e.Kind = kind;
            e.KindName = kindName;
            e.Part = part;
            e.PartName = partName;
            e.Subtype = subtype;
            e.SubtypeName = subtypeName;
            e.Job = job;
            e.JobName = jobName;
            e.SubtypeAxis = SubtypeAxisOf(subtype);

            e.Name = scripts.NameOf(id);
            e.Rarity = s.Rarity;
            e.MinLevel = s.MinLevel;
            e.StackLimit = s.StackLimit;
            e.Stackable = s.Stackable;
            e.StackableItem = s.StackableItem;
            e.Avatar = s.IsAvatarItem;
            e.Price = s.Price;
            e.IconPath = s.Icon ?? "";
            e.IconFrame = s.IconFrame;

            e.Explain = scripts.ExplainOf(id, "explain");
            e.DetailExplain = scripts.ExplainOf(id, "detail explain");
            e.FlavorText = scripts.ExplainOf(id, "flavor text");

            return e;
        }

        private static string Lookup(Dictionary<string, string> map, string key)
        {
            if (string.IsNullOrEmpty(key)) return "";
            string v;
            return map.TryGetValue(key, out v) ? v : key;
        }

        public static string SubtypeAxisOf(string subtype)
        {
            if (string.IsNullOrEmpty(subtype)) return "类型";
            if (Materials.ContainsKey(subtype)) return "材质";
            if (WeaponTypes.ContainsKey(subtype)) return "武器类型";
            return "类型";
        }
    }
}
