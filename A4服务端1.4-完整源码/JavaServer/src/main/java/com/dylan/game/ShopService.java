package com.dylan.game;

import com.dylan.protocol.body.Cmd21BuyItemRequest;
import com.dylan.protocol.body.Cmd21ReplyBody;
import com.dylan.protocol.body.Cmd24ReplyBody;
import com.dylan.protocol.body.Cmd24SellItemRequest;
import com.dylan.protocol.body.EquipmentSlot;
import com.dylan.protocol.body.ItemListType;
import com.dylan.pvf.script.ItemScript;
import com.dylan.pvf.script.ItemShopTable;
import com.dylan.pvf.script.PriceTable;
import com.dylan.store.blob.InvenItem;
import com.dylan.store.blob.ItemArray;

public final class ShopService {

    private ShopService() {
    }

    public record Purchase(int error, int slot, long itemId, long count, long paid,
                           int materialItemId, long materialLeft, String reason) {

        public boolean ok() {
            return error == 0;
        }

        static Purchase rejected(int error, String reason) {
            return new Purchase(error, -1, 0, 0, 0, ItemScript.NO_MATERIAL, 0, reason);
        }
    }

    public static Purchase buy(GameSession session, Cmd21BuyItemRequest req) {
        if (!session.hasCharacter()) {
            return Purchase.rejected(Cmd21ReplyBody.ERROR_BUSY, "还没选角色");
        }
        ItemShopTable shops = session.scripts() == null ? null : session.scripts().itemShops();
        if (shops == null || !shops.sells((int) req.shopNo(), req.itemId())) {
            if (shops != null) {
                session.hack().count(HackType.SHOP_ITEM_NOT_ON_SHELF,
                        (int) req.shopNo(), (int) req.itemId(),
                        "店 " + req.shopNo() + " 的货架上没有物品 " + req.itemId());
            }
            return Purchase.rejected(Cmd21ReplyBody.ERROR_NOT_ON_SHELF,
                    "店 " + req.shopNo() + " 的货架上没有物品 " + req.itemId());
        }

        ItemScript script = session.scripts().item((int) req.itemId()).orElse(null);
        if (script == null) {
            return Purchase.rejected(Cmd21ReplyBody.ERROR_INVALID,
                    "物品 " + req.itemId() + " 没有脚本");
        }
        int count = buyCount(script, req);
        if (count <= 0) {
            return Purchase.rejected(Cmd21ReplyBody.ERROR_INVALID, "件数 " + req.count() + " 不成立");
        }
        long unitPrice = script.shopPrice();
        long total = unitPrice * count;
        int currency = script.priceCurrency();
        if (currency != ItemScript.PRICE_IN_GOLD) {
            return Purchase.rejected(Cmd21ReplyBody.ERROR_NOT_ENOUGH_MONEY,
                    "物品 " + req.itemId() + " 要 " + total + " 通关点，这一版没有这种货币");
        }
        long money = session.inventory().money();
        if (money < total) {
            return Purchase.rejected(Cmd21ReplyBody.ERROR_NOT_ENOUGH_MONEY,
                    "要 " + total + " 金币，身上只有 " + money);
        }
        if (session.inventory().inventory() == null) {
            return Purchase.rejected(Cmd21ReplyBody.ERROR_BUSY, "背包还没载入");
        }
        MaterialCharge charge = findMaterial(session, script, count);
        if (charge.error() != 0) {
            return Purchase.rejected(charge.error(), charge.reason());
        }

        InventoryService.Landing landing = session.inventory().addItem(script, count);
        if (!landing.ok()) {
            return Purchase.rejected(Cmd21ReplyBody.ERROR_BAG_FULL, "背包放不下");
        }
        int slot = landing.slot();
        ItemArray bag = session.inventory().containerOf(landing.itemSpace());
        session.inventory().setMoney(money - total);
        long materialLeft = takeMaterial(session, charge);
        long addInfo = addInfoOnWire(script, count, bag.get(slot));
        return new Purchase(0, slot, req.itemId(), addInfo, total,
                charge.itemId(), materialLeft, "成交");
    }

    private record MaterialCharge(int itemId, int slot, int need, int error, String reason) {

        boolean required() {
            return error == 0 && itemId != ItemScript.NO_MATERIAL;
        }

        static MaterialCharge none() {
            return new MaterialCharge(ItemScript.NO_MATERIAL, -1, 0, 0, "");
        }

