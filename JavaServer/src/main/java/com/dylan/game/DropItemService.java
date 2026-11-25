package com.dylan.game;

import com.dylan.protocol.body.Cmd50DropItemRequest;
import com.dylan.protocol.body.Cmd50ReplyBody;
import com.dylan.protocol.body.EquipmentSlot;
import com.dylan.protocol.body.ItemListType;
import com.dylan.protocol.body.Noti40Body;
import com.dylan.pvf.script.ItemScript;
import com.dylan.store.CharacterInventory;
import com.dylan.store.blob.ItemArray;
import com.dylan.store.blob.InvenItem;

import java.util.List;

public final class DropItemService {

    public static final int ERROR_MALFORMED = 255;

    private DropItemService() {
    }

    public static void dropItem(GameSession s, byte[] plain) {
        Cmd50DropItemRequest req = Cmd50DropItemRequest.parse(plain);

        if (!s.hasCharacter() || req.count() <= 0) {
            reject(s, req, ERROR_MALFORMED, "还没选角色 / 数量不是正数");
            return;
        }
        if (!s.inDungeon()) {
            reject(s, req, Cmd50ReplyBody.ERROR_WRONG_STATE, "不在副本里");
            return;
        }
        if (s.itemLock().isLocked(req.itemSpace(), req.slot())) {
            reject(s, req, Cmd50ReplyBody.ERROR_ITEM_LOCKED, "这一格上着锁");
            return;
        }
        if (req.itemSpace() == ItemListType.LIST_AVATAR) {
            reject(s, req, Cmd50ReplyBody.ERROR_CANNOT_DROP, "装扮栏的东西不能丢");
            return;
        }
        if (req.itemSpace() == ItemListType.LIST_EQUIPPED
                && req.slot() <= EquipmentSlot.SKIN_AVATAR) {
            reject(s, req, Cmd50ReplyBody.ERROR_CANNOT_DROP, "穿在身上的装扮不能丢");
            return;
        }
        if (req.itemSpace() == ItemListType.LIST_MAIN
                && req.slot() == BagLayout.SLOT_GOLD) {
            dropMoney(s, req);
            return;
        }
        dropItemFromBag(s, req);
    }

    private static void dropMoney(GameSession s, Cmd50DropItemRequest req) {
        if (req.count() > Cmd50ReplyBody.MONEY_DROP_LIMIT) {
            reject(s, req, Cmd50ReplyBody.ERROR_MONEY_LIMIT,
                    "一次最多丢 " + Cmd50ReplyBody.MONEY_DROP_LIMIT + " 金币");
            return;
        }
        InventoryService inv = s.inventory();
        if (inv.inventory() == null) {
            reject(s, req, Cmd50ReplyBody.ERROR_INTERNAL, "没有背包行");
            return;
        }
        long before = inv.money();
        if (before < req.count()) {
            reject(s, req, Cmd50ReplyBody.ERROR_INTERNAL,
                    "只有 " + before + " 金币，丢不出 " + req.count());
            return;
        }
        inv.setMoney(before - req.count());
        inv.save();

        int itemNo = s.room().dropByPlayer(
                com.dylan.game.drop.GeneratedItem.money(req.count()), null);
        Noti40Body body = new Noti40Body(s.uniqueId(), req.x(), req.y(), itemNo,
                0, 0, req.count(), 0, 0, 0, 0);
        announce(s, body, "金币 " + req.count());
        s.reply(Cmd50ReplyBody.PACKET_ID,
                Cmd50ReplyBody.success(req.itemSpace(), req.slot(), req.count()));
    }

