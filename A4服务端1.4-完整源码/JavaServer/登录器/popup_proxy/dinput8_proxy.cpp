/*
 * dinput8.dll 代理 —— 让「进游戏声明弹窗」在加载那一刻就不存在。
 *
 *   dinput8.dll   本文件：小代理，游戏加载的是它
 *   act4qol.dll   原版 ACT4 QoL 钩子（Themida 壳），改名放旁边，文件字节一个不动
 *
 * 20 个导出全是 naked 桩：先 Ensure() 把 act4qol.dll 加载好、取到真实函数，
 * 再 jmp 过去，参数/返回值原样透传。游戏和 d3d9.dll 察觉不到多了一层。
 *
 * 补丁（只在 act4qol 的镜像里做）：
 *   1) 找 GBK「本软件完全免费」那句声明，首字节写 0
 *   2) 它在镜像里只有一处引用：push <文本> ; call <显示函数> ; add esp,4
 *      把 call 改成 5 个 nop —— 客户端公告窗（窗口号 0x25）根本不会创建
 *
 * 本版实测（dinput8.dll 3,005,968 字节 / 镜像 0x782000）：
 *   文本 RVA 0x466F2，push RVA 0xEAD8，call RVA 0xEADD -> 0x1041
 * 不写死 RVA：按 GBK 原文找串，再按串的绝对地址找 push。
 *
 * 日志：DNF.exe 同目录 dinput8_proxy.log
 */

#include <windows.h>
#include <psapi.h>

#pragma comment(lib, "psapi.lib")
#pragma comment(lib, "user32.lib")

static const unsigned char kNeedle[] = {
    0xB1, 0xBE, 0xC8, 0xED, 0xBC, 0xFE, 0xCD, 0xEA, 0xC8, 0xAB, 0xC3, 0xE2, 0xB7, 0xD1
};
#define NEEDLE_LEN ((int)sizeof(kNeedle))
#define REAL_MODULE "act4qol.dll"

static void Ensure(void);

/* 导出名必须是 C 链接（不然 x86 会带 ?S_xxx@@YAXXZ 的 C++ 修饰名） */
extern "C" {

static FARPROC p_DirectInput8Create;
__pragma(comment(linker, "/EXPORT:DirectInput8Create=_S_DirectInput8Create"))
__declspec(naked) void __cdecl S_DirectInput8Create(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_DirectInput8Create] }
}

static FARPROC p_act4_font_init;
__pragma(comment(linker, "/EXPORT:act4_font_init=_S_act4_font_init"))
__declspec(naked) void __cdecl S_act4_font_init(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_act4_font_init] }
}

static FARPROC p_qol_cat_copy;
__pragma(comment(linker, "/EXPORT:qol_cat_copy=_S_qol_cat_copy"))
__declspec(naked) void __cdecl S_qol_cat_copy(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_qol_cat_copy] }
}

static FARPROC p_qol_cat_ready;
__pragma(comment(linker, "/EXPORT:qol_cat_ready=_S_qol_cat_ready"))
__declspec(naked) void __cdecl S_qol_cat_ready(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_qol_cat_ready] }
}

static FARPROC p_qol_cat_send;
__pragma(comment(linker, "/EXPORT:qol_cat_send=_S_qol_cat_send"))
__declspec(naked) void __cdecl S_qol_cat_send(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_qol_cat_send] }
}

static FARPROC p_qol_cat_version;
__pragma(comment(linker, "/EXPORT:qol_cat_version=_S_qol_cat_version"))
__declspec(naked) void __cdecl S_qol_cat_version(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_qol_cat_version] }
}

static FARPROC p_qol_render_connected;
__pragma(comment(linker, "/EXPORT:qol_render_connected=_S_qol_render_connected"))
__declspec(naked) void __cdecl S_qol_render_connected(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_qol_render_connected] }
}

static FARPROC p_qol_ui_action;
__pragma(comment(linker, "/EXPORT:qol_ui_action=_S_qol_ui_action"))
__declspec(naked) void __cdecl S_qol_ui_action(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_qol_ui_action] }
}

