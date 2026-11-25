using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Net;

namespace A4Launcher.Services;

/// <summary>
/// 启动 DNF.exe。参数格式和现有的 bat 完全一样（8 个尾部 0）：
///   99?服务器IP?端口?账号?令牌?0?0?0?0?0?0?0?0
/// 令牌模式下密码位放网关发的一次性令牌，命令行里不再出现真实密码。
/// 这一步不需要管理员权限，普通玩家双击就能启动。
/// </summary>
public static class GameLauncher
{
    public static Process Start(string gameHost, int gamePort, string account, string token)
    {
        if (!IPAddress.TryParse(gameHost, out var address) ||
            address.AddressFamily != System.Net.Sockets.AddressFamily.InterNetwork)
            throw new InvalidDataException("游戏地址配置有误，请联系客服");
        if (gamePort is < 1 or > 65535)
            throw new InvalidDataException("游戏端口超出范围：" + gamePort);
        if (!File.Exists(AppPaths.GameExe))
            throw new FileNotFoundException("找不到 DNF.exe，请把登录器放在客户端目录里", AppPaths.GameExe);

        var info = new ProcessStartInfo(AppPaths.GameExe)
        {
            WorkingDirectory = AppPaths.ClientDir,
            UseShellExecute = false,
            CreateNoWindow = true,
        };
        // 和 外网测试-1号.bat 一致：99?ip?port?账号?密码?0×8
        // .NET Framework 没有 ArgumentList，只能自己拼命令行；参数里有 ? 但没空格，
        // 只有引号/反斜杠才需要转义，账号和令牌都是受限字符集，这里仍按规则转义一遍。
        info.Arguments = string.Format(CultureInfo.InvariantCulture,
            "99?{0}?{1}?{2}?{3}?0?0?0?0?0?0?0?0",
            gameHost, gamePort, Escape(account), Escape(token));
        var game = Process.Start(info) ?? throw new InvalidOperationException("DNF.exe 启动失败");

        // 进游戏那句声明弹窗在加壳的 dinput8.dll 里，运行期才解出原文，每次启动都要补一次。
        // 从外面改内存会被反作弊拒绝，所以往游戏进程里注入 a4popuppatch.dll 自己改。
        // 后台线程做，不挡着登录器；失败只写日志，不影响进游戏。
        GameInjector.Schedule(game);
        return game;
    }

    /// <summary>已经在跑的 DNF.exe（客户端自己单开检查，有一个在跑就进不去）。</summary>
    public static Process[] FindRunning() => Process.GetProcessesByName("DNF");

    /// <summary>强制结束所有 DNF.exe，最多等 5 秒。返回没关掉的个数（通常是权限不够）。</summary>
    public static int KillRunning()
    {
        var left = 0;
        foreach (var process in FindRunning())
        {
            using (process)
            {
                try
                {
                    if (!process.HasExited)
                    {
                        process.Kill();
                    }
                    if (!process.WaitForExit(5000))
                    {
                        left++;
                    }
                }
                catch (Exception e) when (e is System.ComponentModel.Win32Exception or InvalidOperationException)
                {
                    // 进程刚好自己退了（InvalidOperation），或者权限不够（Win32：拒绝访问）
                    if (!SafeHasExited(process))
                    {
                        left++;
                        Log.Write("结束 DNF.exe 失败（pid " + process.Id + "）", e);
                    }
                }
            }
        }
        return left;
    }

    private static bool SafeHasExited(Process process)
    {
        try
        {
            return process.HasExited;
        }
        catch (Exception e) when (e is System.ComponentModel.Win32Exception or InvalidOperationException)
        {
            return false;
        }
    }

    /// <summary>
    /// DNF.exe 收到的是整条命令行、按 ? 切分，没法像普通程序那样加引号，
    /// 所以账号/令牌出现分隔符或引号时直接拒绝（账号规则是字母数字下划线，正常不会触发）。
    /// </summary>
    private static string Escape(string value)
    {
        for (var i = 0; i < value.Length; i++)
        {
            var c = value[i];
            if (c == '?' || c == '"' || c == '\\' || char.IsWhiteSpace(c) || char.IsControl(c))
            {
                throw new InvalidDataException("账号或令牌含有不能放进命令行的字符");
            }
        }
        return value;
    }
}
