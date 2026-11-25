#include <windows.h>
#include <stdio.h>
#include <string.h>
#include <wchar.h>
#include <intrin.h>

#include "A5.h"

#ifndef FR_PRIVATE
#define FR_PRIVATE 0x10
#endif

#define VA_IAT_CREATEFONTW  0x00D040A0u
#define VA_SYSTEMFONT_CALL  0x00BC4F69u
#define VA_TRUETYPE_CALL    0x00BAD64Du

typedef HFONT (WINAPI *CreateFontWFn)(int, int, int, int, int,
                                      DWORD, DWORD, DWORD,
                                      DWORD, DWORD, DWORD, DWORD, DWORD,
                                      LPCWSTR);

typedef int (WINAPI *AddFontResourceExWFn)(LPCWSTR, DWORD, PVOID);

static BOOL          g_enabled;
static BOOL          g_trace;
static wchar_t       g_fontPath[MAX_PATH];
static wchar_t       g_face[LF_FACESIZE];
static wchar_t       g_match[LF_FACESIZE];
static int           g_weight;
static int           g_quality;
static int           g_outPrecision;

static CreateFontWFn g_origCreateFontW;
static void        **g_iatSlot;
static LONG          g_calls;
static LONG          g_hits;
static LONG          g_fontLoaded;

static BOOL SafeRead(const void *p, void *out, SIZE_T len)
{
    if (!p)
        return FALSE;
    __try {
        memcpy(out, p, len);
    } __except (EXCEPTION_EXECUTE_HANDLER) {
        return FALSE;
    }
    return TRUE;
}

static unsigned short BE16(const BYTE *p) { return (unsigned short)((p[0] << 8) | p[1]); }
static unsigned int   BE32(const BYTE *p) { return ((unsigned int)p[0] << 24) | ((unsigned int)p[1] << 16) |
                                                   ((unsigned int)p[2] << 8) | p[3]; }

static BOOL ReadAt(HANDLE h, DWORD off, void *buf, DWORD len)
{
    if (SetFilePointer(h, (LONG)off, NULL, FILE_BEGIN) == INVALID_SET_FILE_POINTER &&
        GetLastError() != NO_ERROR)
        return FALSE;
    DWORD got = 0;
    return ReadFile(h, buf, len, &got, NULL) && got == len;
}

