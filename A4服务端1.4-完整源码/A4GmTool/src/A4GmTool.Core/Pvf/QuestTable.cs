using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace A4GmTool.Core.Pvf
{
    /// <summary>
    /// 任务表：quest/quest.lst（任务号 → 任务脚本）。
    /// 从任务脚本里取 [name] 与 [material] 段，任务名走 string.lst 第 Quest 块。
    /// </summary>
    public sealed class QuestTable
    {
        public const string QuestList = "quest/quest.lst";

        private readonly PvfArchive _archive;
        private readonly StringTableSet _strings;
        private readonly Dictionary<int, string> _paths = new Dictionary<int, string>();
        private readonly Dictionary<int, string> _nameCache = new Dictionary<int, string>();
        private readonly Dictionary<int, List<QuestMaterial>> _matCache = new Dictionary<int, List<QuestMaterial>>();

        private static readonly Regex MaterialLine =
            new Regex(@"`([^`]+)`\s+(\d+)", RegexOptions.Compiled);

        private QuestTable(PvfArchive archive, StringTableSet strings)
        {
            _archive = archive;
            _strings = strings;
        }

        public sealed class QuestMaterial
        {
            public int ItemId;
            public int Need;
        }

        public int Count { get { return _paths.Count; } }

        /// <summary>直接用 quest.lst（原版也是这样，没有 .lst 就退回扫 quest/ 目录）。</summary>
        public static QuestTable Load(PvfArchive archive, StringTableSet strings, Action<string> warn)
        {
            QuestTable t = new QuestTable(archive, strings);
            if (archive == null) return t;
            t.LoadInternal(warn);
            return t;
        }

        public static QuestTable Load(PvfArchive archive, Func<int, IList<int>> listProvider, StringTableSet strings, Action<string> warn)
        {
            QuestTable t = new QuestTable(archive, strings);
            if (archive == null) return t;

            if (listProvider != null)
            {
                IList<int> ids = listProvider(-1);
                if (ids != null && ids.Count > 0)
                {
                    int n = 0;
                    foreach (int id in ids)
                    {
                        string path = "quest/" + id + ".qst";
                        if (archive.FindEntry(path) != null)
                        {
                            t._paths[id] = path;
                            n++;
                        }
                    }
                    if (n > 0) return t;
                }
            }

            t.LoadInternal(warn);
            return t;
        }

        private void LoadInternal(Action<string> warn)
        {
            try
            {
                PvfEntry e = _archive.FindEntry(QuestList);
                if (e == null)
                {
                    if (warn != null) warn("PVF 里没有 quest/quest.lst，任务名将显示为编号");
                    return;
                }

                string text = PvfArchive.ScriptCharset.GetString(_archive.ReadEntry(e));
                List<ListFile.Row> rows = ListFile.Parse(text, "quest/", _archive);
                foreach (ListFile.Row row in rows)
                {
                    string path = row.FullPath;
                    if (!path.Contains("/")) path = "quest/" + row.RawPath;
                    if (_archive.FindEntry(path) == null) continue;
                    if (!_paths.ContainsKey(row.Id)) _paths[row.Id] = path;
                }
            }
            catch (Exception ex)
            {
                if (warn != null) warn("读任务表失败：" + ex.Message);
            }
        }

        public bool Has(int questId)
        {
            return _paths.ContainsKey(questId);
        }

        public string NameOf(int questId)
        {
            string cached;
            if (_nameCache.TryGetValue(questId, out cached)) return cached;

            string name = "";
            try
            {
                ScriptDocument doc = LoadDoc(questId);
                if (doc != null)
                {
                    string raw = doc.Raw("name");
                    if (raw.Length > 0)
                        name = _strings == null ? "" : _strings.ResolveOrKey(raw);
                }
            }
            catch { name = ""; }

            if (name.Length == 0) name = "任务 " + questId;
            _nameCache[questId] = name;
            return name;
        }

        /// <summary>任务需要的材料（[material] 段里的 `物品路径` 数量）。</summary>
        public List<QuestMaterial> MaterialsOf(int questId)
        {
            List<QuestMaterial> cached;
            if (_matCache.TryGetValue(questId, out cached)) return cached;

            List<QuestMaterial> list = new List<QuestMaterial>();
            try
            {
                ScriptDocument doc = LoadDoc(questId);
                if (doc != null)
                {
                    // [material] 块：每行 `stackable/xxx.stk` 数量
                    foreach (string line in doc.BlockLines("material"))
                    {
                        Match m = MaterialLine.Match(line);
                        if (!m.Success) continue;

                        int need;
                        if (!int.TryParse(m.Groups[2].Value, out need)) continue;

                        int itemId = ResolveItemId(m.Groups[1].Value.Trim());
                        if (itemId > 0) list.Add(new QuestMaterial { ItemId = itemId, Need = need });
                    }
                }
            }
            catch { }

            _matCache[questId] = list;
            return list;
        }

        private readonly Dictionary<string, int> _pathToId = new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase);

        /// <summary>把物品脚本路径反查成物品编号（脚本里写的是路径不是编号）。</summary>
        public void UseItemIndex(IDictionary<int, string> itemPaths)
        {
            if (itemPaths == null) return;
            foreach (KeyValuePair<int, string> kv in itemPaths)
            {
                if (!_pathToId.ContainsKey(kv.Value)) _pathToId[kv.Value] = kv.Key;
            }
        }

        private int ResolveItemId(string path)
        {
            if (path.Length == 0) return 0;

            int id;
            if (_pathToId.TryGetValue(path, out id)) return id;

            foreach (string prefix in new[] { "stackable/", "equipment/", "quest/" })
            {
                if (_pathToId.TryGetValue(prefix + path, out id)) return id;
            }
            return 0;
        }

        private ScriptDocument LoadDoc(int questId)
        {
            string path;
            if (!_paths.TryGetValue(questId, out path)) return null;
            string text = PvfArchive.ChineseCharset.GetString(_archive.Read(path));
            return ScriptDocument.Parse(text);
        }
    }
}
