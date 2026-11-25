# ACT5 客户端 DLL 扩展支持

**一句话**：客户端现在支持"把扩展 DLL 丢进客户端目录、在 `dll_ext.ini` 里登记一行"
就完成扩展，全程**不改 DNF.exe、不改 A5.dll**。

---

## 1. 它是怎么挂上去的（为什么不用改 A5.dll）

```
DNF.exe --[按名字导入]--> DumpReport.dll      ← 客户端原本就静态导入它
                              ├─ A5Anchor()  → A5.dll   （★ 一个字节都没动）
                              └─ dll_ext.ini → 逐个 LoadLibrary 扩展 DLL
```

- `DNF.exe` 的导入表里按名字引用 `DumpReport.dll` 的
  `InitStackTraceLibrary` / `RegisterExceptionCallback`，所以同目录的
  `DumpReport.dll` 必然在进程启动时被加载。
- 仓库里那个 `DumpReport.dll` 是**我们自己的小壳**（源码 `dumpreport_shim.cpp`），
  它本来只做一件事：`A5Anchor()` 把真正的 `A5.dll` 拉进进程。
- 本次把这一步扩成"拉 A5.dll **+** 读 `dll_ext.ini` 加载扩展 DLL"，
  于是扩展宿主复用了客户端**本来就在走**的那条加载链。

**所以 A5.dll 不参与改动**：它的源码是 MSVC 专有的（`__try` + 依赖 MSVC 栈帧偏移的
内联汇编），本机只有 LLVM-MinGW，重编它反而是最大的"有损"。硬约束是
"A5.dll md5 必须保持 `72673d8fea9b308d79362b792a04d0f6` 不变"。

---

## 2. 装一个扩展 DLL

1. 把扩展 `.dll` 放进客户端目录（`DNF.exe` 旁边），或写相对/绝对路径；
2. 编辑同目录的 `dll_ext.ini`，在 `[plugins]` 段里加一行：

```ini
[plugins]
1=你的扩展.dll
2=另一个扩展.dll
```

3. **重启游戏**（启动时加载一次，游戏中不会热加载）。

一行都不写（保持全注释）= 一个都不加载，行为与没装扩展**完全一致**。

### 清单规则（照 60US 那套的口径）

