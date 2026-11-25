/*
 * dinput8.dll 转发代理 —— 进游戏不再弹 QoL 的声明公告窗（客户端窗口号 0x25）。
 *
 *   dinput8.dll   本文件：只有导出转发 + 一个补丁线程
 *   act4qol.dll   原版 ACT4 QoL（Themida 壳），由原 dinput8.dll 改名而来，文件字节一个不动
 *
 * 和 dinput8_proxy.cpp 的区别：那一版是 naked 桩 + 后台线程里 LoadLibrary，QoL 的 DllMain
 * 比原来晚跑（DNF 入口之后），早期钩子有赶不上的风险。这一版 20 个导出全是 PE 转发
 * （act4qol.xxx），DNF.exe 导入 DirectInput8Create 时系统加载器就顺着转发把 act4qol.dll
 * 装进来并初始化，时机和原来直接加载 dinput8.dll 一样。d3d9.dll 按名字
 * GetProcAddress(dinput8, "qol_ui_get") 拿到的也直接是 act4qol 里的真函数。
 * 原版另有一个 "DirectInput8Create@20"，DNF.exe / d3d8 / d3d9 都不用，不转发。
 *
 * 补丁（只改 act4qol 的进程内镜像，壳解开后才有原文）：
 *   1) 找 GBK「本软件完全免费」，首字节写 0（显示函数 0x1041 看到 0 直接返回）
 *   2) 找唯一引用 push <文本> ; call <显示函数>，call 改 5×nop（add esp,4 保留，栈平）
 *   RVA 不写死（本版文本 0x466F2、call 0xEADD），按原文和绝对地址搜。
 *
 * 日志：DNF.exe 同目录 dinput8_proxy.log
 */

#include <windows.h>
#include <psapi.h>

#pragma comment(lib, "psapi.lib")
#pragma comment(lib, "user32.lib")

#pragma comment(linker, "/EXPORT:DirectInput8Create=act4qol.DirectInput8Create")
#pragma comment(linker, "/EXPORT:act4_font_init=act4qol.act4_font_init")
#pragma comment(linker, "/EXPORT:qol_cat_copy=act4qol.qol_cat_copy")
#pragma comment(linker, "/EXPORT:qol_cat_ready=act4qol.qol_cat_ready")
#pragma comment(linker, "/EXPORT:qol_cat_send=act4qol.qol_cat_send")
#pragma comment(linker, "/EXPORT:qol_cat_version=act4qol.qol_cat_version")
#pragma comment(linker, "/EXPORT:qol_render_connected=act4qol.qol_render_connected")
#pragma comment(linker, "/EXPORT:qol_ui_action=act4qol.qol_ui_action")
#pragma comment(linker, "/EXPORT:qol_ui_get=act4qol.qol_ui_get")
#pragma comment(linker, "/EXPORT:qol_ui_grade_name=act4qol.qol_ui_grade_name")
#pragma comment(linker, "/EXPORT:qol_ui_hwnd=act4qol.qol_ui_hwnd")
#pragma comment(linker, "/EXPORT:qol_ui_log=act4qol.qol_ui_log")
#pragma comment(linker, "/EXPORT:qol_ui_set=act4qol.qol_ui_set")
#pragma comment(linker, "/EXPORT:qol_ui_set_turbo=act4qol.qol_ui_set_turbo")
#pragma comment(linker, "/EXPORT:qol_ui_set_visible=act4qol.qol_ui_set_visible")
#pragma comment(linker, "/EXPORT:qol_ui_turbo=act4qol.qol_ui_turbo")
#pragma comment(linker, "/EXPORT:qol_ui_visible=act4qol.qol_ui_visible")
#pragma comment(linker, "/EXPORT:ttf_cell=act4qol.ttf_cell")
#pragma comment(linker, "/EXPORT:ttf_glyph=act4qol.ttf_glyph")
#pragma comment(linker, "/EXPORT:ttf_ready=act4qol.ttf_ready")

/* GBK: 本软件完全免费 */
static const unsigned char kNeedle[] = {
    0xB1, 0xBE, 0xC8, 0xED, 0xBC, 0xFE, 0xCD, 0xEA, 0xC8, 0xAB, 0xC3, 0xE2, 0xB7, 0xD1
};
#define NEEDLE_LEN ((int)sizeof(kNeedle))

