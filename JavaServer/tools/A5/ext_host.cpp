/* ============================================================
 * ACT5 客户端扩展宿主 —— DLL 扩展支持（挂在 A5.dll 的加载链上）
 * ============================================================
 * 为什么挂在这里：
 *   DNF.exe 按【名字】静态导入 DumpReport.dll 的 InitStackTraceLibrary /
 *   RegisterExceptionCallback，所以同目录的 DumpReport.dll 必然在进程启动
 *   时被加载。仓库里那个 DumpReport.dll 是我们自己的小壳
 *   （dumpreport_shim.cpp），它只做一件事：A5Anchor() 把真正的 A5.dll
 *   拉进进程。
 *
 *   本文件把"拉 A5.dll"这一步扩展成"拉 A5.dll + 读 dll_ext.ini 加载插件"
 *   —— 扩展宿主因此复用了客户端**本来就在走**的那条加载链：
 *
 *     DNF.exe --[按名字导入]--> DumpReport.dll（本宿主，约 4 KB）
 *                                  ├─ A5.dll      （★ 一个字节都不动）
 *                                  └─ dll_ext.ini → 逐个 LoadLibrary 插件
 *
 *   A5.dll 本体不参与本次改动、也不重新编译：它的源码是 MSVC 专有的
 *   （__try + 依赖 MSVC 栈帧偏移的内联汇编），本机只有 LLVM-MinGW，
 *   硬编反而是最大的"有损"。
 *
 * 相对 60US 那套（ijl15 代理）刻意做的取舍：
 *   - 不快照、不建桩、不碰别人内存，只 LoadLibrary。ACT5 启动器零注入，
 *     没有 60US 那套"焊点桩页"布局敏感性，因此不需要钉基址。
 *   - 插件加载失败只写日志，绝不阻断游戏（照 60US 的降级口径）。
 *   - ini 缺失/不可读 = 零挂载（等价于改动前的行为）。
 *
 * 硬约束（照 tools\A5 既有口径）：
 *   1) 导入表只许 kernel32 + A5.dll —— 不用 CRT 的 printf/string 家族；
 *   2) DumpReport.dll 的三个导出与序号必须与改动前逐字一致（.def 管着）；
 *   3) 必须继续调用 A5Anchor() —— 忘了它 A5.dll 就不进进程（杀怪不上报）。
 *
 * 编译：tools\A5\build-clang.cmd（链接进 DumpReport.dll）。
 * 部署：DumpReport.dll 放客户端目录（覆盖原壳，部署前自动备份）。
 * 回退：把备份的 DumpReport.dll 拷回去（或 vendor\DumpReport.dll）。
 * ============================================================ */
#include <windows.h>

extern "C" __declspec(dllimport) void A5Anchor(void);

#define EXT_INI_NAME   "dll_ext.ini"
#define EXT_LOG_NAME   "dll_ext.log"
#define START_DELAY_MS 1000        /* 避开 loader lock：延迟线程里再 LoadLibrary */
#define MAX_PLUGINS    64
#define MAX_LINE       700

static HMODULE g_self = NULL;
static volatile LONG g_started = 0;

static char g_iniPath[MAX_PATH];
static char g_logPath[MAX_PATH];

/* ============================================================
 * 最小字符串/输出工具 —— 全走 kernel32，不引 CRT 格式化
 * ============================================================ */
static unsigned MyStrLen(const char *s)
{
    unsigned n = 0;
    while (s[n]) n++;
    return n;
}

static char *MyStrChr(const char *s, char c)
{
    while (*s) {
        if (*s == c) return (char *)s;
        s++;
    }
    return NULL;
}

static char *MyStrRChr(const char *s, char c)
{
    const char *last = NULL;
    while (*s) {
        if (*s == c) last = s;
        s++;
    }
    return (char *)last;
}

static int MyStrEqI(const char *a, const char *b)
{
    while (*a && *b) {
        char x = *a, y = *b;
        if (x >= 'A' && x <= 'Z') x = (char)(x + 32);
        if (y >= 'A' && y <= 'Z') y = (char)(y + 32);
        if (x != y) return 0;
        a++; b++;
    }
    return *a == 0 && *b == 0;
}

static void MyMemCpy(void *dst, const void *src, unsigned n)
{
    char *d = (char *)dst;
    const char *s = (const char *)src;
    while (n--) *d++ = *s++;
}

/* ============================================================
 * 日志：自建行缓冲 + kernel32 写盘
 * ============================================================ */
static char g_line[MAX_LINE];
static unsigned g_len;

static void LReset(void)
{
    g_len = 0;
    g_line[0] = 0;
}

static void LPush(const char *s)
{
    while (*s && g_len + 1 < MAX_LINE) g_line[g_len++] = *s++;
    g_line[g_len] = 0;
}

