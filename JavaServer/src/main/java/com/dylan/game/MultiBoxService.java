package com.dylan.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.dylan.protocol.body.Cmd221ReplyBody;
import com.dylan.protocol.body.ItemListType;
import com.dylan.protocol.body.Noti86Body;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.ItemScript;
import com.dylan.pvf.script.MultiBoxInfo;
import com.dylan.store.blob.InvenItem;
import com.dylan.store.blob.ItemArray;

public final class MultiBoxService {

    private final GameSession session;
    private final Random random;

    public MultiBoxService(GameSession session) {
        this(session, new Random());
    }

    MultiBoxService(GameSession session, Random random) {
        this.session = session;
        this.random = random;
    }

    public record Prize(Cmd221ReplyBody.Entry entry, int itemId, int upgrade,
                        boolean broadcast, String detail) {}

    public record Result(int error, int boxSlot, int keySlot, List<Prize> prizes) {

        public Result {
            prizes = List.copyOf(prizes);
        }

        public boolean ok() {
            return error == 0;
        }

        public List<Cmd221ReplyBody.Entry> entries() {
            var out = new ArrayList<Cmd221ReplyBody.Entry>(prizes.size());
            for (Prize p : prizes) {
                out.add(p.entry());
            }
            return out;
        }
    }

    private static Result fail(int error, int boxSlot, int keySlot, String detail) {
        return new Result(error, boxSlot, keySlot, List.of());
    }

