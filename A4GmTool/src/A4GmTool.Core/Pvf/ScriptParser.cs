using System;
using System.Collections.Generic;
using System.Text;
using System.Text.RegularExpressions;

namespace A4GmTool.Core.Pvf
{
    /// <summary>
    /// PVF 脚本的轻量标签解析。脚本长这样：
    /// <code>
    /// [name] &lt;3::name_10000&gt;
    /// [grade] 1
    /// [icon] `Item/IconArmor.img` 16
    /// [equipment type] `[coat]` 14
    /// [usable job]
    /// `[all]`
    /// [/usable job]
    /// </code>
    /// 这里只做「标签 → 值」的收集，够 GM 取名字/图标/分类用了。
    /// </summary>
    public sealed class ScriptDocument
    {
        private readonly Dictionary<string, List<string>> _tags =
            new Dictionary<string, List<string>>(StringComparer.OrdinalIgnoreCase);

        /// <summary>正文原文，按标签分块后保存（[name] 这种多行块用得到）。</summary>
        private readonly Dictionary<string, List<string>> _blockLines =
            new Dictionary<string, List<string>>(StringComparer.OrdinalIgnoreCase);

        /// <summary>取某个块标签下的所有正文行（不含标签自身）。</summary>
        public List<string> BlockLines(string tag)
        {
            List<string> list;
            if (_blockLines.TryGetValue(tag, out list)) return list;
            return new List<string>();
        }

        public static readonly Regex StringRef =
            new Regex(@"<\s*(\d+)\s*::\s*([^>]+?)\s*>", RegexOptions.Compiled);

        private static readonly Regex TagLine =
            new Regex(@"^\s*\[([^\]]+)\]\s*(.*)$", RegexOptions.Compiled);

        private static readonly Regex Backtick = new Regex(@"`([^`]*)`", RegexOptions.Compiled);

        public IEnumerable<string> TagNames { get { return _tags.Keys; } }

        public static ScriptDocument Parse(string text)
        {
            ScriptDocument doc = new ScriptDocument();
            if (string.IsNullOrEmpty(text)) return doc;

            string[] lines = text.Replace("\r\n", "\n").Replace('\r', '\n').Split('\n');
            string currentSection = null;

            foreach (string rawLine in lines)
            {
                Match m = TagLine.Match(rawLine);
                if (!m.Success)
                {
                    // 块里的内容行（[material] / [usable job] 这类段落）
                    if (currentSection != null)
                    {
                        string body = rawLine.Trim();
                        if (body.Length > 0) doc.AddBlockLine(currentSection, body);
                    }
                    continue;
                }

                string tag = m.Groups[1].Value.Trim();
                string value = m.Groups[2].Value.Trim();

                if (tag.StartsWith("/", StringComparison.Ordinal))
                {
                    // [/usable job] 之类：结束当前列表块
                    currentSection = null;
                    continue;
                }

                if (value.Length == 0)
                {
                    // [usable job] 这种块开始，后面若干行才是内容
                    currentSection = tag;
                    if (!doc._tags.ContainsKey(tag)) doc._tags[tag] = new List<string>();
                    if (!doc._blockLines.ContainsKey(tag)) doc._blockLines[tag] = new List<string>();
                    continue;
                }

                doc.Add(tag, value);
                currentSection = null;
            }

            return doc;
        }

        private void AddBlockLine(string tag, string line)
        {
            List<string> list;
            if (!_blockLines.TryGetValue(tag, out list))
            {
                list = new List<string>();
                _blockLines[tag] = list;
            }
            list.Add(line);
        }

        private void Add(string tag, string value)
        {
            List<string> list;
            if (!_tags.TryGetValue(tag, out list))
            {
                list = new List<string>();
                _tags[tag] = list;
            }
            list.Add(value);
        }

        public bool Has(string tag)
        {
            return _tags.ContainsKey(tag);
        }

        /// <summary>取标签的第一个原始值（可能还带反引号/尖括号）。</summary>
        public string Raw(string tag)
        {
            List<string> list;
            if (!_tags.TryGetValue(tag, out list) || list.Count == 0) return "";
            return list[0];
        }

        public List<string> All(string tag)
        {
            List<string> list;
            if (!_tags.TryGetValue(tag, out list)) return new List<string>();
            return list;
        }

        public int Int(string tag, int fallback)
        {
            string raw = FirstNumberToken(Raw(tag));
            int v;
            if (int.TryParse(raw, out v)) return v;
            return fallback;
        }

        public long Long(string tag, long fallback)
        {
            string raw = FirstNumberToken(Raw(tag));
            long v;
            if (long.TryParse(raw, out v)) return v;
            return fallback;
        }

