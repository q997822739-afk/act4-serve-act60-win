package com.dylan.game.handler;

import java.util.Map;
import java.util.Set;

import com.dylan.game.CommandHandler;
import com.dylan.game.DeathTower;
import com.dylan.game.GameSession;
import com.dylan.game.InventoryService;
import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Cmd47ReplyBody;
import com.dylan.protocol.body.Cmd47UseStackableRequest;
import com.dylan.protocol.body.ItemListType;
import com.dylan.pvf.script.ItemScript;

public final class UseStackableHandler implements CommandHandler {

    private static final Map<Integer, Integer> FATIGUE_ITEMS = Map.of(7456, 40, 7518, 10);

    private static final Set<Integer> TOWN_USABLE_TYPES = Set.of(
            ItemScript.STACKABLE_RECIPE,
            ItemScript.STACKABLE_DISGUISE,
            ItemScript.STACKABLE_EXPERT_TOWN_POTION);

    private static final Set<Integer> TOWN_USABLE_ITEMS = Set.of(10, 24, 51);

    private static final int TOWER_FREE_ITEM_LOW = 0x1963;

    private static final int TOWER_FREE_ITEM_HIGH = 0x1B57;

    private static final Set<Integer> TOWER_ALLOWED_ITEMS = TOWN_USABLE_ITEMS;

    private static final class Param {
        int error;
        int slot;
        int itemSpace;
        String reason = "";
    }

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd47UseStackableRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        dispatchSig(session, plain);
    }

    private static void dispatchSig(GameSession session, byte[] plain) {
        Cmd47UseStackableRequest request;
        try {
            request = Cmd47UseStackableRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + Cmd47UseStackableRequest.PACKET_ID
                    + " USE_STACKABLE 包体不合法（" + plain.length + " 字节）", e);
            return;
        }
        Cmd47UseStackableRequest.firstNonZeroTail(plain);

        Param param = new Param();
        param.slot = request.slot();
        param.itemSpace = request.itemSpace();

        if (!session.hasCharacter()) {
            param.error = Cmd47ReplyBody.ERROR_NOT_NOW;
            param.reason = "还没选角色（官方 get_state 白名单那一支）";
            send(session, param);
            return;
        }

        if (request.itemSpace() != ItemListType.LIST_MAIN
                && request.itemSpace() != ItemListType.LIST_CREATURE) {
            param.error = Cmd47ReplyBody.ERROR_BAD_SLOT;
            param.reason = "容器 " + request.itemSpace() + " 不能用东西";
            send(session, param);
            return;
        }

        ItemScript item = itemAt(session, request);
        if (item == null) {
            return;
        }

        if (!session.inDungeon()) {
            if (!townCheck(session, request, item, param)) {
                send(session, param);
                return;
            }
        }

        DeathTower tower = session.deathTower();
        if (tower != null && !handleUseStackable(tower, item)) {
            return;
        }

        useStackable(session, request, item, param);
        send(session, param);
    }

    private static boolean townCheck(GameSession session, Cmd47UseStackableRequest request,
                                     ItemScript item, Param param) {
        if (!TOWN_USABLE_TYPES.contains(item.stackableType())
                && !TOWN_USABLE_ITEMS.contains(item.id())) {
            param.error = Cmd47ReplyBody.ERROR_NOT_NOW;
            param.reason = "物品 " + item.id() + "（" + item.name() + "，[stackable type] "
                    + item.stackableType() + "）在城镇里用不了";
            return false;
        }
        Integer recover = FATIGUE_ITEMS.get(item.id());
        if (recover == null) {
            return true;
        }
        int fatigue = session.fatigue().currentFatigue();
        if (fatigue < recover) {
            param.error = Cmd47ReplyBody.ERROR_NO_FATIGUE;
            param.reason = "疲劳药 " + item.id() + " 要回 " + recover
                    + " 点，但主池只用掉了 " + fatigue + " 点";
            return false;
        }
        return true;
    }

    private static boolean handleUseStackable(DeathTower tower, ItemScript item) {
        if (!tower.dungeon().tower().limitOfStackableItem()) {
            return true;
        }
        int id = item.id();
        if (id > TOWER_FREE_ITEM_LOW && id <= TOWER_FREE_ITEM_HIGH) {
            return true;
        }
        return TOWER_ALLOWED_ITEMS.contains(id);
    }

    private static void useStackable(GameSession session, Cmd47UseStackableRequest request,
                                     ItemScript item, Param param) {
        if (!item.isStackableItem()) {
            param.error = Cmd47ReplyBody.ERROR_NOT_CONSUMABLE;
            param.reason = "物品 " + item.id() + "（" + item.name() + "）不是消耗品";
            return;
        }
        if (session.consumables().checkCoolTimeItem(item.id())) {
            param.error = Cmd47ReplyBody.ERROR_NOT_NOW;
            param.reason = "物品 " + item.id() + " 还在冷却中";
            return;
        }

        InventoryService.UseItemOutcome outcome = session.inventory()
                .useItem(request.slot(), 1, request.itemSpace());
        if (!outcome.ok()) {
            param.error = outcome.error();
            param.reason = outcome.detail();
            return;
        }
        if (outcome.consumed()) {
            session.inventory().save();
        }

        session.consumables().processContinuousItem(item.id(), item);

        session.expertJob().onUseStackableItem(item);

        Integer recover = FATIGUE_ITEMS.get(item.id());
        if (recover != null) {
            session.fatigue().recoverFatigueFromItem(recover,
                    "疲劳药 " + item.id() + "（" + item.name() + "）");
        }
    }

    private static ItemScript itemAt(GameSession session, Cmd47UseStackableRequest request) {
        int itemId = session.inventory().itemIdAt(request.itemSpace(), request.slot());
        if (itemId == 0 || session.scripts() == null) {
            return null;
        }
        return session.scripts().item(itemId).orElse(null);
    }

    private static void send(GameSession session, Param param) {
        if (param.error != 0) {
            session.reply(Cmd47ReplyBody.PACKET_ID,
                    Cmd47ReplyBody.rejected(param.error, param.itemSpace));
            return;
        }
        session.reply(Cmd47ReplyBody.PACKET_ID,
                Cmd47ReplyBody.used(param.slot, param.itemSpace));
        // A4 官方实抓（参考日志 6285 / 6476）：REPLY 47 后紧跟 NOTI 14 把这一格的剩余数量发一遍。
        // 客户端 00416710 自己扣 1，这条是校准；格子用空了由 flushEmptiedSlots 发，这里不重复。
        if (ServerConfig.A4 && session.inventory().itemIdAt(param.itemSpace, param.slot) != 0) {
            session.inventory().sendItemUpdate(param.itemSpace, "副本内用掉 1 个后刷新这一格", param.slot);
        }
    }
}
