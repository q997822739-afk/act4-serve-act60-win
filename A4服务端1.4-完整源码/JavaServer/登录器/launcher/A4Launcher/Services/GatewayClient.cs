using System.IO;
using System.Net.Sockets;
using System.Text;

namespace A4Launcher.Services;

/// <summary>
/// 网关客户端。线协议是花枝 6 系那套明文文本：
///   发：&lt;hz&gt;&lt;c&gt;login&lt;/c&gt;&lt;u&gt;账号&lt;/u&gt;&lt;p&gt;密码&lt;/p&gt;&lt;/hz&gt;
///   收：&lt;hz&gt;login|{"code":0,"token":"..."}&lt;/hz&gt;
/// 一条连接一个请求，请求完就断（和花枝后台工具的做法一样）。
/// </summary>
public sealed class GatewayClient
{
    // .NET Framework 4.7.2 的 TcpClient 没有带 CancellationToken 的 ConnectAsync，
    // 所以连接和读超时都用 Task.WhenAny 自己控。
    private static readonly TimeSpan Timeout = TimeSpan.FromSeconds(10);

    private readonly string _host;
    private readonly int _port;

    public GatewayClient(string host, int port)
    {
        _host = host;
        _port = port;
    }

    public sealed class Reply
    {
        public Reply(int code, string message, JsonNode root)
        {
            Code = code;
            Message = message;
            Root = root;
        }

        public int Code { get; }

        public string Message { get; }

        public JsonNode Root { get; }

        public bool Ok => Code == 0;

        public string Text(string property)
            => Root.TryGet(property, out var value) ? value.AsString() : "";
    }

    public async Task<Reply> CallAsync(string command, CancellationToken cancellationToken,
                                       params (string Key, string Value)[] fields)
    {
        var body = new StringBuilder();
        body.Append("<c>").Append(command).Append("</c>");
        foreach (var (key, value) in fields)
        {
            body.Append('<').Append(key).Append('>')
                .Append(value)
                .Append("</").Append(key).Append('>');
        }
        var packet = Encoding.UTF8.GetBytes("<hz>" + body + "</hz>");

        using var deadline = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        deadline.CancelAfter(Timeout);

        using var tcp = new TcpClient { NoDelay = true };
        var connect = tcp.ConnectAsync(_host, _port);
        var finished = await Task.WhenAny(connect, Task.Delay(Timeout, deadline.Token));
        if (finished != connect)
        {
            throw new OperationCanceledException("连接网关超时");
        }
        await connect;

        var stream = tcp.GetStream();
        await stream.WriteAsync(packet, 0, packet.Length, deadline.Token);

        var buffer = new byte[4096];
        var received = new StringBuilder();
        while (received.ToString().IndexOf("</hz>", StringComparison.Ordinal) < 0)
        {
            var read = await stream.ReadAsync(buffer, 0, buffer.Length, deadline.Token);
            if (read <= 0)
            {
                break;
            }
            received.Append(Encoding.UTF8.GetString(buffer, 0, read));
            if (received.Length > 256 * 1024)
            {
                break;
            }
        }

        var text = received.ToString();
        var start = text.IndexOf("<hz>", StringComparison.Ordinal);
        var end = text.IndexOf("</hz>", StringComparison.Ordinal);
        if (start < 0 || end < 0 || end <= start)
        {
            throw new InvalidDataException("网关返回了无法识别的数据：" + Truncate(text));
        }

        var inner = text.Substring(start + 4, end - start - 4);
        var bar = inner.IndexOf('|');
        if (bar < 0)
        {
            throw new InvalidDataException("网关返回缺少分隔符：" + Truncate(inner));
        }

        var json = inner.Substring(bar + 1);
        var root = JsonNode.Parse(json);
        var code = root.TryGet("code", out var codeValue) ? codeValue.AsInt() : -1;
        var message = root.TryGet("msg", out var msgValue) ? msgValue.AsString() : "";
        return new Reply(code, message, root);
    }

    private static string Truncate(string text)
        => text.Length <= 120 ? text : text.Substring(0, 120) + "…";
}
