using System;
using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using A4ServerConsole.Models;

namespace A4ServerConsole.Services.Gateway
{
    /// <summary>
    /// 完整移植自原 Linux C++ gateway/src/main.cpp + handlers.cpp。
    /// 在 Windows 上原生监听 8188，与 A4Launcher.exe 100% 协议兼容。
    /// </summary>
    public sealed class GatewayServer : IDisposable
    {
        private const string TokenAlphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

        private readonly Action<string> _log;
        private readonly ConcurrentDictionary<string, (long Second, int Count)> _rateMap = new ConcurrentDictionary<string, (long, int)>();

        private TcpListener? _listener;
        private CancellationTokenSource? _cts;
        private GatewayDatabase? _db;
        private ConsoleConfig _config = new ConsoleConfig();
        private long _connSeq;

        public bool IsRunning => _listener != null;

        public GatewayServer(Action<string> log)
        {
            _log = log;
        }

        public bool Start(ConsoleConfig config, out string error)
        {
            error = "";
            if (IsRunning)
            {
                Stop();
            }

            _config = config;
            var db = new GatewayDatabase();
            if (!db.Open(config.DbPath, config.GatewayDbPath, out error))
            {
                return false;
            }

            try
            {
                IPAddress bindAddr = config.IsPublicMode ? IPAddress.Any : IPAddress.Loopback;
                var listener = new TcpListener(bindAddr, config.GatewayPort);
                listener.Server.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
                listener.Start(64);

                _db = db;
                _listener = listener;
                _cts = new CancellationTokenSource();

                _log($"[网关] 已启动，监听 {bindAddr}:{config.GatewayPort} -> 下发游戏地址 {config.EffectiveHost}:{config.EntrancePort}");

                Task.Run(() => AcceptLoopAsync(listener, _cts.Token));
                Task.Run(() => PurgeLoopAsync(_cts.Token));
                return true;
            }
            catch (Exception ex)
            {
                db.Dispose();
                error = $"登录器网关监听端口 {config.GatewayPort} 失败：{ex.Message}";
                return false;
            }
        }

        public void Stop()
        {
            try { _cts?.Cancel(); } catch { }
            try { _listener?.Stop(); } catch { }
            _listener = null;
            _cts?.Dispose();
            _cts = null;
            _db?.Dispose();
            _db = null;
            _rateMap.Clear();
            _log("[网关] 已停止");
        }

        private async Task AcceptLoopAsync(TcpListener listener, CancellationToken ct)
        {
            while (!ct.IsCancellationRequested)
            {
                try
                {
                    var client = await listener.AcceptTcpClientAsync().ConfigureAwait(false);
                    _ = Task.Run(() => HandleClientAsync(client, ct), ct);
                }
                catch (ObjectDisposedException)
                {
                    break;
                }
                catch (SocketException)
                {
                    if (ct.IsCancellationRequested) break;
                }
                catch (Exception ex)
                {
                    if (!ct.IsCancellationRequested)
                    {
                        _log("[网关] Accept 异常：" + ex.Message);
                    }
                }
            }
        }

