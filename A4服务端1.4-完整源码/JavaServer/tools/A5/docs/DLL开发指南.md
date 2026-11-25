# ACT5 客户端 DLL 扩展开发指南（面向插件作者）

> **本文给谁看**：想给 S1A5（DNF ACT5）客户端写 DLL 扩展（插件）的开发者 ——
> 从"这是什么机制"到"怎么写、怎么装、怎么排障"，一篇讲全。
>
> **三份文档分工**：本文 = **插件怎么写**；《[DLL扩展说明](../DLL扩展说明.md)》= 玩家向
> （怎么装、怎么看日志、怎么回退）；《[插件说明规范 v1](插件说明规范_v1.md)》=
> 插件的"人话说明"怎么写（纯展示层，让启动器显示小字与【详细】弹窗）。
>
> **现役环境快照**：扩展宿主 = 客户端目录的 `DumpReport.dll`（10752 字节，
> 源码 `tools\A5\ext_host.cpp`）；客户端补丁 = `A5.dll`（172544 字节，
> md5 `72673d8fea9b308d79362b792a04d0f6`，**任何情况下都不动**）；客户端 = `DNF.exe`（32 位 x86）。

---

## 0. 十分钟版本（TL;DR）

1. 写一个 **32 位 x86 DLL**。宿主只对它做 `LoadLibrary`，**不要求任何导出**、不调用任何函数 ——
   你的 `DllMain` 就是入口。
2. `DLL_PROCESS_ATTACH` 里只做轻活：记下模块句柄、`CreateThread` 把活儿交给工作线程、
   立刻返回（loader lock 规则，见 §2.3）。
3. 把 DLL 放进客户端目录（DNF.exe 旁边），在 `dll_ext.ini` 的 `[plugins]` 节加一行
   `N=你的插件.dll`。
4. 重启客户端 → 看客户端目录的 `dll_ext.log`：出现 `OK   插件[N] 已加载: 你的插件.dll`
   即成功（全日志逐行解读见 §8.1）。
5. 插件**加载失败只记日志，游戏照常启动**；但你自己 `DllMain` / 线程里的代码崩了 = 游戏崩 ——
   宿主不做任何隔离。
6. 游戏本体**不会调用你的插件**。一切触发 —— hook、线程、轮询 —— 全部自建。
7. 有一条红线：**不要覆盖或劫持客户端原有的 DLL**，尤其 `A5.dll`（客户端补丁本体）与
   `DumpReport.dll`（宿主自身）。插件起独立的名字。

---

## 1. 整套机制

### 1.1 加载链

```
DNF.exe (32 位)
  └─ DumpReport.dll        ← 静态导入（按名字）：DNF.exe 必须用它，所以必然被加载
       ├─ A5Anchor() ────→ A5.dll        ← 客户端补丁本体（TP 解包 / 反外挂桩 / 字体 /
       │                                    约 130 处代码补丁）。宿主会**同步**把它拉起来，
       │                                    补丁时机与改动前完全一致。
       └─ dll_ext.ini ───→ 逐个 LoadLibrary ← 【你的插件在这里被加载】
                            （进程启动约 1 秒后，在宿主自己的工作线程里）
```

- **为什么挂在 `DumpReport.dll` 上**：`DNF.exe` 的导入表里按名字引用 `DumpReport.dll` 的
  `InitStackTraceLibrary` / `RegisterExceptionCallback` 两个函数 —— 它是客户端启动期
  **必然**被加载的稳定入口，不需要任何注入/劫持手段。
- `DumpReport.dll` 的导出契约（**不许改**）：`ImplicitExceptionHandler@1` /
  `InitStackTraceLibrary@2` / `RegisterExceptionCallback@3`（名字 + 序号都固定）。
- `DumpReport.dll` 的导入表恰好 `kernel32.dll` + `A5.dll`（宿主自身的"无 CRT"硬约束）。
- **`A5.dll` 是客户端补丁，不是插件**：它由这条链直接拉起，**不在** `dll_ext.ini` 里、
  不能被关闭、也不该被任何插件替换。没有它杀怪不上报、怪物打不死。

### 1.2 宿主加载时序（你的插件发生在第 ⑤ 步）