        static MaterialCharge rejected(int error, String reason) {
            return new MaterialCharge(ItemScript.NO_MATERIAL, -1, 0, error, reason);
        }
    }

    private static MaterialCharge findMaterial(GameSession session, ItemScript script, int count) {
        ItemScript.NeedMaterial need = script.needMaterial();
        if (!need.required()) {
            return MaterialCharge.none();
        }
        ItemScript materialScript = session.scripts().item(need.itemId()).orElse(null);
        if (materialScript == null || !materialScript.isStackableItem()) {
            return MaterialCharge.rejected(Cmd21ReplyBody.ERROR_INVALID,
                    "材料 " + need.itemId() + " 查不到脚本或不是可堆叠物品");
        }
        long total = (long) need.count() * count;
        if (total > Integer.MAX_VALUE) {
            return MaterialCharge.rejected(Cmd21ReplyBody.ERROR_NOT_ENOUGH_MONEY,
                    "要 " + total + " 个材料 " + need.itemId() + "，一格装不下这么多");
        }
        ItemArray bag = session.inventory().containerOf(ItemListType.LIST_MAIN);
        int slot = firstSlotOf(bag, need.itemId());
        long have = slot < 0 ? 0 : bag.get(slot).stackCount();
        if (have < total) {
            return MaterialCharge.rejected(Cmd21ReplyBody.ERROR_NOT_ENOUGH_MONEY,
                    "要 " + total + " 个材料 " + materialScript.name() + "（" + need.itemId()
                            + "），身上那一格只有 " + have);
        }
        return new MaterialCharge(need.itemId(), slot, (int) total, 0, "");
    }

    private static int firstSlotOf(ItemArray bag, int itemId) {
        if (bag == null) {
            return -1;
        }
        for (int slot = BagLayout.FIRST_ITEM_SLOT; slot < bag.slots(); slot++) {
            InvenItem item = bag.get(slot);
            if (!item.isEmpty() && item.itemId() == itemId) {
                return slot;
            }
        }
        return -1;
    }

    private static long takeMaterial(GameSession session, MaterialCharge charge) {
        if (!charge.required()) {
            return 0;
        }
        int taken = session.inventory().deleteItem(ItemListType.LIST_MAIN,
                charge.slot(), charge.itemId(), charge.need());
        if (taken != charge.need()) {
            throw new IllegalStateException("材料 " + charge.itemId() + " 第 " + charge.slot()
                    + " 格要扣 " + charge.need() + " 个，实际扣掉 " + taken);
        }
        InvenItem left = session.inventory().containerOf(ItemListType.LIST_MAIN).get(charge.slot());
        return left.isEmpty() || left.itemId() != charge.itemId() ? 0 : left.stackCount();
    }

    static long addInfoOnWire(ItemScript script, int count, InvenItem landed) {
        if (script.isStackableItem()) {
            return count;
        }
        return landed.addInfo();
    }

    private static int buyCount(ItemScript script, Cmd21BuyItemRequest req) {
        if (!script.isStackableItem()) {
            return 1;
        }
        if (req.count() <= 0 || req.count() > Integer.MAX_VALUE) {
            return 0;
        }
        return (int) req.count();
    }

    /** remaining = 卖完后那一格还剩几件（装备 / 整组卖光为 0），A4 REPLY 24 的最后一个 u16 要的就是它。 */
    public record Sale(int error, long gold, long paid, long remaining, String reason) {

        public boolean ok() {
            return error == 0;
        }

        static Sale rejected(int error, String reason) {
            return new Sale(error, 0, 0, 0, reason);
        }
    }

