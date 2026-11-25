package com.dylan.game.handler;

import com.dylan.game.ChatService;
import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Cmd132A4ChatRequest;
import com.dylan.protocol.body.Cmd132CreatureScriptMessageRequest;
import com.dylan.protocol.body.Cmd17ReplyBody;
import com.dylan.protocol.body.Cmd17SendMessageRequest;

import java.util.Set;

public final class ChatHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd17SendMessageRequest.PACKET_ID,
                Cmd132CreatureScriptMessageRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        if (ServerConfig.A4 && packetId == Cmd132A4ChatRequest.PACKET_ID) {
            // A4 的 132 是主聊天框（官方枚举 Chat），见 Cmd132A4ChatRequest
            ChatService.onA4Chat(session, Cmd132A4ChatRequest.parse(plain));
            return;
        }
        switch (packetId) {
            case Cmd17SendMessageRequest.PACKET_ID -> message(session, plain);
            case Cmd132CreatureScriptMessageRequest.PACKET_ID -> creatureScript(session, plain);
            default -> throw new IllegalStateException("不该路由到这里：CMD " + packetId);
        }
    }

    private void message(GameSession session, byte[] plain) {
        Cmd17SendMessageRequest request = Cmd17SendMessageRequest.parse(plain);

        session.reply(Cmd17ReplyBody.PACKET_ID, Cmd17ReplyBody.success());

        ChatService.onMessage(session, request);
    }

    private void creatureScript(GameSession session, byte[] plain) {
        Cmd132CreatureScriptMessageRequest request =
                Cmd132CreatureScriptMessageRequest.parse(plain);

        ChatService.onCreatureScript(session, request);
    }
}
