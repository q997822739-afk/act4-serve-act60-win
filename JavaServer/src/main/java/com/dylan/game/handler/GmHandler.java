package com.dylan.game.handler;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.GmService;
import com.dylan.protocol.body.Cmd65DebugCommandRequest;
import com.dylan.protocol.body.Cmd65ReplyBody;

import java.util.Set;

public final class GmHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd65DebugCommandRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        Cmd65DebugCommandRequest request = Cmd65DebugCommandRequest.parse(plain);
        session.reply(Cmd65ReplyBody.PACKET_ID, GmService.onCommand(session, request));
    }
}