    private static void dropItemFromBag(GameSession s, Cmd50DropItemRequest req) {
        CharacterInventory inv = s.inventory().inventory();
        if (inv == null) {
            reject(s, req, Cmd50ReplyBody.ERROR_INTERNAL, "没有背包行");
            return;
        }
        ItemArray array = containerOf(inv, req.itemSpace());
        if (array == null) {
            reject(s, req, Cmd50ReplyBody.ERROR_INTERNAL,
                    "容器 " + req.itemSpace() + " 这一版没有");
            return;
        }
        if (req.slot() < 0 || req.slot() >= array.slots()) {
            reject(s, req, Cmd50ReplyBody.ERROR_INTERNAL,
                    "槽号 " + req.slot() + " 超出容器 " + req.itemSpace()
                            + " 的 " + array.slots() + " 格");
            return;
        }
        InvenItem item = array.get(req.slot());
        if (item.isEmpty()) {
            reject(s, req, Cmd50ReplyBody.ERROR_INTERNAL, "那一格是空的");
            return;
        }
        ItemScript script = s.scripts() == null
                ? null : s.scripts().item((int) item.itemId()).orElse(null);
        if (script == null) {
            reject(s, req, Cmd50ReplyBody.ERROR_INTERNAL,
                    "物品 " + item.itemId() + " 查不到脚本");
            return;
        }
        int refuse = cannotDrop(script, item, req);
        if (refuse != 0) {
            reject(s, req, refuse, script.name() + " 不让丢");
            return;
        }

        boolean stackable = script.isStackableItem();
        long have = item.stackCount();
        int count = stackable ? req.count() : 1;
        if (stackable && have < count) {
            reject(s, req, Cmd50ReplyBody.ERROR_INTERNAL,
                    "那一格只有 " + have + " 个，丢不出 " + count);
            return;
        }

        InvenItem dropped = stackable ? item.withAddInfo(count) : item;
        InvenItem left = stackable && have > count
                ? item.withAddInfo(have - count)
                : InvenItem.EMPTY;
        array.set(req.slot(), left);
        saveContainer(s, req.itemSpace(), inv);

        int itemNo = s.room().dropByPlayer(
                com.dylan.game.drop.GeneratedItem.item((int) item.itemId(),
                        stackable ? count : item.addInfo(), item.endurance()),
                dropped);
        Noti40Body body = Noti40Body.of(s.uniqueId(), req.x(), req.y(), itemNo,
                dropped, stackable, count);
        announce(s, body, script.name() + (count > 1 ? " x" + count : ""));
        s.reply(Cmd50ReplyBody.PACKET_ID,
                Cmd50ReplyBody.success(req.itemSpace(), req.slot(), count));
    }

    private static int cannotDrop(ItemScript script, InvenItem item,
                                  Cmd50DropItemRequest req) {
        if (script.attachType() == ItemScript.ATTACH_TRADE
                || script.attachType() == ItemScript.ATTACH_TRADE_DELETE) {
            return Cmd50ReplyBody.ERROR_CANNOT_DROP;
        }
        if (script.attachType() == ItemScript.ATTACH_SEALING && item.attachType() != 1) {
            return Cmd50ReplyBody.ERROR_CANNOT_DROP;
        }
        if (item.tradeRestricted()) {
            return Cmd50ReplyBody.ERROR_CANNOT_DROP;
        }
        if (script.rarity() > MAX_DROPPABLE_RARITY) {
            return Cmd50ReplyBody.ERROR_TOO_RARE;
        }
        int equipSlot = script.equipmentSlot();
        if (equipSlot != EquipmentSlot.NONE
                && (equipSlot <= EquipmentSlot.SKIN_AVATAR || equipSlot == EquipmentSlot.TITLE)) {
            return Cmd50ReplyBody.ERROR_CANNOT_DROP;
        }
        return 0;
    }

    private static final int MAX_DROPPABLE_RARITY = 2;

    private static ItemArray containerOf(CharacterInventory inv, int itemSpace) {
        return switch (itemSpace) {
            case ItemListType.LIST_MAIN -> inv.inventory();
            case ItemListType.LIST_EQUIPPED -> inv.equipslot();
            case ItemListType.LIST_CREATURE -> inv.creature();
            default -> null;
        };
    }

    private static void saveContainer(GameSession s, int itemSpace, CharacterInventory inv) {
        if (s.store() == null || s.characNo() < 0) {
            return;
        }
        if (itemSpace == ItemListType.LIST_CREATURE) {
            s.store().inventory().saveCreatureItems(s.characNo(), inv.creature());
        } else if (itemSpace == ItemListType.LIST_EQUIPPED) {
            s.store().inventory().saveEquipslot(s.characNo(), inv.equipslot());
        } else {
            s.store().inventory().saveInventoryItems(s.characNo(), inv.inventory());
        }
    }

    private static void announce(GameSession s, Noti40Body body, String what) {
        byte[] bytes = body.toBytes();
        List<GameSession> members = CombatService.partyMembers(s);
        for (GameSession member : members) {
            member.sendNow(Noti40Body.PACKET_ID, bytes);
        }
    }

    private static void reject(GameSession s, Cmd50DropItemRequest req, int code, String why) {
        s.reply(Cmd50ReplyBody.PACKET_ID, Cmd50ReplyBody.rejected(code, req.itemSpace()));
    }
}
