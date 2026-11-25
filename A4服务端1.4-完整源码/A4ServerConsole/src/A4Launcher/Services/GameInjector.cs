using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

namespace A4Launcher.Services;

/// <summary>
/// 进游戏后弹的那句「本软件完全免费，仅供学习交流…」在 Themida 加壳的 dinput8.dll 里，
/// 文件里是密文，进程外又被 TenProtect 挡着（VirtualQueryEx / ReadProcessMemory 直接 access denied），
/// 所以改文件、从外面改内存都不行。这里换一种做法：DNF.exe 一启动就往它里面注入
/// a4popuppatch.dll，让它在自己进程里等壳把那句话解出来，再把首字节写 0 —— 显示函数看到 0 直接返回。
///
/// 注入用 CreateRemoteThread + LoadLibraryW。登录器必须是 32 位进程（csproj 里 PlatformTarget=x86），
/// 这样 kernel32 在登录器里和 DNF.exe 里的基址、导出地址完全一样，不用跨位数算地址。
/// 失败不影响游戏：只写日志，游戏照常起来，最多弹一次声明。
/// </summary>
public static class GameInjector
{
    private const uint ProcessCreateThread = 0x0002;
    private const uint ProcessQueryInformation = 0x0400;
    private const uint ProcessVmOperation = 0x0008;
    private const uint ProcessVmWrite = 0x0020;
    private const uint ProcessVmRead = 0x0010;
    private const uint MemCommit = 0x1000;
    private const uint MemReserve = 0x2000;
    private const uint MemRelease = 0x8000;
    private const uint PageReadWrite = 0x04;
    private const uint StillActive = 259;   // GetExitCodeThread 的“线程还在跑”

    private static readonly string DllPath = ResolveDll();

    private static string ResolveDll()
    {
        var next = Path.Combine(AppPaths.BaseDir, "a4popuppatch.dll");
        if (File.Exists(next)) return next;
        return Path.Combine(AppPaths.ClientDir, "a4popuppatch.dll");
    }

    /// <summary>后台注入，不挡登录界面。刚 CreateProcess 出来的几毫秒内反作弊还没生效，越早越好。</summary>
    public static void Schedule(Process game)
    {
        // 客户端目录里要是已经换成 dinput8 代理（原版 QoL 改名成 act4qol.dll），
        // 弹窗在加载阶段就被代理掐掉了，这里不用再注入，否则只会白等三分钟写一条“超时”。
        if (File.Exists(Path.Combine(AppPaths.ClientDir, "act4qol.dll")))
        {
            Log.Write("进游戏声明弹窗：客户端已是 dinput8 代理模式（act4qol.dll），跳过注入");
            return;
        }
        if (!File.Exists(DllPath))
        {
            Log.Write("进游戏声明弹窗：没找到 a4popuppatch.dll，跳过补丁");
            return;
        }
        var thread = new Thread(() => Run(game))
        {
            IsBackground = true,
            Name = "popup-inject",
        };
        thread.Start();
    }

    private static void Run(Process game)
    {
        var pid = game.Id;
        // 刚 CreateProcess 那一下目标进程还没枚举到 32 位 kernel32（实测 t=0 注入必失败，
        // t=0.3s 就正常）。先让游戏把加载器跑完，再开始试。
        Thread.Sleep(600);
        for (var attempt = 0; attempt < 40; attempt++)
        {
            if (game.HasExited) return;

            var handle = OpenProcess(ProcessCreateThread | ProcessQueryInformation | ProcessVmOperation |
                                     ProcessVmWrite | ProcessVmRead, false, pid);
            if (handle != IntPtr.Zero)
            {
                try
                {
                    if (TryInject(handle))
                    {
                        Log.Write("进游戏声明弹窗：已注入补丁（pid " + pid + "），补丁结果看游戏目录 popup_patch.log");
                        return;
                    }
                }
                finally
                {
                    CloseHandle(handle);
                }
            }
            Thread.Sleep(250);
        }
        Log.Write("进游戏声明弹窗：注入没成功，游戏照常启动；先检查杀毒是否拦了 a4popuppatch.dll");
    }

    private static bool TryInject(IntPtr process)
    {
        var kernel32 = GetModuleHandle("kernel32.dll");
        if (kernel32 == IntPtr.Zero) return false;
        var loadLibrary = GetProcAddress(kernel32, "LoadLibraryW");
        if (loadLibrary == IntPtr.Zero) return false;

        var bytes = Encoding.Unicode.GetBytes(DllPath + "\0");
        var remote = VirtualAllocEx(process, IntPtr.Zero, (UIntPtr)bytes.Length,
            MemCommit | MemReserve, PageReadWrite);
        if (remote == IntPtr.Zero) return false;

        var thread = IntPtr.Zero;
        var freeBuffer = true;
        try
        {
            if (!WriteProcessMemory(process, remote, bytes, bytes.Length, out _)) return false;

            thread = CreateRemoteThread(process, IntPtr.Zero, 0, loadLibrary, remote, 0, out _);
            if (thread == IntPtr.Zero) return false;

            WaitForSingleObject(thread, 5000);
            if (!GetExitCodeThread(thread, out var code)) return false;

            // 关键：WaitForSingleObject 超时后 GetExitCodeThread 返回 STILL_ACTIVE(259)，
            // 原来 `code != 0` 会把它当成功 —— 于是路径缓冲区被释放、线程还在用那块内存，
            // DLL 实际没加载却记“已注入补丁”，游戏目录里一条 popup_patch.log 都没有。
            if (code == StillActive)
            {
                freeBuffer = false;     // 线程还在跑，缓冲区不能动
                return false;
            }
            return code != 0 && code != 0xFFFFFFFF;
        }
        finally
        {
            if (thread != IntPtr.Zero) CloseHandle(thread);
            if (freeBuffer) VirtualFreeEx(process, remote, UIntPtr.Zero, MemRelease);
        }
    }

    [DllImport("kernel32.dll", CharSet = CharSet.Ansi, SetLastError = true)]
    private static extern IntPtr GetModuleHandle(string moduleName);

    [DllImport("kernel32.dll", CharSet = CharSet.Ansi, SetLastError = true)]
    private static extern IntPtr GetProcAddress(IntPtr module, string name);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern IntPtr OpenProcess(uint access, bool inherit, int processId);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool CloseHandle(IntPtr handle);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern IntPtr VirtualAllocEx(IntPtr process, IntPtr address, UIntPtr size,
        uint allocationType, uint protect);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool VirtualFreeEx(IntPtr process, IntPtr address, UIntPtr size, uint freeType);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool WriteProcessMemory(IntPtr process, IntPtr address, byte[] buffer,
        int size, out IntPtr written);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern IntPtr CreateRemoteThread(IntPtr process, IntPtr attributes, uint stackSize,
        IntPtr startAddress, IntPtr parameter, uint flags, out uint threadId);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern uint WaitForSingleObject(IntPtr handle, uint milliseconds);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool GetExitCodeThread(IntPtr thread, out uint exitCode);
}