static BOOL ReadFontFamily(const wchar_t *path, wchar_t *out, size_t cch)
{
    out[0] = 0;

    HANDLE h = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ, NULL,
                           OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (h == INVALID_HANDLE_VALUE)
        return FALSE;

    BYTE  hdr[12];
    DWORD base = 0;
    if (!ReadAt(h, 0, hdr, 12)) { CloseHandle(h); return FALSE; }

    if (memcmp(hdr, "ttcf", 4) == 0) {
        BYTE off[4];
        if (!ReadAt(h, 12, off, 4)) { CloseHandle(h); return FALSE; }
        base = BE32(off);
        if (!ReadAt(h, base, hdr, 12)) { CloseHandle(h); return FALSE; }
    }

    unsigned int numTables = BE16(hdr + 4);
    if (numTables == 0 || numTables > 512) { CloseHandle(h); return FALSE; }

    DWORD dirBytes = numTables * 16;
    BYTE *dir = (BYTE *)HeapAlloc(GetProcessHeap(), 0, dirBytes);
    if (!dir) { CloseHandle(h); return FALSE; }
    if (!ReadAt(h, base + 12, dir, dirBytes)) {
        HeapFree(GetProcessHeap(), 0, dir);
        CloseHandle(h);
        return FALSE;
    }

    DWORD nameOff = 0, nameLen = 0;
    for (unsigned int i = 0; i < numTables; i++) {
        if (memcmp(dir + i * 16, "name", 4) == 0) {
            nameOff = BE32(dir + i * 16 + 8);
            nameLen = BE32(dir + i * 16 + 12);
            break;
        }
    }
    HeapFree(GetProcessHeap(), 0, dir);

    if (!nameOff || nameLen < 6 || nameLen > 512u * 1024) {
        CloseHandle(h);
        return FALSE;
    }

    BYTE *buf = (BYTE *)HeapAlloc(GetProcessHeap(), 0, nameLen);
    if (!buf) { CloseHandle(h); return FALSE; }

    BOOL ok = ReadAt(h, nameOff, buf, nameLen);
    CloseHandle(h);
    if (!ok) {
        HeapFree(GetProcessHeap(), 0, buf);
        return FALSE;
    }

    const DWORD    size      = nameLen;
    unsigned int   count     = BE16(buf + 2);
    unsigned int   stringOff = BE16(buf + 4);
    int            bestScore = -1;

    for (unsigned int i = 0; i < count; i++) {
        DWORD rec = 6 + i * 12;
        if (rec + 12 > size)
            break;

        unsigned int pid = BE16(buf + rec);
        unsigned int lid = BE16(buf + rec + 4);
        unsigned int nid = BE16(buf + rec + 6);
        unsigned int len = BE16(buf + rec + 8);
        unsigned int off = BE16(buf + rec + 10);
        if (nid != 1 || len == 0)
            continue;

        if (pid != 1 && pid != 3)
            continue;

        DWORD str = stringOff + off;
        if (str + len > size)
            continue;

        int score = (pid == 3 && lid == 0x409) ? 3 : (pid == 3) ? 2 : 1;
        if (score <= bestScore)
            continue;

        if (pid == 3) {
            unsigned int chars = len / 2;
            if (chars == 0 || chars >= cch)
                continue;
            for (unsigned int k = 0; k < chars; k++)
                out[k] = (wchar_t)BE16(buf + str + k * 2);
            out[chars] = 0;
        } else {
            if (len >= cch)
                continue;
            for (unsigned int k = 0; k < len; k++)
                out[k] = (wchar_t)buf[str + k];
            out[len] = 0;
        }
        bestScore = score;
    }

    HeapFree(GetProcessHeap(), 0, buf);
    return bestScore >= 0 && out[0] != 0;
}

#define TAG_HEAD 0x64616568u

typedef HDC     (WINAPI *CreateCompatibleDCFn)(HDC);
typedef BOOL    (WINAPI *DeleteDCFn)(HDC);
typedef HGDIOBJ (WINAPI *SelectObjectFn)(HDC, HGDIOBJ);
typedef DWORD   (WINAPI *GetFontDataFn)(HDC, DWORD, DWORD, PVOID, DWORD);
typedef int     (WINAPI *GetTextFaceWFn)(HDC, int, LPWSTR);

static void LogRealFont(HFONT hf, int cHeight)
{
    static CreateCompatibleDCFn pCreateDC;
    static DeleteDCFn           pDeleteDC;
    static SelectObjectFn       pSelect;
    static GetFontDataFn        pFontData;
    static GetTextFaceWFn       pTextFace;
    static LONG                 resolved;

    if (!hf)
        return;

    if (InterlockedCompareExchange(&resolved, 1, 0) == 0) {
        HMODULE gdi = GetModuleHandleW(L"gdi32.dll");
        if (gdi) {
            pCreateDC = (CreateCompatibleDCFn)GetProcAddress(gdi, "CreateCompatibleDC");
            pDeleteDC = (DeleteDCFn)          GetProcAddress(gdi, "DeleteDC");
            pSelect   = (SelectObjectFn)      GetProcAddress(gdi, "SelectObject");
            pFontData = (GetFontDataFn)       GetProcAddress(gdi, "GetFontData");
            pTextFace = (GetTextFaceWFn)      GetProcAddress(gdi, "GetTextFaceW");
        }
        if (!pCreateDC || !pDeleteDC || !pSelect || !pFontData || !pTextFace)
            A5_Log("[font] gdi32 的诊断函数取不全，指纹这一项没有了（不影响换字体）");
    }

    if (!pCreateDC || !pDeleteDC || !pSelect || !pFontData || !pTextFace)
        return;

    HDC dc = pCreateDC(NULL);
    if (!dc)
        return;

    HGDIOBJ old = pSelect(dc, hf);

    wchar_t face[LF_FACESIZE];
    face[0] = 0;
    pTextFace(dc, LF_FACESIZE, face);

    BYTE  head[54];
    DWORD rev = 0, csum = 0;
    DWORD n = pFontData(dc, TAG_HEAD, 0, head, sizeof(head));
    if (n == sizeof(head)) {
        rev  = BE32(head + 4);
        csum = BE32(head + 8);
    }

    A5_Log("[font] h=%d GDI 实际给的：fontRevision=%08X checkSumAdj=%08X%s face=\"%ls\"",
           cHeight, rev, csum,
           n == sizeof(head) ? "" : "（head 表读不出来，位图字体？）", face);

    pSelect(dc, old);
    pDeleteDC(dc);
}

