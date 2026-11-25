package com.dylan.game;

import com.dylan.net.ServerConfig;
import com.dylan.store.GameStore;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 在线收信：周期性帮**正在游戏里**的角色重扫一遍 postal，把外部进程新插进来的邮件补进内存快照、
 * 推给客户端（NOTI 97 追加 + NOTI 99 提醒）。
 *
 * 为什么需要它（用户 2026-10-11 的诉求："角色在线时直接发邮件，不用小退"）：
 *   {@link MailService#load} 只在**选角那一刻**读一次库，之后这份邮箱快照就固定在内存里了。
 *   而邮件往往来自**另一个进程** —— 网页 GM 投递、或别的玩家寄件 —— 直接往 postal 插行。
 *   服务端看不见这些行，玩家只能"重新选角"（= 小退再进）才会再 load 一次。这就是"发邮件必须小退"
 *   的根。邮箱本身是**追加安全**的（postal 只被整行插入 / 按 postal_id 单行改删），所以运行中重扫、
 *   追加是安全的 —— 和网页 GM 在线发货走同一套 reasoning（见 交接文档 §9.3）。
 *
 * 线程模型：遍历当前在游戏里的会话，每一条都丢回**它自己的会话线程**执行（{@code session.run}）——
 * 邮箱快照是会话私有的，和玩家点邮箱时改它的那些命令（open/extract）必须在同一个线程上跑，
 * 否则会和"刚领走一封"打架。和 {@code EventScheduler} 一样，任何异常只吞掉，绝不影响玩法。
 */
public final class MailPoller {

    private MailPoller() {
    }

    private static ScheduledExecutorService scheduler;
    private static GameStore store;

    public static synchronized void start(GameStore gameStore) {
        store = gameStore;
        if (!ServerConfig.MAIL_POLL || store == null) {
            return;
        }
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mail-poller");
                t.setDaemon(true);
                return t;
            });
        }
        long seconds = Math.max(1, ServerConfig.MAIL_POLL_SECONDS);
        scheduler.scheduleWithFixedDelay(MailPoller::tick, seconds, seconds, TimeUnit.SECONDS);
    }

    public static synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        store = null;
    }

    private static void tick() {
        try {
            for (GameSession session : SessionRegistry.playing()) {
                session.run(() -> poll(session));
            }
        } catch (RuntimeException e) {
            // 纯旁路：轮询失败不能让任何玩法受影响
        }
    }

    /** 在会话线程上跑：重扫邮箱；只有"还没领的信真的变多了"才发包提醒。 */
    private static void poll(GameSession session) {
        try {
            MailService mail = session.mail();
            if (mail == null || !session.hasCharacter()) {
                return;
            }
            int before = mail.unclaimedCount();
            int fresh = mail.syncFromStore("在线收信");
            if (mail.unclaimedCount() <= before) {
                return;
            }
            // 两条提醒一起发：
            //   ① NOTI 99 —— 客户端邮箱入口的角标（安静、不打扰，正常玩法都靠它）；
            //   ② 一条系统聊天 —— 就算这份客户端忽略了 NOTI 99，玩家也一定看得见这一行。
            // 系统聊天那条也是**可验收**的信号：无头驱动器能直接从下行字节里读出来。
            mail.alarmNewMail();
            ChatService.system(session, fresh > 0
                    ? "邮箱有新邮件（" + mail.unclaimedCount() + " 封待领取），按快捷键打开邮箱领取。"
                    : "邮箱有新邮件（" + mail.unclaimedCount() + " 封待领取）。");
            // 这条路径不经过"客户端发命令 → 服务端回包"的常规收尾（那里自然 flush），
            // 轮询是服务端**主动**发起的，得自己把包推出去，否则会一直躺在发送缓冲里
            // 等下一次客户端命令才发出（玩家会觉得"提醒迟了半拍"）。
            session.flush();
        } catch (RuntimeException e) {
            // 同上
        }
    }
}
