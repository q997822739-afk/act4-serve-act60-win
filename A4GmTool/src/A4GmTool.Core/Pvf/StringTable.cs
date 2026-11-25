using System;
using System.Collections.Generic;
using System.Text;

namespace A4GmTool.Core.Pvf
{
    /// <summary>
    /// 一个物品脚本（.equ / .stk 等）的解析结果，只保留 GM 用得到的字段。
    /// </summary>
    public sealed class ItemScript
    {
        public int Id;
        /// <summary>PVF 里的真实路径（带 equipment/ 之类的前缀，即索引中的键）。</summary>
        public string Path;
        public string Name = "";
        public string Name2 = "";
        public string Icon = "";
        public int IconFrame;

        /// <summary>equipment / stackable 两大类。</summary>
        public string ScriptGroup = "";
        /// <summary>[equipment type] 里的类型名，如 coat / weapon / cap。</summary>
        public string EquipType = "";
        public int EquipTypeCode;
        public int SubType = -1;
        public string SubTypeName = "";

        public int Rarity;
        public int Grade;
        public int MinLevel = 1;
        public int MaxLevel;
        public int Price;
        public int Durability;
        public int StackLimit = 1;
        public string UsableJob = "";

        public bool Avatar;
        public bool Creature;
        /// <summary>stackLimit &gt; 0（可叠加）。</summary>
        public bool Stackable;
        /// <summary>脚本里写了 [stackable type]。</summary>
        public bool StackableItem;
        public string StackableType = "";
        public bool StackableTypeKnown;

        /// <summary>attach type：free / trade / seal / account / …</summary>
        public string AttachType = "";
        /// <summary>[attachable] 的可见类型编码（0=自由，1=封装…）。</summary>
        public int AttachTypeCode;

        public List<string> Stats = new List<string>();
        public List<string> Effects = new List<string>();

        public bool IsAvatarItem
        {
            get
            {
                return Avatar
                       || EquipType == "cap" || EquipType == "hat" || EquipType == "hair" || EquipType == "face"
                       || EquipType == "skin" || EquipType == "neck"
                       || (Path != null && Path.Contains("/avatar/"));
            }
        }
    }

    /// <summary>
    /// 从 PVF 里按需读物品脚本。带一个小缓存，避免目录构建时重复解压同一条。
    /// </summary>
    public sealed class ItemReader
    {
        private readonly PvfArchive _archive;
        private readonly Dictionary<string, ItemScript> _cache = new Dictionary<string, ItemScript>(StringComparer.Ordinal);
        private readonly Dictionary<string, string> _rawCache = new Dictionary<string, string>(StringComparer.Ordinal);

        public ItemReader(PvfArchive archive)
        {
            _archive = archive;
        }

        /// <summary>取脚本正文原文（解析说明文本用）。</summary>
        public string RawText(string entryPath)
        {
            if (string.IsNullOrEmpty(entryPath)) return null;
            string cached;
            if (_rawCache.TryGetValue(entryPath, out cached)) return cached;

            PvfEntry entry = _archive.FindEntry(entryPath);
            if (entry == null) return null;

            string text = PvfArchive.ChineseCharset.GetString(_archive.ReadEntry(entry));
            _rawCache[entryPath] = text;
            return text;
        }

        /// <summary>物品脚本正文按 GBK 读（服务端 Catalog 也是用 GBK 解 itemText）。</summary>
        public ItemScript LoadFromEntry(string entryPath)
        {
            if (string.IsNullOrEmpty(entryPath)) return null;
            ItemScript cached;
            if (_cache.TryGetValue(entryPath, out cached)) return cached;

            PvfEntry entry = _archive.FindEntry(entryPath);
            if (entry == null) return null;

            byte[] data = _archive.ReadEntry(entry);
            string text = PvfArchive.ChineseCharset.GetString(data);
            ItemScript script = ParseItemText(entry.Name, text);
            _cache[entryPath] = script;
            return script;
        }

