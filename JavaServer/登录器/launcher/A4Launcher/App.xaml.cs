using System.Runtime.InteropServices;
using System.Threading;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Threading;
using A4Launcher.Services;

namespace A4Launcher;

public partial class App : Application
{
    private Mutex? _single;

    protected override void OnStartup(StartupEventArgs e)
    {
        // 游戏起来以后切 D3D / 全屏 / 分辨率，WPF 的硬件渲染线程会挂（UCEERR_RENDERTHREADFAILURE，
        // 0x88980406），之后每次重绘都抛异常，登录器就「卡死」。启动器界面就是几张位图，
        // 走软件渲染没有任何代价，也不再和游戏抢显卡。
        RenderOptions.ProcessRenderMode = RenderMode.SoftwareOnly;

        // 出错时写 launcher.log，并给玩家一个能看懂的提示（而不是 .NET 那串英文堆栈）
        DispatcherUnhandledException += OnDispatcherError;
        AppDomain.CurrentDomain.UnhandledException += (_, args) =>
            Log.Write("未处理异常", args.ExceptionObject as Exception ?? new Exception("未知错误"));

        // 允许并行调试：带 --allow-multi 就不抢单实例锁
        var allowMulti = e.Args.Any(a => a.Equals("--allow-multi", StringComparison.OrdinalIgnoreCase));
        if (!allowMulti)
        {
            _single = new Mutex(true, "A4_Commercial_Launcher_v1", out var created);
            if (!created)
            {
                MessageBox.Show("登录器已经在运行了。", "DNF 登录器",
                    MessageBoxButton.OK, MessageBoxImage.Information);
                Shutdown();
                return;
            }
        }
        base.OnStartup(e);
    }

    private const int RenderThreadFailure = unchecked((int)0x88980406);   // UCEERR_RENDERTHREADFAILURE
    private static DateTime _lastErrorBox = DateTime.MinValue;

    private static void OnDispatcherError(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        Log.Write("界面异常", e.Exception);
        e.Handled = true;

        // 渲染线程出错：弹框本身也要渲染，只会再抛一次，越弹越卡。只记日志，下一帧会重建渲染目标
        if (e.Exception is COMException com && com.ErrorCode == RenderThreadFailure)
        {
            return;
        }
        // 同一个错误连续触发时别一个接一个地弹
        if ((DateTime.Now - _lastErrorBox).TotalSeconds < 10)
        {
            return;
        }
        _lastErrorBox = DateTime.Now;
        MessageBox.Show(
            "登录器出错了，已经记录到日志，请把日志发给客服：" + Environment.NewLine +
            AppPaths.LauncherLog + Environment.NewLine + Environment.NewLine +
            e.Exception.Message,
            "DNF 登录器", MessageBoxButton.OK, MessageBoxImage.Error);
    }

    protected override void OnExit(ExitEventArgs e)
    {
        try { _single?.ReleaseMutex(); } catch { /* 忽略 */ }
        _single?.Dispose();
        base.OnExit(e);
    }
}
