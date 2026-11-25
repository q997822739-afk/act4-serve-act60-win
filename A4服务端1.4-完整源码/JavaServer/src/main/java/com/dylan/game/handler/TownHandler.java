package com.dylan.game.handler;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.TownService;
import com.dylan.protocol.body.Cmd37SetUserPositionRequest;
import com.dylan.protocol.body.Cmd38SetUserAreaRequest;

import java.util.Set;

public final class TownHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(
                Cmd37SetUserPositionRequest.PACKET_ID,
                Cmd38SetUserAreaRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        switch (packetId) {
            case Cmd37SetUserPositionRequest.PACKET_ID -> TownService.reportPosition(session, plain);
            case Cmd38SetUserAreaRequest.PACKET_ID -> TownService.changeArea(session, plain);
            default -> throw new IllegalStateException("不该路由到这里：" + packetId);
        }
    }
}
