// a4popuppatch.dll —— 由登录器在 DNF.exe 刚创建时注入。
//
// 管的是 ACT4 QoL 进游戏后弹的那个「公告窗」（客户端窗口号 0x25）：
//   1) 文字换成 popup_text.txt 里的内容（窗口照弹，字是你的）
//   2) 可选：N 秒后自动把窗口关掉（调客户端自己的窗口函数再点一次 = 收起）
//   3) 也可以选择干脆不弹（文本首字节 0 + 把那条 call 改 nop）
//
// 位置（Themida 壳，运行期才解出来；RVA 不写死，按 GBK 原文找串再找引用）：
//     push <文本>            ; 68 xx xx xx xx   @ RVA 0xEAD8
//     call 0x1041            ; e8 5f 25 ff ff   @ RVA 0xEADD   ← 换成 call 我们的钩子
//     add esp, 4
//   显示函数 0x1041 内部：push 文本 / push 0x25 / call 客户端 sub_86F320（thiscall，ecx=窗口管理器 [0xD7F2A4]）
//
// popup_text.txt（放 DNF.exe 同目录，UTF-8/GBK 都认）：
//     off                      -> 完全不弹
//     after=8                  -> 第一行写这个 = 8 秒后自动关；底下是公告正文
//     （没有 after 行）        -> 只换文字，不自动关
//     文件不存在               -> 默认文字 + 不自动关
// 正文上限 273 字节 GBK（约 91 个汉字），超了截断。
//
// 只写进程内存，不动文件字节（壳有完整性校验）。关游戏重开壳会重新解出原文，所以每次启动都注一次。

#include <windows.h>
#include <psapi.h>

#pragma comment(lib, "psapi.lib")
#pragma comment(lib, "user32.lib")

// GBK: 本软件完全免费
static const unsigned char kNeedle[] = {
    0xB1, 0xBE, 0xC8, 0xED, 0xBC, 0xFE, 0xCD, 0xEA, 0xC8, 0xAB, 0xC3, 0xE2, 0xB7, 0xD1
};
#define NEEDLE_LEN ((int)sizeof(kNeedle))
#define TEXT_CAP 273
#define TEXT_DEFAULT "单机版无需充值。GM 后台：http://127.0.0.1:17001"

/* 客户端（DNF.exe，无壳、无 ASLR，基址 0x400000）里的两个固定地址 */
#define CLIENT_OPEN_WINDOW 0x0086F320u   /* sub_86F320(this, id, text, flags)：同一个 id 再调一次就是收起 */
#define CLIENT_WINDOW_MGR  0x00D7F2A4u   /* 窗口管理器全局指针 */

#define WINDOW_ID_NOTICE 0x25

static int g_closeAfter = 0;             /* 秒；0 = 不自动关 */
static void* g_origDisplay = NULL;       /* dinput8 里原来的显示函数（call 的目标） */
static const char* g_noticeText = NULL;  /* 换上去的那段字（关闭时要原样传回去） */

static void LogLine(const char* text);

/* 客户端 0x86F320：thiscall，ecx = this（窗口管理器） */
typedef int(__thiscall* PFN_WINDOW)(void* self, int id, const char* text, int flags);

/* 后台线程：等 N 秒，再用同一个 id 调一次客户端窗口函数，把公告窗收起来 */
static DWORD WINAPI CloseLater(LPVOID unused)
{
    void* mgr;
    (void)unused;

    Sleep((DWORD)g_closeAfter * 1000);

    mgr = *(void**)CLIENT_WINDOW_MGR;
    if (mgr == NULL)
    {
        LogLine("定时关闭：窗口管理器还没建好（[0xD7F2A4]=0），跳过");
        return 0;
    }
    __try
    {
        PFN_WINDOW open = (PFN_WINDOW)CLIENT_OPEN_WINDOW;
        open(mgr, WINDOW_ID_NOTICE, g_noticeText, 0);
        LogLine("定时关闭：已调客户端窗口函数收起 0x25 号公告窗");
    }
    __except (EXCEPTION_EXECUTE_HANDLER)
    {
        LogLine("定时关闭：调窗口函数时抛异常，已吞掉（不影响游戏）");
    }
    return 0;
}

