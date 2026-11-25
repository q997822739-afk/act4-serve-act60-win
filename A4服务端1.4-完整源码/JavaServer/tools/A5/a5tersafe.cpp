#include <windows.h>
#include <stdio.h>
#include <string.h>
#include <intrin.h>

#include "A5.h"

#define SLOT_COUNT 64
#define OBJ_COUNT  8

#define VA_B58EC0 0x00B58EC0u

static const BYTE kProlog[9] = {
    0x55, 0x8B, 0xEC, 0x81, 0xEC, 0x08, 0x02, 0x00, 0x00
};
#define PROLOG_LEN 9

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

static void HexLine(const BYTE *p, int len, char *out, size_t cch)
{
    size_t used = 0;
    out[0] = 0;
    for (int i = 0; i < len && used + 4 < cch; i++) {
        int n = _snprintf(out + used, cch - used, i ? " %02X" : "%02X", p[i]);
        if (n < 0) break;
        used += (size_t)n;
    }
}

static BOOL WriteBytes(BYTE *addr, const BYTE *bytes, SIZE_T len)
{
    DWORD old = 0;
    if (!VirtualProtect(addr, len, PAGE_EXECUTE_READWRITE, &old))
        return FALSE;
    memcpy(addr, bytes, len);
    DWORD tmp = 0;
    VirtualProtect(addr, len, old, &tmp);
    FlushInstructionCache(GetCurrentProcess(), addr, len);
    return TRUE;
}

static void Describe(void *addr, char *out, size_t cch)
{
    MEMORY_BASIC_INFORMATION mbi;
    if (!addr || VirtualQuery(addr, &mbi, sizeof(mbi)) == 0 ||
        mbi.State != MEM_COMMIT) {
        _snprintf(out, cch, "%p", addr);
        out[cch - 1] = 0;
        return;
    }
    char path[MAX_PATH];
    HMODULE mod = (HMODULE)mbi.AllocationBase;
    if (mod && GetModuleFileNameA(mod, path, MAX_PATH)) {
        const char *base = strrchr(path, '\\');
        base = base ? base + 1 : path;
        _snprintf(out, cch, "%p = %s+%X", addr, base,
                  (unsigned)((BYTE *)addr - (BYTE *)mod));
    } else {
        _snprintf(out, cch, "%p = <无模块>", addr);
    }
    out[cch - 1] = 0;
}

typedef void *(__cdecl *B58EC0Fn)(int id, wchar_t *src);

static B58EC0Fn g_trampoline;
static BYTE    *g_hookAddr;
static BYTE     g_origBytes[PROLOG_LEN];

typedef struct {
    void **realVt;
    void  *fakeVt[SLOT_COUNT];
    void  *obj;
    int    id;
    BOOL   used;
} ObjSlot;

static ObjSlot g_objs[OBJ_COUNT];
static LONG    g_objCount;
static LONG    g_calls;
static BYTE    g_dumped[OBJ_COUNT][SLOT_COUNT];

static BOOL g_stubMode;
static LONG g_stubUnknown;

#define FAKE_OBJ_SIZE 0x400

#define RET_UNKNOWN (-1)

static int StubRetN(int id, int slot)
{
    static const struct { short id, slot, n; } kStubRetN[] = {
        { 3,  0, 0x0C },
        { 3,  1, 0x00 },
        { 3,  4, 0x00 },
        { 3,  5, 0x00 },
        { 3,  6, 0x00 },
        { 3,  7, 0x00 },
        { 3,  8, 0x1C },
        { 3, 10, 0x1C },
        { 3, 12, 0x04 },
        { 7,  0, 0x04 },
        { 7,  1, 0x08 },
        { 7,  2, 0x08 },
        { 7, 11, 0x08 },
        { 7, 13, 0x00 },
        { 7, 15, 0x00 },
        { 7, 16, 0x04 },
        { 7, 20, 0x08 },
        { 7, 25, 0x10 },
        { 7, 31, 0x00 },
    };
    for (int i = 0; i < (int)(sizeof(kStubRetN) / sizeof(kStubRetN[0])); i++)
        if (kStubRetN[i].id == id && kStubRetN[i].slot == slot)
            return kStubRetN[i].n;
    return RET_UNKNOWN;
}

static BYTE    g_measured[OBJ_COUNT][SLOT_COUNT];
static int     g_slotRetN[OBJ_COUNT][SLOT_COUNT];
static LONG    g_measInProgress;
static void   *g_measRetaddr;
static void   *g_measEspS;
static int     g_measEncoded;