```
① 装载器解析 DNF.exe 的静态导入 → 加载 DumpReport.dll → 调它的 DllMain(DLL_PROCESS_ATTACH)
② DllMain 里依次：DisableThreadLibraryCalls → A5Anchor()（把 A5.dll 拉起来，补丁立即生效）
                                    → ExtHost_Install()（建宿主工作线程，立刻返回）
③ 装载继续：A5.dll 的 DllMain 跑完，进程初始化往下走
④ 宿主工作线程先 Sleep(1000)  —— 避开 loader lock 窗口
⑤ 线程醒来：写 dll_ext.log 抬头 → 读 dll_ext.ini 的 [plugins] 段
       → 逐条 LoadLibrary(<客户端目录>\<条目>)   ← 【你的 DllMain 在这里被调】
       → 每条的成败各写一行日志
⑥ 写"宿主收工"，线程退出；游戏继续跑
```

**关键点**：你的插件是在**进程启动约 1 秒后**、由宿主**自己的工作线程**加载的 —— 这比
60US 那套（在代理自己的 DllMain 里、进程初始化最深处加载）宽松得多。但 `DllMain` 本身的
纪律不变：**调用你的 DllMain 时 loader lock 依然被持有**（这是 Windows 的规则，与谁调用无关）。

### 1.3 相关文件一览