static void EnsureFontLoaded(void)
{
    if (InterlockedCompareExchange(&g_fontLoaded, 1, 0) != 0)
        return;

    if (!g_fontPath[0]) {
        A5_Log("[font] 没有配置字体文件，只改 CreateFontW 的参数");
        return;
    }

    if (GetFileAttributesW(g_fontPath) == INVALID_FILE_ATTRIBUTES) {
        A5_Log("[font] 字体文件不存在：%ls —— 这一处放弃，字还是系统回退的那个", g_fontPath);
        g_fontLoaded = 2;
        return;
    }

    HMODULE gdi = GetModuleHandleW(L"gdi32.dll");
    AddFontResourceExWFn addEx = gdi
        ? (AddFontResourceExWFn)GetProcAddress(gdi, "AddFontResourceExW")
        : NULL;
    if (!addEx) {
        A5_Log("[font] 取不到 gdi32!AddFontResourceExW，这一处放弃");
        g_fontLoaded = 2;
        return;
    }

    int added = addEx(g_fontPath, FR_PRIVATE, NULL);
    if (added <= 0) {
        A5_Log("[font] AddFontResourceExW 失败 (err=%lu)：%ls", GetLastError(), g_fontPath);
        g_fontLoaded = 2;
        return;
    }

    A5_Log("[font] 已私有加载 %ls（%d 个字体，仅本进程可见）", g_fontPath, added);
    A5_Log("[font] 族名 \"%ls\"%s", g_face,
           g_face[0] ? "" : " —— 空的，face name 不会被替换");
}

static HFONT WINAPI A5_CreateFontW(int cHeight, int cWidth, int cEscapement,
                                   int cOrientation, int cWeight,
                                   DWORD bItalic, DWORD bUnderline, DWORD bStrikeOut,
                                   DWORD iCharSet, DWORD iOutPrecision,
                                   DWORD iClipPrecision, DWORD iQuality,
                                   DWORD iPitchAndFamily, LPCWSTR pszFaceName)
{
    void *ret = _ReturnAddress();
    LONG  n   = InterlockedIncrement(&g_calls);

    BOOL hit = (pszFaceName && g_match[0] && _wcsicmp(pszFaceName, g_match) == 0);

    if (g_trace || n <= 8)
        A5_Log("[font] #%ld CreateFontW h=%d w=%d cs=%u q=%u op=%u face=\"%ls\"%s "
               "<- 返回地址 %p",
               n, cHeight, cWeight, iCharSet, iQuality, iOutPrecision,
               pszFaceName ? pszFaceName : L"(null)", hit ? " [命中]" : "", ret);

    if (!hit) {
        HFONT miss = g_origCreateFontW(cHeight, cWidth, cEscapement, cOrientation,
                                       cWeight, bItalic, bUnderline, bStrikeOut,
                                       iCharSet, iOutPrecision, iClipPrecision,
                                       iQuality, iPitchAndFamily, pszFaceName);
        if (n <= 8)
            LogRealFont(miss, cHeight);
        return miss;
    }

    EnsureFontLoaded();

    LPCWSTR face = (g_face[0] && g_fontLoaded != 2) ? g_face : pszFaceName;
    int     w    = g_weight       ? g_weight            : cWeight;
    DWORD   q    = (g_quality      >= 0) ? (DWORD)g_quality      : iQuality;
    DWORD   op   = (g_outPrecision >= 0) ? (DWORD)g_outPrecision : iOutPrecision;

    LONG h = InterlockedIncrement(&g_hits);
    if (h <= 3)
        A5_Log("[font] 命中 #%ld：h=%d \"%ls\" -> \"%ls\"，weight %d->%d "
               "quality %u->%u outprec %u->%u（调用者 %p）",
               h, cHeight, pszFaceName, face, cWeight, w, iQuality, q,
               iOutPrecision, op, ret);

    HFONT hf = g_origCreateFontW(cHeight, cWidth, cEscapement, cOrientation, w,
                                 bItalic, bUnderline, bStrikeOut, iCharSet,
                                 op, iClipPrecision, q, iPitchAndFamily, face);

    if (h <= 3)
        LogRealFont(hf, cHeight);

    return hf;
}

