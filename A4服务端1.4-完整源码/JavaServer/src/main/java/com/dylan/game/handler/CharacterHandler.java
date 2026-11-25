package com.dylan.game.handler;

import com.dylan.game.CharacterService;
import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.protocol.body.Cmd146QueryCharacInfoRequest;
import com.dylan.protocol.body.Cmd4SelectCharacterRequest;
import com.dylan.protocol.body.Cmd5CreateCharacterRequest;
import com.dylan.protocol.body.Cmd6DeleteCharacterRequest;
import com.dylan.protocol.body.Cmd7ReplyBody;

import java.util.Set;

public final class CharacterHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(
                Cmd4SelectCharacterRequest.PACKET_ID,
                Cmd5CreateCharacterRequest.PACKET_ID,
                Cmd6DeleteCharacterRequest.PACKET_ID,
                Cmd7ReplyBody.PACKET_ID,
                Cmd146QueryCharacInfoRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        switch (packetId) {
            case Cmd4SelectCharacterRequest.PACKET_ID ->
                    CharacterService.selectCharacter(session, plain);
            case Cmd5CreateCharacterRequest.PACKET_ID ->
                    CharacterService.createCharacter(session, plain);
            case Cmd6DeleteCharacterRequest.PACKET_ID ->
                    CharacterService.deleteCharacter(session, plain);
            case Cmd7ReplyBody.PACKET_ID -> CharacterService.returnToSelect(session);
            case Cmd146QueryCharacInfoRequest.PACKET_ID ->
                    CharacterService.queryCharacInfo(session, plain);
            default -> throw new IllegalStateException("不该路由到这里：" + packetId);
        }
    }
}
