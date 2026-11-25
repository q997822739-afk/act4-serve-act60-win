using System;
using System.Collections.Generic;
using System.Text;

namespace A4GmTool.Core.Pvf
{
    /// <summary>
    /// 技能名与技能图标：技能表 = skill/skilllist.lst（职业 → 该职业的 .lst）
    /// → 每行 <c>序号 `路径`</c> → 技能脚本里 <c>[name] &lt;12::name_xxx&gt;</c>
    /// → 用 string.lst 第 12 块（Skill/Skill.chn.str）查中文。
    /// </summary>
    public sealed class SkillTable
    {
        public const int StringChunk = 12;

        // skilllist.lst 的职业顺序
        public static readonly string[] JobNames = { "swordman", "fighter", "gunner", "mage", "priest", "at gunner" };

        private readonly Dictionary<string, Dictionary<int, string>> _paths =
            new Dictionary<string, Dictionary<int, string>>(StringComparer.OrdinalIgnoreCase);

        private readonly PvfArchive _archive;
        private readonly StringTableSet _strings;
        private readonly Dictionary<long, string> _nameCache = new Dictionary<long, string>();
        private readonly Dictionary<long, string[]> _iconCache = new Dictionary<long, string[]>();

        private SkillTable(PvfArchive archive, StringTableSet strings)
        {
            _archive = archive;
            _strings = strings;
        }

        public static SkillTable Load(PvfArchive archive, StringTableSet strings, Action<string> warn)
        {
            SkillTable t = new SkillTable(archive, strings);
            if (archive == null) return t;

            try
            {
                PvfEntry listOfLists = archive.FindEntry("skill/skilllist.lst");
                if (listOfLists == null)
                {
                    if (warn != null) warn("PVF 里没有 skill/skilllist.lst，技能名将显示为空");
                    return t;
                }

                string text = PvfArchive.ScriptCharset.GetString(archive.ReadEntry(listOfLists));
                List<ListFile.Row> jobs = ListFile.Parse(text, "skill/", archive);

                foreach (ListFile.Row job in jobs)
                {
                    string jobKey = job.Id >= 0 && job.Id < JobNames.Length ? JobNames[job.Id] : ("job" + job.Id);
                    string full = job.FullPath.Contains("/") ? job.FullPath : "skill/" + job.FullPath;

                    PvfEntry e = archive.FindEntry(full) ?? archive.FindEntry("skill/" + job.RawPath);
                    if (e == null) continue;

                    string listText = PvfArchive.ScriptCharset.GetString(archive.ReadEntry(e));
                    List<ListFile.Row> skills = ListFile.Parse(listText, "skill/", archive);

                    Dictionary<int, string> map = new Dictionary<int, string>();
                    foreach (ListFile.Row s in skills)
                    {
                        string path = s.FullPath.Contains("/") ? s.FullPath : ("skill/" + s.FullPath);
                        if (archive.FindEntry(path) == null) path = "skill/" + s.RawPath;
                        map[s.Id] = path;
                    }
                    t._paths[jobKey] = map;
                }
            }
            catch (Exception ex)
            {
                if (warn != null) warn("读技能表失败：" + ex.Message);
            }

            return t;
        }

        public static int JobIndex(string jobTag)
        {
            string t = (jobTag ?? "").Trim().Trim('`').Trim('[', ']').ToLowerInvariant();
            switch (t)
            {
                case "swordman": return 0;
                case "fighter": return 1;
                case "gunner": return 2;
                case "mage": return 3;
                case "priest": return 4;
                case "at gunner": return 5;
                case "atgunner": return 5;
                default: return 0;
            }
        }

        /// <summary>按职业标签 + 技能序号的技能名。</summary>
        public string Name(string jobTag, int skillIndex)
        {
            return ByJob(JobIndex(jobTag), skillIndex);
        }

        /// <summary>按职业下标 + 技能序号的技能名；查不到退到剑士表（原版行为）。</summary>
        public string ByJob(int jobIndex, int skillIndex)
        {
            string s = Lookup(jobIndex, skillIndex);
            if (s.Length == 0 && jobIndex != 0) s = Lookup(0, skillIndex);
            return s;
        }

        public string[] IconByJob(int jobIndex, int skillIndex)
        {
            string[] r = IconLookup(jobIndex, skillIndex);
            if (r == null && jobIndex != 0) r = IconLookup(0, skillIndex);
            return r;
        }

        private string[] IconLookup(int jobIndex, int skillIndex)
        {
            long key = (long)jobIndex * 100000L + skillIndex + 900000000L;
            string[] cached;
            if (_iconCache.TryGetValue(key, out cached)) return cached;

            string[] result = null;
            try
            {
                string path = Resolve(jobIndex, skillIndex);
                if (path != null)
                {
                    string text = PvfArchive.ChineseCharset.GetString(_archive.Read(path));
                    ScriptDocument doc = ScriptDocument.Parse(text);
                    string icon = doc.IconPath();
                    if (icon.Length > 0) result = new[] { icon, doc.IconFrame().ToString() };
                }
            }
            catch { result = null; }

            _iconCache[key] = result;
            return result;
        }

        private string Lookup(int jobIndex, int skillIndex)
        {
            long key = (long)jobIndex * 100000L + skillIndex;
            string cached;
            if (_nameCache.TryGetValue(key, out cached)) return cached;

            string name = "";
            try
            {
                string path = Resolve(jobIndex, skillIndex);
                if (path != null)
                {
                    string text = PvfArchive.ChineseCharset.GetString(_archive.Read(path));
                    ScriptDocument doc = ScriptDocument.Parse(text);
                    string raw = doc.Raw("name");
                    if (raw.Length > 0) name = _strings == null ? "" : _strings.ResolveOrKey(raw);
                }
            }
            catch { name = ""; }

            _nameCache[key] = name;
            return name;
        }

        private string Resolve(int jobIndex, int skillIndex)
        {
            string jobKey = jobIndex >= 0 && jobIndex < JobNames.Length ? JobNames[jobIndex] : "swordman";
            Dictionary<int, string> map;
            if (!_paths.TryGetValue(jobKey, out map)) return null;

            string path;
            return map.TryGetValue(skillIndex, out path) ? path : null;
        }
    }
}
