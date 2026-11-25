package com.dylan.game.handler;

import java.util.Set;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.IncreaseStatusService;
import com.dylan.protocol.body.Cmd32IncreaseStatusRequest;
import com.dylan.protocol.body.Cmd32ReplyBody;

public final class IncreaseStatusHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd32IncreaseStatusRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        Cmd32IncreaseStatusRequest req;
        try {
            req = Cmd32IncreaseStatusRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + Cmd32IncreaseStatusRequest.PACKET_ID
                    + " INCREASE_STATUS 包体不合法（" + plain.length + " 字节）", e);
            return;
        }

        if (!session.hasCharacter()) {
            session.reply(Cmd32ReplyBody.PACKET_ID,
                    Cmd32ReplyBody.rejected(Cmd32ReplyBody.ERROR_BAD_STATE));
            return;
        }

        if (req.isQuestItemSlot()) {
            session.reply(Cmd32ReplyBody.PACKET_ID,
                    Cmd32ReplyBody.rejected(Cmd32ReplyBody.ERROR_CANNOT_USE));
            return;
        }

        if (session.itemLock().isLocked(
                com.dylan.protocol.body.ItemListType.LIST_MAIN, req.slot())) {
            session.reply(Cmd32ReplyBody.PACKET_ID,
                    Cmd32ReplyBody.rejected(com.dylan.game.ItemLockService.ERROR_ITEM_LOCKED));
            return;
        }

        IncreaseStatusService.Outcome outcome = session.increaseStatus().use(req.slot());
        if (outcome.silent()) {
            return;
        }
        if (!outcome.ok()) {
            session.reply(Cmd32ReplyBody.PACKET_ID,
                    Cmd32ReplyBody.rejected(outcome.error()));
            return;
        }
        session.reply(Cmd32ReplyBody.PACKET_ID,
                Cmd32ReplyBody.applied(req.slot(), outcome.type(), outcome.value(),
                        outcome.extra()));
        if (outcome.afterReply() != null) {
            outcome.afterReply().run();
        }
    }
}
