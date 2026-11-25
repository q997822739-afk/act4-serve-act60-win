using System.IO;

namespace A4Launcher.Services;

/// <summary>
/// 登录器放在客户端目录里（和 DNF.exe 同级）或它的上一级都能用。
/// CurrentDirectory 不可靠（双击时可能是任意目录），用进程自己的 exe 路径。
/// .NET Framework 4.7.2 没有 Environment.ProcessPath，从主模块拿。
/// </summary>
public static class AppPaths
{
    public static string BaseDir { get; } = ResolveBaseDir();

    private static string ResolveBaseDir()
    {
        string? exe = null;
        try
        {
            using var process = System.Diagnostics.Process.GetCurrentProcess();
            exe = process.MainModule?.FileName;
        }
        catch (Exception e) when (e is InvalidOperationException or System.ComponentModel.Win32Exception)
        {
            // 拿不到就退回 AppContext
        }
        if (!string.IsNullOrEmpty(exe))
        {
            var dir = Path.GetDirectoryName(exe);
            if (!string.IsNullOrEmpty(dir)) return dir!;
        }
        return AppContext.BaseDirectory;
    }

    /// <summary>DNF.exe 所在目录：本目录、或它的子目录「DNF60A4这里启动」。</summary>
    public static string ClientDir { get; } = ResolveClientDir();

    private static string ResolveClientDir()
    {
        var candidates = new[]
        {
            BaseDir,
            Path.Combine(BaseDir, "DNF60A4这里启动"),
            Path.GetDirectoryName(BaseDir) ?? BaseDir,
        };
        foreach (var dir in candidates)
        {
            if (File.Exists(Path.Combine(dir, "DNF.exe"))) return Path.GetFullPath(dir);
        }
        return BaseDir;
    }

    public static string GameExe => Path.Combine(ClientDir, "DNF.exe");

    /// <summary>launcher.ini：服务器地址、端口、区名。</summary>
    public static string LauncherIni => File.Exists(Path.Combine(BaseDir, "launcher.ini"))
        ? Path.Combine(BaseDir, "launcher.ini")
        : Path.Combine(ClientDir, "launcher.ini");

    /// <summary>登录器自己的数据（记住的账号、日志）。</summary>
    public static string DataDir
    {
        get
        {
            var dir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "A4Launcher");
            Directory.CreateDirectory(dir);
            return dir;
        }
    }

    public static string LauncherLog => Path.Combine(DataDir, "launcher.log");
}
