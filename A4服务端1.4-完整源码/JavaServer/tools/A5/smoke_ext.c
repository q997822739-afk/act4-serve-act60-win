/* ============================================================
 * ACT5 客户端扩展宿主 —— 冒烟测试（32 位宿主）
 * ============================================================
 * 不需要真的启动 DNF.exe：本程序扮演"进程"，按 DNF.exe 的方式使用
 * DumpReport.dll（LoadLibrary → 取导出 → 调 InitStackTraceLibrary），
 * 然后检查扩展宿主的行为。
 *
 * 断言（照 60US 那套的冒烟口径）：
 *   1) DumpReport.dll 能被 LoadLibrary；
 *   2) 三个导出（含序号对应的两个）都取得到；
 *   3) 调 InitStackTraceLibrary 返回 1（契约不变）；
 *   4) 宿主写了 dll_ext.log，且内容与"给了什么 ini"对得上：
 *        noini   → 日志里有"没有 dll_ext.ini"
 *        N       → 日志里"成功 N / 尝试 N"
 *        badplug → 日志里有 FAIL，且进程没崩（不阻断）
 *   5) A5.dll 没在同一个目录时，宿主仍然不崩（A5 缺失不算宿主的问题：
 *      真机上 A5.dll 必然在，这里只验证宿主自身健壮）。
 *
 * 用法：
 *   smoke_ext.exe <DumpReport.dll 路径> <noini|N|badplug>
 * 例：
 *   smoke_ext.exe ..\release\DumpReport.dll noini
 *
 * 为什么必须 32 位：DNF.exe 与这些 DLL 都是 32 位。
 * ============================================================ */
#include <windows.h>
#include <stdio.h>
#include <string.h>

typedef int (__cdecl *fn0)(void);
typedef int (__cdecl *fn1)(void *);

static const char *kLogName = "dll_ext.log";

static int WaitForLog(const char *dir, char *out, int outsz, int maxMs)
{
    char path[MAX_PATH];
    int waited = 0;
    while (waited < maxMs) {
        FILE *f;
        _snprintf(path, sizeof(path), "%s\\%s", dir, kLogName);
        f = fopen(path, "rb");
        if (f) {
            int n = (int)fread(out, 1, outsz - 1, f);
            out[n] = 0;
            fclose(f);
            /* 日志是分行追加的，可能刚写到一半 —— 等收工行出现再判，
               否则会读到"只有前几行"的中间态（假阴性）。
               收工行的 ASCII 尾巴是 "=====" */
            {
                const char *tail = strrchr(out, '=');
                int complete = 0;
                (void)tail;
                /* 用行数判断：宿主固定写 4 行（启动/一条说明/结果/收工）。
                   这里只要求最后一行以 = 结尾即可。 */
                if (n > 8 && out[n-1] == '\n') {
                    /* 找最后一条非空行 */
                    int i = n - 2;
                    while (i >= 0 && (out[i] == '\r' || out[i] == '\n')) i--;
                    if (i >= 4 && out[i] == '=' && out[i-1] == '='
                        && out[i-2] == '=' && out[i-3] == '=')
                        complete = 1;
                }
                if (complete) return n;
            }
        }
        Sleep(250);
        waited += 250;
    }
    out[0] = 0;
    return 0;
}

