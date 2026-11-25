package com.dylan.game;

import com.dylan.store.GameStore;

/**
 * 把"服务端此刻持有哪个角色"这件事写进库（server_presence 表），供**外部工具**（网页 GM）读。
 *
 * 背景（2026-10-10）：网页 GM 是另一个进程。它要在服务端运行时直写某个角色的背包 / 技能 /
 * 数值，只有"服务端没把这些整块数据拿在手里"时才安全 —— 否则服务端存档整块回写会盖掉直写。
 * 服务端原来**只把会话状态放在堆里、不向外界暴露**，所以 GM 只能让用户手动点「已小退」。
 * 用户反馈"有些功能必须点小退才能用"——这个开关就是要消掉它。
 *
 * 现在服务端主动记录：角色进游戏（选角加载**之前**就登记）→ 记一行；小退（CMD 7）/断线 → 删掉。
 * 语义：**有行 = 在游戏中（GM 不许直写）；没行 = 选择界面或没登录（可以直写）**。
 *
 * 两个方向都要谨慎，所以：
 *   - **登记要早**：在把角色的整块数据 load 进内存**之前**就登记。宁可早登记导致 GM 暂时不许写
 *     （fail-safe），也不能晚登记让 GM 在服务端已持有数据时写进来（那是数据丢失）；
 *   - **注销要全**：小退、断线、服务端启动（清空整表）三条路都注销，避免残留行把 GM 永久卡住。
 *
 * 这是给外部读的旁路信息，**对玩法零影响**：任何一步失败都只吞掉，绝不能让登记失败把选角/小退搞崩。
 */
public final class PlayerPresence {

    private PlayerPresence() {
    }

    /** 角色即将进游戏：登记"服务端持有它"。**要在 load 角色数据之前调用**。 */
    public static void enter(GameStore store, int characNo, String characName, long mId) {
        if (store == null || characNo < 0) {
            return;
        }
        try {
            store.gm().markPresent(characNo, characName, mId);
        } catch (RuntimeException e) {
            // 纯旁路，失败不影响玩法
        }
    }

    /** 小退 / 断线：注销。characNo 传**释放之前**捕获的那个。 */
    public static void leave(GameStore store, int characNo) {
        if (store == null || characNo < 0) {
            return;
        }
        try {
            store.gm().clearPresence(characNo);
        } catch (RuntimeException e) {
            // 同上
        }
    }

    /** 服务端启动时清空整表：上一个进程可能崩溃退出，残留行会让 GM 误判成"还在游戏里"。 */
    public static void resetAll(GameStore store) {
        if (store == null) {
            return;
        }
        try {
            store.gm().clearAllPresence();
        } catch (RuntimeException e) {
            // 同上
        }
    }
}
