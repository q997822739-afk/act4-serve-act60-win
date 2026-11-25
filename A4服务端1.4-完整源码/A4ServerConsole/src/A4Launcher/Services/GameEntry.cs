using System.Windows;
using System.Windows.Media;

namespace A4Launcher.Services;

/// <summary>
/// 选大区 / 选服务器列表里的一行。
/// 私服一般就一台游戏服，列表是照原版做的「样子」：大区名、服务器名都可以在
/// launcher.ini 里随便写（`名字|状态`），选哪个都连同一台服务器。
///
/// 每一行的底图直接用的是原版 DNFLauncher 进程里拆出来的 89×22 图片：
/// 大区（无图标）和服务器（左侧一个服务器柜图标 + 状态颜色）各三态——
/// 常态 / 悬停 / 选中。状态颜色照原版图例：空闲 绿、繁忙 黄、拥挤 红、维护 灰。
/// </summary>
public sealed class GameEntry
{
    public const string Idle = "空闲";
    public const string Busy = "繁忙";
    public const string Crowded = "拥挤";
    public const string Maintain = "维护";

    private const string PackPrefix = "pack://application:,,,/A4Launcher;component/Assets/";

    private GameEntry(string name, string status, string plate, SolidColorBrush textBrush,
                      HorizontalAlignment textAlign, Thickness textMargin)
    {
        Name = name;
        Status = status;
        NormalImage = PackPrefix + "item_" + plate + "_a.png";
        HoverImage = PackPrefix + "item_" + plate + "_b.png";
        SelectedImage = PackPrefix + "item_" + plate + "_c.png";
        TextBrush = textBrush;
        TextAlign = textAlign;
        TextMargin = textMargin;
    }

    public string Name { get; }

    public string Status { get; }

    public string NormalImage { get; }

    public string HoverImage { get; }

    public string SelectedImage { get; }

    public Brush TextBrush { get; }

    public HorizontalAlignment TextAlign { get; }

    public Thickness TextMargin { get; }

    /// <summary>大区条目：整块 89×22 的橙色牌子，名字居中，只有三态没有状态色。</summary>
    public static GameEntry Region(string name) =>
        new(name, "", "region", BrushFrom(0xEB, 0xD0, 0x2B), HorizontalAlignment.Center, new Thickness(0));

    /// <summary>服务器条目：左边有服务器图标，名字靠图标右边排，牌子颜色就是状态色。</summary>
    public static GameEntry Server(string name, string? status)
    {
        var value = string.IsNullOrWhiteSpace(status) ? Idle : status!;
        var plate = value switch
        {
            Busy => "busy",
            Crowded => "crowded",
            Maintain => "maintain",
            _ => "idle",
        };
        // 文字起点 24px 让开左边的服务器图标（照原版抓图量的）
        return new GameEntry(name, value, plate, BrushFrom(0xEB, 0xD0, 0x2B),
                             HorizontalAlignment.Left, new Thickness(24, 0, 6, 0));
    }

    private static SolidColorBrush BrushFrom(byte r, byte g, byte b)
    {
        var brush = new SolidColorBrush(Color.FromRgb(r, g, b));
        brush.Freeze();
        return brush;
    }

    /// <summary>把 ini 里的一行 `名字,名字|状态,…` 拆成条目；没写状态就用默认的。</summary>
    public static IReadOnlyList<GameEntry> Parse(string? text, string defaultStatus, bool region)
    {
        var list = new List<GameEntry>();
        if (string.IsNullOrWhiteSpace(text)) return list;
        foreach (var raw in text!.Split(new[] { ',' }, StringSplitOptions.RemoveEmptyEntries))
        {
            var item = raw.Trim();
            if (item.Length == 0) continue;
            var bar = item.IndexOf('|');
            var name = bar < 0 ? item : item.Substring(0, bar).Trim();
            var status = bar < 0 ? defaultStatus : item.Substring(bar + 1).Trim();
            if (name.Length == 0) continue;
            list.Add(region ? Region(name) : Server(name, status));
        }
        return list;
    }

    public override string ToString() => Name;
}
