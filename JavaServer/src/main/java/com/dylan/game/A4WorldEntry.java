package com.dylan.game;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Noti66SpecialItemBody;

/**
 * A4 选角进世界时官方成品服多发、A5 没有的几个通知。只补发包，数据仍来自 A5 服务（会员表由 PremiumService 算）。
 * 依据：满级假人 20 个角色官方实抓（中间数据/dummy/enter_off_NN.txt），每个角色都是同样的顺序和字节：
 * <pre>
 *   REPLY 4 → NOTI 181 {00} → NOTI 182 {00} → NOTI 66 ×会员数 → NOTI 2/0 → NOTI 178 {00} → … → NOTI 192 {00 00}
 * </pre>
 * 181/182/178/192 的内容 20 个角色恒定，含义未知。
 */
public final class A4WorldEntry {

    private A4WorldEntry() {
    }

    public static final int NOTI_181 = 181;
    public static final int NOTI_182 = 182;
    public static final int NOTI_178 = 178;
    public static final int NOTI_192 = 192;

    /**
     * 官方选角时每个生效会员一条 NOTI 66，kind 恒为 2（假人类型 27：{@code 02 00 1B <u32 剩余秒>}），
     * 与 A5 买会员道具时 {@link PremiumService#notifyGranted} 同一布局。
     */
    public static final int LOGIN_PREMIUM_KIND = 2;

    private static boolean active() {
        return ServerConfig.A4 && "normal".equals(ServerConfig.SELECT_REPLY) && ServerConfig.INSERT_GAME_WORLD;
    }

    /** REPLY 4 之后、NOTI 13 / 2/0 之前。 */
    static void afterSelectReply(GameSession s) {
        if (!active()) {
            return;
        }
        s.send(NOTI_181, new byte[1]);
        s.send(NOTI_182, new byte[1]);
        long now = java.time.Instant.now().getEpochSecond();
        for (var p : s.premium().wirePremiums(now).entrySet()) {
            if (p.getKey() > Noti66SpecialItemBody.MAX_PREMIUM_TYPE) {
                continue;
            }
            s.send(Noti66SpecialItemBody.PACKET_ID,
                    Noti66SpecialItemBody.premium(LOGIN_PREMIUM_KIND, p.getKey(), p.getValue()).toBytes());
        }
    }

    /** NOTI 2/0（本人角色信息）之后：NOTI 178 = 免费复活币开关（ACT5 NOTI 191），00 今天可领、01 已领。 */
    static void afterOwnCharacter(GameSession s) {
        if (active()) {
            DailyGrantService.sendA4Openflag(s);
        }
    }

    /** 进世界这批包的最后一个。 */
    static void afterEnterWorld(GameSession s) {
        if (active()) {
            // NOTI 192 = 锁列表（官方没有锁定功能所以恒空）；装备栏 NOTI 2/0 已经发过，这里发全量才不会被重建冲掉
            s.itemLock().sendLockList("进世界");
        }
    }
}