static void StartCloseTimer(void)
{
    HANDLE t = CreateThread(NULL, 0, CloseLater, NULL, 0, NULL);
    if (t)
    {
        CloseHandle(t);
    }
}

/* 替换掉的 call 会跳到这里：先照常弹（用原显示函数），再排个定时关闭 */
static void __cdecl OurDisplay(const char* text)
{
    typedef void(__cdecl * PFN_DISPLAY)(const char*);
    PFN_DISPLAY orig = (PFN_DISPLAY)g_origDisplay;

    if (orig != NULL)
    {
        orig(text);
    }
    if (g_closeAfter > 0)
    {
        StartCloseTimer();
    }
}

static void LogLine(const char* text)
{
    char path[MAX_PATH];
    DWORD len = GetModuleFileNameA(NULL, path, MAX_PATH);
    while (len > 0 && path[len - 1] != '\\')
    {
        len--;
    }
    lstrcpynA(path + len, "popup_patch.log", MAX_PATH - len);

    HANDLE file = CreateFileA(path, FILE_APPEND_DATA, FILE_SHARE_READ | FILE_SHARE_WRITE,
                              NULL, OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (file == INVALID_HANDLE_VALUE)
    {
        return;
    }
    DWORD written = 0;
    WriteFile(file, text, (DWORD)lstrlenA(text), &written, NULL);
    WriteFile(file, "\r\n", 2, &written, NULL);
    CloseHandle(file);
}

static void ExeDirFile(const char* name, char* out, int cap)
{
    char path[MAX_PATH];
    DWORD len = GetModuleFileNameA(NULL, path, MAX_PATH);
    while (len > 0 && path[len - 1] != '\\')
    {
        len--;
    }
    lstrcpynA(path + len, name, MAX_PATH - len);
    lstrcpynA(out, path, cap);
}

/* 把 UTF-8 或 GBK 的一段字节转成 GBK 放进 out。返回字节数（<= cap） */
static int ToGbk(const char* src, int len, char* out, int cap)
{
    int i, ascii = 1, n = 0;
    WCHAR* wide;
    int wlen;

    if (len <= 0)
    {
        return 0;
    }
    for (i = 0; i < len; i++)
    {
        if ((unsigned char)src[i] >= 0x80)
        {
            ascii = 0;
            break;
        }
    }
    if (ascii)
    {
        n = len > cap ? cap : len;
        memcpy(out, src, n);
        return n;
    }

    /* 先试 UTF-8（非法序列就退回 GBK） */
    wlen = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, src, len, NULL, 0);
    if (wlen <= 0)
    {
        n = len > cap ? cap : len;
        memcpy(out, src, n);
        return n;
    }
    wide = (WCHAR*)HeapAlloc(GetProcessHeap(), 0, (wlen + 1) * sizeof(WCHAR));
    if (wide == NULL)
    {
        return 0;
    }
    MultiByteToWideChar(CP_UTF8, 0, src, len, wide, wlen);
    n = WideCharToMultiByte(936, 0, wide, wlen, out, cap, NULL, NULL);
    HeapFree(GetProcessHeap(), 0, wide);
    return n > 0 ? n : 0;
}

/* 读 popup_text.txt。
   返回：>0 正文 GBK 字节数（写在 out）；0 没文件/空；-1 明确 off
   顺带解析第一行 after=<秒> */
