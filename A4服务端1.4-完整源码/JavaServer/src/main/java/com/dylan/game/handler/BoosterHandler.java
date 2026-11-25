package com.dylan.game.handler;

import java.util.Set;

import com.dylan.game.BoosterService;
import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.protocol.body.Cmd170ReplyBody;
import com.dylan.protocol.body.Cmd170UseBoosterItemRequest;

public final class BoosterHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd170UseBoosterItemRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        Cmd170UseBoosterItemRequest req;
        try {
            req = Cmd170UseBoosterItemRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + packetId + " 拆礼包包体不合法（" + plain.length + " 字节）", e);
            return;
        }

        if (!session.hasCharacter()) {
            session.reply(packetId, Cmd170ReplyBody.rejected(Cmd170ReplyBody.ERROR_CANNOT_USE));
            return;
        }

        BoosterService.Result result = session.booster().use(req.slot());
        if (result.fatal() != null) {
            session.fail("CMD " + packetId + " 拆礼包：" + result.fatal());
            return;
        }
        if (!result.ok()) {
            session.reply(packetId, Cmd170ReplyBody.rejected(result.error()));
            return;
        }
        session.reply(packetId,
                Cmd170ReplyBody.opened(result.slot(), result.remain(), result.entries()));
    }
}