static FARPROC p_qol_ui_get;
__pragma(comment(linker, "/EXPORT:qol_ui_get=_S_qol_ui_get"))
__declspec(naked) void __cdecl S_qol_ui_get(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_qol_ui_get] }
}

static FARPROC p_qol_ui_grade_name;
__pragma(comment(linker, "/EXPORT:qol_ui_grade_name=_S_qol_ui_grade_name"))
__declspec(naked) void __cdecl S_qol_ui_grade_name(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_qol_ui_grade_name] }
}

static FARPROC p_qol_ui_hwnd;
__pragma(comment(linker, "/EXPORT:qol_ui_hwnd=_S_qol_ui_hwnd"))
__declspec(naked) void __cdecl S_qol_ui_hwnd(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_qol_ui_hwnd] }
}

static FARPROC p_qol_ui_log;
__pragma(comment(linker, "/EXPORT:qol_ui_log=_S_qol_ui_log"))
__declspec(naked) void __cdecl S_qol_ui_log(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_qol_ui_log] }
}

static FARPROC p_qol_ui_set;
__pragma(comment(linker, "/EXPORT:qol_ui_set=_S_qol_ui_set"))
__declspec(naked) void __cdecl S_qol_ui_set(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_qol_ui_set] }
}

static FARPROC p_qol_ui_set_turbo;
__pragma(comment(linker, "/EXPORT:qol_ui_set_turbo=_S_qol_ui_set_turbo"))
__declspec(naked) void __cdecl S_qol_ui_set_turbo(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_qol_ui_set_turbo] }
}

static FARPROC p_qol_ui_set_visible;
__pragma(comment(linker, "/EXPORT:qol_ui_set_visible=_S_qol_ui_set_visible"))
__declspec(naked) void __cdecl S_qol_ui_set_visible(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_qol_ui_set_visible] }
}

static FARPROC p_qol_ui_turbo;
__pragma(comment(linker, "/EXPORT:qol_ui_turbo=_S_qol_ui_turbo"))
__declspec(naked) void __cdecl S_qol_ui_turbo(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_qol_ui_turbo] }
}

static FARPROC p_qol_ui_visible;
__pragma(comment(linker, "/EXPORT:qol_ui_visible=_S_qol_ui_visible"))
__declspec(naked) void __cdecl S_qol_ui_visible(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_qol_ui_visible] }
}

static FARPROC p_ttf_cell;
__pragma(comment(linker, "/EXPORT:ttf_cell=_S_ttf_cell"))
__declspec(naked) void __cdecl S_ttf_cell(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_ttf_cell] }
}

static FARPROC p_ttf_glyph;
__pragma(comment(linker, "/EXPORT:ttf_glyph=_S_ttf_glyph"))
__declspec(naked) void __cdecl S_ttf_glyph(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_ttf_glyph] }
}

static FARPROC p_ttf_ready;
__pragma(comment(linker, "/EXPORT:ttf_ready=_S_ttf_ready"))
__declspec(naked) void __cdecl S_ttf_ready(void)
{
    __asm { call Ensure }
    __asm { jmp dword ptr [p_ttf_ready] }
}

/* 原版还多导出一个 "DirectInput8Create@20"（stdcall 装饰名）。
   链接器解析 "name@20=sym" 这种导出名会失配，而且系统 dinput8.dll 本身也只导出
   裸名 DirectInput8Create，游戏按名字取的就是裸名，所以这里不转发它。 */

}   /* extern "C" */

static HMODULE gReal = NULL;
static LONG gState = 0;

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
        return 1;
    }
    base = (BYTE*)info.lpBaseOfDll;
    size = info.SizeOfImage;
    if (base == NULL || size < 0x1000)
    {
        return 1;
    }

    for (i = 0; i + NEEDLE_LEN <= size; i++)
    {
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

    if (*text != 0 && VirtualProtect(text, 1, PAGE_EXECUTE_READWRITE, &old))
    {
        *text = 0;
        VirtualProtect(text, 1, old, &old);
        Note("文本首字节已写 0（显示函数会直接返回）");
    }

    for (i = 0; i + 10 <= size; i++)
    {
        BYTE* p;
        if (base[i] != 0x68)
        {
            continue;
        }
        p = base + i + 1;
        if (*(DWORD*)p != (DWORD)va)
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
        wsprintfA(line, "已把 push 后的 call 改成 5×nop：RVA=0x%X（公告窗不会再创建）",
                  (unsigned int)(i + 5));
        Note(line);
        return 1;
    }

    Note("文本已清零，但没找到 push+call（QoL 版本不同？）");
    return 1;
}

