package com.dylan.game.handler;

import java.util.Set;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.MultiBoxService;
import com.dylan.protocol.body.Cmd221OpenMultiBoxRequest;
import com.dylan.protocol.body.Cmd221ReplyBody;

public final class MultiBoxHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Cmd221OpenMultiBoxRequest.packetIds();
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        Cmd221OpenMultiBoxRequest req;
        try {
            req = Cmd221OpenMultiBoxRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + packetId + " 开魔盒包体不合法（" + plain.length + " 字节）", e);
            return;
        }
        int boxItemId = Cmd221OpenMultiBoxRequest.boxItemIdOf(packetId);

        if (!session.hasCharacter()) {
            session.reply(packetId, Cmd221ReplyBody.rejected(Cmd221ReplyBody.ERROR_CANNOT_OPEN));
            return;
        }

        MultiBoxService.Result result =
                session.multiBox().open(req.boxSlot(), req.keySlot(), boxItemId);
        if (!result.ok()) {
            session.reply(packetId, Cmd221ReplyBody.rejected(result.error()));
            return;
        }
        session.reply(packetId,
                Cmd221ReplyBody.opened(result.boxSlot(), result.keySlot(), result.entries()));
    }
}