static void **FindImportSlot(HMODULE mod, const char *dllName, const char *funcName)
{
    BYTE *base = (BYTE *)mod;

    IMAGE_DOS_HEADER dos;
    if (!SafeRead(base, &dos, sizeof(dos)) || dos.e_magic != IMAGE_DOS_SIGNATURE)
        return NULL;

    IMAGE_NT_HEADERS nt;
    if (!SafeRead(base + dos.e_lfanew, &nt, sizeof(nt)) || nt.Signature != IMAGE_NT_SIGNATURE)
        return NULL;

    DWORD impRva = nt.OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_IMPORT].VirtualAddress;
    if (!impRva)
        return NULL;

    IMAGE_IMPORT_DESCRIPTOR *desc = (IMAGE_IMPORT_DESCRIPTOR *)(base + impRva);
    for (int i = 0; ; i++) {
        IMAGE_IMPORT_DESCRIPTOR d;
        if (!SafeRead(&desc[i], &d, sizeof(d)) || d.Name == 0)
            break;

        const char *name = (const char *)(base + d.Name);
        char        dll[64];
        if (!SafeRead(name, dll, sizeof(dll)))
            continue;
        dll[sizeof(dll) - 1] = 0;
        if (_stricmp(dll, dllName) != 0)
            continue;

        DWORD nameThunkRva = d.OriginalFirstThunk ? d.OriginalFirstThunk : d.FirstThunk;
        IMAGE_THUNK_DATA *names = (IMAGE_THUNK_DATA *)(base + nameThunkRva);
        IMAGE_THUNK_DATA *iat   = (IMAGE_THUNK_DATA *)(base + d.FirstThunk);

        for (int k = 0; ; k++) {
            IMAGE_THUNK_DATA t;
            if (!SafeRead(&names[k], &t, sizeof(t)) || t.u1.AddressOfData == 0)
                break;
            if (t.u1.Ordinal & IMAGE_ORDINAL_FLAG)
                continue;

            IMAGE_IMPORT_BY_NAME *imp = (IMAGE_IMPORT_BY_NAME *)(base + t.u1.AddressOfData);
            char fn[128];
            if (!SafeRead(imp->Name, fn, sizeof(fn)))
                continue;
            fn[sizeof(fn) - 1] = 0;
            if (strcmp(fn, funcName) == 0)
                return (void **)&iat[k].u1.Function;
        }
    }
    return NULL;
}

static BOOL WriteSlot(void **slot, void *value)
{
    DWORD old = 0;
    if (!VirtualProtect(slot, sizeof(void *), PAGE_READWRITE, &old))
        return FALSE;
    *slot = value;
    DWORD tmp = 0;
    VirtualProtect(slot, sizeof(void *), old, &tmp);
    return TRUE;
}

#define WATCH_INTERVAL_MS 250

static int  g_watchSeconds = 60;
static LONG g_rewrites;

