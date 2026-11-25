using System;
using System.Collections.Generic;
using System.Text;

namespace A4GmTool.Core.Pvf
{
    /// <summary>
    /// .lst 列表文件解析：每行 <c>编号  `路径`</c>，路径相对该 .lst 所在大类目录。
    /// 例：
    /// <code>
    /// 10000	`character/common/jacket/cloth/sht_ragcloth.equ`
    /// </code>
    /// 实际索引键要补上前缀：equipment/character/common/jacket/...（实测必须补）。
    /// </summary>
    public static class ListFile
    {
        public sealed class Row
        {
            public int Id;
            public string RawPath;
            public string FullPath;
        }

        /// <summary>解析一个 .lst。prefix 是补在路径前面的目录（如 "equipment/"）。</summary>
        public static List<Row> Parse(string text, string prefix, PvfArchive archive)
        {
            List<Row> rows = new List<Row>();
            if (string.IsNullOrEmpty(text)) return rows;

            foreach (string line in text.Split('\n'))
            {
                string t = line.Trim();
                if (t.Length == 0 || t.StartsWith("//", StringComparison.Ordinal)) continue;

                int id;
                string path;
                if (!TrySplitIdPath(t, out id, out path)) continue;

                string full = path;
                Row row = new Row { Id = id, RawPath = path };

                if (archive != null && archive.FindEntry(full) == null && prefix != null && prefix.Length > 0)
                {
                    // .lst 里的路径可能已经带了子目录前缀（如 character/...），
                    // 也可能直接是 bare 名（如 coin.stk）。只认「真实存在的那个」，
                    // 不存在就保持原样交给上层丢弃 —— 不能硬套前缀把缺失的救回来。
                    string withPrefix = prefix + path;
                    if (archive.FindEntry(withPrefix) != null) full = withPrefix;
                }

                row.FullPath = full;
                rows.Add(row);
            }
            return rows;
        }

        private static bool TrySplitIdPath(string line, out int id, out string path)
        {
            id = 0;
            path = null;

            int i = 0;
            while (i < line.Length && (char.IsDigit(line[i]) || line[i] == '-')) i++;
            if (i == 0) return false;
            if (!int.TryParse(line.Substring(0, i), out id)) return false;

            string rest = line.Substring(i).Trim();
            if (rest.Length == 0) return false;

            int bt = rest.IndexOf('`');
            if (bt >= 0)
            {
                int bt2 = rest.IndexOf('`', bt + 1);
                path = bt2 > bt ? rest.Substring(bt + 1, bt2 - bt - 1) : rest.Substring(bt + 1);
            }
            else
            {
                int sp = rest.IndexOfAny(new[] { ' ', '\t' });
                path = sp < 0 ? rest : rest.Substring(0, sp);
            }

            path = path.Trim().Trim('`');
            return path.Length > 0;
        }
    }

    /// <summary>
    /// 把 Script.pvf 里的装备 / 消耗品 / 时装脚本读成 GM 用的物品目录。
    /// </summary>
    public sealed class GameScripts : IDisposable
    {
        public const string EquipmentList = "equipment/equipment.lst";
        public const string StackableList = "stackable/stackable.lst";

        private readonly PvfArchive _archive;
        private readonly ItemReader _reader;

        public int ItemCount { get { return _items.Count; } }
        public Dictionary<int, ItemScript> Items { get { return _items; } }
        public Dictionary<int, string> ItemPaths { get { return _paths; } }

        private readonly Dictionary<int, ItemScript> _items = new Dictionary<int, ItemScript>();
        private readonly Dictionary<int, string> _paths = new Dictionary<int, string>();
        private StringTableSet _strings;

        public StringTableSet StringTables { get { return _strings; } }

        private GameScripts(PvfArchive archive)
        {
            _archive = archive;
            _reader = new ItemReader(archive);
        }

        /// <summary>
        /// 只建索引（编号 → 路径），不解析每一条脚本 —— 7 万条里装备/消耗品约两万，
        /// 全解一遍太慢；具体某件物品在用到时再按需解析并缓存。
        /// </summary>
        public static GameScripts Load(PvfArchive archive, Action<string> warn)
        {
            GameScripts gs = new GameScripts(archive);

            gs.LoadList(EquipmentList, "equipment/", warn);
            gs.LoadList(StackableList, "stackable/", warn);

            try { gs._strings = StringTableSet.Load(archive); }
            catch (Exception ex) { if (warn != null) warn("读 string.lst 失败：" + ex.Message); }

            return gs;
        }

