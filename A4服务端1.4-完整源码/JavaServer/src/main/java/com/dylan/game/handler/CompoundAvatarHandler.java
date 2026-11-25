package com.dylan.game.handler;

import java.util.Set;

import com.dylan.game.CommandHandler;
import com.dylan.game.CompoundAvatarService;
import com.dylan.game.GameSession;
import com.dylan.protocol.body.Cmd102CompoundAvatarRequest;
import com.dylan.protocol.body.Cmd102ReplyBody;

public final class CompoundAvatarHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd102CompoundAvatarRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        Cmd102CompoundAvatarRequest req;
        try {
            req = Cmd102CompoundAvatarRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + Cmd102CompoundAvatarRequest.PACKET_ID + " COMPOUND_AVATAR 包体不合法（"
                    + plain.length + " 字节）", e);
            return;
        }

        if (!session.hasCharacter()) {
            session.reply(Cmd102ReplyBody.PACKET_ID,
                    Cmd102ReplyBody.rejected(Cmd102ReplyBody.ERROR_FAILED));
            return;
        }

        CompoundAvatarService.Outcome outcome = session.compoundAvatar().compound(req);
        if (!outcome.ok()) {
            session.reply(Cmd102ReplyBody.PACKET_ID, Cmd102ReplyBody.rejected(outcome.error()));
            return;
        }

        session.inventory().save();
        session.reply(Cmd102ReplyBody.PACKET_ID,
                Cmd102ReplyBody.ok(outcome.changes(), outcome.slot(), outcome.itemId(),
                        outcome.remainSeconds(), outcome.abilityNo()));
    }
}