| 位置 | 文件 | 谁写谁读 |
|---|---|---|
| 客户端目录 | `DNF.exe` | 游戏本体，**不动** |
| 客户端目录 | `A5.dll` | 客户端补丁本体，**不动**（md5 `72673d8f…`） |
| 客户端目录 | `DumpReport.dll` | 扩展宿主（本项目源码 `tools\A5\ext_host.cpp` + `dumpreport_shim.cpp`） |
| 客户端目录 | `dll_ext.ini` | **三方接口**：宿主启动时读 / 启动器挂载时写 / 玩家可手工改（§6.3） |
| 客户端目录 | `dll_ext.log` | 宿主只追加（§8.1 逐行解读） |
| 客户端目录 | 各插件 `.dll` | 你的插件本体；宿主按"客户端目录 + ini 条目"拼接定位（§6.1） |
| 启动器目录 | `runtime\DLL扩展\*.dll` + `<名>.plugin.json` | 启动器扩展库（挂载时复制进客户端目录） |
| 启动器目录 | `runtime\DLL扩展\备份\` | 挂载时被同名顶掉的客户端原文件的备份，撤下时还原 |

---

## 2. 你的插件运行在什么环境（写码前必读的事实）

### 2.1 进程与版本基线

- **32 位 x86 进程**，客户端是 2009 年代老引擎。你的插件同样必须是 **32 位 x86** DLL
  （64 位在 `LoadLibrary` 时直接 `gle=193` 失败）。
- 目标系统放宽到老 Windows 也没问题（`WINVER/_WIN32_WINNT` 定义 `0x0501` 即可）。
  运行在现代 Windows 上一切照常。

### 2.2 加载时机：很早，但没有 60US 那么早

- 加载发生在**进程启动约 1 秒后**（宿主线程里的 `Sleep(1000)`，见 `ext_host.cpp` 的
  `START_DELAY_MS`）。
- 但"约 1 秒"不代表游戏就绪：那时窗口可能还没出来、D3D 没起、资源没载、游戏循环更没开始。
  想要"等游戏可绪"得自己等（见 §2.3 与 §3.1 的门控做法）。
- 反过来，这个时机**不适合做"进程最开始"的事**（比如接管某个早期初始化调用）——
  真有这种需求就不该走插件这条路。

### 2.3 loader lock 持有中 —— DllMain 经典纪律全部适用

你的 `DllMain` 是在宿主的 `LoadLibrary` 调用里被调进来的，**此刻加载器锁被持有**：

- **只做轻活**：置标志、读自身路径、`CreateThread` 后立刻返回。
  不要等待/同步其它线程、不要 `SendMessage` 跨进程窗口、不要 COM、不要调用可能二次
  获取加载器锁的 API。
- `CreateThread` 允许（标准做法）：新线程要等 loader lock 释放才真正开跑 ——
  所以线程开头 `Sleep` 一小段 + 轮询"游戏是否就绪"是这里验证过的正确姿势。
- 在 `DllMain` 里 `LoadLibrary` 你自己的依赖 DLL 是可以的，但注意 §2.4。
- 进程里不保证在场的库：`user32`/`gdi32`/`d3d8` 等此时可能还没被加载（`A5.dll` 自己就
  遇到过这个）。要么把 API 解析推迟到你的工作线程，要么自己 `LoadLibrary + GetProcAddress`。
- `DllMain` 返回 `FALSE` = 加载失败（`gle=1114`），宿主会记 `FAIL` 并继续 —— **游戏不受影响**。

### 2.4 依赖与运行库

- 插件可以用正常 CRT（`stdio`/`string.h` 随便），不必学宿主的 no-CRT ——
  那是宿主自身的硬约束（它的导入表只许 `kernel32 + A5.dll`），不是你的。
- 但你产生的依赖（UCRT、libgcc、pthread-w32…）加载时也必须找得到：
  **放客户端目录**（DNF.exe 所在目录是依赖搜索的第一站）或静态链接
  （MinGW 加 `-static-libgcc` 或 `-static`；MSVC 用 `/MT`）。
- **依赖找不到 = `gle=126`**（§8.2）。
- 客户端目录现成可蹭的运行库：`msvcr71.dll`（老 VC71 运行库，游戏自带）——
  能蹭但别赌，静态链接最稳。
- 子目录里的插件：宿主的 `LoadLibrary` 不带 `LOAD_WITH_ALTERED_SEARCH_PATH`，
  所以**插件自己所在的子目录不会自动进依赖搜索路径** —— 依赖请放客户端目录，
  或者插件自己按绝对路径加载依赖。

### 2.5 没有人会调用你

宿主只 `LoadLibrary`，**不调用任何导出、不要求任何导出**。客户端游戏本体也不会知道你在。
**结论**：不要指望"宿主调用我的导出"这种模式。挂上 = 你的 `DllMain` 自己干活。
（想看游戏在干什么：hook 你感兴趣的客户端函数；事件源与地址要自己找 / 自己逆向。）

### 2.6 没有"卸载"

宿主只 `LoadLibrary` 不 `FreeLibrary`。`DLL_PROCESS_DETACH` 只在进程退出时来
（而且那时别的线程早已被杀，别在 detach 里做需要线程配合的清理）。插件常驻到进程结束。
**改插件 = 换文件 + 重启客户端**（运行中 DLL 被映射，删不掉也覆盖不了）。

### 2.7 红线：不要覆盖或劫持客户端原有的 DLL

- `A5.dll`：客户端补丁本体（md5 `72673d8fea9b308d79362b792a04d0f6`）。
  **任何插件都不该碰它**，也不该试图用同名文件顶替它。
- `DumpReport.dll`：宿主自身。顶替它 = 整个扩展链断掉。
- 其它客户端文件（`ijl15.dll`、`d3d8.dll`、`version.dll`、`BugTrap.dll`、`RS.dll` …）：
  它们是客户端的组成部分，劫持它们属于"玩火"——出了问题很难分清是谁的责任。
- **给插件起独立的名字**（如 `MyPlugin.dll`）。同名冲突时启动器会备份旧文件、撤下时还原，
  但别依赖这个兜底。

### 2.8 基址：ACT5 不要求钉（与 60US 的关键差异）

60US 那套要求插件"链接期钉死加载基址"（`-Wl,--image-base=… --disable-dynamicbase`），
因为**那个客户端的启动器有 50 多个 `VirtualAllocEx` 动态分配的焊点桩页**，
模块基址一变，桩页落点整体平移，曾造成"Boss 击杀瞬间卡死"的事故。

**ACT5 没有这个问题**：启动器**零注入**（没有 `VirtualAllocEx`/`WriteProcessMemory`），
客户端也没有那套焊点桩页。所以：

- **不需要、也不建议钉基址** —— 保持默认的 `.reloc` 即可。
- 照抄 60US 的钉基址要求在这里只会多一个"基址被占"的分支，没有收益。
- 如果你自己出于其它理由要钉（比如代码里写了绝对地址假设），那请自便 ——
  但这不是本项目的要求。

### 2.9 数字速查

| 项 | 值 | 出处（`ext_host.cpp`） |
|---|---|---|
| 插件数上限 | **64** 个，多出的静默忽略 | `MAX_PLUGINS` |
| ini 大小上限 | 整文件 1 MB，超出当"无 ini" | `LoadPlugins()` 的 `size > (1u << 20)` |
| 单行缓冲上限 | 700 字符（仅影响日志行的构造，不影响 ini 解析） | `MAX_LINE` |
| 加载延迟 | 进程启动后约 **1000 ms** | `START_DELAY_MS` |
| 插件路径 | 无路径分隔符 → `<客户端目录>\<名字>`；**带 `\` `/` → 原样交给系统** | `LoadPlugins()` |
| ini 编码 | UTF-8（带/不带 BOM）或 ANSI(GBK)；**UTF-16 整份拒绝** | `LooksUtf8` / BOM 检查 |
| 加载顺序 | ini 行序（= 优先级） | 顺序解析 |
| 日志 | `<客户端目录>\dll_ext.log` 追加，格式 `[HH:MM:SS] msg` | `Log()` / `LFlush()` |

---

## 3. 写一个最小插件（可编译范例）

### 3.1 源码骨架（marker + 工作线程，姿势标准）

```c
/* my_first_plugin.c — ACT5 扩展宿主插件最小骨架
 * 规矩：DllMain 轻活 + CreateThread；重活全在线程里，线程开头先避 loader lock。 */
