package com.dylan.game.handler;

import java.util.Set;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.ResetItemAttrService;
import com.dylan.protocol.body.Cmd84ReplyBody;
import com.dylan.protocol.body.Cmd84ResetItemAttrRequest;
import com.dylan.protocol.body.ItemListType;

public final class ResetItemAttrHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd84ResetItemAttrRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {

        Cmd84ResetItemAttrRequest req;
        try {
            req = Cmd84ResetItemAttrRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + Cmd84ResetItemAttrRequest.PACKET_ID + " RESET_ITEM_ATTR "
                    + "包体不合法（" + plain.length + " 字节）", e);
            return;
        }

        if (!session.hasCharacter() || session.inDungeon()) {
            session.reply(Cmd84ReplyBody.PACKET_ID,
                    Cmd84ReplyBody.rejected(Cmd84ReplyBody.ERROR_NOT_APPLICABLE));
            return;
        }

        if (session.itemLock().isLocked(ItemListType.LIST_MAIN, req.slot())) {
            session.reply(Cmd84ReplyBody.PACKET_ID,
                    Cmd84ReplyBody.rejected(com.dylan.game.ItemLockService.ERROR_ITEM_LOCKED));
            return;
        }

        ResetItemAttrService.Outcome outcome = session.resetItemAttr().execute(req);
        if (outcome.bagChanged()) {
            session.inventory().save();
        }
        send(session, outcome);
    }

    private static void send(GameSession session, ResetItemAttrService.Outcome outcome) {
        if (!outcome.ok()) {
            session.reply(Cmd84ReplyBody.PACKET_ID, Cmd84ReplyBody.rejected(outcome.error()));
            return;
        }

        session.reply(Cmd84ReplyBody.PACKET_ID,
                Cmd84ReplyBody.accepted(outcome.boxSlot(), outcome.boxLeft(), outcome.kind()));

        if (outcome.kind() == Cmd84ReplyBody.KIND_RESET_GRADE) {
            session.inventory().sendItemRebuild(ItemListType.LIST_MAIN, "品级重掷过的那件装备",
                    outcome.slot());
        } else {
            session.inventory().sendItemUpdate(ItemListType.LIST_MAIN, "调整过的那件装备",
                    outcome.slot());
        }

        session.resetItemAttr().returnPart(outcome);
    }
}
