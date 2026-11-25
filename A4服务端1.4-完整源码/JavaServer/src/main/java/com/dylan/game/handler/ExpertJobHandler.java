package com.dylan.game.handler;

import java.util.Set;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.SessionRegistry;
import com.dylan.protocol.body.Cmd258GiveupExpertJobReplyBody;

public final class ExpertJobHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd258GiveupExpertJobReplyBody.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {

        if (SessionRegistry.presenceOf(session) == null) {
            return;
        }
        if (!session.hasCharacter()) {
            return;
        }

        Cmd258GiveupExpertJobReplyBody reply = session.expertJob().giveup();
        byte[] body = reply.toBytes();
        session.reply(Cmd258GiveupExpertJobReplyBody.PACKET_ID, body);
        session.flush();
    }
}
