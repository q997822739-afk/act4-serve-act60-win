package com.dylan.game.handler;

import java.util.Set;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.LotteryService;
import com.dylan.protocol.body.Cmd29ReplyBody;
import com.dylan.protocol.body.Cmd29UseLotteryItemRequest;

public final class LotteryHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd29UseLotteryItemRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        Cmd29UseLotteryItemRequest req;
        try {
            req = Cmd29UseLotteryItemRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + Cmd29UseLotteryItemRequest.PACKET_ID
                    + " USE_LOTTERY_ITEM 包体不合法（" + plain.length + " 字节）", e);
            return;
        }

        if (!session.hasCharacter()) {
            session.reply(Cmd29ReplyBody.PACKET_ID,
                    Cmd29ReplyBody.rejected(Cmd29ReplyBody.ERROR_CANNOT_OPEN));
            return;
        }

        LotteryService.Outcome outcome = session.lottery().open(req.slot());
        if (!outcome.ok()) {
            session.reply(Cmd29ReplyBody.PACKET_ID, Cmd29ReplyBody.rejected(outcome.error()));
            return;
        }

        if (outcome.gold()) {
            session.reply(Cmd29ReplyBody.PACKET_ID,
                    Cmd29ReplyBody.gold(outcome.slot(), outcome.addInfo()));
            return;
        }
        session.reply(Cmd29ReplyBody.PACKET_ID,
                Cmd29ReplyBody.opened(outcome.slot(), outcome.insertedSlot(), outcome.itemId(),
                        outcome.addInfo(), outcome.endurance(), outcome.itemAttr(), 0, 0,
                        outcome.avatarTail()));
    }
}
