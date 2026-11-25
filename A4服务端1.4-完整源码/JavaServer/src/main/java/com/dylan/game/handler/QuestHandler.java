package com.dylan.game.handler;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.QuestService;
import com.dylan.protocol.body.Cmd33AcceptQuestRequest;
import com.dylan.protocol.body.Cmd34GiveUpQuestRequest;
import com.dylan.protocol.body.Cmd35SetQuestTriggerRequest;
import com.dylan.protocol.body.Cmd36FinishQuestRequest;

import java.util.Set;

public final class QuestHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd33AcceptQuestRequest.PACKET_ID,
                Cmd34GiveUpQuestRequest.PACKET_ID,
                Cmd35SetQuestTriggerRequest.PACKET_ID,
                Cmd36FinishQuestRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        switch (packetId) {
            case Cmd33AcceptQuestRequest.PACKET_ID -> QuestService.accept(session, plain);
            case Cmd34GiveUpQuestRequest.PACKET_ID -> QuestService.giveUp(session, plain);
            case Cmd35SetQuestTriggerRequest.PACKET_ID -> QuestService.setTrigger(session, plain);
            case Cmd36FinishQuestRequest.PACKET_ID -> QuestService.finish(session, plain);
            default -> throw new IllegalStateException("不该路由到这里：CMD " + packetId);
        }
    }
}