static void LPushU(unsigned long v)
{
    char tmp[12];
    int i = 0;
    if (v == 0) { LPush("0"); return; }
    while (v > 0 && i < 11) {
        tmp[i++] = (char)('0' + (v % 10));
        v /= 10;
    }
    while (i > 0) {
        if (g_len + 1 >= MAX_LINE) break;
        g_line[g_len++] = tmp[--i];
    }
    g_line[g_len] = 0;
}

static void LStamp(void)
{
    SYSTEMTIME st;
    GetLocalTime(&st);
    LPush("[");
    if (st.wHour < 10) LPush("0");
    LPushU(st.wHour);
    LPush(":");
    if (st.wMinute < 10) LPush("0");
    LPushU(st.wMinute);
    LPush(":");
    if (st.wSecond < 10) LPush("0");
    LPushU(st.wSecond);
    LPush("] ");
}

static void LFlush(void)
{
    HANDLE h;
    DWORD written = 0;
    if (!g_logPath[0]) return;
    g_line[g_len++] = '\r';
    g_line[g_len++] = '\n';
    g_line[g_len]   = 0;
    h = CreateFileA(g_logPath, FILE_APPEND_DATA,
                    FILE_SHARE_READ | FILE_SHARE_WRITE, NULL,
                    OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (h != INVALID_HANDLE_VALUE) {
        WriteFile(h, g_line, g_len, &written, NULL);
        CloseHandle(h);
    }
}

static void Log(const char *msg)
{
    LReset();
    LStamp();
    LPush(msg);
    LFlush();
}

/* <本模块目录>\<name> */
static BOOL SelfDirFile(char *out, unsigned cch, const char *name)
{
    DWORD n = GetModuleFileNameA(g_self, out, cch);
    char *slash;
    unsigned used, room, k;

    if (n == 0 || n >= cch) return FALSE;
    slash = MyStrRChr(out, '\\');
    if (!slash) return FALSE;
    slash[1] = 0;
    used = (unsigned)(slash - out) + 1;
    if (used >= cch) return FALSE;
    room = cch - used;
    for (k = 0; name[k] && k + 1 < room; k++) out[used + k] = name[k];
    out[used + k] = 0;
    return TRUE;
}

/* ============================================================
 * dll_ext.ini 解析
 * ============================================================ */
static BOOL LooksUtf8(const char *s)
{
    unsigned i = 0;
    while (s[i]) {
        unsigned char c = (unsigned char)s[i];
        if (c < 0x80) { i++; continue; }
        if ((c & 0xE0) == 0xC0) {
            if (!s[i+1] || ((unsigned char)s[i+1] & 0xC0) != 0x80) return FALSE;
            i += 2; continue;
        }
        if ((c & 0xF0) == 0xE0) {
            if (!s[i+1] || !s[i+2]) return FALSE;
            if (((unsigned char)s[i+1] & 0xC0) != 0x80) return FALSE;
            if (((unsigned char)s[i+2] & 0xC0) != 0x80) return FALSE;
            i += 3; continue;
        }
        return FALSE;
    }
    return TRUE;
}

static void Trim(char *s)
{
    char *p = s;
    unsigned len;
    while (*p == ' ' || *p == '\t') p++;
    if (p != s) {
        unsigned n = MyStrLen(p);
        MyMemCpy(s, p, n + 1);
    }
    len = MyStrLen(s);
    while (len > 0 && (s[len-1] == ' ' || s[len-1] == '\t' || s[len-1] == '\r'))
        s[--len] = 0;
}

static HMODULE LoadOnePlugin(const char *pathBytes, BOOL utf8)
{
    HMODULE m = NULL;
    if (utf8) {
        int wlen = MultiByteToWideChar(CP_UTF8, 0, pathBytes, -1, NULL, 0);
        if (wlen > 0 && wlen < MAX_PATH) {
            wchar_t wpath[MAX_PATH];
            if (MultiByteToWideChar(CP_UTF8, 0, pathBytes, -1, wpath, wlen) > 0)
                m = LoadLibraryW(wpath);
        }
    }
    if (!m) m = LoadLibraryA(pathBytes);
    return m;
}

static void LoadPlugins(void)
{
    HANDLE h;
    DWORD size, got = 0;
    char *buf;
    char *line;
    BOOL inPlugins = FALSE;
    BOOL utf8 = TRUE;
    int loaded = 0, tried = 0;

    h = CreateFileA(g_iniPath, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE,
                    NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (h == INVALID_HANDLE_VALUE) {
        Log("INFO 没有 dll_ext.ini —— 零挂载（等价于改动前）");
        return;
    }

    size = GetFileSize(h, NULL);
    if (size == INVALID_FILE_SIZE || size == 0 || size > (1u << 20)) {
        CloseHandle(h);
        Log("WARN dll_ext.ini 大小异常 —— 当无 ini 处理，零挂载");
        return;
    }

    buf = (char *)HeapAlloc(GetProcessHeap(), 0, size + 2);
    if (!buf) { CloseHandle(h); return; }
    if (!ReadFile(h, buf, size, &got, NULL)) got = 0;
    CloseHandle(h);
    buf[got] = 0;

    if (got >= 2 &&
        (((unsigned char)buf[0] == 0xFF && (unsigned char)buf[1] == 0xFE) ||
         ((unsigned char)buf[0] == 0xFE && (unsigned char)buf[1] == 0xFF))) {
        HeapFree(GetProcessHeap(), 0, buf);
        Log("WARN dll_ext.ini 是 UTF-16 —— 不支持，零挂载（请另存为 UTF-8 或 ANSI）");
        return;
    }

    if (got >= 3 && (unsigned char)buf[0] == 0xEF &&
        (unsigned char)buf[1] == 0xBB && (unsigned char)buf[2] == 0xBF) {
        MyMemCpy(buf, buf + 3, got - 3 + 1);
    }

    utf8 = LooksUtf8(buf);

    line = buf;
    while (line && *line) {
        char *nl = MyStrChr(line, '\n');
        if (nl) *nl = 0;
        Trim(line);

        if (*line == '[') {
            inPlugins = MyStrEqI(line, "[plugins]");
        } else if (inPlugins && *line && *line != ';' && *line != '#') {
            char *eq = MyStrChr(line, '=');
            if (eq) {
                char *name = eq + 1;
                Trim(name);
                if (*name) {
                    char full[MAX_PATH];
                    full[0] = 0;
                    /* 带路径分隔符的照原样用；否则按同目录解析 */
                    if (MyStrChr(name, '\\') || MyStrChr(name, '/')) {
                        unsigned n = MyStrLen(name);
                        if (n < MAX_PATH) MyMemCpy(full, name, n + 1);
                    } else {
                        SelfDirFile(full, MAX_PATH, name);
                    }
                    if (full[0] && tried < MAX_PLUGINS) {
                        HMODULE m = LoadOnePlugin(full, utf8);
                        tried++;
                        if (m) {
                            loaded++;
                            LReset(); LStamp();
                            LPush("OK   插件[");
                            LPushU((unsigned long)tried);
                            LPush("] 已加载: ");
                            LPush(name);
                            LFlush();
                        } else {
                            LReset(); LStamp();
                            LPush("FAIL 插件[");
                            LPushU((unsigned long)tried);
                            LPush("] 加载失败 gle=");
                            LPushU((unsigned long)GetLastError());
                            LPush(": ");
                            LPush(name);
                            LFlush();
                        }
                    }
                }
            }
        }
        line = nl ? nl + 1 : NULL;
    }

    if (tried == 0) {
        Log("INFO [plugins] 段里没有启用的条目 —— 零挂载");
    } else {
        LReset(); LStamp();
        LPush("INFO 插件加载完毕：成功 ");
        LPushU((unsigned long)loaded);
        LPush(" / 尝试 ");
        LPushU((unsigned long)tried);
        LFlush();
    }

    HeapFree(GetProcessHeap(), 0, buf);
}

/* ============================================================
 * 宿主线程：延迟 1 秒（loader lock 已放掉）再干活。
 *   A5.dll 的加载留在 DllMain 里同步做 —— 它要尽早打补丁，
 *   插件则可以晚一点（照 60US 的"失败不阻断"口径）。
 * ============================================================ */
static DWORD WINAPI ExtHostWorker(LPVOID param)
{
    (void)param;
    Sleep(START_DELAY_MS);

    SelfDirFile(g_iniPath, MAX_PATH, EXT_INI_NAME);
    SelfDirFile(g_logPath, MAX_PATH, EXT_LOG_NAME);

    Log("===== ACT5 DLL 扩展宿主启动（挂在 A5 加载链上）=====");
    Log("INFO A5.dll 已由同链加载；现在读扩展清单");
    LoadPlugins();
    Log("===== 宿主收工（游戏继续运行）=====");
    return 0;
}

/* 由 DumpReport 的 DllMain 调用：一次性的宿主启动 */
extern "C" void ExtHost_Install(void)
{
    if (InterlockedCompareExchange(&g_started, 1, 0) != 0)
        return;

    g_self = GetModuleHandleA("DumpReport.dll");
    if (!g_self) g_self = GetModuleHandleA(NULL);

    /* 日志路径现在就定下来（线程里再取也行，但这时一定成功） */
    SelfDirFile(g_logPath, MAX_PATH, EXT_LOG_NAME);

    {
        HANDLE h = CreateThread(NULL, 0, ExtHostWorker, NULL, 0, NULL);
        if (h) CloseHandle(h);
    }
}

/* 供冒烟测试查询宿主是否已启动 */
extern "C" int ExtHost_Started(void)
{
    return (int)g_started;
}

/* A5.dll 的锚点在这里被引用一次，确保链接器把依赖带进导入表 */
extern "C" void ExtHost_KeepA5Anchor(void)
{
    A5Anchor();
}