static DWORD WINAPI FontWatchThread(LPVOID)
{
    int  rounds = g_watchSeconds * 1000 / WATCH_INTERVAL_MS;
    BOOL alive  = TRUE;

    for (int i = 0; i < rounds; i++) {
        Sleep(WATCH_INTERVAL_MS);
        if (!alive || !g_iatSlot)
            continue;

        void *now = NULL;
        if (!SafeRead(g_iatSlot, &now, sizeof(now)))
            continue;
        if (now == (void *)A5_CreateFontW)
            continue;

        if (now == (void *)g_origCreateFontW) {
            if (WriteSlot(g_iatSlot, (void *)A5_CreateFontW)) {
                LONG n = InterlockedIncrement(&g_rewrites);
                A5_Log("[font] IAT 被还原（累计第 %ld 次），已重挂。%.2fs",
                       n, i * WATCH_INTERVAL_MS / 1000.0);
            } else {
                A5_Log("[font] IAT 被还原，重挂失败 (err=%lu)，停止看护", GetLastError());
                alive = FALSE;
            }
        } else {
            A5_Log("[font] IAT 变成了 %p —— 既不是我们的也不是原来的，停止看护", now);
            alive = FALSE;
        }
    }

    A5_Log("[font] 前 %d 秒：CreateFontW 共 %ld 次，其中 \"%ls\" 命中 %ld 次"
           "（不是一局总数，退出时还会再报一次）",
           g_watchSeconds, g_calls, g_match, g_hits);
    return 0;
}

void FontHook_Report(void)
{
    if (!g_enabled || !g_iatSlot)
        return;

    A5_Log("[font] 收尾：CreateFontW 共 %ld 次，其中 \"%ls\" 命中 %ld 次",
           g_calls, g_match, g_hits);

    if (g_hits == 0 && g_calls > 0)
        A5_Log("[font] 一次都没命中，但 CreateFontW 被调过 %ld 次 —— 客户端要的不是 "
               "\"%ls\"。前面那几行 \"#N CreateFontW ... face=\" 里就是它真正要的名字，"
               "把 A5.h 的 A5_FONT_MATCH 改成它", g_calls, g_match);
    else if (g_calls == 0)
        A5_Log("[font] 整局一次 CreateFontW 都没有 —— 正文不走 GDI 字体，"
               "该回头查 CNFontSystemBitmap 那条位图字体的路了");
}