static int LoadConfig(char* out, int cap)
{
    char path[MAX_PATH];
    HANDLE f;
    DWORD size, got = 0;
    char* raw;
    char* body;
    int len;

    g_closeAfter = 0;

    ExeDirFile("popup_text.txt", path, MAX_PATH);
    f = CreateFileA(path, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING,
                    FILE_ATTRIBUTE_NORMAL, NULL);
    if (f == INVALID_HANDLE_VALUE)
    {
        return 0;
    }
    size = GetFileSize(f, NULL);
    if (size == INVALID_FILE_SIZE || size == 0 || size > 65536)
    {
        CloseHandle(f);
        return 0;
    }
    raw = (char*)HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY, size + 1);
    if (raw == NULL)
    {
        CloseHandle(f);
        return 0;
    }
    ReadFile(f, raw, size, &got, NULL);
    CloseHandle(f);
    raw[got] = 0;
    size = got;

    if (size >= 3 && (unsigned char)raw[0] == 0xEF && (unsigned char)raw[1] == 0xBB &&
        (unsigned char)raw[2] == 0xBF)
    {
        memmove(raw, raw + 3, size - 3);
        size -= 3;
        raw[size] = 0;
    }

    body = raw;

    /* 第一行：after=N 或者 off */
    {
        char* eol = body;
        int firstLen;
        while (*eol && *eol != '\r' && *eol != '\n')
        {
            eol++;
        }
        firstLen = (int)(eol - body);
        if (firstLen == 3 && (body[0] == 'o' || body[0] == 'O') &&
            (body[1] == 'f' || body[1] == 'F') && (body[2] == 'f' || body[2] == 'F'))
        {
            HeapFree(GetProcessHeap(), 0, raw);
            return -1;
        }
        if (firstLen > 6 && body[0] == 'a' && body[1] == 'f' && body[2] == 't' && body[3] == 'e' &&
            body[4] == 'r' && body[5] == '=')
        {
            int v = 0, k = 6;
            while (k < firstLen && body[k] >= '0' && body[k] <= '9')
            {
                v = v * 10 + (body[k] - '0');
                k++;
            }
            if (v >= 0 && v <= 3600)
            {
                g_closeAfter = v;
            }
            /* 正文从下一行开始 */
            body = eol;
            while (*body == '\r' || *body == '\n')
            {
                body++;
            }
        }
    }

    len = (int)lstrlenA(body);
    while (len > 0 && (body[len - 1] == '\r' || body[len - 1] == '\n' || body[len - 1] == ' '))
    {
        body[--len] = 0;
    }
    if (len <= 0)
    {
        HeapFree(GetProcessHeap(), 0, raw);
        return 0;
    }
    {
        int n = ToGbk(body, len, out, cap);
        HeapFree(GetProcessHeap(), 0, raw);
        return n > 0 ? n : 0;
    }
}

