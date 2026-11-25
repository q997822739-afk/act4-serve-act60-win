package com.dylan.game.handler;

import com.dylan.game.CharacterService;
import com.dylan.game.CommandHandler;
import com.dylan.game.CreatureService;
import com.dylan.game.GameSession;
import com.dylan.game.InventoryService;
import com.dylan.protocol.body.Cmd103RenameCreatureRequest;
import com.dylan.protocol.body.Cmd103ReplyBody;
import com.dylan.protocol.body.Cmd104ResponseCreatureRequest;
import com.dylan.protocol.body.Cmd105HatchCreatureRequest;
import com.dylan.protocol.body.Cmd105ReplyBody;
import com.dylan.protocol.body.ItemListType;
import com.dylan.protocol.body.Noti101RenameCreatureBody;
import com.dylan.protocol.body.Noti105CreatureItemListBody;

import java.util.Set;

public final class CreatureHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd103RenameCreatureRequest.PACKET_ID,
                Cmd104ResponseCreatureRequest.PACKET_ID,
                Cmd105HatchCreatureRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        switch (packetId) {
            case Cmd103RenameCreatureRequest.PACKET_ID -> rename(session, plain);
            case Cmd104ResponseCreatureRequest.PACKET_ID -> respond(session, plain);
            case Cmd105HatchCreatureRequest.PACKET_ID -> hatch(session, plain);
            default -> throw new IllegalStateException("不该路由到这里：CMD " + packetId);
        }
    }

    private void respond(GameSession session, byte[] plain) {
        Cmd104ResponseCreatureRequest.parse(plain);
        if (!session.hasCharacter() || session.inDungeon()) {
            return;
        }
        CreatureService.respond(session);
    }

    private void rename(GameSession session, byte[] plain) {
        Cmd103RenameCreatureRequest request;
        try {
            request = Cmd103RenameCreatureRequest.parse(plain);
        } catch (RuntimeException e) {
            session.reply(Cmd103ReplyBody.PACKET_ID,
                    Cmd103ReplyBody.rejected(Cmd103ReplyBody.ERROR_BAD_NAME).toBytes());
            return;
        }

        if (!session.hasCharacter()) {
            session.reply(Cmd103ReplyBody.PACKET_ID,
                    Cmd103ReplyBody.rejected(Cmd103ReplyBody.ERROR_NO_CREATURE).toBytes());
            return;
        }
        InventoryService inventory = session.inventory();
        var outcome = inventory.renameCreature(request.slot(), request.itemSpace(),
                request.name());
        if (!outcome.ok()) {
            session.reply(Cmd103ReplyBody.PACKET_ID,
                    Cmd103ReplyBody.rejected(outcome.errorCode()).toBytes());
            return;
        }
        session.reply(Cmd103ReplyBody.PACKET_ID,
                Cmd103ReplyBody.accepted(request.slot(), request.itemSpace()).toBytes());

        byte[] noti = new Noti101RenameCreatureBody(session.uniqueId(),
                inventory.equippedCreatureName()).toBytes();
        session.send(Noti101RenameCreatureBody.PACKET_ID, noti);
        sendSlotUpdate(session, request.itemSpace(), request.slot(), "改名用掉道具");
        session.flush();
    }

    private void hatch(GameSession session, byte[] plain) {
        Cmd105HatchCreatureRequest request;
        try {
            request = Cmd105HatchCreatureRequest.parse(plain);
        } catch (RuntimeException e) {
            session.reply(Cmd105ReplyBody.PACKET_ID,
                    Cmd105ReplyBody.rejected(Cmd105ReplyBody.ERROR_NOT_FOUND).toBytes());
            return;
        }

        if (!session.hasCharacter()) {
            session.reply(Cmd105ReplyBody.PACKET_ID,
                    Cmd105ReplyBody.rejected(Cmd105ReplyBody.ERROR_NOT_FOUND).toBytes());
            return;
        }
        if (session.itemLock().isLocked(request.itemSpace(), request.slot())) {
            session.reply(Cmd105ReplyBody.PACKET_ID,
                    Cmd105ReplyBody.rejected(
                            com.dylan.game.ItemLockService.ERROR_ITEM_LOCKED).toBytes());
            return;
        }
        InventoryService inventory = session.inventory();
        var outcome = inventory.hatchCreature(request.itemSpace(), request.slot());
        if (!outcome.ok()) {
            session.reply(Cmd105ReplyBody.PACKET_ID,
                    Cmd105ReplyBody.rejected(outcome.errorCode()).toBytes());
            return;
        }
        session.reply(Cmd105ReplyBody.PACKET_ID, Cmd105ReplyBody.accepted().toBytes());

        Noti105CreatureItemListBody list = inventory.creatureItemList();
        byte[] body = list.toBytes();
        session.send(Noti105CreatureItemListBody.PACKET_ID, body);
        sendSlotUpdate(session, request.itemSpace(), request.slot(), "孵化");
        if (request.itemSpace() == ItemListType.LIST_EQUIPPED) {
            CharacterService.sendEquipChanged(session, request.slot(), "孵化");
        }
        session.flush();
    }

    private static void sendSlotUpdate(GameSession session, int itemSpace, int slot, String why) {
        if (itemSpace == ItemListType.LIST_EQUIPPED) {
            return;
        }
        session.inventory().sendItemUpdate(itemSpace, why, slot);
    }
}
