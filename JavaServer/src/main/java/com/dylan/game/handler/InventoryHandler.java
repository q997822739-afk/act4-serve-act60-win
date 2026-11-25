package com.dylan.game.handler;

import com.dylan.game.CharacterService;
import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.InventoryService;
import com.dylan.protocol.body.Cmd18DeleteItemRequest;
import com.dylan.protocol.body.Cmd18ReplyBody;
import com.dylan.protocol.body.Cmd19MoveItemSpaceRequest;
import com.dylan.protocol.body.Cmd19ReplyBody;
import com.dylan.protocol.body.Cmd20ReplyBody;
import com.dylan.protocol.body.Cmd51ReplyBody;
import com.dylan.protocol.body.EquipmentSlot;
import com.dylan.protocol.body.ItemListType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class InventoryHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd18DeleteItemRequest.PACKET_ID, Cmd19MoveItemSpaceRequest.PACKET_ID,
                Cmd20ReplyBody.PACKET_ID, Cmd51ReplyBody.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        switch (packetId) {
            case Cmd18DeleteItemRequest.PACKET_ID -> deleteItem(session, plain);
            case Cmd19MoveItemSpaceRequest.PACKET_ID -> moveItemSpace(session, plain);
            case Cmd20ReplyBody.PACKET_ID -> sortItem(session, plain);
            case Cmd51ReplyBody.PACKET_ID -> decreaseDurability(session, plain);
            default -> throw new IllegalStateException("不该路由到这里：CMD " + packetId);
        }
    }

    private void deleteItem(GameSession session, byte[] plain) {
        Cmd18DeleteItemRequest req;
        try {
            req = Cmd18DeleteItemRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + Cmd18DeleteItemRequest.PACKET_ID + " DELETE_ITEM 包体不合法（"
                    + plain.length + " 字节）", e);
            return;
        }
        if (!session.hasCharacter()) {
            session.reply(Cmd18ReplyBody.PACKET_ID,
                    Cmd18ReplyBody.rejected(Cmd18ReplyBody.ERROR_SILENT, req.listType()));
            return;
        }
        if (req.listType() == ItemListType.LIST_AVATAR) {
            session.reply(Cmd18ReplyBody.PACKET_ID,
                    Cmd18ReplyBody.rejected(Cmd18ReplyBody.ERROR_CANNOT_DELETE, req.listType()));
            return;
        }

        List<int[]> deleted = new ArrayList<>();
        int error = 0;
        for (var entry : req.entries()) {
            if (session.itemLock().isLocked(req.listType(), entry.slot())) {
                error = com.dylan.game.ItemLockService.ERROR_ITEM_LOCKED;
                break;
            }
            int taken = session.inventory().deleteItem(req.listType(), entry.slot(),
                    entry.itemId(), entry.amount());
            if (taken < 0) {
                error = -taken;
                break;
            }
            deleted.add(new int[]{entry.slot(), taken});
            if (req.listType() == ItemListType.LIST_EQUIPPED) {
                CharacterService.sendEquipChanged(session, entry.slot(), "扔掉身上的装备");
            }
        }
        if (!deleted.isEmpty()) {
            session.reply(Cmd18ReplyBody.PACKET_ID,
                    Cmd18ReplyBody.accepted(req.listType(), deleted));
        }
        if (error != 0) {
            session.reply(Cmd18ReplyBody.PACKET_ID,
                    Cmd18ReplyBody.rejected(error, req.listType()));
        } else if (deleted.isEmpty()) {
            session.reply(Cmd18ReplyBody.PACKET_ID,
                    Cmd18ReplyBody.accepted(req.listType(), List.of()));
        }
    }

    private static String describe(Cmd18DeleteItemRequest req) {
        StringBuilder sb = new StringBuilder();
        for (var entry : req.entries()) {
            sb.append(" {槽 ").append(entry.slot())
                    .append(" 物品 ").append(entry.itemId())
                    .append(" ×").append(entry.amount())
                    .append(entry.discard() ? " 扔掉" : " 消耗（原因 " + entry.reason() + "）")
                    .append('}');
        }
        return sb.toString();
    }

    private void decreaseDurability(GameSession session, byte[] plain) {
        if (plain.length < 1) {
            session.reply(Cmd51ReplyBody.PACKET_ID,
                    Cmd51ReplyBody.rejected(Cmd51ReplyBody.ERROR_SILENT));
            return;
        }
        int slot = plain[0] & 0xFF;
        if (slot >= EquipmentSlot.COUNT || !session.hasCharacter()) {
            session.reply(Cmd51ReplyBody.PACKET_ID,
                    Cmd51ReplyBody.rejected(Cmd51ReplyBody.ERROR_SILENT));
            return;
        }
        session.inventory().decreaseDurability(slot);
        session.reply(Cmd51ReplyBody.PACKET_ID, Cmd51ReplyBody.accepted(slot));
    }

    private void moveItemSpace(GameSession session, byte[] plain) {
        Cmd19MoveItemSpaceRequest req;
        try {
            req = Cmd19MoveItemSpaceRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + Cmd19MoveItemSpaceRequest.PACKET_ID
                    + " MOVE_ITEMSPACE 包体不合法（" + plain.length + " 字节）", e);
            return;
        }

        if (!session.hasCharacter()) {
            reject(session, req, "还没选角色");
            return;
        }
        if (session.itemLock().blocksMove(req.srcType(), req.srcSlot(), req.dstType())) {
            session.reply(Cmd19ReplyBody.PACKET_ID,
                    Cmd19ReplyBody.rejected(com.dylan.game.ItemLockService.ERROR_ITEM_LOCKED,
                            req.srcType(), req.dstType()));
            return;
        }
        // A4 交易：进出交易栏（list 4）由 TradeService 记账，不真挪背包（07_协议检修/社交/交易.md）
        if (com.dylan.net.ServerConfig.A4 && com.dylan.game.TradeService.move(session, req)) {
            return;
        }
        InventoryService inventory = session.inventory();
        InventoryService.MoveOutcome outcome = inventory.move(req);
        if (!outcome.ok()) {
            session.reply(Cmd19ReplyBody.PACKET_ID,
                    Cmd19ReplyBody.rejected(outcome.errorCode(), req.srcType(), req.dstType()));
            return;
        }
        session.reply(Cmd19ReplyBody.PACKET_ID,
                Cmd19ReplyBody.accepted(req.srcType(), req.srcSlot(), outcome.count(),
                        req.dstType(), outcome.dstSlot()));
        refreshAfterMove(session, req);
    }

    private static void refreshAfterMove(GameSession session, Cmd19MoveItemSpaceRequest req) {
        if (req.isEquipping()) {
            CharacterService.sendEquipChanged(session, req.dstSlot(), "穿上装备");
        } else if (req.isUnequipping()) {
            CharacterService.sendEquipChanged(session, req.srcSlot(), "脱下装备");
        }
    }

    private void sortItem(GameSession session, byte[] plain) {
        int listType = plain.length > 0 ? plain[0] & 0xFF : ItemListType.LIST_MAIN;

        if (!session.hasCharacter()) {
            session.reply(Cmd20ReplyBody.PACKET_ID,
                    Cmd20ReplyBody.rejected(Cmd20ReplyBody.ERROR_SILENT, listType));
            return;
        }
        if (!session.inventory().sort(listType)) {
            session.reply(Cmd20ReplyBody.PACKET_ID,
                    Cmd20ReplyBody.rejected(Cmd20ReplyBody.ERROR_SILENT, listType));
            return;
        }
        session.reply(Cmd20ReplyBody.PACKET_ID, Cmd20ReplyBody.accepted(listType));
        session.inventory().sendItemList(listType, "整理之后的全量");
        if (!com.dylan.net.ServerConfig.A4) {
            session.itemLock().sendLockList("整理之后");
        }
    }

    private static void reject(GameSession session, Cmd19MoveItemSpaceRequest req, String why) {
        session.reply(Cmd19ReplyBody.PACKET_ID,
                Cmd19ReplyBody.rejected(Cmd19ReplyBody.ERROR_CANNOT_MOVE,
                        req.srcType(), req.dstType()));
    }
}