#include <windows.h>

static volatile int g_running = 0;
static HMODULE g_self = NULL;

static DWORD WINAPI worker_thread(LPVOID arg)
{
    (void)arg;

    /* ① 避开 loader lock 窗口。宿主在进程启动约 1 秒后加载我们，
       所以这里再等一会儿就够了；要更稳妥可以轮询"主窗口是否出现"。 */
    Sleep(1000);

    /* ② 这里起才安全：解析 API、建窗口、hook、读配置…… */
    {
        /* 示例：写一个 marker 证明真的跑起来了（全 W API，中文目录安全） */
        wchar_t path[MAX_PATH];
        DWORD n = GetModuleFileNameW(g_self, path, MAX_PATH);
        if (n > 0 && n < MAX_PATH) {
            /* 把结尾的 .dll 换成 .log */
            wchar_t *dot = wcsrchr(path, L'.');
            if (dot) lstrcpyW(dot, L".log");

            HANDLE fh = CreateFileW(path, FILE_APPEND_DATA,
                                    FILE_SHARE_READ | FILE_SHARE_WRITE, NULL,
                                    OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
            if (fh != INVALID_HANDLE_VALUE) {
                DWORD w = 0;
                WriteFile(fh, "my plugin alive\r\n", 17, &w, NULL);
                CloseHandle(fh);
            }
        }
    }

    /* ③ 主循环示例 */
    while (g_running) {
        /* ……你的功能…… */
        Sleep(500);
    }
    return 0;
}

BOOL WINAPI DllMain(HINSTANCE self, DWORD reason, LPVOID reserved)
{
    (void)reserved;
    if (reason == DLL_PROCESS_ATTACH) {
        g_self = self;
        DisableThreadLibraryCalls(self);       /* 省去无谓的线程 attach/detach 通知 */
        g_running = 1;
        {
            HANDLE h = CreateThread(NULL, 0, worker_thread, NULL, 0, NULL);
            if (h) CloseHandle(h);
        }
    } else if (reason == DLL_PROCESS_DETACH) {
        g_running = 0;   /* 只发生在进程退出；别指望它做复杂清理 */
    }
    return TRUE;         /* 返回 FALSE = gle=1114，加载失败（游戏不受影响） */
}
```

### 3.2 编译

本机在用的 32 位工具链是 **LLVM-MinGW i686**（你自己的 `DfoVibration_OLD` 项目也在用它）：

```bat
set "TOOL=C:\Users\12290\AppData\Local\Microsoft\WinGet\Packages\MartinStorsjo.LLVM-MinGW.UCRT_Microsoft.Winget.Source_8wekyb3d8bbwe\llvm-mingw-20260602-ucrt-x86_64\bin"

"%TOOL%\i686-w64-mingw32-gcc.exe" -m32 -O2 -shared -static-libgcc ^
  -o MyPlugin.dll my_first_plugin.c -lkernel32
```

- **不需要 `.def`、不需要任何导出** —— 宿主只 `LoadLibrary`。
- 用了 C++ 就换 `i686-w64-mingw32-g++.exe`，需要 `-static-libstdc++`。
- 用了 CRT 又想少依赖：`-static-libgcc`（或干脆 `-static`）；
  MSVC 侧等价 `cl /LD /MT`。
- **不需要钉基址**（§2.8）。
- 验证产物架构：`"%TOOL%\llvm-readobj.exe" --file-headers MyPlugin.dll | findstr Machine`
  应显示 `IMAGE_FILE_MACHINE_I386`。

### 3.3 装载三步

1. 把 `MyPlugin.dll` 放进客户端目录（`DNF.exe` 旁边）。
2. 编辑客户端目录的 `dll_ext.ini`（UTF-8 无 BOM 或 ANSI 都行）：
   ```ini
   [plugins]
   1=MyPlugin.dll
   ```
3. **重启客户端**，看 `dll_ext.log`：
   ```
   [10:00:01] OK   插件[1] 已加载: MyPlugin.dll
   [10:00:01] INFO 插件加载完毕：成功 1 / 尝试 1
   ```
   失败按 §8.2 对号入座。

### 3.4 不开游戏自测

自测你的 `DllMain` 不崩、依赖齐全：用任意 **32 位**宿主 `LoadLibrary` 你的插件即可。
注意 **64 位程序加载不了 32 位 DLL**（`gle=193`），测试宿主必须 32 位。

本仓库现成的例子：`tools\A5\smoke_ext.exe`（32 位；它还顺带验证宿主自身 —— 用法见
`smoke_ext.c` 头部注释）。

---

## 4. 真实范例：DfoVibration_OLD（可参考的 32 位插件）

现成已实机验证的插件（手柄力反馈震动）：hook 游戏事件 → 写共享内存 → 常驻面板程序
消费并驱动手柄。

**源码**（本机）：`E:\LX\DfoVibration_OLD\DLL源码\` ——
`dfovib_old.c`（主模块/共享内存/去抖）+ `hooks_old.c/.s`（inline hook 引擎）
+ `common\`（协议/ini 工具）。

### 4.1 值得抄的工程决策

| 决策 | 对应哪条环境事实 |
|---|---|
| `DllMain` 里只置标志 + `CreateThread`（worker/collector 双线程） | §2.3 loader lock 轻活 |
| worker 线程开头 `Sleep(1000)` | §2.2 时机很早 + §2.3 线程要等 lock 释放 |
| 事件去抖/限频（高频 hook 无条件 push 曾造成事件风暴） | **hook 高频函数必配去抖**，这是实机教训 |
| 30s tick 统计日志（一眼看出事件有没有发、哪类发了） | 排障靠日志，不靠弹窗 |
| 60s 一次 hook 完整性自检（锚点首字节应为 `E9`，被覆写就 WARN） | 与其它 hook 共存的自保 |
| `DLL_PROCESS_DETACH` 里只做轻量回收 | §2.6 没有真正意义的卸载 |

### 4.2 别学的地方

它在 60US 上要求钉基址（`--image-base`）—— 那是 60US 客户端/启动器的特殊性，
**ACT5 不需要**（§2.8）。

---

## 5. 插件作者守则（十条）

1. **32 位 x86 only**；64 位 = `gle=193` 挂不上。
2. **`DllMain` 轻活**：置标志、开线程、返回。等待/跨线程同步/COM/跨进程消息一律推迟到线程里（§2.3）。
3. **你的崩溃 = 游戏的崩溃**。宿主不隔离插件：你线程里一个空指针，玩家丢一局。
   核心逻辑配 SEH（`__try/__except`，MSVC）或至少配日志。
4. **失败别弹窗**：启动期 `MessageBox` 会挂住整条启动链。写日志文件。
5. **别覆盖客户端原有 DLL**（`A5.dll`/`DumpReport.dll`/`ijl15.dll`/`d3d8.dll`…）——
   给插件起独立的名字（§2.7）。
6. **换插件/改 ini 前完全退出游戏**：运行中 DLL 被映射，删不掉也覆盖不了。
7. **路径全用 W API**：客户端目录可能含中文，用 `*W` + 宽字符，别用 A API 读自己的路径。
8. **依赖最小化**：静态链接优先；非静态依赖放客户端目录。`gle=126` 九成是依赖缺失。
9. **不要动 `A5.dll` 的地盘**：它对客户端代码做的补丁是它的事。你的 hook 只装在你
   自己找到的锚点上 —— 装前先查首字节，避免与别人的 hook 套娃（§4.1 的自检思路）。
10. **变更后进游戏冒烟**：改完插件（或换了插件组合）进游戏打一局，确认
    **怪物能被打死**（= `A5.dll` 仍在正常工作）且你自己的功能生效。

---

## 6. dll_ext.ini 与挂载（权威口径）

> 宿主解析实现：`tools\A5\ext_host.cpp` 的 `LoadPlugins()`；
> 启动器读写：`启动器源码\Services\DllExtService.cs`。两边口径已对齐。

### 6.1 语法细则（宿主逐条实现）

| 规则 | 说明 |
|---|---|
| 节 | 只认 `[plugins]`（ASCII 大小写不敏感）；其它节的行全忽略 |
| 条目 | 节内含 `=` 的行；**`=` 前面的键名被忽略**（写 `1=` `2=` 只是给人看的习惯），`=` 后面的值才是文件名 |
| 值清洗 | 前导空格/Tab 跳过；行尾空白裁掉；空值行忽略 |
| 注释 | 行首（裁剪后）`;` 或 `#` 的行跳过 |
| 文件名定位 | 无路径分隔符 → `<宿主所在目录>\<值>`（即客户端目录）；**带 `\` 或 `/` → 原样交给系统解析**（相对当前工作目录，或写绝对路径） |
| 数量/大小 | 最多 **64** 条（超出忽略）；整文件最多 **1 MB**（超出当"无 ini"） |
| 编码 | UTF-8（带 BOM 也行，会跳过 BOM）或 ANSI(GBK)；**UTF-16（`FF FE`/`FE FF` BOM）→ 整份拒绝**（等价"无 ini"，一个都不挂，游戏不受影响）；CRLF/LF 均可 |
| 加载 | 按行序逐个 `LoadLibrary`；成功记一行 `OK`，失败记一行 `FAIL` 后**继续下一个** |

### 6.2 行序 = 加载顺序 = 优先级

先挂载的先 `DllMain`。多个插件要 hook 同一锚点时，后装的会 hook 在先装的之上（套娃），
谁在外层由行序决定。启动器【DLL 扩展】页的 **⋮ 拖拽排序**就是在排这个行序。

### 6.3 三方接口（宿主读 / 启动器写 / 人手工改）

- **启动器写盘规矩**：一律 UTF-8 无 BOM；写后**回读校验**（条目没写进去/没删掉会明确报错，
  最常见原因是 ini 正被编辑器占用）。
- **合并规则**：挂载/撤下只增删**启动器登记过**的条目；你手工加进 ini 的行启动器永远不动
  （但建议遵守 §6.1 语法，写在 `[plugins]` 节内）。
- **挂载语义**：复制进客户端目录 + 注册 ini 条目；万一插件名与客户端文件同名，
  旧文件先备份到 `扩展目录\备份\`，撤下时还原；上次挂载、本次未勾选的件会被回收
  （文件 + 条目一起撤）。
- **手工装**（不走启动器）：DLL 放客户端目录 + ini 加行 + 重启客户端，三步与 §3.3 相同。

### 6.4 常见翻车点

- ini 存成了 **UTF-16**（记事本"Unicode"）→ 宿主静默零挂载（日志里有一行 `WARN`）。
  另存为 UTF-8 或 ANSI。
- 文件名拼错/大小写不符/文件没真放进客户端目录 → `gle=126`。
- 第 65 个插件没加载 → 64 上限，静默忽略。
- ini 里行序随手调 → 加载优先级跟着变（§6.2）。
- 改完 ini 忘了重启客户端 → 进程期只加载一次，不热加载。

---

## 7. 让启动器显示你的插件说明

启动器【DLL 扩展】页给每个插件显示一句话小字 +【详细】弹窗，说明来源按
四级回退链取第一个命中：

1. **随件说明文件** `<DLL名>.plugin.json`（UTF-8 无 BOM，与 DLL 同目录）——**推荐**，信息最全；
2. 启动器**内置目录**（按 md5 命中，只收实机验证过的插件）；
3. DLL 的 PE **版本资源**（`FileDescription`/`Comments`/`ProductVersion`/`CompanyName`）；
4. 默认文案（"未提供说明…"）。

最小 `.plugin.json`：

```json
{
  "schema": 1,
  "name": "我的插件",
  "version": "1.0.0",
  "author": "你的名字",
  "summary": "一句话说明（列表页小字）",
  "notes": ["注意事项一", "注意事项二"],
  "homepage": ""
}
```

字段含义、四级回退的精确规则、文件属性写法 —— 见
《[插件说明规范 v1](插件说明规范_v1.md)》。

---

## 8. 排障

### 8.1 `dll_ext.log` 逐行解读

一次正常的会话（带两个插件、其中一个坏了）：

```
[10:00:00] ===== ACT5 DLL 扩展宿主启动（挂在 A5 加载链上）=====
[10:00:00] INFO A5.dll 已由同链加载；现在读扩展清单
[10:00:00] OK   插件[1] 已加载: GoodPlugin.dll
[10:00:00] FAIL 插件[2] 加载失败 gle=126: Missing.dll
[10:00:00] INFO 插件加载完毕：成功 1 / 尝试 2
[10:00:00] ===== 宿主收工（游戏继续运行）=====
```

| 日志行 | 含义 | 怎么办 |
|---|---|---|
| 只有 5 行、且没有 `OK`/`FAIL` 插件行，而是 `INFO [plugins] 段里没有启用的条目 —— 零挂载` | ini 在，但一行都没启用 | 检查 ini 条目是不是都被注释了 |
| `INFO 没有 dll_ext.ini —— 零挂载（等价于改动前）` | 清单文件不在客户端目录（或名字不对） | 新建/改名成 `dll_ext.ini` |
| `WARN dll_ext.ini 是 UTF-16 —— 不支持，零挂载` | 编码错 | 用记事本另存为 UTF-8 |
| `WARN dll_ext.ini 大小异常 —— 当无 ini 处理，零挂载` | 文件 0 字节或超过 1 MB | 检查文件 |
| `FAIL 插件[N] 加载失败 gle=…` | 见 §8.2 | 对号入座 |
| `OK   插件[N] 已加载: …` | 成功（`DllMain` 返回了 `TRUE`） | —— |

**注意**：日志**没有**"游戏目录/路径"之类的字样 —— 如果 `dll_ext.log` 里出现的是
**别的项目**的日志格式，说明你看错了客户端目录（或者有另一个宿主在跑）。

### 8.2 `gle` 错误码表

| gle | 含义 | 最常见原因 |
|---|---|---|
| **126** | `ERROR_MOD_NOT_FOUND` | 文件不在（名字写错/没放进客户端目录）；**或插件的依赖 DLL 缺失** |
| **193** | `ERROR_BAD_EXE_FORMAT` | 不是有效的 32 位 DLL —— 64 位 DLL / 文件损坏 |
| **1114** | `ERROR_INIT_FAILED` | 你的 `DllMain` 返回了 `FALSE`，或初始化失败 |
| **5** | `ERROR_ACCESS_DENIED` | 权限/杀软拦截 |
| **998** | `ERROR_NOACCESS` | 无效访问（少见，通常是文件严重损坏） |

### 8.3 "插上了但没效果"

宿主日志说 `OK` 只代表 `DllMain` 返回了 `TRUE` —— **功能是否生效要看你自己的插件日志**。
按 §3.1 的写法给你的插件写一个 marker/日志文件，先确认"线程真的跑起来了"，
再往下查功能。

### 8.4 "游戏起不来了 / 打不死怪了"

- 打不死怪 = `A5.dll` 没生效。**先看 `dll_ext.log` 有没有抬头**：
  - 没有抬头 → `DumpReport.dll` 可能被顶替了 → 用备份还原（见
    《[DLL扩展说明](../DLL扩展说明.md)》§5 回退）。
  - 有抬头 → 宿主跑起来了，问题在某个插件：把 `dll_ext.ini` 全注释掉（零挂载），
    重启验证游戏是否正常 → 逐个恢复条目定位。
- 永远别用"改 `A5.dll`/`DumpReport.dll`"来排障 —— 回退件在哪见玩家向文档 §5。

---

*（本文与实现同步维护；实现改动时同步更新本文。）*