| 项 | 规则 |
| --- | --- |
| 段落 | 只认 `[plugins]` 段，段外的行忽略 |
| 注释 | 行首 `;` 或 `#` |
| 顺序 | 按文件里出现的顺序加载（序号只是写法） |
| 编码 | UTF-8（带不带 BOM 都行）或 ANSI(GBK) 都支持；**UTF-16 不认** → 当成"没有清单" |
| 文件名 | 不带路径分隔符 → 按客户端目录解析；带 `\` 或 `/` → 照原样用 |
| 条数上限 | 64 条 |

### 加载结果看 `dll_ext.log`（同一目录）

```
[07:45:13] ===== ACT5 DLL 扩展宿主启动（挂在 A5 加载链上）=====
[07:45:13] INFO A5.dll 已由同链加载；现在读扩展清单
[07:45:13] OK   插件[1] 已加载: TestPlugin.dll
[07:45:13] INFO 插件加载完毕：成功 1 / 尝试 1
[07:45:13] ===== 宿主收工（游戏继续运行）=====
```

| 日志 | 含义 |
| --- | --- |
| `没有 dll_ext.ini` | 清单不在客户端目录（或名字不对）→ 零挂载 |
| `[plugins] 段里没有启用的条目` | 清单在，但一行都没启用 → 零挂载 |
| `UTF-16 —— 不支持` | 另存为 UTF-8 或 ANSI |
| `FAIL ... gle=126` | 找不到那个 dll（名字写错 / 没放进来） |
| `FAIL ... gle=193` | 那个文件不是有效的 32 位 DLL |
| `OK ... 已加载` | 成功 |

**失败只写日志，不阻断游戏**，也不影响清单里其它插件（60US 的降级口径）。

---

## 2.5 启动器挂载的备份语义（改了你的文件，怎么保证能还回来）

用启动器【DLL 扩展】页挂载/撤下时，围绕"客户端目录里那个同名文件是谁的"有一套规矩：

- **只有真的是你的文件才会被备份。** 启动器记着"上次挂进去的是哪一份（内容 md5）"；
  目标位置放着的若是它自己上次拷的，就直接覆盖，不产生备份。
- **备份只在撤下时还原，用完即弃。** 撤下后备份文件会被清掉 —— 不留陈旧备份，
  免得下一次挂载把陈年备份误当成"你的原文件"又给你装回去。
- **撤下 = 回到挂载前的样子。** 你原本没有同名文件 → 撤下后也没有；
  你原本有一个（比如自己改过的版本）→ 撤下后原样回来。
- **手工写进 `dll_ext.ini` 的行永远不动。** 启动器只增删自己登记的条目；
  你手工加的行（指向扩展目录之外的 dll）位置和内容都保持原样，且排在管理条目前面。

> 这三条是 2026-10-09 修掉的三个真实缺陷的结论（连续两次【应用更改】后撤下留文件、
> 玩家原文件被无备份覆盖、陈旧备份被"还原"）。回归用例在
> `tests\dllext_probe\`（`run.cmd`，47 条断言），改这块代码前先跑它。

---

## 3. 为什么不会重犯 60US 的那些问题

60US 那套（ijl15 代理）出过的事故与这次的设计取舍一一对应：

| 60US 出过的事 | 根因 | 本工程怎么避开 |
| --- | --- | --- |
| **Boss 击杀卡死** | 代理 DLL 被 ASLR 随机分配基址 → 平移了启动器 50+ 个 `VirtualAllocEx(NULL)` **焊点桩页**的落点 | ACT5 启动器**零注入**（查过：没有 `VirtualAllocEx`/`WriteProcessMemory`），没有焊点桩页，不存在布局敏感性 → **不钉基址**，保持 `.reloc` |
| 按序号导入导致启动即崩 | nengine 按**序号** 2/3/4/5 导入 ijl15 | ACT5 的 `DumpReport.dll` 是 DNF.exe **按名字**导入的，导出表用 `.def` 固定，序数照旧（1/2/3） |
| ini 被编辑器改写后静默失效 | 三方接口 | 加载失败/没清单都写日志；`dll_ext.log` 一目了然地说明当局读到了什么 |
| 客户端文件被"还原"顶掉 | 人为重解包 | 部署时 `.before-ext-host` 备份 + `vendor\DumpReport.dll` 原厂件双退路 |

---

## 4. 构建（改完扩展宿主后）

```bat
cd 服务端源码\tools\A5
build-clang.cmd                     :: → release\DumpReport.dll
python verify_dumpreport.py         :: 10 项字节级校验（导出/序号/导入/无CRT）
smoke_ext.exe release\DumpReport.dll noini    :: 三个冒烟场景见脚本头
```

`build-clang.cmd` 用 **LLVM-MinGW i686**（`C:\Users\12290\...\llvm-mingw-*\bin`），
因为本机没有 MSVC。它只编 `DumpReport.dll`（壳 + 扩展宿主），**不碰 A5.dll**。

校验口径（`verify_dumpreport.py` 全 PASS 才部署）：
- 导出表与改动前**逐名字 + 逐序号**一致（`ImplicitExceptionHandler@1` /
  `InitStackTraceLibrary@2` / `RegisterExceptionCallback@3`）；
- 导入表恰好 `kernel32.dll` + `A5.dll`（无 CRT 残留）；
- PE32/i386；扩展宿主字符串在二进制里。

---

## 5. 回退（30 秒）

游戏**没在运行**时，任选其一：

```bat
:: 用部署前的备份顶回去
copy /Y 服务端源码\tools\A5\release_backup\DumpReport.dll.before-ext-host game\client\DumpReport.dll

:: 或用原厂件（客户端自带那份，仓库存档）
copy /Y 服务端源码\tools\A5\vendor\DumpReport.dll game\client\DumpReport.dll
```

回退后 `dll_ext.ini` / `dll_ext.log` 留着无害（老壳根本不读它）。
**A5.dll 全程没动过**，不需要回退。

---

## 6. 已知边界

1. **游戏中途不能热加插件** —— 启动时加载一次（要加就重启客户端）。
2. **只支持 32 位 DLL**（DNF.exe 是 32 位）；64 位的放进去会记 `gle=193`。
3. **扩展 DLL 的先后顺序就是 ini 里的行序** —— 有依赖关系的插件要自己排好。
4. **没有 UI**（启动器里那个"DLL 扩展页"是 60US 的界面，且 60US 那套管的是
   `GameGaurd.ini` 插件机制，ACT5 没有）。要 UI 得另做，见交接文档。
5. 扩展 DLL 崩了会带走整个游戏（同在进程内，这是 DLL 扩展的固有性质）。
