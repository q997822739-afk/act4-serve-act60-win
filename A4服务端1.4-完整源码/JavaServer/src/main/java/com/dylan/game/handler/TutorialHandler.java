package com.dylan.game.handler;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.TutorialService;
import com.dylan.protocol.body.Cmd153ChangeTutorialFlagRequest;

import java.util.Set;

public final class TutorialHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd153ChangeTutorialFlagRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        TutorialService.changeFlag(session, plain);
    }
}