static void *__cdecl ReportSlot(int encoded, void *retAddr, void *thisPtr,
                                DWORD *args);
static void  __cdecl RecordRetN(int n, int encoded);
static void MeasureTrampoline(void);

static void __declspec(naked) StubReturnOne(void)
{
    __asm { mov eax, 1 }
    __asm { ret }
}

__declspec(naked) static void CommonThunk(void)
{
    __asm {
        push ebp
        mov  ebp, esp
        pushad
        lea  eax, [ebp + 12]
        push eax
        push ecx
        mov  eax, [ebp + 8]
        push eax
        mov  eax, [ebp + 4]
        push eax
        call ReportSlot
        add  esp, 16
        test eax, 1
        jnz  measure
        mov  [ebp + 4], eax
        popad
        mov  esp, ebp
        pop  ebp
        ret
    measure:
        and  eax, 0xFFFFFFFE
        mov  [ebp + 4], eax
        popad
        mov  esp, ebp
        pop  ebp
        pop  eax
        mov  edx, [esp]
        mov  g_measRetaddr, edx
        mov  dword ptr [esp], offset MeasureTrampoline
        mov  g_measEspS, esp
        jmp  eax
    }
}

__declspec(naked) static void MeasureTrampoline(void)
{
    __asm {
        mov  eax, esp
        sub  eax, g_measEspS
        sub  eax, 4
        mov  edx, g_measRetaddr
        pushad
        pushfd
        push g_measEncoded
        push eax
        call RecordRetN
        add  esp, 8
        popfd
        popad
        jmp  edx
    }
}

static int __cdecl StubReport(int encoded, void *retAddr, void *thisPtr,
                              DWORD *args);

__declspec(naked) static void StubThunk(void)
{
    __asm {
        push ebp
        mov  ebp, esp
        pushad
        lea  eax, [ebp + 12]
        push eax
        push ecx
        mov  eax, [ebp + 8]
        push eax
        mov  eax, [ebp + 4]
        push eax
        call StubReport
        add  esp, 16
        mov  [ebp + 4], eax
        popad
        mov  edx, [ebp + 8]
        mov  eax, [ebp + 4]
        mov  esp, ebp
        pop  ebp
        add  esp, 8
        add  esp, eax
        xor  eax, eax
        jmp  edx
    }
}

static int __cdecl StubReport(int encoded, void *retAddr, void *thisPtr,
                              DWORD *args)
{
    int objIdx = (encoded >> 8) & 0xFF;
    int slot   = encoded & 0xFF;
    int id     = (objIdx < OBJ_COUNT && g_objs[objIdx].used) ? g_objs[objIdx].id : -1;
    int n      = StubRetN(id, slot);

    LONG c = InterlockedIncrement(&g_calls);
    char where[160];

    if (n == RET_UNKNOWN) {
        Describe(retAddr, where, sizeof(where));
        LONG u = InterlockedIncrement(&g_stubUnknown);
        A5_Log("[tersafe] !! 表外槽 obj%d(id=%d) slot %d(+0x%02X)  this=%p  "
               "参数 %08X %08X %08X %08X  <- %s",
               objIdx, id, slot, slot * 4, thisPtr,
               args[0], args[1], args[2], args[3], where);
        A5_Log("[tersafe] !! 不知道它该 ret 几，按 ret 0 走，栈可能就此歪掉。"
               "把这个槽补进 kStubRetN[] 再跑一局（累计第 %ld 次）", u);
        return 0;
    }

    if (c <= 800) {
        Describe(retAddr, where, sizeof(where));
        A5_Log("[stub] obj%d(id=%d) slot %-3d(+0x%02X) ret %-2d  this=%p  "
               "参数 %08X %08X %08X %08X  <- %s",
               objIdx, id, slot, slot * 4, n, thisPtr,
               args[0], args[1], args[2], args[3], where);
    }
    return n;
}

