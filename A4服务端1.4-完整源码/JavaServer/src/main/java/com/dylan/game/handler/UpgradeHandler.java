package com.dylan.game.handler;

import java.util.Set;

import com.dylan.game.BagLayout;
import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.UpgradeService;
import com.dylan.protocol.body.Cmd83ReplyBody;
import com.dylan.protocol.body.Cmd83UpgradeItemRequest;
import com.dylan.protocol.body.ItemListType;

public final class UpgradeHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd83UpgradeItemRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        Cmd83UpgradeItemRequest req;
        try {
            req = Cmd83UpgradeItemRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + Cmd83UpgradeItemRequest.PACKET_ID + " UPGRADE_ITEM 包体不合法（"
                    + plain.length + " 字节）", e);
            return;
        }

        int gate = checkError(session);
        if (gate != 0) {
            session.reply(Cmd83ReplyBody.PACKET_ID, Cmd83ReplyBody.rejected(gate));
            return;
        }

        if (session.itemLock().isLocked(ItemListType.LIST_MAIN, req.slot())) {
            session.reply(Cmd83ReplyBody.PACKET_ID,
                    Cmd83ReplyBody.rejected(Cmd83ReplyBody.ERROR_NOT_UPGRADABLE));
            return;
        }

        UpgradeService.Outcome outcome = session.upgrades().upgrade(req);
        if (outcome.ok()) {
            persistAndRefresh(session);
        }
        send(session, req, outcome);
    }

    private static int checkError(GameSession session) {
        if (!session.hasCharacter() || session.inDungeon()) {
            return Cmd83ReplyBody.ERROR_NOT_UPGRADABLE;
        }
        return 0;
    }

    private static void persistAndRefresh(GameSession session) {
        session.inventory().save();
        session.inventory().sendItemUpdate(ItemListType.LIST_MAIN, "强化扣掉的金币",
                BagLayout.SLOT_GOLD);
    }

    private static void send(GameSession session, Cmd83UpgradeItemRequest req,
                             UpgradeService.Outcome outcome) {
        if (!outcome.ok()) {
            session.reply(Cmd83ReplyBody.PACKET_ID, Cmd83ReplyBody.rejected(outcome.error()));
            return;
        }
        if (outcome.destroyed()) {
            session.reply(Cmd83ReplyBody.PACKET_ID,
                    Cmd83ReplyBody.destroyed(req.amplify(), outcome.materialSlot(),
                            outcome.materialLeft(), outcome.slot(), outcome.fragments()));
            return;
        }
        session.reply(Cmd83ReplyBody.PACKET_ID,
                Cmd83ReplyBody.kept(req.amplify(), outcome.materialSlot(),
                        outcome.materialLeft(), outcome.result(), outcome.upgradeAfter(),
                        outcome.slot()));
    }
}