static void Note(const char* msg)
{
    char path[MAX_PATH];
    DWORD len = GetModuleFileNameA(NULL, path, MAX_PATH);
    while (len > 0 && path[len - 1] != '\\')
    {
        len--;
    }
    lstrcpynA(path + len, "dinput8_proxy.log", MAX_PATH - len);

    HANDLE f = CreateFileA(path, FILE_APPEND_DATA, FILE_SHARE_READ | FILE_SHARE_WRITE,
                           NULL, OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    DWORD n = 0;
    if (f == INVALID_HANDLE_VALUE)
    {
        return;
    }
    WriteFile(f, msg, lstrlenA(msg), &n, NULL);
    WriteFile(f, "\r\n", 2, &n, NULL);
    CloseHandle(f);
}

static int Same(const BYTE* a, const BYTE* b, int n)
{
    int i;
    for (i = 0; i < n; i++)
    {
        if (a[i] != b[i])
        {
            return 0;
        }
    }
    return 1;
}

/* 1 = 这轮结束（成功或失败），0 = 壳还没解出来，继续等 */
static int Patch(HMODULE mod)
{
    MODULEINFO info;
    SIZE_T i, off = 0;
    int found = 0;
    BYTE* base;
    SIZE_T size;
    BYTE* text;
    UINT_PTR va;
    char line[200];
    DWORD old = 0;

    ZeroMemory(&info, sizeof(info));
    if (!GetModuleInformation(GetCurrentProcess(), mod, &info, sizeof(info)))
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
        MEMORY_BASIC_INFORMATION mbi;
        /* 跳过没提交/不可读的页（.themida 节在解壳过程中可能还没映射好） */
        if ((i & 0xFFF) == 0)
        {
            if (VirtualQuery(base + i, &mbi, sizeof(mbi)) == 0 || mbi.State != MEM_COMMIT ||
                (mbi.Protect & (PAGE_NOACCESS | PAGE_GUARD)) != 0)
            {
                i += 0xFFF;
                continue;
            }
        }
        if (base[i] == kNeedle[0] && Same(base + i, kNeedle, NEEDLE_LEN))
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
    Note(line);

    if (VirtualProtect(text, 1, PAGE_EXECUTE_READWRITE, &old))
    {
        *text = 0;
        VirtualProtect(text, 1, old, &old);
        Note("文本首字节已写 0（显示函数会直接返回）");
    }

    for (i = 0; i + 10 <= size; i++)
    {
        BYTE* p;
        MEMORY_BASIC_INFORMATION mbi;
        if ((i & 0xFFF) == 0)
        {
            if (VirtualQuery(base + i, &mbi, sizeof(mbi)) == 0 || mbi.State != MEM_COMMIT ||
                (mbi.Protect & (PAGE_NOACCESS | PAGE_GUARD)) != 0)
            {
                i += 0xFFF;
                continue;
            }
        }
        if (base[i] != 0x68 || *(DWORD*)(base + i + 1) != (DWORD)va)
        {
            continue;
        }
        p = base + i + 5;
        if (p[0] != 0xE8)
        {
            continue;
        }
        if (!VirtualProtect(p, 5, PAGE_EXECUTE_READWRITE, &old))
        {
            Note("push 找到了，但 VirtualProtect 失败，call 没改成 nop");
            return 1;
        }
        memset(p, 0x90, 5);
        VirtualProtect(p, 5, old, &old);
        FlushInstructionCache(GetCurrentProcess(), p, 5);
        wsprintfA(line, "已把 push 后的 call 改成 5×nop：RVA=0x%X（公告窗不会再创建）",
                  (unsigned int)(i + 5));
        Note(line);
        return 1;
    }

    Note("文本已清零，但没找到 push+call（QoL 版本不同？）");
    return 1;
}

static DWORD WINAPI PatchThread(LPVOID unused)
{
    int round;
    HMODULE qol = NULL;
    (void)unused;

    for (round = 0; round < 6000; round++)   /* 最多两分钟，每 20ms 一轮 */
    {
        if (qol == NULL)
        {
            qol = GetModuleHandleA("act4qol.dll");
        }
        if (qol != NULL && Patch(qol))
        {
            return 0;
        }
        Sleep(20);
    }
    Note(qol == NULL ? "补丁超时：act4qol.dll 没加载（文件在不在？）"
                     : "补丁超时：两分钟内没找到那句话");
    return 0;
}

BOOL APIENTRY DllMain(HMODULE module, DWORD reason, LPVOID reserved)
{
    (void)reserved;
    if (reason == DLL_PROCESS_ATTACH)
    {
        HANDLE t;
        DisableThreadLibraryCalls(module);
        /* 线程要等加载器锁释放才真正跑，那时 act4qol 已经初始化（解壳）完了 */
        t = CreateThread(NULL, 0, PatchThread, NULL, 0, NULL);
        if (t)
        {
            CloseHandle(t);
        }
    }
    return TRUE;
}