        /// <summary>去掉反引号，只取主值部分（第一个反引号或第一个空白之前）。</summary>
        public string Simple(string tag)
        {
            string raw = Raw(tag);
            return FirstToken(raw);
        }

        /// <summary>取反引号里的内容，比如 `[coat]` 15 → coat；取不到就退回首 token。</summary>
        public string Bracket(string tag)
        {
            string raw = Raw(tag);
            Match m = Backtick.Match(raw);
            if (m.Success)
            {
                string inner = m.Groups[1].Value.Trim();
                if (inner.StartsWith("[", StringComparison.Ordinal) && inner.EndsWith("]", StringComparison.Ordinal))
                    return inner.Substring(1, inner.Length - 2).Trim();
                return inner;
            }
            return FirstToken(raw);
        }

        /// <summary>取第一条反引号路径 + 后面的帧号，用于 [icon]。</summary>
        public string IconPath()
        {
            return Bracket("icon");
        }

        public int IconFrame()
        {
            string raw = Raw("icon");
            Match m = Backtick.Match(raw);
            if (!m.Success) return 0;
            string rest = raw.Substring(m.Index + m.Length).Trim();
            int v;
            if (int.TryParse(FirstNumberToken(rest), out v)) return v;
            return 0;
        }

        private static string FirstToken(string raw)
        {
            if (string.IsNullOrEmpty(raw)) return "";
            Match m = Backtick.Match(raw);
            if (m.Success) return m.Groups[1].Value.Trim();

            string s = raw.TrimStart('`').TrimEnd('`').Trim();
            int sp = s.IndexOfAny(new[] { ' ', '\t' });
            return sp < 0 ? s : s.Substring(0, sp);
        }

        private static string FirstNumberToken(string raw)
        {
            if (string.IsNullOrEmpty(raw)) return "";
            raw = raw.Trim();
            int i = 0;
            while (i < raw.Length && !(char.IsDigit(raw[i]) || (raw[i] == '-' && i + 1 < raw.Length && char.IsDigit(raw[i + 1])))) i++;
            if (i >= raw.Length) return "";
            int start = i;
            if (raw[i] == '-') i++;
            while (i < raw.Length && char.IsDigit(raw[i])) i++;
            return raw.Substring(start, i - start);
        }

        /// <summary>把 &lt;3::name_10000&gt; 这种引用拆成 (块号, key)。</summary>
        public static bool TryParseStringRef(string raw, out int chunk, out string key)
        {
            chunk = 0;
            key = null;
            if (string.IsNullOrEmpty(raw)) return false;
            Match m = StringRef.Match(raw);
            if (!m.Success) return false;
            int.TryParse(m.Groups[1].Value, out chunk);
            key = m.Groups[2].Value.Trim();
            return true;
        }
    }

    /// <summary>
    /// 一个分块字符串表（*.chn.str / *.kor.str）。格式：每行 <c>key&gt;值</c>。
    /// </summary>
    public sealed class StringTable
    {
        private readonly Dictionary<string, string> _values = new Dictionary<string, string>(StringComparer.Ordinal);

        public string Path { get; private set; }
        public int Size { get { return _values.Count; } }

        public static StringTable Empty()
        {
            return new StringTable { Path = "" };
        }

        public static Encoding CharsetFor(string path)
        {
            string lower = (path ?? "").ToLowerInvariant();
            if (lower.Contains(".kor.")) return PvfArchive.ScriptCharset;
            if (lower.Contains(".jpn."))
            {
                try { return Encoding.GetEncoding(932); } catch { return PvfArchive.ScriptCharset; }
            }
            return PvfArchive.ChineseCharset;
        }

        public static StringTable Load(PvfArchive archive, string path)
        {
            byte[] data = archive.Read(path);
            return Parse(path, CharsetFor(path).GetString(data));
        }

        public static StringTable Parse(string path, string text)
        {
            StringTable t = new StringTable { Path = path };
            foreach (string line in text.Split('\n'))
            {
                string trimmed = line.Trim();
                if (trimmed.Length == 0 || trimmed.StartsWith("//", StringComparison.Ordinal)) continue;

                int at = trimmed.IndexOf('>');
                if (at > 0)
                {
                    string key = trimmed.Substring(0, at).Trim();
                    string val = trimmed.Substring(at + 1).Trim();
                    t._values[key] = val;
                }
            }
            return t;
        }

        public string Get(string key)
        {
            if (key == null) return null;
            string v;
            return _values.TryGetValue(key, out v) ? v : null;
        }

        /// <summary>把 &lt;3::name_10000&gt; 解析成真实文本；解析不出来返回空串。</summary>
        public string Resolve(string raw)
        {
            int chunk;
            string key;
            if (!ScriptDocument.TryParseStringRef(raw, out chunk, out key)) return "";
            string v = Get(key);
            return v ?? "";
        }
    }
}
