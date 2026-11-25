package com.dylan.game.handler;

import java.util.Set;

import com.dylan.game.CharacterService;
import com.dylan.game.CommandHandler;
import com.dylan.game.EmotionService;
import com.dylan.game.GameSession;
import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Cmd273ChangeEmotionRequest;
import com.dylan.protocol.body.Cmd273ReplyBody;

public final class EmotionHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        // A4 心情是 195（0091098E 写 u16）。回 273 的话客户端对不上这个号，界面会一直等。
        return ServerConfig.A4
                ? Set.of(Cmd273ChangeEmotionRequest.PACKET_ID, Cmd273ChangeEmotionRequest.A4_PACKET_ID)
                : Set.of(Cmd273ChangeEmotionRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        Cmd273ChangeEmotionRequest req;
        try {
            req = Cmd273ChangeEmotionRequest.parse(plain);
        } catch (RuntimeException e) {
            // 00910FAD 也会发 195，宽度在寄存器里，不够 2 字节时不能拆连接。
            if (ServerConfig.A4) {
                session.reply(packetId, Cmd273ReplyBody.rejected(Cmd273ReplyBody.ERROR_INVALID).toBytes());
                session.flush();
                return;
            }
            session.fail("CMD " + packetId + " CHANGE_EMOTION 包体不合法（"
                    + (plain == null ? "null" : plain.length + " 字节") + "）", e);
            return;
        }

        Cmd273ReplyBody reply = EmotionService.change(session, req);
        if (reply == null) {
            return;
        }

        byte[] body = reply.toBytes();
        session.reply(packetId, body);

        if (!reply.ok()) {
            session.flush();
            return;
        }
        CharacterService.resendOwnCharacterInfo(session, "换了心情");
        session.flush();
    }
}
