package com.dylan.game.handler;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.ShusiaService;
import com.dylan.protocol.body.Cmd9ReplyBody;
import java.util.Set;

public final class StaminaHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd9ReplyBody.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {

        ShusiaService.Outcome result = ShusiaService.recover(session);
        if (!result.ok()) {
            session.reply(Cmd9ReplyBody.PACKET_ID, Cmd9ReplyBody.rejected(result.error()));
            return;
        }

        byte[] body = Cmd9ReplyBody.recovered(result.money());
        session.reply(Cmd9ReplyBody.PACKET_ID, body);
        session.inventory().save();

    }
}