/* 1 = 这轮结束（成功或失败），0 = 壳还没解出来，继续等 */
static int PatchOnce(void)
{
    HMODULE qol = GetModuleHandleA("dinput8.dll");
    MODULEINFO info;
    BYTE* base;
    SIZE_T size, i, off = 0;
    int found = 0;
    BYTE* text;
    UINT_PTR va;
    DWORD old = 0;
    char line[260];
    char custom[TEXT_CAP + 16];
    int n, modeOff;

    if (qol == NULL)
    {
        return 0;
    }
    ZeroMemory(&info, sizeof(info));
    if (!GetModuleInformation(GetCurrentProcess(), qol, &info, sizeof(info)))
    {
        return 0;
    }
    base = (BYTE*)info.lpBaseOfDll;
    size = info.SizeOfImage;
    if (base == NULL || size < 0x1000)
    {
        return 0;
    }

    for (i = 0; i + NEEDLE_LEN <= size; i++)
    {
        if (base[i] == kNeedle[0] && memcmp(base + i, kNeedle, NEEDLE_LEN) == 0)
        {
            off = i;
            found = 1;
            break;
        }
    }
    if (!found)
    {
        return 0;
    }
    text = base + off;
    va = (UINT_PTR)text;
    wsprintfA(line, "找到声明文本 RVA=0x%X 地址=0x%08X", (unsigned int)off, (unsigned int)va);
    LogLine(line);

    n = LoadConfig(custom, TEXT_CAP);
    modeOff = (n < 0);

    /* 找 push <text> 和它后面的 call */
    {
        SIZE_T callOff = 0;
        int haveCall = 0;
        for (i = 0; i + 10 <= size; i++)
        {
            BYTE* p;
            if (base[i] != 0x68)
            {
                continue;
            }
            if (*(DWORD*)(base + i + 1) != (DWORD)va)
            {
                continue;
            }
            p = base + i + 5;
            if (p[0] != 0xE8)
            {
                continue;
            }
            callOff = i + 5;
            haveCall = 1;
            break;
        }
        if (!haveCall)
        {
            LogLine("没找到 push+call（QoL 版本不同？）");
            return 1;
        }

        /* off：文本清零 + call 改 nop（窗口不创建） */
        if (modeOff)
        {
            if (VirtualProtect(text, 1, PAGE_EXECUTE_READWRITE, &old))
            {
                *text = 0;
                VirtualProtect(text, 1, old, &old);
            }
            if (VirtualProtect(base + callOff, 5, PAGE_EXECUTE_READWRITE, &old))
            {
                memset(base + callOff, 0x90, 5);
                VirtualProtect(base + callOff, 5, old, &old);
                wsprintfA(line, "popup_text.txt = off：call 已改 5×nop（RVA 0x%X）",
                          (unsigned int)callOff);
                LogLine(line);
                return 1;
            }
            LogLine("off：VirtualProtect 失败");
            return 1;
        }

        /* text：先换字，再把 call 换成我们的钩子（这样才有机会定时关） */
        if (n == 0)
        {
            n = (int)lstrlenA(TEXT_DEFAULT);
            memcpy(custom, TEXT_DEFAULT, n);
            custom[n] = 0;
            LogLine("没有 popup_text.txt，用默认文字");
        }
        else
        {
            custom[n] = 0;
        }

        if (VirtualProtect(text, TEXT_CAP + 1, PAGE_EXECUTE_READWRITE, &old))
        {
            memcpy(text, custom, n);
            text[n] = 0;
            VirtualProtect(text, TEXT_CAP + 1, old, &old);
            wsprintfA(line, "公告文字已替换（%d 字节 GBK），自动关闭=%d 秒", n, g_closeAfter);
            LogLine(line);
            wsprintfA(line, "  内容：%s", custom);
            LogLine(line);
        }
        else
        {
            LogLine("VirtualProtect 失败，文字没换成");
            return 1;
        }

        if (g_closeAfter > 0)
        {
            /* call 的目标就是原来的显示函数；我们把它重定向到自己，弹完再排定时关闭 */
            INT_PTR target = (INT_PTR)(base + callOff + 5) + *(INT*)(base + callOff + 1);
            INT_PTR rel = (INT_PTR)(&OurDisplay) - (INT_PTR)(base + callOff + 5);
            g_origDisplay = (void*)target;
            g_noticeText = (const char*)text;

            if (rel >= -0x80000000LL && rel <= 0x7FFFFFFFLL)
            {
                if (VirtualProtect(base + callOff, 5, PAGE_EXECUTE_READWRITE, &old))
                {
                    BYTE patch[5];
                    patch[0] = 0xE8;   /* call rel32 -> OurDisplay */
                    *(INT*)(patch + 1) = (INT)rel;
                    memcpy(base + callOff, patch, 5);
                    VirtualProtect(base + callOff, 5, old, &old);
                    wsprintfA(line, "call 已挂到钩子（原显示函数 0x%08X，%d 秒后收起）",
                              (unsigned int)target, g_closeAfter);
                    LogLine(line);
                }
            }
            else
            {
                LogLine("钩子距离太远，rel32 够不着；这次只换字不自动关");
            }
        }
        return 1;
    }
}

static DWORD WINAPI PatcherThread(LPVOID param)
{
    (void)param;
    for (int round = 0; round < 3600; round++)   /* 最多三分钟，每 50ms 一轮 */
    {
        if (PatchOnce())
        {
            return 0;
        }
        Sleep(50);
    }
    LogLine("补丁超时：三分钟内没找到那句话");
    return 0;
}

BOOL APIENTRY DllMain(HMODULE module, DWORD reason, LPVOID reserved)
{
    (void)reserved;
    if (reason == DLL_PROCESS_ATTACH)
    {
        DisableThreadLibraryCalls(module);
        HANDLE thread = CreateThread(NULL, 0, PatcherThread, NULL, 0, NULL);
        if (thread)
        {
            CloseHandle(thread);
        }
    }
    return TRUE;
}
