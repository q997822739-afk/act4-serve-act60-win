using System;
using System.Collections.Generic;
using System.Text;

namespace A4ServerConsole.Services.Gateway
{
    /// <summary>
    /// 完整移植自原 Linux C++ gateway/src/proto.cpp。
    /// 线协议：&lt;hz&gt;&lt;c&gt;cmd&lt;/c&gt;&lt;u&gt;...&lt;/u&gt;&lt;/hz&gt;  /  回包：&lt;hz&gt;cmd|{json}&lt;/hz&gt;
    /// </summary>
    public static class GatewayProtocol
    {
        private const string OpenTag = "<hz>";
        private const string CloseTag = "</hz>";

        public static string Field(string data, string key)
        {
            string open = "<" + key + ">";
            string close = "</" + key + ">";
            int a = data.IndexOf(open, StringComparison.Ordinal);
            if (a < 0) return "";
            a += open.Length;
            int b = data.IndexOf(close, a, StringComparison.Ordinal);
            if (b < 0) return "";
            return data.Substring(a, b - a);
        }

        public static List<string> DrainFrames(StringBuilder buffer)
        {
            var payloads = new List<string>();
            while (true)
            {
                string current = buffer.ToString();
                int a = current.IndexOf(OpenTag, StringComparison.Ordinal);
                if (a < 0)
                {
                    if (buffer.Length > OpenTag.Length)
                    {
                        buffer.Remove(0, buffer.Length - (OpenTag.Length - 1));
                    }
                    return payloads;
                }

                int b = current.IndexOf(CloseTag, a + OpenTag.Length, StringComparison.Ordinal);
                if (b < 0)
                {
                    if (a > 0)
                    {
                        buffer.Remove(0, a);
                    }
                    return payloads;
                }

                payloads.Add(current.Substring(a + OpenTag.Length, b - a - OpenTag.Length));
                buffer.Remove(0, b + CloseTag.Length);
            }
        }

        public static string Frame(string command, string body)
            => "<hz>" + command + "|" + body + "</hz>";

        public static string JsonEscape(string? text)
        {
            if (string.IsNullOrEmpty(text)) return "";
            var sb = new StringBuilder(text!.Length + 8);
            foreach (char ch in text)
            {
                switch (ch)
                {
                    case '"': sb.Append("\\\""); break;
                    case '\\': sb.Append("\\\\"); break;
                    case '\n': sb.Append("\\n"); break;
                    case '\r': sb.Append("\\r"); break;
                    case '\t': sb.Append("\\t"); break;
                    default:
                        sb.Append(ch < 0x20 ? ' ' : ch);
                        break;
                }
            }
            return sb.ToString();
        }
    }
}
