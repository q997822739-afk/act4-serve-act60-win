using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Net;
using System.Runtime.InteropServices;

namespace A4Launcher.Services;

/// <summary>
/// 启动 DNF.exe。参数格式和现有的 bat 完全一样（8 个尾部 0）：
///   99?服务器IP?端口?账号?令牌?0?0?0?0?0?0?0?0
/// 令牌模式下密码位放网关发的一次性令牌，命令行里不再出现真实密码。
/// 这一步不需要管理员权限，普通玩家双击就能启动。
/// </summary>
public static class GameLauncher
{
    // DNF.exe (ACT4 1.0.44.1) 单开互斥检查点：
    //   0x009D6BE7: 3D B7 00 00 00   cmp eax, 0xB7 (ERROR_ALREADY_EXISTS)
    //   0x009D6BEC: 75 23            jne +0x23
    // 仅在 [debug] allow_multi = 1 时于进程挂起态将 75 改为 EB（无条件跳过单开退出），不改动磁盘文件。
    private static readonly IntPtr MultiCheckAddr = new IntPtr(0x009D6BE7);
    private static readonly IntPtr MultiJumpAddr = new IntPtr(0x009D6BEC);
    private static readonly byte[] MultiExpected = { 0x3D, 0xB7, 0x00, 0x00, 0x00, 0x75, 0x23 };

    public static Process Start(string gameHost, int gamePort, string account, string token, bool allowMulti = false)
    {
        if (!IPAddress.TryParse(gameHost, out var address) ||
            address.AddressFamily != System.Net.Sockets.AddressFamily.InterNetwork)
            throw new InvalidDataException("游戏地址配置有误，请联系客服");
        if (gamePort is < 1 or > 65535)
            throw new InvalidDataException("游戏端口超出范围：" + gamePort);
        if (!File.Exists(AppPaths.GameExe))
            throw new FileNotFoundException("找不到 DNF.exe，请把登录器放在客户端目录里", AppPaths.GameExe);

        var arguments = string.Format(CultureInfo.InvariantCulture,
            "99?{0}?{1}?{2}?{3}?0?0?0?0?0?0?0?0",
            gameHost, gamePort, Escape(account), Escape(token));

        Process game;
        if (allowMulti)
        {
            game = StartSuspendedAndPatchMulti(AppPaths.GameExe, arguments, AppPaths.ClientDir);
        }
        else
        {
            var info = new ProcessStartInfo(AppPaths.GameExe)
            {
                WorkingDirectory = AppPaths.ClientDir,
                UseShellExecute = false,
                CreateNoWindow = true,
                Arguments = arguments,
            };
            game = Process.Start(info) ?? throw new InvalidOperationException("DNF.exe 启动失败");
        }

        // 进游戏那句声明弹窗在加壳的 dinput8.dll 里，运行期才解出原文，每次启动都要补一次。
        // 从外面改内存会被反作弊拒绝，所以往游戏进程里注入 a4popuppatch.dll 自己改。
        // 后台线程做，不挡着登录器；失败只写日志，不影响进游戏。
        GameInjector.Schedule(game);
        return game;
    }

    private static Process StartSuspendedAndPatchMulti(string exePath, string arguments, string workDir)
    {
        var si = new STARTUPINFO { cb = Marshal.SizeOf<STARTUPINFO>() };
        var cmdLine = "\"" + exePath + "\" " + arguments;
        const uint CREATE_SUSPENDED = 0x00000004;

        if (!CreateProcessW(exePath, cmdLine, IntPtr.Zero, IntPtr.Zero, false,
                CREATE_SUSPENDED, IntPtr.Zero, workDir, ref si, out var pi))
        {
            throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error(), "DNF.exe 启动失败（CreateProcessW）");
        }

        try
        {
            var buf = new byte[MultiExpected.Length];
            if (ReadProcessMemory(pi.hProcess, MultiCheckAddr, buf, buf.Length, out var read) &&
                read == buf.Length &&
                BytesMatch(buf, MultiExpected))
            {
                const uint PAGE_EXECUTE_READWRITE = 0x40;
                if (VirtualProtectEx(pi.hProcess, MultiJumpAddr, (UIntPtr)1, PAGE_EXECUTE_READWRITE, out var oldProt))
                {
                    var patch = new byte[] { 0xEB };
                    if (WriteProcessMemory(pi.hProcess, MultiJumpAddr, patch, 1, out _))
                    {
                        FlushInstructionCache(pi.hProcess, MultiJumpAddr, (UIntPtr)1);
                        Log.Write("调试多开：已内存放行 DNF.exe 单开互斥检查 (0x009D6BEC: 75 -> EB)");
                    }
                    VirtualProtectEx(pi.hProcess, MultiJumpAddr, (UIntPtr)1, oldProt, out _);
                }
            }
            else
            {
                Log.Write("调试多开：未匹配到预期单开特征字节，按原样继续启动 DNF.exe");
            }

            ResumeThread(pi.hThread);
            return Process.GetProcessById((int)pi.dwProcessId);
        }
        finally
        {
            CloseHandle(pi.hThread);
            CloseHandle(pi.hProcess);
        }
    }

    private static bool BytesMatch(byte[] actual, byte[] expected)
    {
        for (int i = 0; i < expected.Length; i++)
        {
            if (actual[i] != expected[i]) return false;
        }
        return true;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct STARTUPINFO
    {
        public int cb;
        public string? lpReserved;
        public string? lpDesktop;
        public string? lpTitle;
        public int dwX, dwY, dwXSize, dwYSize, dwXCountChars, dwYCountChars, dwFillAttribute, dwFlags;
        public short wShowWindow, cbReserved2;
        public IntPtr lpReserved2, hStdInput, hStdOutput, hStdError;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct PROCESS_INFORMATION
    {
        public IntPtr hProcess;
        public IntPtr hThread;
        public uint dwProcessId;
        public uint dwThreadId;
    }

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern bool CreateProcessW(
        string? lpApplicationName,
        string lpCommandLine,
        IntPtr lpProcessAttributes,
        IntPtr lpThreadAttributes,
        bool bInheritHandles,
        uint dwCreationFlags,
        IntPtr lpEnvironment,
        string lpCurrentDirectory,
        ref STARTUPINFO lpStartupInfo,
        out PROCESS_INFORMATION lpProcessInformation);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool ReadProcessMemory(IntPtr hProcess, IntPtr lpBaseAddress, byte[] lpBuffer, int dwSize, out int lpNumberOfBytesRead);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool WriteProcessMemory(IntPtr hProcess, IntPtr lpBaseAddress, byte[] lpBuffer, int nSize, out int lpNumberOfBytesWritten);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool VirtualProtectEx(IntPtr hProcess, IntPtr lpAddress, UIntPtr dwSize, uint flNewProtect, out uint lpflOldProtect);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool FlushInstructionCache(IntPtr hProcess, IntPtr lpBaseAddress, UIntPtr dwSize);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern uint ResumeThread(IntPtr hThread);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool CloseHandle(IntPtr hObject);

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