    public static Sale sell(GameSession session, Cmd24SellItemRequest req) {
        if (!session.hasCharacter()) {
            return Sale.rejected(Cmd24ReplyBody.ERROR_BUSY, "还没选角色");
        }
        if (req.itemSpace() == ItemListType.LIST_AVATAR
                || (req.itemSpace() == ItemListType.LIST_EQUIPPED
                        && req.slot() <= EquipmentSlot.SKIN_AVATAR)) {
            return Sale.rejected(Cmd24ReplyBody.ERROR_NOT_SELLABLE, "时装不能卖给 NPC");
        }
        ItemArray array = containerFor(session, req.itemSpace());
        if (array == null || req.slot() < 0 || req.slot() >= array.slots()) {
            return Sale.rejected(Cmd24ReplyBody.ERROR_INVALID,
                    "容器 " + req.itemSpace() + " 第 " + req.slot() + " 格取不到");
        }
        InvenItem item = array.get(req.slot());
        if (item.isEmpty()) {
            return Sale.rejected(Cmd24ReplyBody.ERROR_INVALID, "那一格是空的");
        }
        ItemScript script = session.scripts() == null
                ? null : session.scripts().item((int) item.itemId()).orElse(null);
        if (script == null) {
            return Sale.rejected(Cmd24ReplyBody.ERROR_INVALID,
                    "物品 " + item.itemId() + " 没有脚本");
        }

        long price = sellPrice(session, req, item, script);
        if (price < 0) {
            return Sale.rejected((int) -price, sellRejectReason((int) -price, script));
        }

        boolean deleted = deleteSold(session, req, item);
        if (!deleted) {
            return Sale.rejected(Cmd24ReplyBody.ERROR_INVALID, "那一格删不掉");
        }
        InvenItem left = array.get(req.slot());
        long remaining = left.isEmpty() || left.equipable() ? 0 : left.stackCount();
        long gold = session.inventory().money() + price;
        session.inventory().setMoney(gold);
        if (req.itemSpace() == ItemListType.LIST_EQUIPPED) {
            CharacterService.sendEquipChanged(session, req.slot(), "卖掉了穿在身上的装备");
        }
        return new Sale(0, gold, price, remaining, "成交");
    }

    private static ItemArray containerFor(GameSession session, int itemSpace) {
        return switch (itemSpace) {
            case ItemListType.LIST_MAIN, ItemListType.LIST_CARGO,
                 ItemListType.LIST_EQUIPPED, ItemListType.LIST_CREATURE ->
                    session.inventory().containerOf(itemSpace);
            default -> null;
        };
    }

    private static long sellPrice(GameSession session, Cmd24SellItemRequest req,
                                  InvenItem item, ItemScript script) {
        if (script.priceCurrency() != ItemScript.PRICE_IN_GOLD) {
            return -Cmd24ReplyBody.ERROR_NOT_SELLABLE;
        }
        if (script.attachType() == ItemScript.ATTACH_TRADE_DELETE) {
            return -Cmd24ReplyBody.ERROR_NOT_SELLABLE;
        }
        boolean inDungeon = session.inDungeon();
        PriceTable rates = session.scripts().priceTable();
        long base = script.shopBasePrice();

        if (!item.equipable()) {
            long have = Math.max(1L, item.addInfo());
            if (req.count() <= 0 || have < req.count()) {
                session.hack().count(HackType.MEMORY_MODIFIED,
                        HackAnalyzer.Site.SELL_COUNT_MISMATCH, (int) item.itemId(),
                        "要卖容器 " + req.itemSpace() + " 第 " + req.slot() + " 格的 "
                                + req.count() + " 件，那一格实际只有 " + have);
                return -Cmd24ReplyBody.ERROR_INVALID;
            }
            return rates.sellPrice(base, inDungeon, 0, 0) * req.count();
        }

        if (!npcBuysEquipment(script.equipmentSlot())) {
            return -Cmd24ReplyBody.ERROR_NOT_SELLABLE;
        }
        int max = script.durability();
        int current = Math.min(item.endurance(), max);
        return rates.sellPrice(base, inDungeon, current, max);
    }

    static boolean npcBuysEquipment(int equipmentSlot) {
        return equipmentSlot > EquipmentSlot.SKIN_AVATAR && equipmentSlot != EquipmentSlot.TITLE;
    }

    private static String sellRejectReason(int error, ItemScript script) {
        return switch (error) {
            case Cmd24ReplyBody.ERROR_NOT_SELLABLE -> script.name() + " 不能卖给 NPC";
            case Cmd24ReplyBody.ERROR_INVALID -> "件数和那一格对不上";
            default -> "错误码 " + error;
        };
    }

    private static boolean deleteSold(GameSession session, Cmd24SellItemRequest req,
                                      InvenItem item) {
        if (req.itemSpace() == ItemListType.LIST_CARGO) {
            var cargo = session.inventory().cargo();
            boolean ok = Cargo.deleteItem(cargo.items(), cargo.usableSlots(), req.slot(),
                    Math.max(1, req.count()));
            if (ok) {
                session.inventory().markCargoDirty();
            }
            return ok;
        }
        int taken = session.inventory().deleteItem(req.itemSpace(), req.slot(),
                (int) item.itemId(), Math.max(1, req.count()));
        return taken > 0;
    }
}
