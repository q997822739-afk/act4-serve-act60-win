package com.dylan.game.handler;

import java.util.Set;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.ShopService;
import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Cmd21BuyItemRequest;
import com.dylan.protocol.body.Cmd21ReplyBody;
import com.dylan.protocol.body.Cmd24ReplyBody;
import com.dylan.protocol.body.Cmd24SellItemRequest;

public final class ShopHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd21BuyItemRequest.PACKET_ID, Cmd24SellItemRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        switch (packetId) {
            case Cmd21BuyItemRequest.PACKET_ID -> buy(session, plain);
            case Cmd24SellItemRequest.PACKET_ID -> sell(session, plain);
            default -> throw new IllegalStateException("ShopHandler 收到了不该它管的 " + packetId);
        }
    }

    private static void buy(GameSession session, byte[] plain) {
        Cmd21BuyItemRequest req;
        try {
            req = Cmd21BuyItemRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + Cmd21BuyItemRequest.PACKET_ID + " BUY_ITEM 包体不合法（"
                    + plain.length + " 字节）", e);
            return;
        }

        ShopService.Purchase result = ShopService.buy(session, req);
        if (!result.ok()) {
            session.reply(Cmd21ReplyBody.PACKET_ID, Cmd21ReplyBody.rejected(result.error()));
            return;
        }

        session.inventory().save();
        byte[] body = Cmd21ReplyBody.bought(session.inventory().money(), session.winPoint(),
                FINISH_POINT, session.cera(), result.slot(), result.itemId(), result.count(),
                result.materialItemId(), result.materialLeft());
        session.reply(Cmd21ReplyBody.PACKET_ID, body);
    }

    private static final long FINISH_POINT = 0;

    private static void sell(GameSession session, byte[] plain) {
        Cmd24SellItemRequest req;
        try {
            req = Cmd24SellItemRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + Cmd24SellItemRequest.PACKET_ID + " SELL_ITEM 包体不合法（"
                    + plain.length + " 字节）", e);
            return;
        }

        if (session.hasCharacter()
                && session.itemLock().isLocked(req.itemSpace(), req.slot())) {
            session.reply(Cmd24ReplyBody.PACKET_ID,
                    Cmd24ReplyBody.rejected(com.dylan.game.ItemLockService.ERROR_ITEM_LOCKED));
            return;
        }

        ShopService.Sale result = ShopService.sell(session, req);
        if (!result.ok()) {
            session.reply(Cmd24ReplyBody.PACKET_ID, Cmd24ReplyBody.rejected(result.error()));
            return;
        }

        session.inventory().save();
        // A4：客户端 00413018 把最后一个 u16 当「这一格剩几件」：比现有数量小就改成它，否则删格子。
        // 官方实抓 卖 5 / 2085 → 尾巴 2080（0x0820）+ NOTI 14 刷这一格；整组卖光 → 0 + NOTI 14 空格。
        // 发卖出件数的话，部分卖出时客户端会把剩下的显示成卖出数。
        int tail = ServerConfig.A4 ? (int) Math.min(result.remaining(), 0xFFFF) : req.count();
        byte[] body = Cmd24ReplyBody.sold(result.gold(), req.itemSpace(), req.slot(), tail);
        session.reply(Cmd24ReplyBody.PACKET_ID, body);
        if (ServerConfig.A4 && result.remaining() > 0) {
            // 整组卖光时格子已进 emptiedSlots，命令结束统一发 NOTI 14；部分卖出要在这里补
            session.inventory().sendItemUpdate(req.itemSpace(), "卖掉一部分后刷新这一格", req.slot());
        }
    }
}