        private async Task PurgeLoopAsync(CancellationToken ct)
        {
            while (!ct.IsCancellationRequested)
            {
                try
                {
                    await Task.Delay(TimeSpan.FromMinutes(10), ct).ConfigureAwait(false);
                    _db?.PurgeExpiredTokens();
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch
                {
                }
            }
        }

        private async Task HandleClientAsync(TcpClient client, CancellationToken ct)
        {
            long seq = Interlocked.Increment(ref _connSeq);
            string ip = ((client.Client.RemoteEndPoint as IPEndPoint)?.Address.ToString()) ?? "0.0.0.0";

            using (client)
            {
                try
                {
                    client.NoDelay = true;
                    client.ReceiveTimeout = 120_000;
                    client.SendTimeout = 120_000;
                    using var stream = client.GetStream();
                    var recvBuf = new byte[4096];
                    var textBuf = new StringBuilder();

                    while (!ct.IsCancellationRequested)
                    {
                        int got = await stream.ReadAsync(recvBuf, 0, recvBuf.Length, ct).ConfigureAwait(false);
                        if (got <= 0) break;

                        textBuf.Append(Encoding.UTF8.GetString(recvBuf, 0, got));
                        var payloads = GatewayProtocol.DrainFrames(textBuf);
                        if (payloads.Count == 0)
                        {
                            if (textBuf.Length > 64 * 1024)
                            {
                                _log($"[网关] [连接 {seq} {ip}] 数据过长且无完整帧，断开");
                                break;
                            }
                            continue;
                        }

                        foreach (var payload in payloads)
                        {
                            string command = GatewayProtocol.Field(payload, "c");
                            if (string.IsNullOrEmpty(command)) continue;

                            string body = !AllowRequest(ip)
                                ? "{\"code\":8,\"msg\":\"请求过于频繁\"}"
                                : Dispatch(command, payload, ip);

                            string reply = GatewayProtocol.Frame(command, body);
                            byte[] outBytes = Encoding.UTF8.GetBytes(reply);
                            await stream.WriteAsync(outBytes, 0, outBytes.Length, ct).ConfigureAwait(false);
                            _log($"[网关] [{ip}] {command} -> {(body.Length > 140 ? body.Substring(0, 140) + "..." : body)}");
                        }
                    }
                }
                catch (Exception)
                {
                    // 客户端断开或超时，静默结束
                }
            }
        }

        private bool AllowRequest(string ip)
        {
            long now = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
            int limit = Math.Max(1, _config.GatewayQpsPerIp);
            var updated = _rateMap.AddOrUpdate(
                ip,
                _ => (now, 1),
                (_, prev) => prev.Second == now ? (now, prev.Count + 1) : (now, 1));
            return updated.Count <= limit;
        }

        private string Dispatch(string command, string payload, string clientIp)
        {
            switch (command)
            {
                case "login": return HandleLogin(payload, clientIp);
                case "reg": return HandleReg(payload, clientIp);
                case "reSetPassword": return HandleResetPassword(payload, clientIp);
                case "cdk": return HandleCdkey(payload);
                case "news": return HandleNews();
                case "version": return HandleVersion();
                case "ping":
                case "05":
                    return "{\"code\":0,\"msg\":\"pong\"}";
                default:
                    return Fail(9, "未知命令 " + command);
            }
        }

        private string HandleLogin(string payload, string clientIp)
        {
            var db = _db;
            if (db == null) return Fail(4, "网关数据库不可用");

            string account = GatewayProtocol.Field(payload, "u");
            string password = GatewayProtocol.Field(payload, "p");

            if (!IsValidUserId(account, out string why) || !IsValidPassword(password, out why))
            {
                db.LogLogin(account, clientIp, 0, why);
                return Fail(3, why);
            }

            var found = db.FindAccount(account);
            if (!found.Found || found.Passwd != password)
            {
                db.LogLogin(account, clientIp, 0, "账号或密码错误");
                return Fail(1, "账号或密码不正确");
            }
            if (found.State != 1)
            {
                db.LogLogin(account, clientIp, 0, "账号已封停");
                return Fail(2, "该账号已被封停");
            }

            string token = MakeToken();
            if (!db.IssueToken(account, found.MId, token, _config.TokenTtlSeconds, clientIp, out string err))
            {
                db.LogLogin(account, clientIp, 0, "发令牌失败：" + err);
                return Fail(4, "服务器繁忙，请稍后再试");
            }

            db.TouchLastPlay(account);
            db.LogLogin(account, clientIp, 1, "登录成功");

            string displayName = string.IsNullOrEmpty(found.Nickname) ? found.UserId : found.Nickname;
            string host = _config.EffectiveHost;

            return "{\"code\":0,\"msg\":\"ok\"" +
                   ",\"token\":\"" + token + "\"" +
                   ",\"uid\":" + found.MId +
                   ",\"name\":\"" + GatewayProtocol.JsonEscape(displayName) + "\"" +
                   ",\"ttl\":" + _config.TokenTtlSeconds +
                   ",\"host\":\"" + GatewayProtocol.JsonEscape(host) + "\"" +
                   ",\"port\":" + _config.EntrancePort + "}";
        }

        private string HandleReg(string payload, string clientIp)
        {
            var db = _db;
            if (db == null) return Fail(4, "网关数据库不可用");

            string account = GatewayProtocol.Field(payload, "u");
            string password = GatewayProtocol.Field(payload, "p");
            string cdkey = GatewayProtocol.Field(payload, "k");

            if (!IsValidUserId(account, out string why) || !IsValidPassword(password, out why))
            {
                return Fail(3, why);
            }

            if (db.IsCdkRequired())
            {
                if (string.IsNullOrEmpty(cdkey))
                {
                    return Fail(5, "需要邀请码");
                }
                if (!db.ConsumeCdkey(cdkey, account, out string cdkErr))
                {
                    return Fail(6, cdkErr);
                }
            }

            if (db.FindAccount(account).Found)
            {
                return Fail(7, "账号已存在");
            }

            long mId = db.CreateAccount(account, password, out string err);
            if (mId <= 0)
            {
                return Fail(4, "注册失败：" + err);
            }

            db.LogLogin(account, clientIp, 1, "注册成功");
            return "{\"code\":0,\"msg\":\"注册成功\",\"uid\":" + mId + "}";
        }

        private string HandleResetPassword(string payload, string clientIp)
        {
            var db = _db;
            if (db == null) return Fail(4, "网关数据库不可用");

            string account = GatewayProtocol.Field(payload, "u");
            string oldPass = GatewayProtocol.Field(payload, "p");
            string newPass = GatewayProtocol.Field(payload, "n");

            if (!IsValidUserId(account, out string why) || !IsValidPassword(newPass, out why))
            {
                return Fail(3, why);
            }

            var found = db.FindAccount(account);
            if (!found.Found || found.Passwd != oldPass)
            {
                db.LogLogin(account, clientIp, 0, "改密：原密码错误");
                return Fail(1, "账号或原密码不正确");
            }

            if (!db.UpdatePassword(account, newPass, out string err))
            {
                return Fail(4, "改密失败：" + err);
            }

            db.LogLogin(account, clientIp, 1, "改密成功");
            return "{\"code\":0,\"msg\":\"密码已修改\"}";
        }

        private string HandleCdkey(string payload)
        {
            var db = _db;
            if (db == null) return Fail(4, "网关数据库不可用");

            string account = GatewayProtocol.Field(payload, "u");
            string code = GatewayProtocol.Field(payload, "k");
            if (string.IsNullOrEmpty(account) || string.IsNullOrEmpty(code))
            {
                return Fail(3, "参数不全");
            }
            if (!db.ConsumeCdkey(code, account, out string err))
            {
                return Fail(6, err);
            }
            return "{\"code\":0,\"msg\":\"邀请码可用\"}";
        }

        private string HandleNews()
        {
            var db = _db;
            if (db == null) return "{\"code\":0,\"count\":0,\"list\":[]}";

            var list = db.GetNotices(20, onlyEnabled: true);
            var sb = new StringBuilder();
            sb.Append("{\"code\":0,\"count\":").Append(list.Count).Append(",\"list\":[");
            for (int i = 0; i < list.Count; i++)
            {
                if (i > 0) sb.Append(',');
                var n = list[i];
                sb.Append("{\"id\":").Append(n.Id)
                  .Append(",\"title\":\"").Append(GatewayProtocol.JsonEscape(n.Title))
                  .Append("\",\"content\":\"").Append(GatewayProtocol.JsonEscape(n.Content))
                  .Append("\",\"state\":\"").Append(GatewayProtocol.JsonEscape(n.State))
                  .Append("\"}");
            }
            sb.Append("]}");
            return sb.ToString();
        }

        private string HandleVersion()
        {
            return "{\"code\":0,\"host\":\"" + GatewayProtocol.JsonEscape(_config.EffectiveHost) +
                   "\",\"port\":" + _config.EntrancePort +
                   ",\"update\":\"" + GatewayProtocol.JsonEscape(_config.GatewayUpdateDir) + "\"}";
        }

        private static string Fail(int code, string message)
            => "{\"code\":" + code + ",\"msg\":\"" + GatewayProtocol.JsonEscape(message) + "\"}";

        private static bool IsValidUserId(string userId, out string why)
        {
            why = "";
            if (string.IsNullOrEmpty(userId))
            {
                why = "请输入账号";
                return false;
            }
            if (userId.Length < 1 || userId.Length > 10)
            {
                why = "账号长度需要在 1 到 10 位之间";
                return false;
            }
            foreach (char ch in userId)
            {
                bool ok = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                          || (ch >= '0' && ch <= '9') || ch == '_';
                if (!ok)
                {
                    why = "账号只能包含字母、数字和下划线";
                    return false;
                }
            }
            return true;
        }

        private static bool IsValidPassword(string pass, out string why)
        {
            why = "";
            if (string.IsNullOrEmpty(pass) || pass.Length > 16)
            {
                why = "密码长度需要在 1 到 16 位之间";
                return false;
            }
            foreach (char ch in pass)
            {
                if (ch == '?' || ch == '"')
                {
                    why = "密码不能包含问号或双引号";
                    return false;
                }
                if (ch < '!' || ch > '~')
                {
                    why = "密码只能是可见的英文、数字或符号";
                    return false;
                }
            }
            return true;
        }

        private static string MakeToken()
        {
            var raw = new byte[16];
            using var rng = RandomNumberGenerator.Create();
            rng.GetBytes(raw);
            var chars = new char[16];
            for (int i = 0; i < 16; i++)
            {
                chars[i] = TokenAlphabet[raw[i] % TokenAlphabet.Length];
            }
            return new string(chars);
        }

        public void Dispose()
        {
            Stop();
        }
    }
}