static void *__cdecl ReportSlot(int encoded, void *retAddr, void *thisPtr,
                                DWORD *args)
{
    int objIdx = (encoded >> 8) & 0xFF;
    int slot   = encoded & 0xFF;

    if (objIdx < OBJ_COUNT && slot < SLOT_COUNT && g_objs[objIdx].used) {
        void **rv = g_objs[objIdx].realVt;
        void  *real = (rv) ? rv[slot] : NULL;

        if (!g_dumped[objIdx][slot] && real) {
            g_dumped[objIdx][slot] = 1;
            BYTE code[48];
            if (SafeRead(real, code, sizeof(code))) {
                char hex[200];
                HexLine(code, sizeof(code), hex, sizeof(hex));
                A5_Log("[tersafe] * obj%d(id=%d) slot %d(+0x%02X) 真槽 %p 机器码: %s",
                       objIdx, g_objs[objIdx].id, slot, slot * 4, real, hex);
            }
        }

        LONG n = InterlockedIncrement(&g_calls);
        if (n <= 800) {
            char where[160];
            Describe(retAddr, where, sizeof(where));
            A5_Log("obj%d(id=%d) slot %-3d(+0x%02X)  this=%p  参数 %08X %08X %08X %08X  <- %s",
                   objIdx, g_objs[objIdx].id, slot, slot * 4, thisPtr,
                   args[0], args[1], args[2], args[3], where);
        }
        if (real) {
            if (!g_measured[objIdx][slot] &&
                InterlockedCompareExchange(&g_measInProgress, 1, 0) == 0) {
                if (!g_measured[objIdx][slot]) {
                    g_measEncoded = encoded;
                    return (void *)((UINT_PTR)real | 1);
                }
                g_measInProgress = 0;
            }
            return real;
        }
    }
    return (void *)StubReturnOne;
}

static void __cdecl RecordRetN(int n, int encoded)
{
    int objIdx = (encoded >> 8) & 0xFF;
    int slot   = encoded & 0xFF;
    if (objIdx < OBJ_COUNT && slot < SLOT_COUNT) {
        g_slotRetN[objIdx][slot] = n;
        g_measured[objIdx][slot] = 1;
        A5_Log("[tersafe] ## obj%d(id=%d) slot %d(+0x%02X) 实测 ret N = %d (0x%X)",
               objIdx, g_objs[objIdx].id, slot, slot * 4, n, n);
    }
    g_measInProgress = 0;
}

static BYTE *g_thunkArea;

static void *MakeThunk(BYTE *at, int encoded, void *target)
{
    at[0] = 0x68;
    *(DWORD *)(at + 1) = (DWORD)encoded;
    at[5] = 0xE9;
    *(LONG *)(at + 6) = (LONG)((BYTE *)target - (at + 10));
    return at;
}

static BOOL BuildThunks(void)
{
    SIZE_T need = (SIZE_T)OBJ_COUNT * SLOT_COUNT * 10;
    g_thunkArea = (BYTE *)VirtualAlloc(NULL, need, MEM_COMMIT | MEM_RESERVE,
                                       PAGE_EXECUTE_READWRITE);
    if (!g_thunkArea) {
        A5_Log("[tersafe] 桩表 VirtualAlloc 失败 (err=%lu)", GetLastError());
        return FALSE;
    }
    void *target = g_stubMode ? (void *)StubThunk : (void *)CommonThunk;
    for (int o = 0; o < OBJ_COUNT; o++) {
        for (int s = 0; s < SLOT_COUNT; s++) {
            BYTE *at = g_thunkArea + ((SIZE_T)o * SLOT_COUNT + s) * 10;
            g_objs[o].fakeVt[s] = MakeThunk(at, (o << 8) | s, target);
        }
    }
    return TRUE;
}

static void OnCreateObj(int id, void *obj, void *retAddr)
{
    char where[160];
    Describe(retAddr, where, sizeof(where));
    A5_Log("[tersafe] CreateObj(id=%d) -> obj=%p  <- %s", id, obj, where);

    if (!obj)
        return;

    void **realVt = NULL;
    if (!SafeRead(obj, &realVt, sizeof(realVt)) || !realVt) {
        A5_Log("[tersafe]   对象没有可读的虚表指针，不换");
        return;
    }

    LONG i = InterlockedIncrement(&g_objCount) - 1;
    if (i >= OBJ_COUNT) {
        A5_Log("[tersafe]   对象槽用光了（只准备了 %d 个），这个不记账", OBJ_COUNT);
        return;
    }

    ObjSlot *os = &g_objs[i];
    os->id     = id;
    os->obj    = obj;
    os->realVt = realVt;
    os->used   = TRUE;

    void *vtPtr = os->fakeVt;
    if (!WriteBytes((BYTE *)obj, (const BYTE *)&vtPtr, sizeof(vtPtr))) {
        A5_Log("[tersafe]   换虚表失败（写对象头 %p，err=%lu）", obj, GetLastError());
        os->used = FALSE;
        return;
    }

    char s0[64], s5[64], s11[64];
    void *p0 = NULL, *p5 = NULL, *p11 = NULL;
    SafeRead(&realVt[0], &p0, sizeof(p0));
    SafeRead(&realVt[5], &p5, sizeof(p5));
    SafeRead(&realVt[11], &p11, sizeof(p11));
    Describe(p0, s0, sizeof(s0));
    Describe(p5, s5, sizeof(s5));
    Describe(p11, s11, sizeof(s11));
    A5_Log("[tersafe]   obj%d 真虚表 %p 已换成记账桩。slot0=%s  slot5=%s  slot11=%s",
           i, realVt, s0, s5, s11);
}

