#include <windows.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <wchar.h>
#include <locale.h>

#include "A5.h"

extern "C" __declspec(dllexport) void A5Anchor(void)
{
}

wchar_t g_clientDir[MAX_PATH];

#if A5_LOG
static wchar_t          g_logPath[MAX_PATH];
static CRITICAL_SECTION g_logLock;
static BOOL             g_logReady;
#endif

static BOOL GetClientDir(wchar_t *out, size_t cch)
{
    wchar_t exePath[MAX_PATH];
    DWORD len = GetModuleFileNameW(NULL, exePath, MAX_PATH);
    if (len == 0 || len >= MAX_PATH)
        return FALSE;

    wchar_t *slash = wcsrchr(exePath, L'\\');
    if (!slash)
        return FALSE;
    slash[1] = 0;

    _snwprintf(out, cch, L"%s", exePath);
    out[cch - 1] = 0;
    return TRUE;
}

#if A5_LOG

void A5_Log(const char *fmt, ...)
{
    if (!g_logReady)
        return;

    char line[1024];
    SYSTEMTIME st;
    GetLocalTime(&st);
    int n = _snprintf(line, sizeof(line) - 3, "[%02u:%02u:%02u.%03u] ",
                      st.wHour, st.wMinute, st.wSecond, st.wMilliseconds);
    if (n < 0)
        return;

    va_list ap;
    va_start(ap, fmt);
    int m = _vsnprintf(line + n, sizeof(line) - n - 3, fmt, ap);
    va_end(ap);
    if (m < 0)
        m = (int)strlen(line + n);
    n += m;
    line[n++] = '\r';
    line[n++] = '\n';

    EnterCriticalSection(&g_logLock);
    HANDLE h = CreateFileW(g_logPath, FILE_APPEND_DATA,
                           FILE_SHARE_READ | FILE_SHARE_WRITE, NULL,
                           OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (h != INVALID_HANDLE_VALUE) {
        DWORD written;
        WriteFile(h, line, (DWORD)n, &written, NULL);
        CloseHandle(h);
    }
    LeaveCriticalSection(&g_logLock);
}

static void ResetLog(void)
{
    HANDLE h = CreateFileW(g_logPath, GENERIC_WRITE, FILE_SHARE_READ, NULL,
                           CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (h != INVALID_HANDLE_VALUE)
        CloseHandle(h);
}

static void OpenLog(void)
{
    wchar_t logDir[MAX_PATH];
    _snwprintf(logDir, MAX_PATH, L"%slogs", g_clientDir);
    logDir[MAX_PATH - 1] = 0;
    CreateDirectoryW(logDir, NULL);

    HANDLE first = CreateMutexW(NULL, TRUE, L"Local\\A5LogOwner");
    BOOL   taken = (first != NULL && GetLastError() == ERROR_ALREADY_EXISTS);

    if (taken)
        _snwprintf(g_logPath, MAX_PATH, L"%s\\A5.%lu.log", logDir,
                   GetCurrentProcessId());
    else
        _snwprintf(g_logPath, MAX_PATH, L"%s\\A5.log", logDir);
    g_logPath[MAX_PATH - 1] = 0;

    InitializeCriticalSection(&g_logLock);
    g_logReady = TRUE;
    ResetLog();
}

#endif

BOOL WINAPI DllMain(HINSTANCE inst, DWORD reason, LPVOID reserved)
{
    UNREFERENCED_PARAMETER(reserved);

    if (reason == DLL_PROCESS_ATTACH) {
        DisableThreadLibraryCalls(inst);

        if (!GetClientDir(g_clientDir, MAX_PATH))
            return TRUE;

        setlocale(LC_CTYPE, "");

#if A5_LOG
        OpenLog();
#endif

        A5_Log("A5.dll 挂载成功（DumpReport.dll -> A5.dll，DNF.exe 未改动）");
        A5_Log("客户端目录 %ls", g_clientDir);
        A5_Log("pid=%lu  命令行 %ls", GetCurrentProcessId(), GetCommandLineW());

        CodePatch_Install();

        TerSafeProbe_Install();

        FontHook_Install();
    } else if (reason == DLL_PROCESS_DETACH) {
#if A5_LOG
        if (g_logReady) {
            FontHook_Report();
            g_logReady = FALSE;
            DeleteCriticalSection(&g_logLock);
        }
#endif
    }
    return TRUE;
}
