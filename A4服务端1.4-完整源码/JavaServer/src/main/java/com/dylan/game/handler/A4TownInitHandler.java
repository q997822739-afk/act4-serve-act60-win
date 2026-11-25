package com.dylan.game.handler;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.MentorService;
import com.dylan.game.QuestService;
import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Noti30Body;

import java.util.Set;

/**
 * A4 进城时客户端额外发的两个命令，A5 没有对应 handler（客户端收不到回包会崩在 IDA 0047C4CC）。
 * 只做回包序列化，数据仍由 A5 服务算。字节照官方日志 09:13:35.845 / 09:13:36.131：
 * <ul>
 *   <li>CMD 82 → 师徒名单（DNF.exe {@code 00419301}）。没师徒时仍是 18 字节 {@code 01} + 17 个 0，
 *       和官方进城回包一致。再发 NOTI 21（A5 {@link QuestService} 的可接任务表）。</li>
 *   <li>CMD 130 → 回 {@code 01 00}，再发空 NOTI 30（FinishLoading）。</li>
 * </ul>
 */
public final class A4TownInitHandler implements CommandHandler {

    public static final int CMD_82 = 82;
    public static final int CMD_130 = 130;

    @Override
    public Set<Integer> handles() {
        return ServerConfig.A4 ? Set.of(CMD_82, CMD_130) : Set.of();
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        switch (packetId) {
            case CMD_82 -> {
                session.reply(CMD_82, MentorService.listBody(session));
                QuestService.sendAcceptableQuests(session, "A4 CMD 82");
                session.flush();
            }
            case CMD_130 -> {
                session.reply(CMD_130, new byte[] {1, 0});
                session.sendNow(Noti30Body.PACKET_ID, Noti30Body.toBytes());
            }
            default -> throw new IllegalStateException("不该路由到这里：" + packetId);
        }
    }
}