static void *MakeFakeObject(int id, void *retAddr)
{
    char where[160];
    Describe(retAddr, where, sizeof(where));

    LONG i = InterlockedIncrement(&g_objCount) - 1;
    if (i >= OBJ_COUNT) {
        A5_Log("[tersafe] [stub] CreateObj(id=%d) 对象槽用光了（只准备了 %d 个），"
               "返回 NULL —— 客户端拿它当 this 且不做 NULL 检查，会崩。"
               "把 OBJ_COUNT 调大重编 <- %s", id, OBJ_COUNT, where);
        return NULL;
    }

    void *obj = HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY, FAKE_OBJ_SIZE);
    if (!obj) {
        A5_Log("[tersafe] [stub] CreateObj(id=%d) 假对象分配失败 (err=%lu)",
               id, GetLastError());
        return NULL;
    }

    ObjSlot *os = &g_objs[i];
    os->id     = id;
    os->obj    = obj;
    os->realVt = NULL;
    os->used   = TRUE;

    void *vtPtr = os->fakeVt;
    *(void **)obj = vtPtr;

    A5_Log("[tersafe] [stub] CreateObj(id=%d) -> 假对象 %p（虚表 %p，%d 槽）  <- %s",
           id, obj, vtPtr, SLOT_COUNT, where);
    return obj;
}

static void *__cdecl My_B58EC0(int id, wchar_t *src)
{
    void *ret = _ReturnAddress();

    if (g_stubMode)
        return MakeFakeObject(id, ret);

    void *obj = g_trampoline ? g_trampoline(id, src) : NULL;
    OnCreateObj(id, obj, ret);
    return obj;
}

#define WATCH_INTERVAL_MS 250

static int  g_watchSeconds = 60;
static LONG g_rewrites;

static void BuildEntryPatch(BYTE out[PROLOG_LEN])
{
    out[0] = 0xE9;
    *(LONG *)(out + 1) = (LONG)((BYTE *)My_B58EC0 - (g_hookAddr + 5));
    out[5] = 0x90;
    out[6] = 0x90;
    out[7] = 0x90;
    out[8] = 0x90;
}

static DWORD WINAPI WatchThread(LPVOID)
{
    BYTE want[PROLOG_LEN];
    BuildEntryPatch(want);

    int  rounds = g_watchSeconds * 1000 / WATCH_INTERVAL_MS;
    BOOL alive  = TRUE;
    for (int i = 0; i < rounds && alive; i++) {
        Sleep(WATCH_INTERVAL_MS);

        BYTE now[PROLOG_LEN];
        if (!SafeRead(g_hookAddr, now, PROLOG_LEN))
            continue;
        if (memcmp(now, want, PROLOG_LEN) == 0)
            continue;

        if (memcmp(now, g_origBytes, PROLOG_LEN) == 0) {
            if (WriteBytes(g_hookAddr, want, PROLOG_LEN)) {
                LONG n = InterlockedIncrement(&g_rewrites);
                A5_Log("[tersafe] 入口被还原（累计第 %ld 次），已重挂。%.2fs",
                       n, i * WATCH_INTERVAL_MS / 1000.0);
            } else {
                A5_Log("[tersafe] 入口被还原，重挂失败 (err=%lu)，停止看护",
                       GetLastError());
                alive = FALSE;
            }
        } else {
            A5_Log("[tersafe] 入口字节既不是钩子也不是原样，停止看护");
            alive = FALSE;
        }
    }

    A5_Log("[tersafe] 看护结束：%d 秒内共记账 %ld 次、重挂 %ld 次，分配对象槽 %ld 个",
           g_watchSeconds, g_calls, g_rewrites, g_objCount);
    return 0;
}

