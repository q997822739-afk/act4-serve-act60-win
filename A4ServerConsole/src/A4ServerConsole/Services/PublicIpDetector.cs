using System;
using System.Net;
using System.Net.Http;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace A4ServerConsole.Services
{
    /// <summary>
    /// 公网 IP 自动识别：依次请求多个国内外稳定纯文本接口，均失败时回退到本机非回环网卡 IP。
    /// </summary>
    public static class PublicIpDetector
    {
        private static readonly string[] Endpoints =
        {
            "https://4.ipw.cn",
            "https://api.ipify.org",
            "https://ifconfig.me/ip",
            "http://members.3322.org/dyndns/getip",
            "https://ipinfo.io/ip"
        };

        private static readonly Regex Ipv4Regex = new Regex(@"\b(?:\d{1,3}\.){3}\d{1,3}\b", RegexOptions.Compiled);

        public static async Task<(string Ip, string Source)> DetectAsync(CancellationToken ct = default)
        {
            using (var http = new HttpClient { Timeout = TimeSpan.FromSeconds(4) })
            {
                http.DefaultRequestHeaders.TryAddWithoutValidation("User-Agent", "A4ServerConsole/1.0");
                foreach (var url in Endpoints)
                {
                    try
                    {
                        using (var resp = await http.GetAsync(url, ct).ConfigureAwait(false))
                        {
                            if (!resp.IsSuccessStatusCode) continue;
                            var body = (await resp.Content.ReadAsStringAsync().ConfigureAwait(false)).Trim();
                            var match = Ipv4Regex.Match(body);
                            if (match.Success && IsValidIpv4(match.Value))
                            {
                                return (match.Value, new Uri(url).Host);
                            }
                        }
                    }
                    catch
                    {
                        // 尝试下一个源
                    }
                }
            }

            string local = DetectLocalNonLoopbackIp();
            return (local, "本机网卡兜底");
        }

        public static bool IsValidIpv4(string? text)
        {
            if (string.IsNullOrWhiteSpace(text)) return false;
            return IPAddress.TryParse(text!.Trim(), out var ip) &&
                   ip.AddressFamily == AddressFamily.InterNetwork;
        }

        public static string DetectLocalNonLoopbackIp()
        {
            string fallback = "127.0.0.1";
            try
            {
                foreach (var nic in NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (nic.OperationalStatus != OperationalStatus.Up) continue;
                    if (nic.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;

                    foreach (var ua in nic.GetIPProperties().UnicastAddresses)
                    {
                        if (ua.Address.AddressFamily != AddressFamily.InterNetwork) continue;
                        string ip = ua.Address.ToString();
                        if (ip == "127.0.0.1" || ip.StartsWith("169.254.", StringComparison.Ordinal)) continue;
                        if (!ip.StartsWith("10.", StringComparison.Ordinal) &&
                            !ip.StartsWith("192.168.", StringComparison.Ordinal) &&
                            !ip.StartsWith("172.", StringComparison.Ordinal))
                        {
                            return ip;
                        }
                        fallback = ip;
                    }
                }
            }
            catch
            {
                // 忽略网卡枚举异常
            }
            return fallback;
        }
    }
}