static void Ensure(void)
{
    LONG prev = InterlockedCompareExchange(&gState, 1, 0);
    if (prev == 2)
    {
        return;
    }
    if (prev == 1)
    {
        while (gState != 2)
        {
            Sleep(0);
        }
        return;
    }

    {
        char path[MAX_PATH];
        char* slash;
        HMODULE self = GetModuleHandleA("dinput8.dll");
        GetModuleFileNameA(self, path, MAX_PATH);
        slash = path;
        while (*slash)
        {
            slash++;
        }
        while (slash > path && slash[-1] != '\\' && slash[-1] != '/')
        {
            slash--;
        }
        lstrcpynA(slash, REAL_MODULE, MAX_PATH - (int)(slash - path));
        gReal = LoadLibraryA(path);
        if (gReal == NULL)
        {
            char msg[160];
            wsprintfA(msg, "LoadLibrary %s failed %lu", REAL_MODULE, GetLastError());
            Note(msg);
            InterlockedExchange(&gState, 2);
            return;
        }
    }

    p_DirectInput8Create = GetProcAddress(gReal, "DirectInput8Create");
    p_act4_font_init = GetProcAddress(gReal, "act4_font_init");
    p_qol_cat_copy = GetProcAddress(gReal, "qol_cat_copy");
    p_qol_cat_ready = GetProcAddress(gReal, "qol_cat_ready");
    p_qol_cat_send = GetProcAddress(gReal, "qol_cat_send");
    p_qol_cat_version = GetProcAddress(gReal, "qol_cat_version");
    p_qol_render_connected = GetProcAddress(gReal, "qol_render_connected");
    p_qol_ui_action = GetProcAddress(gReal, "qol_ui_action");
    p_qol_ui_get = GetProcAddress(gReal, "qol_ui_get");
    p_qol_ui_grade_name = GetProcAddress(gReal, "qol_ui_grade_name");
    p_qol_ui_hwnd = GetProcAddress(gReal, "qol_ui_hwnd");
    p_qol_ui_log = GetProcAddress(gReal, "qol_ui_log");
    p_qol_ui_set = GetProcAddress(gReal, "qol_ui_set");
    p_qol_ui_set_turbo = GetProcAddress(gReal, "qol_ui_set_turbo");
    p_qol_ui_set_visible = GetProcAddress(gReal, "qol_ui_set_visible");
    p_qol_ui_turbo = GetProcAddress(gReal, "qol_ui_turbo");
    p_qol_ui_visible = GetProcAddress(gReal, "qol_ui_visible");
    p_ttf_cell = GetProcAddress(gReal, "ttf_cell");
    p_ttf_glyph = GetProcAddress(gReal, "ttf_glyph");
    p_ttf_ready = GetProcAddress(gReal, "ttf_ready");

    InterlockedExchange(&gState, 2);
}

static DWORD WINAPI BootThread(LPVOID unused)
{
    int round;
    (void)unused;

    Ensure();
    if (gReal == NULL)
    {
        return 0;
    }
    Note("act4qol.dll 已加载，开始打补丁");

    for (round = 0; round < 6000; round++)   /* 最多两分钟 */
    {
        if (Patch(gReal))
        {
            return 0;
        }
        Sleep(20);
    }
    Note("补丁超时：两分钟内没找到那句话");
    return 0;
}

BOOL APIENTRY DllMain(HMODULE module, DWORD reason, LPVOID reserved)
{
    (void)reserved;
    if (reason == DLL_PROCESS_ATTACH)
    {
        DisableThreadLibraryCalls(module);
        CreateThread(NULL, 0, BootThread, NULL, 0, NULL);
    }
    return TRUE;
}