void TerSafeProbe_Install(void)
{
    g_stubMode = A5_TERSAFE_STUB != 0;

    if (!g_stubMode && !A5_TERSAFE_PROBE) {
        A5_Log("[tersafe] probe=0 stub=0，不挂（TP 照常跑，默认就是这样）");
        return;
    }

    HMODULE exe = GetModuleHandleW(NULL);
    if (!exe) {
        A5_Log("[tersafe] 取不到主模块句柄，跳过");
        return;
    }
    DWORD_PTR delta = (DWORD_PTR)exe - 0x00400000u;
    g_hookAddr = (BYTE *)(VA_B58EC0 + delta);

    BYTE now[16];
    if (!SafeRead(g_hookAddr, now, sizeof(now))) {
        A5_Log("[tersafe] 入口 %p 读不出来，跳过", g_hookAddr);
        return;
    }
    if (memcmp(now, kProlog, PROLOG_LEN) != 0) {
        char got[64], want[64];
        for (int i = 0, u = 0; i < PROLOG_LEN; i++)
            u += _snprintf(got + u, sizeof(got) - u, i ? " %02X" : "%02X", now[i]);
        for (int i = 0, u = 0; i < PROLOG_LEN; i++)
            u += _snprintf(want + u, sizeof(want) - u, i ? " %02X" : "%02X", kProlog[i]);
        A5_Log("[tersafe] 入口 %p 签名对不上，跳过 —— 换客户端了？", g_hookAddr);
        A5_Log("          期望 %s", want);
        A5_Log("          实际 %s", got);
        return;
    }

    if (!BuildThunks())
        return;

    memcpy(g_origBytes, now, PROLOG_LEN);

    BYTE *tramp = (BYTE *)VirtualAlloc(NULL, 16, MEM_COMMIT | MEM_RESERVE,
                                       PAGE_EXECUTE_READWRITE);
    if (!tramp) {
        A5_Log("[tersafe] trampoline VirtualAlloc 失败 (err=%lu)", GetLastError());
        return;
    }
    memcpy(tramp, g_origBytes, PROLOG_LEN);
    tramp[PROLOG_LEN] = 0xE9;
    *(LONG *)(tramp + PROLOG_LEN + 1) =
        (LONG)((g_hookAddr + PROLOG_LEN) - (tramp + PROLOG_LEN + 5));
    g_trampoline = (B58EC0Fn)tramp;

    BYTE patch[PROLOG_LEN];
    BuildEntryPatch(patch);
    if (!WriteBytes(g_hookAddr, patch, PROLOG_LEN)) {
        A5_Log("[tersafe] 写入口失败 (err=%lu)，跳过", GetLastError());
        g_trampoline = NULL;
        return;
    }

    A5_Log("[tersafe] 已挂上 sub_B58EC0 @ %p  ->  My_B58EC0 %p（trampoline %p）",
           g_hookAddr, (void *)My_B58EC0, tramp);

    if (g_stubMode) {
        A5_Log("[tersafe] ★ stub=1 纯桩模式：真 TerSafe.dll 不加载、TenSafe.exe 不启动，"
               "start\\TenProtect\\ 可以删");
        A5_Log("[tersafe] 假对象 %d 字节 × 最多 %d 个，桩表 %p（%d 槽/对象）。"
               "平栈表 19 槽（17 实测 + 2 静态）",
               FAKE_OBJ_SIZE, OBJ_COUNT, g_thunkArea, SLOT_COUNT);
        A5_Log("[tersafe] ★ 日志里出现 `!!` 表外槽 = 纯桩的洞，把那个槽补进 "
               "kStubRetN[] 再跑；出现即大概率随后就崩");
    } else {
        A5_Log("[tersafe] 记账桩表 %p，%d 对象 × %d 槽。等客户端连服务器时抄账",
               g_thunkArea, OBJ_COUNT, SLOT_COUNT);
    }

    if (!A5_WATCH_ENABLED)
        return;

    g_watchSeconds = A5_WATCH_SECONDS;
    if (g_watchSeconds < 1)   g_watchSeconds = 1;
    if (g_watchSeconds > 3600) g_watchSeconds = 3600;

    HANDLE th = CreateThread(NULL, 0, WatchThread, NULL, 0, NULL);
    if (th)
        CloseHandle(th);
    else
        A5_Log("[tersafe] 看护线程起不来 (err=%lu)，钩子本身不受影响", GetLastError());
}
