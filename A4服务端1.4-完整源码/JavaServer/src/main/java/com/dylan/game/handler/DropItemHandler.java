package com.dylan.game.handler;

import com.dylan.game.CommandHandler;
import com.dylan.game.DropItemService;
import com.dylan.game.GameSession;
import com.dylan.protocol.body.Cmd50DropItemRequest;

import java.util.Set;

public final class DropItemHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd50DropItemRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        if (packetId != Cmd50DropItemRequest.PACKET_ID) {
            throw new IllegalStateException("不该路由到这里：" + packetId);
        }
        DropItemService.dropItem(session, plain);
    }
}
