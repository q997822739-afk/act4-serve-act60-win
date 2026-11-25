using System.IO;

namespace A4Launcher.Services;

/// <summary>launcher.ini（服务器地址，和 Zero 的启动器同格式）+ 启动器自己的记住账号配置。</summary>
public sealed class Settings
{
    public string GatewayHost { get; set; } = "127.0.0.1";
    public int GatewayPort { get; set; } = 8188;
    public string GameHost { get; set; } = "";     // 空 = 用网关返回的地址
    public int GamePort { get; set; } = 7001;
    public string Region { get; set; } = "华南区 / 广东一区";   // 逗号分隔多个大区
    public string ServerName { get; set; } = "";   // 空 = 显示游戏地址
    public string ServerState { get; set; } = "空闲";

    // 第二页「选择大区 / 服务器」：照原版做电信 / 网通两个大区分类，
    // 名字随便写（`名字|状态`），选哪个都连同一台游戏服。
    // 私服基本上就是一个大区一台服，所以默认各写一条（照原版的样子：华南区 / 广东一区）
    public string TelecomRegions { get; set; } = "华南区";
    public string NetcomRegions { get; set; } = "华南区";
    public string ServerNames { get; set; } = "广东一区";
    public bool Fullscreen { get; set; }        // 第二页右上角「全屏显示游戏」，跟着记住的账号一起存

    // 顶部导航按钮打开的网页（[links]）
    public string WebsiteUrl { get; set; } = "";
    public string HelperUrl { get; set; } = "";
    public string ShopUrl { get; set; } = "";
    public string ForumUrl { get; set; } = "";
    public string ServiceUrl { get; set; } = "";

    // 调试多开开关：[debug] allow_multi = 1（跳过登录器单实例锁和 DNF.exe 单开拦截）
    public bool AllowMulti { get; set; }

    // 记住的账号
    public string SavedAccount { get; set; } = "";
    public string ProtectedPassword { get; set; } = "";

    private static string StateFile => Path.Combine(AppPaths.DataDir, "settings.json");

    public static Settings Load()
    {
        var settings = new Settings();
        try
        {
            var ini = AppPaths.LauncherIni;
            var text = IniCrypt.ReadText(ini);
            if (text is not null)
            {
                var host = IniFile.GetFromText(text, "gateway", "host");
                if (!string.IsNullOrWhiteSpace(host)) settings.GatewayHost = host!;
                var port = IniFile.GetFromText(text, "gateway", "port");
                if (int.TryParse(port, out var p) && p is > 0 and < 65536) settings.GatewayPort = p;
                var gameHost = IniFile.GetFromText(text, "game", "host");
                if (!string.IsNullOrWhiteSpace(gameHost)) settings.GameHost = gameHost!;
                var gamePort = IniFile.GetFromText(text, "game", "port");
                if (int.TryParse(gamePort, out var gp) && gp is > 0 and < 65536) settings.GamePort = gp;
                var region = IniFile.GetFromText(text, "login", "region");
                if (!string.IsNullOrWhiteSpace(region)) settings.Region = region!;
                var server = IniFile.GetFromText(text, "login", "server");
                if (!string.IsNullOrWhiteSpace(server)) settings.ServerName = server!;
                var state = IniFile.GetFromText(text, "login", "server_state");
                if (!string.IsNullOrWhiteSpace(state)) settings.ServerState = state!;
                settings.WebsiteUrl = IniFile.GetFromText(text, "links", "website") ?? settings.WebsiteUrl;
                settings.HelperUrl = IniFile.GetFromText(text, "links", "helper") ?? settings.HelperUrl;
                settings.ShopUrl = IniFile.GetFromText(text, "links", "shop") ?? settings.ShopUrl;
                settings.ForumUrl = IniFile.GetFromText(text, "links", "forum") ?? settings.ForumUrl;
                settings.ServiceUrl = IniFile.GetFromText(text, "links", "service") ?? settings.ServiceUrl;
                var telecom = IniFile.GetFromText(text, "regions", "telecom");
                if (!string.IsNullOrWhiteSpace(telecom)) settings.TelecomRegions = telecom!;
                var netcom = IniFile.GetFromText(text, "regions", "netcom");
                if (!string.IsNullOrWhiteSpace(netcom)) settings.NetcomRegions = netcom!;
                var servers = IniFile.GetFromText(text, "servers", "list");
                if (!string.IsNullOrWhiteSpace(servers)) settings.ServerNames = servers!;
                var allowMulti = IniFile.GetFromText(text, "debug", "allow_multi");
                settings.AllowMulti = allowMulti == "1" || string.Equals(allowMulti, "true", StringComparison.OrdinalIgnoreCase);
            }
        }
        catch (IOException)
        {
            // 读不到就用默认值，界面会显示出来
        }

        try
        {
            if (File.Exists(StateFile))
            {
                var saved = JsonNode.Parse(File.ReadAllText(StateFile));
                if (saved.TryGet("account", out var account))
                {
                    settings.SavedAccount = account.AsString();
                }
                if (saved.TryGet("password", out var password))
                {
                    settings.ProtectedPassword = password.AsString();
                }
                if (saved.TryGet("fullscreen", out var fullscreen))
                {
                    settings.Fullscreen = fullscreen.AsString() == "1";
                }
            }
        }
        catch (Exception e) when (e is IOException or FormatException)
        {
            // 配置坏了就当没记住
        }
        return settings;
    }

    public void Save()
    {
        var json = "{\n  \"account\": " + JsonNode.Quote(SavedAccount) +
                   ",\n  \"password\": " + JsonNode.Quote(ProtectedPassword) +
                   ",\n  \"fullscreen\": " + JsonNode.Quote(Fullscreen ? "1" : "0") + "\n}\n";
        File.WriteAllText(StateFile, json);
    }
}