        public static ItemScript ParseItemText(string entryPath, string text)
        {
            ScriptDocument doc = ScriptDocument.Parse(text);
            ItemScript s = new ItemScript();
            s.Path = entryPath ?? "";
            s.ScriptGroup = entryPath != null && entryPath.StartsWith("stackable/", StringComparison.OrdinalIgnoreCase) ? "stackable" : "equipment";

            s.Name = doc.Bracket("name");
            s.Name2 = doc.Simple("name2");
            if (s.Name2.Length > 0 && s.Name2.StartsWith("<", StringComparison.Ordinal)) s.Name2 = "";

            s.Icon = doc.IconPath();
            s.IconFrame = doc.IconFrame();

            s.EquipType = doc.Bracket("equipment type");
            s.EquipTypeCode = doc.Int("equipment type", -1);
            s.SubType = doc.Int("sub type", -1);
            s.SubTypeName = doc.Bracket("sub type");

            s.Rarity = doc.Int("rarity", 0);
            s.Grade = doc.Int("grade", 0);
            // 原版：minLevel 直接取 [minimum level]，没有就是 0（不是 1）
            s.MinLevel = doc.Int("minimum level", 0);
            s.MaxLevel = doc.Int("maximum level", 0);
            s.Price = doc.Int("price", 0);
            s.Durability = doc.Int("durability", 0);
            // 原版 isStackable() = stackLimit > 0；没有 [stack limit] 就是 0
            s.StackLimit = doc.Int("stack limit", 0);
            s.StackableType = doc.Bracket("stackable type");
            s.StackableTypeKnown = s.StackableType.Length > 0;

            s.UsableJob = doc.Bracket("usable job");
            s.AttachType = doc.Bracket("attach type");
            s.AttachTypeCode = doc.Int("attach type", 0);

            s.Avatar = entryPath != null && entryPath.IndexOf("/avatar/", StringComparison.OrdinalIgnoreCase) >= 0;
            s.Creature = entryPath != null && entryPath.StartsWith("equipment/creature/", StringComparison.OrdinalIgnoreCase);

            // 服务端 ItemScript 的口径：
            //   isStackable()     = stackLimit > 0
            //   isStackableItem() = stackableType != -1（脚本里写了 [stackable type]）
            s.Stackable = s.StackLimit > 0;
            s.StackableItem = s.StackableTypeKnown;

            s.Stats.AddRange(doc.All("equipment physical attack"));
            s.Stats.AddRange(doc.All("equipment magical attack"));
            s.Stats.AddRange(doc.All("equipment physical defense"));
            s.Stats.AddRange(doc.All("equipment magical defense"));

            foreach (string tag in doc.TagNames)
            {
                if (tag.StartsWith("equipment ", StringComparison.OrdinalIgnoreCase) && !s.Stats.Contains(tag)) { }
            }

            return s;
        }
    }

    /// <summary>
    /// 读取 <c>string.lst</c>（块号 → 字符串表路径）并缓存 chn / kor 两套表。
    /// </summary>
    public sealed class StringTableSet
    {
        private readonly Dictionary<int, StringTable> _chinese = new Dictionary<int, StringTable>();
        private readonly Dictionary<int, StringTable> _korean = new Dictionary<int, StringTable>();

        public int Count { get { return _chinese.Count; } }

        public static StringTableSet Load(PvfArchive archive)
        {
            StringTableSet set = new StringTableSet();
            if (archive == null) return set;

            PvfEntry listEntry = archive.FindEntry("string.lst");
            if (listEntry == null) return set;

            string listText = PvfArchive.ScriptCharset.GetString(archive.ReadEntry(listEntry));
            foreach (string line in listText.Split('\n'))
            {
                string t = line.Trim();
                if (t.Length == 0 || t.StartsWith("//", StringComparison.Ordinal)) continue;

                int tab = t.IndexOfAny(new[] { '\t', ' ' });
                if (tab <= 0) continue;

                int chunk;
                if (!int.TryParse(t.Substring(0, tab).Trim(), out chunk)) continue;
                string path = t.Substring(tab).Trim().Trim('`');
                if (path.Length == 0) continue;

                try
                {
                    if (archive.FindEntry(path) != null)
                        set._chinese[chunk] = StringTable.Load(archive, path);

                    string korPath = path.Replace(".chn.", ".kor.");
                    if (!string.Equals(korPath, path, StringComparison.Ordinal) && archive.FindEntry(korPath) != null)
                        set._korean[chunk] = StringTable.Load(archive, korPath);
                }
                catch
                {
                    // 单个块坏了不影响其它块
                }
            }
            return set;
        }

        /// <summary>把 &lt;3::name_10000&gt; 解析成中文。</summary>
        public string ResolveChinese(string raw)
        {
            int chunk;
            string key;
            if (!ScriptDocument.TryParseStringRef(raw, out chunk, out key)) return "";

            StringTable table;
            if (_chinese.TryGetValue(chunk, out table))
            {
                string v = table.Get(key);
                if (!string.IsNullOrEmpty(v)) return v;
            }
            if (_korean.TryGetValue(chunk, out table))
            {
                string v = table.Get(key);
                if (!string.IsNullOrEmpty(v)) return v;
            }
            return "";
        }

        /// <summary>优先中文，解析不出来就返回原始 key（不带尖括号）。</summary>
        public string ResolveOrKey(string raw)
        {
            string v = ResolveChinese(raw);
            if (!string.IsNullOrEmpty(v)) return v;

            int chunk;
            string key;
            if (ScriptDocument.TryParseStringRef(raw, out chunk, out key)) return key ?? "";
            return "";
        }
    }
}