    public Result open(int boxSlot, int keySlot, int expectBoxItem) {
        InventoryService inventory = session.inventory();
        ItemArray bag = inventory.containerOf(ItemListType.LIST_MAIN);
        if (bag == null || boxSlot < 0 || boxSlot >= bag.slots()) {
            return fail(Cmd221ReplyBody.ERROR_CANNOT_OPEN, boxSlot, keySlot, "魔盒槽号越界");
        }
        InvenItem box = bag.get(boxSlot);
        if (box.isEmpty() || box.itemId() != expectBoxItem) {
            return fail(Cmd221ReplyBody.ERROR_CANNOT_OPEN, boxSlot, keySlot,
                    "槽 " + boxSlot + " 不是这条包该开的魔盒 " + expectBoxItem
                            + "（是 " + (box.isEmpty() ? "空的" : box.itemId()) + "）");
        }
        GameScripts scripts = session.scripts();
        ItemScript boxScript = scripts == null
                ? null : scripts.item((int) box.itemId()).orElse(null);
        if (boxScript == null) {
            return fail(Cmd221ReplyBody.ERROR_CANNOT_OPEN, boxSlot, keySlot,
                    "槽 " + boxSlot + " 的 " + box.itemId() + " 在 pvf 里查不到");
        }
        if (!boxScript.isMultiBoxItem()) {
            return fail(Cmd221ReplyBody.ERROR_CANNOT_OPEN, boxSlot, keySlot,
                    boxScript.name() + " 不是魔盒（[stackable type] "
                            + boxScript.stackableType() + "）");
        }
        MultiBoxInfo info = boxScript.multiBox();
        if (info.isEmpty()) {
            return fail(Cmd221ReplyBody.ERROR_CANNOT_OPEN, boxSlot, keySlot,
                    boxScript.name() + " 是魔盒但没有池子（[RANDOMBOX*] 没读出来）");
        }

        MultiBoxInfo.KeyItem key = info.key();
        int keyNeeded = 0;
        if (key == null) {
            if (keySlot != Cmd221ReplyBody.Entry.SLOT_NONE) {
                return fail(Cmd221ReplyBody.ERROR_CANNOT_OPEN, boxSlot, keySlot,
                        boxScript.name() + " 不用钥匙，但请求里带了钥匙槽 " + keySlot);
            }
        } else {
            if (keySlot < 0 || keySlot >= bag.slots()) {
                return fail(Cmd221ReplyBody.ERROR_CANNOT_OPEN, boxSlot, keySlot, "钥匙槽号越界");
            }
            InvenItem held = bag.get(keySlot);
            if (held.isEmpty() || held.itemId() != key.itemId()) {
                return fail(Cmd221ReplyBody.ERROR_CANNOT_OPEN, boxSlot, keySlot,
                        "槽 " + keySlot + " 不是 " + boxScript.name() + " 要的钥匙 "
                                + key.itemId() + "（是 " + held.itemId() + "）");
            }
            keyNeeded = Math.max(1, key.count());
            if (held.addInfo() < keyNeeded) {
                return fail(Cmd221ReplyBody.ERROR_CANNOT_OPEN, boxSlot, keySlot,
                        "钥匙不够：要 " + keyNeeded + " 把，只有 " + held.addInfo() + " 把");
            }
        }

        int boxNeeded = info.boxCount();
        if (box.addInfo() < boxNeeded) {
            return fail(Cmd221ReplyBody.ERROR_CANNOT_OPEN, boxSlot, keySlot,
                    "魔盒不够：要 " + boxNeeded + " 个，只有 " + box.addInfo() + " 个");
        }

        var rolls = new ArrayList<MultiBoxInfo.Roll>(info.pools().size());
        for (MultiBoxInfo.Pool pool : info.pools()) {
            rolls.add(pool.roll(random));
        }

        var made = new ArrayList<InvenItem>(rolls.size());
        var prizeScripts = new ArrayList<ItemScript>(rolls.size());
        var specials = new ArrayList<CeraShopSpecialItems.Special>(rolls.size());
        int wantPlain = 0;
        int wantCreature = 0;
        for (MultiBoxInfo.Roll roll : rolls) {
            if (roll.isEventCoin()) {
                made.add(null);
                prizeScripts.add(null);
                specials.add(null);
                continue;
            }
            ItemScript prize = scripts.item(roll.itemId()).orElse(null);
            if (prize == null) {
                return fail(Cmd221ReplyBody.ERROR_CANNOT_OPEN, boxSlot, keySlot,
                        "抽中的 " + roll.itemId() + " 在 pvf 里查不到");
            }
            InvenItem item = InventoryService.newItem(prize, roll.count());
            made.add(item);
            prizeScripts.add(prize);
            CeraShopSpecialItems.Special special = specialOf(prize);
            specials.add(special);
            if (special != null) {
                continue;
            }
            if (!prize.isAvatar()
                    && !InventoryService.isCreatureItemType(InventoryService.itemTypeOf(prize))) {
                wantPlain++;
            } else if (!prize.isAvatar()) {
                wantCreature++;
            }
        }
        if (wantPlain > 0 && emptySlots(bag) < wantPlain) {
            return fail(Cmd221ReplyBody.ERROR_INVENTORY_FULL, boxSlot, keySlot,
                    "主背包要 " + wantPlain + " 个空格，只有 " + emptySlots(bag) + " 个");
        }
        ItemArray pen = inventory.containerOf(ItemListType.LIST_CREATURE);
        if (wantCreature > 0 && emptySlots(pen) < wantCreature) {
            return fail(Cmd221ReplyBody.ERROR_INVENTORY_FULL, boxSlot, keySlot,
                    "宠物栏要 " + wantCreature + " 个空格，只有 " + emptySlots(pen) + " 个");
        }

        int taken = inventory.deleteItem(ItemListType.LIST_MAIN, boxSlot,
                (int) box.itemId(), boxNeeded);
        if (taken <= 0) {
            return fail(Cmd221ReplyBody.ERROR_CANNOT_OPEN, boxSlot, keySlot, "魔盒删不掉");
        }
        if (key != null) {
            inventory.deleteItem(ItemListType.LIST_MAIN, keySlot,
                    key.itemId(), keyNeeded);
        }

        var prizes = new ArrayList<Prize>(rolls.size());
        for (int i = 0; i < rolls.size(); i++) {
            MultiBoxInfo.Roll roll = rolls.get(i);
            if (roll.isEventCoin()) {
                prizes.add(giveEventCoin(roll));
            } else if (specials.get(i) != null) {
                prizes.add(giveSpecial(roll, prizeScripts.get(i), specials.get(i)));
            } else {
                prizes.add(givePrize(roll, prizeScripts.get(i), made.get(i)));
            }
        }
        inventory.save();

        Result result = new Result(0, boxSlot, keySlot, prizes);
        notice(boxScript, result);
        return result;
    }

