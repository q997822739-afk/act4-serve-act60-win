using System.IO;
using System.Text;
using System.Text.RegularExpressions;

namespace A4Launcher.Services;

/// <summary>
/// 极简 INI 读写：只负责读/改「指定节区里的某个键」，其余行（注释、空行、其它节）
/// 原样保留。用于安全地翻 <c>ai_companions.ini</c> / <c>fake_market.ini</c> /
/// <c>features.ini</c> 里的开关，不破坏玩家手写的注释。
/// </summary>
public static class IniFile
{
    private static readonly Regex SectionRe = new(@"^\s*\[(?<name>[^\]]+)\]\s*$", RegexOptions.Compiled);
    private static readonly Regex KeyRe     = new(@"^(?<lead>\s*)(?<key>[A-Za-z0-9_]+)(?<mid>\s*=\s*)(?<val>.*?)(?<tail>\s*)$", RegexOptions.Compiled);

    /// <summary>读取 [section] key 的值；文件/节区/键不存在返回 null。</summary>
    public static string? Get(string path, string section, string key)
    {
        if (!File.Exists(path)) return null;

        var (lines, _) = Read(path);
        var cur = "";
        foreach (var line in lines)
        {
            var sm = SectionRe.Match(line);
            if (sm.Success) { cur = sm.Groups["name"].Value.Trim(); continue; }
            if (!cur.Equals(section, StringComparison.OrdinalIgnoreCase)) continue;

            var km = KeyRe.Match(line);
            if (km.Success && km.Groups["key"].Value.Equals(key, StringComparison.OrdinalIgnoreCase))
                return km.Groups["val"].Value;
        }
        return null;
    }

    public static bool GetBool(string path, string section, string key, bool fallback = false)
    {
        var v = Get(path, section, key);
        if (v is null) return fallback;
        v = v.Trim();
        if (v == "1" || v.Equals("true", StringComparison.OrdinalIgnoreCase)) return true;
        if (v == "0" || v.Equals("false", StringComparison.OrdinalIgnoreCase)) return false;
        return fallback;
    }

    /// <summary>把 [section] key 改成 value（0/1 之外的值原样写）。改动成功返回 true。</summary>
    public static bool Set(string path, string section, string key, string value)
    {
        if (!File.Exists(path)) return false;

        var (lines, newline) = Read(path);
        var cur = "";
        var changed = false;

        for (var i = 0; i < lines.Count; i++)
        {
            var sm = SectionRe.Match(lines[i]);
            if (sm.Success) { cur = sm.Groups["name"].Value.Trim(); continue; }
            if (!cur.Equals(section, StringComparison.OrdinalIgnoreCase)) continue;

            var km = KeyRe.Match(lines[i]);
            if (!km.Success || !km.Groups["key"].Value.Equals(key, StringComparison.OrdinalIgnoreCase)) continue;

            var updated = km.Groups["lead"].Value + km.Groups["key"].Value +
                          km.Groups["mid"].Value + value + km.Groups["tail"].Value;
            if (updated != lines[i]) { lines[i] = updated; changed = true; }
            break;
        }

        if (changed) Write(path, lines, newline);
        return changed;
    }

    public static bool SetBool(string path, string section, string key, bool on)
        => Set(path, section, key, on ? "1" : "0");

    // ---- 编码/换行：读进来什么样，写回去什么样（保留 BOM 与 CRLF/LF） ----

    private static (List<string> Lines, string Newline) Read(string path)
    {
        var raw = File.ReadAllBytes(path);
        var hasBom = raw.Length >= 3 && raw[0] == 0xEF && raw[1] == 0xBB && raw[2] == 0xBF;
        var text = new UTF8Encoding(false).GetString(raw, hasBom ? 3 : 0, raw.Length - (hasBom ? 3 : 0));

        var newline = text.Contains("\r\n") ? "\r\n" : "\n";
        var lines = new List<string>(text.Split('\n'));
        for (var i = 0; i < lines.Count; i++) lines[i] = lines[i].TrimEnd('\r');

        // text.Split 会在末尾多出一个空串，去掉它，避免每写一次就长一行
        if (lines.Count > 0 && lines[lines.Count - 1].Length == 0) lines.RemoveAt(lines.Count - 1);
        return (lines, newline);
    }

    private static void Write(string path, List<string> lines, string newline)
    {
        var raw = File.ReadAllBytes(path);
        var hasBom = raw.Length >= 3 && raw[0] == 0xEF && raw[1] == 0xBB && raw[2] == 0xBF;

        var text = string.Join(newline, lines) + newline;
        var enc = new UTF8Encoding(hasBom);
        File.WriteAllBytes(path, enc.GetPreamble().Concat(enc.GetBytes(text)).ToArray());
    }
}
