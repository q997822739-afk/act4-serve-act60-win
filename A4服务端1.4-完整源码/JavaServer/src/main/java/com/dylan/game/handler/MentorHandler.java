package com.dylan.game.handler;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.MentorService;
import com.dylan.net.ServerConfig;

import java.util.Set;

/**
 * A4 师徒。82 仍由 {@link A4TownInitHandler} 回名单（进城还要接着发 NOTI 21），这里不接 82。
 */
public final class MentorHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return ServerConfig.A4 ? Set.of(79, 80, 81) : Set.of();
    }

    @Override
    public void handle(GameSession s, int packetId, byte[] plain) {
        if (!s.hasCharacter()) {
            return;
        }
        switch (packetId) {
            case 79 -> MentorService.requestEnter(s, plain);
            case 80 -> MentorService.answer(s, plain);
            case 81 -> MentorService.secede(s, plain);
            default -> throw new IllegalStateException("不该路由到这里：CMD " + packetId);
        }
    }
}
