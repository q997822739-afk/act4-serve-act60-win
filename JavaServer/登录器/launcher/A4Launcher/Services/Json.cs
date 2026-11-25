using System.Globalization;
using System.Text;

namespace A4Launcher.Services;

/// <summary>
/// 极简 JSON 读写。
/// 为什么不用 System.Text.Json：登录器要跑在 Win7~Win11，只能目标 .NET Framework 4.7.2，
/// 带上 Json 的 NuGet 包就没法保持「单个 exe」了。网关回包的结构就那么几种，自己解足够。
/// </summary>
public sealed class JsonNode
{
    public enum Kind
    {
        Null,
        Bool,
        Number,
        String,
        Array,
        Object,
    }

    private readonly Kind _kind;
    private readonly string _string = "";
    private readonly double _number;
    private readonly bool _bool;
    private readonly List<JsonNode> _items = new();
    private readonly Dictionary<string, JsonNode> _members = new(StringComparer.Ordinal);

    private JsonNode(Kind kind, string text, double number, bool flag)
    {
        _kind = kind;
        _string = text;
        _number = number;
        _bool = flag;
    }

    public Kind NodeKind => _kind;

    public IReadOnlyList<JsonNode> Items => _items;

    public static JsonNode Parse(string text)
    {
        var i = 0;
        var node = ParseValue(text, ref i) ?? throw new FormatException("JSON 为空");
        SkipSpace(text, ref i);
        if (i != text.Length)
        {
            throw new FormatException("JSON 末尾有多余字符");
        }
        return node;
    }

    public bool TryGet(string key, out JsonNode value)
    {
        if (_kind == Kind.Object && _members.TryGetValue(key, out var found))
        {
            value = found;
            return true;
        }
        value = new JsonNode(Kind.Null, "", 0, false);
        return false;
    }

    public string AsString() => _kind switch
    {
        Kind.String => _string,
        Kind.Number => _number.ToString(CultureInfo.InvariantCulture),
        Kind.Bool => _bool ? "true" : "false",
        _ => "",
    };

    public int AsInt() => _kind switch
    {
        Kind.Number => (int)_number,
        Kind.String => int.TryParse(_string, out var n) ? n : 0,
        _ => 0,
    };

    public static string Quote(string text)
    {
        var sb = new StringBuilder(text.Length + 2);
        sb.Append('"');
        foreach (var c in text)
        {
            switch (c)
            {
                case '"': sb.Append("\\\""); break;
                case '\\': sb.Append("\\\\"); break;
                case '\n': sb.Append("\\n"); break;
                case '\r': sb.Append("\\r"); break;
                case '\t': sb.Append("\\t"); break;
                default:
                    if (c < ' ')
                    {
                        sb.Append("\\u").Append(((int)c).ToString("x4", CultureInfo.InvariantCulture));
                    }
                    else
                    {
                        sb.Append(c);
                    }
                    break;
            }
        }
        sb.Append('"');
        return sb.ToString();
    }

    // ---------- 解析 ----------

    private static JsonNode? ParseValue(string s, ref int i)
    {
        SkipSpace(s, ref i);
        if (i >= s.Length)
        {
            return null;
        }
        var c = s[i];
        switch (c)
        {
            case '{': return ParseObject(s, ref i);
            case '[': return ParseArray(s, ref i);
            case '"': return StringNode(ParseString(s, ref i));
            case 't': Expect(s, ref i, "true"); return BoolNode(true);
            case 'f': Expect(s, ref i, "false"); return BoolNode(false);
            case 'n': Expect(s, ref i, "null"); return new JsonNode(Kind.Null, "", 0, false);
            default: return ParseNumber(s, ref i);
        }
    }

