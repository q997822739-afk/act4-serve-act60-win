using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using A4GmTool.Core.Services;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace A4GmTool.Core.Web
{
    /// <summary>
    /// 本地网页 GM 的 HTTP 服务。
    ///
    /// 为什么不用 HttpListener：它要求管理员权限或预先注册 urlacl（netsh http add urlacl），
    /// 普通玩家双击 exe 会直接「拒绝访问」。这里直接裸写 TcpListener，只看 127.0.0.1，
    /// 任何权限都能起来，也没有防火墙弹窗。
    /// </summary>
    public sealed class GmHttpServer : IDisposable
    {
        private readonly GmApiRouter _router;
        private readonly string _webRoot;
        private readonly Action<string> _log;
        private readonly Assembly _resourceHost;
        private readonly byte[] _passwordHash;
        private readonly bool _secureCookie;
        private readonly string _localCsrf;
        private readonly byte[] _loginKey;
        private readonly object _authLock = new object();
        private readonly Dictionary<string, Session> _sessions = new Dictionary<string, Session>(StringComparer.Ordinal);
        private DateTime _nextLoginAttempt;
        private int _failedLogins;
        private static readonly RandomNumberGenerator TokenRandom = RandomNumberGenerator.Create();
        private const int SessionHours = 8;
        private const int SessionLimit = 128;
        private const string CsrfHeader = "X-A4-GM-CSRF";

        private sealed class Session
        {
            public string Csrf;
            public DateTime Expires;
        }

        private TcpListener _listener;
        private Thread _acceptThread;
        private volatile bool _running;

        public int Port { get; private set; }
        public bool Running { get { return _running; } }
        public string Url { get { return "http://127.0.0.1:" + Port + "/"; } }

        public GmHttpServer(GmApiRouter router, string webRoot, Action<string> log)
            : this(router, webRoot, log, null, false) { }

        public GmHttpServer(GmApiRouter router, string webRoot, Action<string> log, string password, bool secureCookie)
        {
            if (!string.IsNullOrEmpty(password) && password.Trim().Length == 0)
                throw new ArgumentException("GM password cannot consist of whitespace only.", "password");
            if (secureCookie && string.IsNullOrEmpty(password))
                throw new ArgumentException("A GM password is required for secure-cookie mode.", "password");
            _router = router;
            _webRoot = webRoot;
            _log = log;
            _resourceHost = typeof(GmHttpServer).Assembly;
            _passwordHash = string.IsNullOrEmpty(password) ? null : Hash(password);
            _secureCookie = secureCookie;
            _localCsrf = _passwordHash == null ? NewToken() : null;
            _loginKey = _passwordHash == null ? null : RandomBytes();
        }

        /// <summary>启动监听。port 传 0 表示让系统挑一个空闲端口。</summary>
        public bool Start(int port, out string error)
        {
            error = "";
            Stop();

            try
            {
                _listener = new TcpListener(IPAddress.Loopback, port);
                _listener.ExclusiveAddressUse = true;
                _listener.Start(64);
                Port = ((IPEndPoint)_listener.LocalEndpoint).Port;
                _running = true;

                _acceptThread = new Thread(AcceptLoop);
                _acceptThread.IsBackground = true;
                _acceptThread.Name = "a4gm-http";
                _acceptThread.Start();

                Log("网页 GM 已启动：" + Url);
                return true;
            }
            catch (Exception ex)
            {
                error = "端口 " + port + " 起不来：" + ex.Message;
                Stop();
                return false;
            }
        }

        public void Stop()
        {
            _running = false;
            try { if (_listener != null) _listener.Stop(); } catch { }
            _listener = null;
            lock (_authLock)
            {
                _sessions.Clear();
                _nextLoginAttempt = DateTime.MinValue;
                _failedLogins = 0;
            }
        }

        private void AcceptLoop()
        {
            while (_running)
            {
                TcpClient client = null;
                try
                {
                    client = _listener.AcceptTcpClient();
                }
                catch
                {
                    if (!_running) return;
                    continue;
                }

                TcpClient c = client;
                ThreadPool.QueueUserWorkItem(_ => HandleClient(c));
            }
        }

        private void HandleClient(TcpClient client)
        {
            try
            {
                client.ReceiveTimeout = 15000;
                client.SendTimeout = 30000;

                using (NetworkStream stream = client.GetStream())
                {
                    Request req = ReadRequest(stream);
                    if (req != null) Route(stream, req);
                }
            }
            catch
            {
                // 浏览器断连 / 请求畸形都直接丢掉，不影响服务
            }
            finally
            {
                try { client.Close(); } catch { }
            }
        }

        private sealed class Request
        {
            public string Method = "GET";
            public string Path = "/";
            public string Query = "";
            public Dictionary<string, string> Headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
            public byte[] Body = new byte[0];
        }

        private Request ReadRequest(NetworkStream stream)
        {
            // 读头
            MemoryStream head = new MemoryStream();
            byte[] one = new byte[1];
            int matched = 0;
            while (matched < 4)
            {
                int n = stream.Read(one, 0, 1);
                if (n <= 0) return null;
                head.WriteByte(one[0]);
                byte b = one[0];
                if ((matched == 0 && b == 13) || (matched == 1 && b == 10) || (matched == 2 && b == 13) || (matched == 3 && b == 10))
                    matched++;
                else
                    matched = (b == 13) ? 1 : 0;

                if (head.Length > 65536) return null;
            }

            string headText = Encoding.UTF8.GetString(head.ToArray());
            string[] lines = headText.Split(new[] { "\r\n" }, StringSplitOptions.None);
            if (lines.Length == 0) return null;

            string[] requestLine = lines[0].Split(' ');
            if (requestLine.Length != 3) return null;

            Request req = new Request();
            req.Method = requestLine[0];

            // The reverse proxy must send origin-form requests. Keep one interpretation of the route.
            string target = requestLine[1];
            if (!target.StartsWith("/", StringComparison.Ordinal) || target.StartsWith("//", StringComparison.Ordinal)
                || target.IndexOf('#') >= 0 || target.IndexOf('\\') >= 0) return null;
            int q = target.IndexOf('?');
            req.Path = q >= 0 ? target.Substring(0, q) : target;
            req.Query = q >= 0 ? target.Substring(q + 1) : "";

            for (int i = 1; i < lines.Length && lines[i].Length > 0; i++)
            {
                int colon = lines[i].IndexOf(':');
                if (colon <= 0) return null;
                string name = lines[i].Substring(0, colon);
                if (name.Trim() != name || req.Headers.ContainsKey(name)) return null;
                req.Headers.Add(name, lines[i].Substring(colon + 1).Trim());
            }

            // We only support fixed-length bodies and process one request per connection.
            if (req.Headers.ContainsKey("Transfer-Encoding")) return null;
            string cl;
            int length = 0;
            if (req.Headers.TryGetValue("Content-Length", out cl) &&
                (!int.TryParse(cl, NumberStyles.None, CultureInfo.InvariantCulture, out length) || length > 4 * 1024 * 1024))
                return null;
            if (length > 0)
            {
                req.Body = new byte[length];
                int got = 0;
                while (got < length)
                {
                    int n = stream.Read(req.Body, got, length - got);
                    if (n <= 0) return null;
                    got += n;
                }
            }

            return req;
        }

        private void Route(NetworkStream stream, Request req)
        {
            string path = (req.Path ?? "/").TrimEnd('/');
            if (path.Length == 0) path = "/";
            bool api = path == "/api" || path.StartsWith("/api/", StringComparison.Ordinal);
            if (_passwordHash == null && !IsLoopbackHost(req))
            {
                Reject(stream, 403, "独立模式只接受本机地址。", api);
                return;
            }

            Session session = null;
            string sessionId = null;
            if (_passwordHash != null)
            {
                sessionId = ReadCookie(req, CookieName("session"));
                session = FindSession(sessionId);
                if (path == "/login")
                {
                    Login(stream, req, session);
                    return;
                }
                if (session == null)
                {
                    if (!api && req.Method == "GET") Redirect(stream, "/login", "");
                    else Reject(stream, 401, "请先登录 GM。", api);
                    return;
                }
            }

            string csrf = session == null ? _localCsrf : session.Csrf;
            if (path == "/logout")
            {
                if (req.Method != "POST") Reject(stream, 405, "只允许 POST。", api, "Allow: POST\r\n");
                else if (session == null) Reject(stream, 404, "未启用网页登录。", api);
                else if (!HasCsrf(req, csrf)) Reject(stream, 403, "CSRF 验证失败。", api);
                else
                {
                    lock (_authLock) _sessions.Remove(sessionId);
                    Redirect(stream, "/login", Cookie("session", "", 0));
                }
                return;
            }

            if (api)
            {
                RouteApi(stream, req, path, csrf);
                return;
            }

            if (req.Method != "GET")
            {
                Reject(stream, 405, "只允许 GET。", false, "Allow: GET\r\n");
                return;
            }
            ServeStatic(stream, path == "/" ? "/index.html" : path);
        }

        private void RouteApi(NetworkStream stream, Request req, string path, string csrf)
        {
            bool csrfEndpoint = path == "/api/csrf";
            bool readOnly = GmApiRouter.IsReadOnly(path);
            bool get = req.Method == "GET";
            if (!(get ? readOnly || csrfEndpoint : req.Method == "POST" && !csrfEndpoint))
            {
                string allow = csrfEndpoint ? "GET" : readOnly ? "GET, POST" : "POST";
                Reject(stream, 405, "不允许此请求方法。", true, "Allow: " + allow + "\r\n");
                return;
            }
            if (csrfEndpoint)
            {
                WriteText(stream, 200, "application/json; charset=utf-8",
                    JsonConvert.SerializeObject(new { csrfToken = csrf, authenticated = _passwordHash != null }));
                return;
            }

            JObject body = new JObject();
            if (!get)
            {
                if (!HasCsrf(req, csrf))
                {
                    Reject(stream, 403, "CSRF 验证失败。", true);
                    return;
                }
                if (!HasContentType(req, "application/json"))
                {
                    Reject(stream, 415, "只接受 JSON 请求。", true);
                    return;
                }
                try { body = JObject.Parse(Encoding.UTF8.GetString(req.Body)); }
                catch (JsonException)
                {
                    Reject(stream, 400, "JSON 请求无效。", true);
                    return;
                }
            }
            else
            {
                foreach (KeyValuePair<string, string> kv in ParseQuery(req.Query))
                    body[kv.Key] = kv.Value;
            }

            string json;
            try { json = _router.Handle(path, body); }
            catch (Exception ex)
            {
                json = JsonConvert.SerializeObject(new { ok = false, error = "后端异常：" + ex.Message });
                Log("处理 " + path + " 出错：" + ex.Message);
            }
            WriteText(stream, 200, "application/json; charset=utf-8", json);
        }

        private void Login(NetworkStream stream, Request req, Session session)
        {
            if (req.Method != "GET" && req.Method != "POST")
            {
                Reject(stream, 405, "不允许此请求方法。", false, "Allow: GET, POST\r\n");
                return;
            }
            if (req.Method == "GET")
            {
                if (session != null) Redirect(stream, "/", "");
                else LoginPage(stream, 200, "");
                return;
            }
            if (!HasContentType(req, "application/x-www-form-urlencoded") || req.Body.Length > 16384)
            {
                Reject(stream, 400, "登录表单无效。", false);
                return;
            }

            Dictionary<string, string> form = ParseQuery(Encoding.UTF8.GetString(req.Body));
            string token;
            if (!form.TryGetValue("csrf", out token) || !TokenEquals(ReadCookie(req, CookieName("login")), token)
                || !IsLoginTokenValid(token))
            {
                Reject(stream, 403, "登录表单已失效，请重新打开登录页。", false);
                return;
            }
            if (session != null)
            {
                Redirect(stream, "/", Cookie("login", "", 0));
                return;
            }

            string password;
            form.TryGetValue("password", out password);
            string id = null;
            int retryAfter = 0;
            bool full = false;
            lock (_authLock)
            {
                DateTime now = DateTime.UtcNow;
                if (now < _nextLoginAttempt)
                    retryAfter = (int)Math.Ceiling((_nextLoginAttempt - now).TotalSeconds);
                else if (!ConstantTimeEquals(_passwordHash, Hash(password ?? "")))
                {
                    // All proxied clients may share a socket address; do not trust client-IP headers.
                    _failedLogins = Math.Min(_failedLogins + 1, 7);
                    _nextLoginAttempt = now.AddSeconds(1 << (_failedLogins - 1));
                }
                else
                {
                    _failedLogins = 0;
                    _nextLoginAttempt = DateTime.MinValue;
                    List<string> expired = new List<string>();
                    foreach (KeyValuePair<string, Session> pair in _sessions)
                        if (pair.Value.Expires <= now) expired.Add(pair.Key);
                    foreach (string key in expired) _sessions.Remove(key);
                    if (_sessions.Count >= SessionLimit) full = true;
                    else
                    {
                        id = NewToken();
                        _sessions.Add(id, new Session { Csrf = NewToken(), Expires = now.AddHours(SessionHours) });
                    }
                }
            }

            if (retryAfter != 0)
                LoginPage(stream, 429, "尝试过于频繁，请稍后再试。", "Retry-After: " + retryAfter.ToString(CultureInfo.InvariantCulture) + "\r\n");
            else if (full) LoginPage(stream, 503, "登录会话已满，请稍后再试。");
            else if (id == null) LoginPage(stream, 401, "密码不正确。");
            else Redirect(stream, "/", Cookie("session", id, SessionHours * 3600) + Cookie("login", "", 0));
        }

        private void LoginPage(NetworkStream stream, int status, string message, string headers = "")
        {
            const int lifetimeMinutes = 10;
            string data = DateTime.UtcNow.AddMinutes(lifetimeMinutes).Ticks.ToString(CultureInfo.InvariantCulture) + "." + NewToken();
            string token = data + "." + SignLoginToken(data);
            string html = "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>网页 GM 登录</title>"
                + "<style>:root{color-scheme:dark;--bg:#151821;--panel:#1c212d;--input:#11151e;--line:#2c3446;"
                + "--text:#f0f3fa;--muted:#9aa5bc;--accent:#f7b500;--hover:#ffc629;--error:#ff9090}"
                + "*{box-sizing:border-box}body{margin:0;padding:24px 16px;min-height:100vh;display:grid;place-items:center;"
                + "background:var(--bg);color:var(--text);font:14px/1.5 'Microsoft YaHei UI','Segoe UI',system-ui,sans-serif}"
                + "main{width:100%;max-width:360px;padding:24px;background:var(--panel);border:1px solid var(--line);border-radius:6px}"
                + "h1{margin:0 0 20px;font-size:20px;font-weight:600}form{display:grid;gap:8px}label{color:var(--muted)}"
                + "input,button{font:inherit;min-width:0;width:100%;min-height:40px;padding:8px 12px;border:1px solid var(--line);border-radius:6px}"
                + "input{background:var(--input);color:var(--text)}input:focus-visible,button:focus-visible{outline:2px solid var(--accent);outline-offset:2px}"
                + "button{margin-top:4px;background:var(--accent);border-color:var(--accent);color:#191500;font-weight:600;cursor:pointer}"
                + "button:hover{background:var(--hover);border-color:var(--hover)}[role=alert]{margin:0 0 14px;color:var(--error)}</style></head>"
                + "<body><main><h1>A4 网页 GM</h1>"
                + (message.Length == 0 ? "" : "<p role=\"alert\">" + message + "</p>")
                + "<form method=\"post\" action=\"/login\" accept-charset=\"UTF-8\">"
                + "<input type=\"hidden\" name=\"csrf\" value=\"" + token + "\">"
                + "<label for=\"password\">GM 密码</label><input id=\"password\" type=\"password\" name=\"password\" autocomplete=\"current-password\" required> "
                + "<button type=\"submit\">登录</button></form></main></body></html>";
            WriteText(stream, status, "text/html; charset=utf-8", html, headers + Cookie("login", token, lifetimeMinutes * 60) +
                "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'\r\n");
        }

        private bool IsLoginTokenValid(string token)
        {
            if (string.IsNullOrEmpty(token) || token.Length > 128) return false;
            int first = token.IndexOf('.');
            int last = token.LastIndexOf('.');
            long expires;
            return first > 0 && last > first &&
                long.TryParse(token.Substring(0, first), NumberStyles.None, CultureInfo.InvariantCulture, out expires) &&
                DateTime.UtcNow.Ticks < expires && TokenEquals(SignLoginToken(token.Substring(0, last)), token.Substring(last + 1));
        }

        private string SignLoginToken(string data)
        {
            using (HMACSHA256 hmac = new HMACSHA256(_loginKey))
                return Base64Url(hmac.ComputeHash(Encoding.ASCII.GetBytes(data)));
        }

        private Session FindSession(string id)
        {
            if (id == null || id.Length != 43) return null;
            lock (_authLock)
            {
                Session session;
                if (!_sessions.TryGetValue(id, out session)) return null;
                if (session.Expires > DateTime.UtcNow) return session;
                _sessions.Remove(id);
                return null;
            }
        }

        private bool IsLoopbackHost(Request req)
        {
            string host;
            if (!req.Headers.TryGetValue("Host", out host)) return false;
            int colon = host.IndexOf(':');
            if (colon >= 0)
            {
                if (host.Substring(colon + 1) != Port.ToString(CultureInfo.InvariantCulture)) return false;
                host = host.Substring(0, colon);
            }
            return host == "127.0.0.1" || string.Equals(host, "localhost", StringComparison.OrdinalIgnoreCase);
        }

        private string CookieName(string suffix)
        {
            return (_secureCookie ? "__Host-" : "") + "a4gm_" + suffix;
        }

        private string Cookie(string suffix, string value, int maxAge)
        {
            return "Set-Cookie: " + CookieName(suffix) + "=" + value + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" +
                maxAge.ToString(CultureInfo.InvariantCulture) + (_secureCookie ? "; Secure" : "") + "\r\n";
        }

        private static string ReadCookie(Request req, string name)
        {
            string header;
            if (!req.Headers.TryGetValue("Cookie", out header)) return null;
            string result = null;
            string prefix = name + "=";
            foreach (string part in header.Split(';'))
            {
                string cookie = part.Trim();
                if (!cookie.StartsWith(prefix, StringComparison.Ordinal)) continue;
                if (result != null) return null;
                result = cookie.Substring(prefix.Length);
            }
            return result;
        }

        private static bool HasCsrf(Request req, string expected)
        {
            string supplied;
            return req.Headers.TryGetValue(CsrfHeader, out supplied) && TokenEquals(expected, supplied);
        }

        private static bool HasContentType(Request req, string expected)
        {
            string header;
            if (!req.Headers.TryGetValue("Content-Type", out header)) return false;
            int semi = header.IndexOf(';');
            if (semi >= 0) header = header.Substring(0, semi).Trim();
            return string.Equals(header, expected, StringComparison.OrdinalIgnoreCase);
        }

        private static byte[] Hash(string text)
        {
            using (SHA256 hash = SHA256.Create()) return hash.ComputeHash(Encoding.UTF8.GetBytes(text));
        }

        private static bool TokenEquals(string expected, string supplied)
        {
            return expected != null && supplied != null && expected.Length == supplied.Length &&
                ConstantTimeEquals(Encoding.UTF8.GetBytes(expected), Encoding.UTF8.GetBytes(supplied));
        }

        private static bool ConstantTimeEquals(byte[] expected, byte[] supplied)
        {
            if (expected.Length != supplied.Length) return false;
            int diff = 0;
            for (int i = 0; i < expected.Length; i++) diff |= expected[i] ^ supplied[i];
            return diff == 0;
        }

        private static byte[] RandomBytes()
        {
            byte[] data = new byte[32];
            lock (TokenRandom) TokenRandom.GetBytes(data);
            return data;
        }

        private static string Base64Url(byte[] bytes)
        {
            return Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');
        }

        private static string NewToken() { return Base64Url(RandomBytes()); }

        private void ServeStatic(NetworkStream stream, string path)
        {
            string clean = path.TrimStart('/');
            // Only bundled asset types are served; reject rooted/drive and alternate-stream paths on Windows.
            if (clean.Contains("..") || clean.IndexOfAny(new[] { ':', '\\', '%', '\0' }) >= 0 ||
                MimeOf(clean) == "application/octet-stream")
            {
                Reject(stream, 404, "找不到这个文件。", false);
                return;
            }

            string file = null;
            if (!string.IsNullOrEmpty(_webRoot))
            {
                string candidate = Path.Combine(_webRoot, clean.Replace('/', Path.DirectorySeparatorChar));
                if (File.Exists(candidate)) file = candidate;
            }

            if (file != null)
            {
                byte[] data = File.ReadAllBytes(file);
                WriteResponse(stream, 200, MimeOf(clean), data, "");
                return;
            }

            // 磁盘上没有就找嵌入资源（单文件发布时用）
            byte[] embedded = ReadEmbedded(clean);
            if (embedded != null)
            {
                WriteResponse(stream, 200, MimeOf(clean), embedded, "");
                return;
            }

            Reject(stream, 404, "找不到这个文件。", false);
        }

        private byte[] ReadEmbedded(string clean)
        {
            try
            {
                string resourceName = "A4GmTool.web." + clean.Replace('/', '.');
                using (Stream s = _resourceHost.GetManifestResourceStream(resourceName))
                {
                    if (s == null) return null;
                    using (MemoryStream ms = new MemoryStream())
                    {
                        byte[] buf = new byte[81920];
                        int n;
                        while ((n = s.Read(buf, 0, buf.Length)) > 0) ms.Write(buf, 0, n);
                        return ms.ToArray();
                    }
                }
            }
            catch
            {
                return null;
            }
        }

        private static string MimeOf(string path)
        {
            string lower = (path ?? "").ToLowerInvariant();
            if (lower.EndsWith(".html")) return "text/html; charset=utf-8";
            if (lower.EndsWith(".css")) return "text/css; charset=utf-8";
            if (lower.EndsWith(".js")) return "application/javascript; charset=utf-8";
            if (lower.EndsWith(".png")) return "image/png";
            if (lower.EndsWith(".ico")) return "image/x-icon";
            if (lower.EndsWith(".svg")) return "image/svg+xml";
            if (lower.EndsWith(".json")) return "application/json; charset=utf-8";
            return "application/octet-stream";
        }

        private static void Redirect(NetworkStream stream, string location, string headers)
        {
            WriteText(stream, 303, "text/plain; charset=utf-8", "", "Location: " + location + "\r\n" + headers);
        }

        private static void Reject(NetworkStream stream, int status, string error, bool api, string headers = "")
        {
            WriteText(stream, status, api ? "application/json; charset=utf-8" : "text/plain; charset=utf-8",
                api ? JsonConvert.SerializeObject(new { ok = false, error = error }) : error, headers);
        }

        private static void WriteText(NetworkStream stream, int status, string contentType, string body, string headers = "")
        {
            WriteResponse(stream, status, contentType, Encoding.UTF8.GetBytes(body), headers);
        }

        private static void WriteResponse(NetworkStream stream, int status, string contentType, byte[] body, string headers)
        {
            StringBuilder sb = new StringBuilder();
            sb.Append("HTTP/1.1 ").Append(status).Append(' ').Append(StatusText(status)).Append("\r\n");
            sb.Append("Content-Type: ").Append(contentType).Append("\r\n");
            sb.Append("Content-Length: ").Append(body.Length).Append("\r\n");
            sb.Append("Cache-Control: no-store\r\n");
            sb.Append("Connection: close\r\n");
            sb.Append("X-Content-Type-Options: nosniff\r\n");
            sb.Append("X-Frame-Options: DENY\r\n");
            sb.Append("Referrer-Policy: no-referrer\r\n");
            sb.Append("Cross-Origin-Resource-Policy: same-origin\r\n");
            sb.Append(headers).Append("\r\n");

            byte[] head = Encoding.ASCII.GetBytes(sb.ToString());
            stream.Write(head, 0, head.Length);
            if (body.Length > 0) stream.Write(body, 0, body.Length);
            stream.Flush();
        }

        private static string StatusText(int status)
        {
            switch (status)
            {
                case 200: return "OK";
                case 303: return "See Other";
                case 400: return "Bad Request";
                case 401: return "Unauthorized";
                case 403: return "Forbidden";
                case 404: return "Not Found";
                case 405: return "Method Not Allowed";
                case 415: return "Unsupported Media Type";
                case 429: return "Too Many Requests";
                case 503: return "Service Unavailable";
                default: return "OK";
            }
        }

        private static Dictionary<string, string> ParseQuery(string query)
        {
            Dictionary<string, string> map = new Dictionary<string, string>(StringComparer.Ordinal);
            if (string.IsNullOrEmpty(query)) return map;

            foreach (string pair in query.Split('&'))
            {
                if (pair.Length == 0) continue;
                int eq = pair.IndexOf('=');
                string k = eq >= 0 ? pair.Substring(0, eq) : pair;
                string v = eq >= 0 ? pair.Substring(eq + 1) : "";
                map[Decode(k)] = Decode(v);
            }
            return map;
        }

        private static string Decode(string s)
        {
            try { return Uri.UnescapeDataString(s.Replace('+', ' ')); }
            catch { return s; }
        }

        private void Log(string message)
        {
            if (_log != null) _log(message);
        }

        public void Dispose()
        {
            Stop();
        }
    }
}