    private static int emptySlots(ItemArray bag) {
        if (bag == null) {
            return 0;
        }
        int free = 0;
        for (int i = 0; i < bag.slots(); i++) {
            if (bag.get(i).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    private Prize giveEventCoin(MultiBoxInfo.Roll roll) {
        InventoryService inventory = session.inventory();
        int want = roll.count();
        long given = 0;
        if (want > 0 && want <= MultiBoxInfo.EVENT_COIN_MAX) {
            var items = inventory.inventory();
            long before = items == null ? 0 : items.eventCoin();
            inventory.setCoins(items == null ? 0 : items.coin(),
                    items == null ? 0 : items.payCoin(), before + want);
            given = (inventory.inventory() == null ? 0 : inventory.inventory().eventCoin()) - before;
            inventory.sendItemUpdate(ItemListType.LIST_MAIN, "开魔盒给了活动币",
                    BagLayout.SLOT_COIN);
        }
        return new Prize(new Cmd221ReplyBody.Entry(Cmd221ReplyBody.Entry.SLOT_NONE,
                MultiBoxInfo.EVENT_COIN_ITEM_ID, given, 0, 0),
                MultiBoxInfo.EVENT_COIN_ITEM_ID, 0, roll.broadcast(),
                "活动币 " + given);
    }

    private CeraShopSpecialItems.Special specialOf(ItemScript prize) {
        return SpecialItemService.deliverable(prize).orElse(null);
    }

    private Prize giveSpecial(MultiBoxInfo.Roll roll, ItemScript prize,
                              CeraShopSpecialItems.Special special) {
        var applied = SpecialItemService.apply(session, prize, special, roll.count());
        String detail = applied.detail();
        return new Prize(new Cmd221ReplyBody.Entry(Cmd221ReplyBody.Entry.SLOT_NONE,
                prize.id(), roll.count(), 0, 0),
                prize.id(), 0, roll.broadcast(), detail);
    }

    private Prize givePrize(MultiBoxInfo.Roll roll, ItemScript prize, InvenItem item) {
        InventoryService inventory = session.inventory();
        int inserted;
        int listType;
        long addInfo;
        if (prize.isAvatar()) {
            inserted = inventory.grantAvatarItem(prize.id(), prize.usablePeriod(), 0, 0);
            listType = ItemListType.LIST_AVATAR;
            addInfo = prize.usablePeriod();
        } else if (prize.isCreature()) {
            inserted = inventory.grantCreature(prize);
            listType = ItemListType.LIST_CREATURE;
            addInfo = 0;
        } else if (InventoryService.isCreatureItemType(InventoryService.itemTypeOf(prize))) {
            InventoryService.Landing landing = inventory.addItem(prize, roll.count());
            inserted = landing.slot();
            listType = ItemListType.LIST_CREATURE;
            addInfo = item.addInfo();
        } else {
            ItemArray bag = inventory.containerOf(ItemListType.LIST_MAIN);
            inserted = inventory.insertExisting(bag, item);
            listType = ItemListType.LIST_MAIN;
            addInfo = item.addInfo();
        }
        if (inserted < 0) {
            return new Prize(new Cmd221ReplyBody.Entry(Cmd221ReplyBody.Entry.SLOT_NONE,
                    prize.id(), 0, 0, 0), prize.id(), 0, false, prize.name() + "（没放进去）");
        }
        return new Prize(new Cmd221ReplyBody.Entry(inserted, prize.id(), addInfo,
                item.endurance(), item.itemAttr()),
                prize.id(), item.upgrade(), roll.broadcast(),
                prize.name() + " × " + roll.count());
    }

    private void notice(ItemScript box, Result result) {
        for (Prize p : result.prizes()) {
            if (!p.broadcast()) {
                continue;
            }
            byte[] body = Noti86Body.multiBox(session.uniqueId(), p.itemId(), p.upgrade(),
                    box.id());
            int sent = 0;
            for (GameSession other : SessionRegistry.playing()) {
                other.send(Noti86Body.PACKET_ID, body);
                sent++;
            }
        }
    }
}