void FontHook_Install(void)
{
    g_enabled = A5_FONT_ENABLED != 0;
    g_trace   = A5_LOG != 0;
    if (!g_enabled) {
        A5_Log("[font] 未启用，跳过");
        return;
    }

    wchar_t file[MAX_PATH];
    wcsncpy(file, A5_FONT_FILE, MAX_PATH - 1);
    file[MAX_PATH - 1] = 0;
    wcsncpy(g_match, A5_FONT_MATCH, LF_FACESIZE - 1);
    g_match[LF_FACESIZE - 1] = 0;
    wcsncpy(g_face, A5_FONT_FACE, LF_FACESIZE - 1);
    g_face[LF_FACESIZE - 1] = 0;

    g_weight       = A5_FONT_WEIGHT;
    g_quality      = A5_FONT_QUALITY;
    g_outPrecision = A5_FONT_OUTPRECISION;

    if (file[0]) {
        if (file[1] == L':' || file[0] == L'\\') {
            wcsncpy(g_fontPath, file, MAX_PATH - 1);
        } else {
            _snwprintf(g_fontPath, MAX_PATH, L"%s%s", g_clientDir, file);
        }
        g_fontPath[MAX_PATH - 1] = 0;
    }

    if (!g_face[0] && g_fontPath[0]) {
        if (ReadFontFamily(g_fontPath, g_face, LF_FACESIZE))
            A5_Log("[font] 从 %ls 读出族名 \"%ls\"", g_fontPath, g_face);
        else
            A5_Log("[font] 读不出 %ls 的族名 —— 在 A5.h 里手填 A5_FONT_FACE 可以绕过",
                   g_fontPath);
    }

    A5_Log("[font] file=%ls face=\"%ls\" match=\"%ls\" weight=%d quality=%d "
           "outprec=%d trace=%d",
           g_fontPath[0] ? g_fontPath : L"(无)", g_face, g_match,
           g_weight, g_quality, g_outPrecision, g_trace);

    if (g_face[0] && g_match[0] && _wcsicmp(g_face, g_match) == 0)
        A5_Log("[font] 注意：face 和 match 同名（\"%ls\"），face 替换是空操作，拿到私有的"
               "还是系统的由 GDI 决定。看「GDI 实际给的」那几行的 checkSumAdj 对指纹 —— "
               "要是对上了系统那份，就得把字体文件的族名改成独有的再来",
               g_face);

    HMODULE exe = GetModuleHandleW(NULL);
    if (!exe) {
        A5_Log("[font] 取不到主模块句柄，跳过");
        return;
    }

    void **slot = FindImportSlot(exe, "GDI32.dll", "CreateFontW");
    if (!slot) {
        A5_Log("[font] 导入表里找不到 GDI32.dll!CreateFontW，跳过 —— 换客户端了？");
        return;
    }

    DWORD_PTR delta  = (DWORD_PTR)exe - 0x00400000u;
    DWORD_PTR expect = VA_IAT_CREATEFONTW + delta;
    if ((DWORD_PTR)slot != expect)
        A5_Log("[font] IAT 槽位 %p，和预期的 %p 不一致（按导入表实测值走）",
               slot, (void *)expect);

    void *orig = NULL;
    if (!SafeRead(slot, &orig, sizeof(orig)) || !orig) {
        A5_Log("[font] IAT 槽位 %p 读不出来，跳过", slot);
        return;
    }

    HMODULE gdi = GetModuleHandleW(L"gdi32.dll");
    if (gdi) {
        IMAGE_DOS_HEADER dos;
        IMAGE_NT_HEADERS nt;
        if (SafeRead(gdi, &dos, sizeof(dos)) && dos.e_magic == IMAGE_DOS_SIGNATURE &&
            SafeRead((BYTE *)gdi + dos.e_lfanew, &nt, sizeof(nt)) &&
            nt.Signature == IMAGE_NT_SIGNATURE) {
            BYTE *lo = (BYTE *)gdi;
            BYTE *hi = lo + nt.OptionalHeader.SizeOfImage;
            if ((BYTE *)orig < lo || (BYTE *)orig >= hi) {
                A5_Log("[font] IAT 槽位 %p 里是 %p，不在 gdi32 [%p,%p) 内 —— "
                       "导入还没解析完？这一处不装", slot, orig, lo, hi);
                return;
            }
        }
    }

    g_origCreateFontW = (CreateFontWFn)orig;
    if (!WriteSlot(slot, (void *)A5_CreateFontW)) {
        A5_Log("[font] 写 IAT 失败 (err=%lu)，跳过", GetLastError());
        g_origCreateFontW = NULL;
        return;
    }
    g_iatSlot = slot;

    A5_Log("[font] 已挂上 %p：CreateFontW %p -> %p（两个调用点 %p / %p 都走这里）",
           slot, orig, (void *)A5_CreateFontW,
           (void *)(VA_SYSTEMFONT_CALL + delta), (void *)(VA_TRUETYPE_CALL + delta));

    if (!A5_WATCH_ENABLED)
        return;

    g_watchSeconds = A5_WATCH_SECONDS;
    if (g_watchSeconds < 1)
        g_watchSeconds = 1;
    if (g_watchSeconds > 3600)
        g_watchSeconds = 3600;

    HANDLE th = CreateThread(NULL, 0, FontWatchThread, NULL, 0, NULL);
    if (th)
        CloseHandle(th);
    else
        A5_Log("[font] 看护线程起不来 (err=%lu)，钩子本身不受影响", GetLastError());
}