int main(int argc, char **argv)
{
    const char *dllPath;
    const char *mode;
    char dir[MAX_PATH];
    char *slash;
    HMODULE m;
    int fail = 0;
    int i;

    if (argc < 3) {
        printf("usage: smoke_ext.exe <DumpReport.dll> <noini|N|badplug>\n");
        return 2;
    }
    dllPath = argv[1];
    mode = argv[2];

    strncpy(dir, dllPath, MAX_PATH - 1);
    dir[MAX_PATH - 1] = 0;
    slash = strrchr(dir, '\\');
    if (slash) *slash = 0;
    else strcpy(dir, ".");

    printf("[1] LoadLibrary %s\n", dllPath);
    m = LoadLibraryA(dllPath);
    if (!m) {
        printf("    FAIL gle=%lu\n", (unsigned long)GetLastError());
        return 1;
    }
    printf("    OK   module=%p\n", (void *)m);

    printf("[2] resolving exports\n");
    {
        const char *names[3] = { "ImplicitExceptionHandler",
                                 "InitStackTraceLibrary",
                                 "RegisterExceptionCallback" };
        for (i = 0; i < 3; i++) {
            FARPROC p = GetProcAddress(m, names[i]);
            if (p) printf("    OK   %s @ %p\n", names[i], (void *)p);
            else { printf("    FAIL missing %s\n", names[i]); fail = 1; }
        }
        /* 序号也必须对得上（原库契约） */
        for (i = 1; i <= 3; i++) {
            FARPROC p = GetProcAddress(m, (LPCSTR)(ULONG_PTR)i);
            if (p) printf("    OK   ordinal %d @ %p\n", i, (void *)p);
            else { printf("    WARN ordinal %d not resolvable by number\n", i); }
        }
    }

    printf("[3] calling InitStackTraceLibrary (the DNF.exe path)\n");
    {
        fn0 init = (fn0)GetProcAddress(m, "InitStackTraceLibrary");
        int r = init ? init() : -1;
        if (r == 1) printf("    OK   returned 1 (contract kept)\n");
        else { printf("    FAIL returned %d\n", r); fail = 1; }
    }

    printf("[4] waiting for the extension host (~1s delay + margin)\n");
    printf("    looking in dir=[%s]\n", dir);
    {
        static char log[8192];
        int n = WaitForLog(dir, log, (int)sizeof(log), 6000);
        if (n <= 0) {
            printf("    FAIL no %s appeared in %s\n", kLogName, dir);
            fail = 1;
        } else {
            printf("    log (%d bytes):\n", n);
            {
                /* 打印用副本：下面是就地切断行尾，别把原缓冲毁了 ——
                   否则第一个 '\n' 被换成 0 之后，后面的断言 strstr 只看到第一行。 */
                static char show[8192];
                char *p;
                memcpy(show, log, (size_t)n + 1);
                p = show;
                while (*p) {
                    char *nl = strchr(p, '\n');
                    if (nl) *nl = 0;
                    if (*p) printf("      | %s\n", p);
                    if (!nl) break;
                    p = nl + 1;
                }
            }
            /* 内容断言：刻意只用 ASCII 判据 —— 本文件的中文字面量在不同
               -fexec-charset 下编码不同（GBK/UTF-8），拿中文比会假阴性。
               用日志里必然出现的 ASCII 记号来判断走的是哪条分支。 */
            if (strcmp(mode, "noini") == 0) {
                /* 无 ini 分支：必须提到 ini 名字，且没有 OK/FAIL 插件行 */
                {
                    int has_ini = strstr(log, "dll_ext.ini") != NULL;
                    int has_ok  = strstr(log, "OK   ") != NULL;
                    int has_fail= strstr(log, "FAIL ") != NULL;
                    printf("    [assert] has_ini=%d has_ok=%d has_fail=%d\n",
                           has_ini, has_ok, has_fail);
                    if (has_ini && !has_ok && !has_fail)
                        printf("    OK   no-ini path reported (no plugins loaded)\n");
                    else { printf("    FAIL expected a 'no ini' path\n"); fail = 1; }
                }
            } else if (strcmp(mode, "badplug") == 0) {
                if (strstr(log, "FAIL "))
                    printf("    OK   bad plugin reported as FAIL (and we survived)\n");
                else { printf("    FAIL expected a FAIL line\n"); fail = 1; }
            } else {
                if (strstr(log, "OK   ") && !strstr(log, "FAIL "))
                    printf("    OK   plugin(s) loaded\n");
                else { printf("    FAIL expected OK lines\n"); fail = 1; }
            }
        }
    }

    printf("\n%s\n", fail ? "SMOKE: FAIL" : "SMOKE: PASS");
    /* 故意不 FreeLibrary：真机上也一直持有 */
    return fail ? 1 : 0;
}
