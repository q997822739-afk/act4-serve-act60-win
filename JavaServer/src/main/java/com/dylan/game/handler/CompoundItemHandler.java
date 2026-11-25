package com.dylan.game.handler;

import java.util.Set;

import com.dylan.game.ChatService;
import com.dylan.game.CommandHandler;
import com.dylan.game.CompoundItemService;
import com.dylan.game.GameSession;
import com.dylan.protocol.body.Cmd27CompoundItemRequest;
import com.dylan.protocol.body.Cmd27ReplyBody;
import com.dylan.protocol.body.ItemListType;

public final class CompoundItemHandler implements CommandHandler {

    private static final Set<Integer> CLIENT_KNOWN_ERRORS =
            Set.of(Cmd27ReplyBody.ERROR_INVENTORY_FULL, Cmd27ReplyBody.ERROR_ITEM_LOCKED);

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd27CompoundItemRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        Cmd27CompoundItemRequest req;
        try {
            req = Cmd27CompoundItemRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + Cmd27CompoundItemRequest.PACKET_ID + " COMPOUND_ITEM 包体不合法（"
                    + plain.length + " 字节）", e);
            return;
        }

        if (!session.hasCharacter() || session.inDungeon()) {
            reject(session, Cmd27ReplyBody.ERROR_CANNOT_MAKE, "还没进世界或人在副本里");
            return;
        }
        if (req.count() == 0) {
            reject(session, Cmd27ReplyBody.ERROR_CANNOT_MAKE, "制作次数是 0");
            return;
        }
        if (!req.byItemId()
                && session.itemLock().isLocked(ItemListType.LIST_MAIN,
                        (int) req.slotOrItemId())) {
            reject(session, Cmd27ReplyBody.ERROR_ITEM_LOCKED,
                    "槽 " + req.slotOrItemId() + " 的设计图被锁了");
            return;
        }

        CompoundItemService.Result result = session.compoundItem().compound(req);
        if (!result.ok()) {
            reject(session, result.error(), result.detail());
            return;
        }
        session.reply(Cmd27ReplyBody.PACKET_ID,
                Cmd27ReplyBody.made(result.changes(), result.outputs()));
        if (result.outputs().isEmpty()) {
            ChatService.system(session, "制作失败了。");
        }
    }

    private static void reject(GameSession session, int error, String why) {
        session.reply(Cmd27ReplyBody.PACKET_ID, Cmd27ReplyBody.rejected(error));
        if (!CLIENT_KNOWN_ERRORS.contains(error)) {
            ChatService.system(session, "做不了：" + why);
        }
    }
}