    private static JsonNode ParseObject(string s, ref int i)
    {
        var node = new JsonNode(Kind.Object, "", 0, false);
        i++; // {
        SkipSpace(s, ref i);
        if (i < s.Length && s[i] == '}')
        {
            i++;
            return node;
        }
        while (i < s.Length)
        {
            SkipSpace(s, ref i);
            var key = ParseString(s, ref i);
            SkipSpace(s, ref i);
            if (i >= s.Length || s[i] != ':')
            {
                throw new FormatException("JSON 对象缺少冒号");
            }
            i++;
            var value = ParseValue(s, ref i) ?? new JsonNode(Kind.Null, "", 0, false);
            node._members[key] = value;
            SkipSpace(s, ref i);
            if (i < s.Length && s[i] == ',')
            {
                i++;
                continue;
            }
            if (i < s.Length && s[i] == '}')
            {
                i++;
                return node;
            }
            throw new FormatException("JSON 对象没有正常结束");
        }
        throw new FormatException("JSON 对象被截断");
    }

    private static JsonNode ParseArray(string s, ref int i)
    {
        var node = new JsonNode(Kind.Array, "", 0, false);
        i++; // [
        SkipSpace(s, ref i);
        if (i < s.Length && s[i] == ']')
        {
            i++;
            return node;
        }
        while (i < s.Length)
        {
            var value = ParseValue(s, ref i) ?? new JsonNode(Kind.Null, "", 0, false);
            node._items.Add(value);
            SkipSpace(s, ref i);
            if (i < s.Length && s[i] == ',')
            {
                i++;
                continue;
            }
            if (i < s.Length && s[i] == ']')
            {
                i++;
                return node;
            }
            throw new FormatException("JSON 数组没有正常结束");
        }
        throw new FormatException("JSON 数组被截断");
    }

    private static string ParseString(string s, ref int i)
    {
        if (i >= s.Length || s[i] != '"')
        {
            throw new FormatException("JSON 期待字符串");
        }
        i++;
        var sb = new StringBuilder();
        while (i < s.Length)
        {
            var c = s[i++];
            if (c == '"')
            {
                return sb.ToString();
            }
            if (c != '\\')
            {
                sb.Append(c);
                continue;
            }
            if (i >= s.Length)
            {
                break;
            }
            var e = s[i++];
            switch (e)
            {
                case '"': sb.Append('"'); break;
                case '\\': sb.Append('\\'); break;
                case '/': sb.Append('/'); break;
                case 'b': sb.Append('\b'); break;
                case 'f': sb.Append('\f'); break;
                case 'n': sb.Append('\n'); break;
                case 'r': sb.Append('\r'); break;
                case 't': sb.Append('\t'); break;
                case 'u':
                    if (i + 4 > s.Length)
                    {
                        throw new FormatException("JSON \\u 转义不完整");
                    }
                    sb.Append((char)Convert.ToInt32(s.Substring(i, 4), 16));
                    i += 4;
                    break;
                default:
                    throw new FormatException("JSON 无法识别的转义 \\" + e);
            }
        }
        throw new FormatException("JSON 字符串被截断");
    }

    private static JsonNode ParseNumber(string s, ref int i)
    {
        var start = i;
        while (i < s.Length && "+-0123456789.eE".IndexOf(s[i]) >= 0)
        {
            i++;
        }
        if (i == start)
        {
            throw new FormatException("JSON 无法识别的值");
        }
        return NumberNode(double.Parse(s.Substring(start, i - start), CultureInfo.InvariantCulture));
    }

    private static JsonNode NumberNode(double v) => new(Kind.Number, "", v, false);

    private static JsonNode StringNode(string v) => new(Kind.String, v, 0, false);

    private static JsonNode BoolNode(bool v) => new(Kind.Bool, "", 0, v);

    private static void Expect(string s, ref int i, string literal)
    {
        if (i + literal.Length > s.Length || string.CompareOrdinal(s, i, literal, 0, literal.Length) != 0)
        {
            throw new FormatException("JSON 期待 " + literal);
        }
        i += literal.Length;
    }

    private static void SkipSpace(string s, ref int i)
    {
        while (i < s.Length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\r' || s[i] == '\n'))
        {
            i++;
        }
    }
}