        private void LoadList(string listPath, string prefix, Action<string> warn)
        {
            try
            {
                PvfEntry e = _archive.FindEntry(listPath);
                if (e == null)
                {
                    if (warn != null) warn("PVF 里没有 " + listPath);
                    return;
                }

                string text = PvfArchive.ScriptCharset.GetString(_archive.ReadEntry(e));
                List<ListFile.Row> rows = ListFile.Parse(text, prefix, _archive);
                foreach (ListFile.Row row in rows)
                {
                    // 同一个编号在 .lst 里出现多次时「后写的覆盖前面的」——
                    // 原版 ScriptList 用的是 TreeMap.put，最后一条才算数。
                    // 实测 equipment.lst 的 30211 被列了两次，第二条指向不存在的文件，
                    // 于是原版那边这个编号就整个消失了。
                    _paths[row.Id] = row.FullPath;
                }

                // 指向包里不存在的文件就丢掉（ScriptList.load 里同样会 dangling 掉）
                List<int> drop = new List<int>();
                foreach (KeyValuePair<int, string> kv in _paths)
                {
                    if (_archive.FindEntry(kv.Value) == null) drop.Add(kv.Key);
                }
                foreach (int id in drop) _paths.Remove(id);
                if (drop.Count > 0 && warn != null) warn(listPath + "：" + drop.Count + " 个编号指向包里没有的文件，已忽略");
            }
            catch (Exception ex)
            {
                if (warn != null) warn("读 " + listPath + " 失败：" + ex.Message);
            }
        }

        /// <summary>按编号取物品脚本（带缓存）。</summary>
        public ItemScript Get(int itemId)
        {
            ItemScript cached;
            if (_items.TryGetValue(itemId, out cached)) return cached;

            string path;
            if (!_paths.TryGetValue(itemId, out path)) return null;

            ItemScript script;
            try { script = _reader.LoadFromEntry(path); }
            catch (Exception) { script = null; }

            if (script != null) script.Id = itemId;
            _items[itemId] = script;
            return script;
        }

        /// <summary>物品中文名（解析 &lt;3::name_10000&gt;）。</summary>
        public string NameOf(int itemId)
        {
            ItemScript s = Get(itemId);
            if (s == null) return "";
            if (_strings != null)
            {
                string resolved = _strings.ResolveOrKey(s.Name);
                if (resolved.Length > 0) return resolved;
            }
            return s.Name ?? "";
        }

        /// <summary>不解析名字，只取路径（用来判断是不是 PVF 里有的物品）。</summary>
        public bool Has(int itemId)
        {
            return _paths.ContainsKey(itemId);
        }

        /// <summary>
        /// 物品说明文本。先看脚本里有没有该标签，没有再按原版规则去字符串表里找
        /// explain_N / basic_explain_N / detail_explain_N / flavor_text_N。
        /// </summary>
        public string ExplainOf(int itemId, string tag)
        {
            ItemScript script = Get(itemId);
            if (script == null) return "";

            string text;
            try { text = _reader.RawText(script.Path); }
            catch { return ""; }
            if (string.IsNullOrEmpty(text)) return "";

            // 1. 脚本正文里直接写了 [explain] <12::xxx> 或 `字面量`
            string direct = ExtractTagText(text, tag);
            if (direct != null) return direct;

            // 2. 字符串表兜底
            if (_strings == null) return "";

            string kind = script.Path != null && script.Path.StartsWith("stackable/", StringComparison.OrdinalIgnoreCase)
                ? "stackable" : "equipment";

            string[] candidates;
            if (tag == "detail explain") candidates = new[] { "detail_explain_" };
            else if (tag == "flavor text") candidates = new[] { "flavor_text_" };
            else if (kind == "stackable") candidates = new[] { "explain_", "basic_explain_" };
            else candidates = new[] { "basic_explain_", "explain_" };

            foreach (string prefix in candidates)
            {
                string v = _strings.ResolveOrKey("<0::" + prefix + itemId + ">");
                if (!string.IsNullOrEmpty(v) && v != prefix + itemId) return v;
            }
            return "";
        }

        private static string ExtractTagText(string text, string tag)
        {
            System.Text.RegularExpressions.Regex re = new System.Text.RegularExpressions.Regex(
                @"(?im)^\s*\[" + System.Text.RegularExpressions.Regex.Escape(tag) + @"\]\s*(?:<\d+::([A-Za-z0-9_]+)>|`([^`\r\n]+)`)");

            System.Text.RegularExpressions.Match m = re.Match(text);
            if (!m.Success) return null;

            // 反引号里的字面量直接返回；<n::key> 交给字符串表
            if (m.Groups[2].Success) return m.Groups[2].Value.Trim();
            return null;
        }

        public string PathOf(int itemId)
        {
            string p;
            return _paths.TryGetValue(itemId, out p) ? p : null;
        }

        public IEnumerable<int> ItemIds { get { return _paths.Keys; } }

        public void Dispose()
        {
        }
    }
}
