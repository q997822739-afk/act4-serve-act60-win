package com.dylan.game.handler;

import java.util.Set;

import com.dylan.game.CommandHandler;
import com.dylan.game.DisjointService;
import com.dylan.game.GameSession;
import com.dylan.protocol.body.Cmd28DisjointItemRequest;
import com.dylan.protocol.body.Cmd28ReplyBody;

public final class DisjointHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd28DisjointItemRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        Cmd28DisjointItemRequest req;
        try {
            req = Cmd28DisjointItemRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + Cmd28DisjointItemRequest.PACKET_ID + " DISJOINT_ITEM 包体不合法（"
                    + plain.length + " 字节）", e);
            return;
        }

        if (!session.hasCharacter()) {
            reject(session, Cmd28ReplyBody.ERROR_NO_SUCH_ITEM, "还没进世界");
            return;
        }

        if (session.itemLock().isLocked(req.itemSpace(), req.slot())) {
            reject(session, Cmd28ReplyBody.ERROR_ITEM_LOCKED,
                    "容器 " + req.itemSpace() + " 第 " + req.slot() + " 格锁着");
            return;
        }

        DisjointService.Outcome outcome = session.disjoints().disjoint(req);
        if (!outcome.ok()) {
            reject(session, outcome.error(), outcome.reason());
            return;
        }
        session.inventory().save();
        session.reply(Cmd28ReplyBody.PACKET_ID,
                Cmd28ReplyBody.done(outcome.slot(), outcome.itemSpace(), outcome.products()));
    }

    private static void reject(GameSession session, int error, String why) {
        session.reply(Cmd28ReplyBody.PACKET_ID, Cmd28ReplyBody.rejected(error));
    }
}
