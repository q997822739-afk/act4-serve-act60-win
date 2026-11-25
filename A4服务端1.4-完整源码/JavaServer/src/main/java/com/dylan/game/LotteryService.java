package com.dylan.game;

import java.util.Random;

import com.dylan.protocol.body.Cmd29ReplyBody;
import com.dylan.protocol.body.EquipmentSlot;
import com.dylan.protocol.body.ItemListType;
import com.dylan.protocol.body.Noti2PartyEntry;
import com.dylan.protocol.body.Noti86Body;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.ItemScript;
import com.dylan.pvf.script.LotteryInfo;
import com.dylan.pvf.script.ServerParameter;
import com.dylan.store.blob.InvenItem;
import com.dylan.store.blob.ItemArray;
import com.dylan.store.blob.ItemType;

public final class LotteryService {

    private final GameSession session;
    private final Random random;

    public LotteryService(GameSession session) {
        this(session, new Random());
    }

    LotteryService(GameSession session, Random random) {
        this.session = session;
        this.random = random;
    }

    public record Outcome(int error, int slot, int insertedSlot, int itemId, long addInfo,
                          int endurance, int itemAttr, int upgrade, int listType,
                          boolean avatarTail, String detail) {

        public boolean ok() {
            return error == 0;
        }

        public boolean gold() {
            return itemId == 0;
        }
    }

    private static boolean avatarTail(ItemScript prize) {
        return Noti2PartyEntry.isAvatarSlot(prize.equipmentSlot());
    }

    private static Outcome fail(int error, int slot, String detail) {
        return new Outcome(error, slot, 0, 0, 0, 0, 0, 0, ItemListType.LIST_MAIN, false, detail);
    }

    public Outcome open(int slot) {
        InventoryService inventory = session.inventory();
        ItemArray bag = inventory.containerOf(ItemListType.LIST_MAIN);
        if (bag == null || slot < 0 || slot >= bag.slots()) {
            return fail(Cmd29ReplyBody.ERROR_NO_SUCH_ITEM, slot, "槽号越界");
        }
        InvenItem jar = bag.get(slot);
        GameScripts scripts = session.scripts();
        ItemScript jarScript = jar.isEmpty() || scripts == null
                ? null : scripts.item((int) jar.itemId()).orElse(null);
        if (jarScript == null) {
            return fail(Cmd29ReplyBody.ERROR_NO_SUCH_ITEM, slot,
                    "槽 " + slot + " 的 " + jar.itemId() + " 在 pvf 里查不到");
        }
        if (jarScript.minimumLevel() > session.level()) {
            return fail(Cmd29ReplyBody.ERROR_LEVEL_TOO_LOW, slot,
                    "要 " + jarScript.minimumLevel() + " 级，角色只有 " + session.level() + " 级");
        }
        if (!jarScript.isLotteryItem()) {
            return fail(Cmd29ReplyBody.ERROR_CANNOT_OPEN, slot,
                    jarScript.name() + " 不是罐子（[stackable type] " + jarScript.stackableType() + "）");
        }

        LotteryInfo pool = jarScript.lottery();
        LotteryInfo.Roll roll = pool.roll(random);

        if (roll.isGold()) {
            return giveGold(slot, jar, roll);
        }
        return givePrize(slot, jar, jarScript, roll);
    }

    private Outcome giveGold(int slot, InvenItem jar, LotteryInfo.Roll roll) {
        InventoryService inventory = session.inventory();
        long before = inventory.money();
        long want = roll.count();
        inventory.setMoney(before + want);
        long gained = inventory.money() - before;

        int taken = inventory.deleteItem(ItemListType.LIST_MAIN, slot, (int) jar.itemId(), 1);
        if (taken <= 0) {
            inventory.setMoney(before);
            return fail(Cmd29ReplyBody.ERROR_CANNOT_OPEN, slot, "罐子删不掉，金币已回滚");
        }
        inventory.save();
        return new Outcome(0, slot, 0, 0, gained, 0, 0, 0, ItemListType.LIST_MAIN, false,
                "金币 " + gained);
    }

    private Outcome givePrize(int slot, InvenItem jar, ItemScript jarScript,
                              LotteryInfo.Roll roll) {
        GameScripts scripts = session.scripts();
        ItemScript prize = scripts == null ? null
                : scripts.item(roll.itemId()).orElse(null);
        if (prize == null) {
            return fail(Cmd29ReplyBody.ERROR_CANNOT_OPEN, slot,
                    "抽中的 " + roll.itemId() + " 在 pvf 里查不到");
        }

        InventoryService inventory = session.inventory();
        InvenItem made = applyLegacyUpgrade(jarScript, prize,
                InventoryService.newItem(prize, roll.count()));

        int free = freeSlotFor(prize, made);
        if (free < 0) {
            return fail(Cmd29ReplyBody.ERROR_INVENTORY_FULL, slot,
                    prize.name() + " 放不下");
        }

        int taken = inventory.deleteItem(ItemListType.LIST_MAIN, slot, (int) jar.itemId(), 1);
        if (taken <= 0) {
            return fail(Cmd29ReplyBody.ERROR_CANNOT_OPEN, slot, "罐子删不掉");
        }

        Outcome outcome;
        int itemType = InventoryService.itemTypeOf(prize);
        if (prize.isAvatar()) {
            outcome = giveAvatar(slot, prize, made);
        } else if (prize.isCreature()) {
            outcome = giveCreature(slot, prize, made);
        } else if (InventoryService.isCreatureItemType(itemType)) {
            outcome = giveCreatureSupply(slot, prize, made);
        } else {
            outcome = giveItem(slot, prize, made);
        }
        inventory.save();
        if (outcome.ok()) {
            notice(jarScript, prize, outcome);
        }
        return outcome;
    }

    private InvenItem applyLegacyUpgrade(ItemScript jar, ItemScript prize, InvenItem made) {
        if (jar.stackableType() != ItemScript.STACKABLE_UPGRADABLE_LEGACY
                || made.itemType() != ItemType.EQUIPMENT.value()
                || prize.equipmentSlot() == EquipmentSlot.TITLE) {
            return made;
        }
        ServerParameter params = session.scripts() == null
                ? null : session.scripts().serverParameter();
        if (params == null) {
            return made;
        }
        int level = params.rollLegacyUpgrade(random);
        if (level == 0) {
            return made;
        }
        return made.withUpgrade(level);
    }

    private void notice(ItemScript jar, ItemScript prize, Outcome outcome) {
        if (jar.stackableType() != ItemScript.STACKABLE_UPGRADABLE_LEGACY) {
            return;
        }
        ServerParameter params = session.scripts() == null
                ? null : session.scripts().serverParameter();
        if (params == null) {
            return;
        }
        boolean byRarity = params.noticeLegacyRarity() <= prize.rarity();
        boolean bySlot = prize.isAvatar() && params.noticeLegacySlot(prize.equipmentSlot());
        boolean byUpgrade = params.noticeLegacyUpgrade() <= outcome.upgrade();
        if (!byRarity && !bySlot && !byUpgrade) {
            return;
        }
        byte[] body = Noti86Body.lottery(session.uniqueId(), prize.id(), outcome.upgrade());
        int sent = 0;
        for (GameSession other : SessionRegistry.playing()) {
            other.send(Noti86Body.PACKET_ID, body);
            sent++;
        }
    }

    private int freeSlotFor(ItemScript prize, InvenItem made) {
        InventoryService inventory = session.inventory();
        if (prize.isAvatar()) {
            return inventory.firstFreeAvatarSlot();
        }
        if (InventoryService.isCreatureItemType(InventoryService.itemTypeOf(prize))) {
            ItemArray pen = inventory.containerOf(ItemListType.LIST_CREATURE);
            return InventoryService.tryCreatureSlot(pen, prize, (int) made.stackCount());
        }
        ItemArray bag = inventory.containerOf(ItemListType.LIST_MAIN);
        return bag == null ? -1 : inventory.tryInsertExisting(bag, made);
    }

    private Outcome giveAvatar(int slot, ItemScript prize, InvenItem made) {
        int inserted = session.inventory().grantAvatarItem(prize.id(), prize.usablePeriod(),
                0, 0);
        if (inserted < 0) {
            return fail(Cmd29ReplyBody.ERROR_INVENTORY_FULL, slot, "装扮栏满了");
        }
        return new Outcome(0, slot, inserted, prize.id(), prize.usablePeriod(),
                made.endurance(), made.itemAttr(), made.upgrade(), ItemListType.LIST_AVATAR,
                avatarTail(prize),
                "时装 " + prize.name() + (prize.hasUsablePeriod()
                        ? "（" + prize.usablePeriod() + " 天）" : "（永久）"));
    }

    private Outcome giveCreature(int slot, ItemScript prize, InvenItem made) {
        int inserted = session.inventory().grantCreature(prize);
        if (inserted < 0) {
            return fail(Cmd29ReplyBody.ERROR_INVENTORY_FULL, slot, "宠物栏满了");
        }
        return new Outcome(0, slot, inserted, prize.id(), 0,
                made.endurance(), made.itemAttr(), made.upgrade(), ItemListType.LIST_CREATURE,
                avatarTail(prize), "宠物 " + prize.name());
    }

    private Outcome giveCreatureSupply(int slot, ItemScript prize, InvenItem made) {
        InventoryService inventory = session.inventory();
        InventoryService.Landing landing = inventory.addItem(prize, (int) made.stackCount());
        if (!landing.ok()) {
            return fail(Cmd29ReplyBody.ERROR_INVENTORY_FULL, slot, "宠物栏满了");
        }
        return new Outcome(0, slot, landing.slot(), prize.id(), made.addInfo(),
                made.endurance(), made.itemAttr(), made.upgrade(),
                ItemListType.LIST_CREATURE, avatarTail(prize),
                prize.name() + " × " + made.stackCount());
    }

    private Outcome giveItem(int slot, ItemScript prize, InvenItem made) {
        InventoryService inventory = session.inventory();
        ItemArray bag = inventory.containerOf(ItemListType.LIST_MAIN);
        int inserted = inventory.insertExisting(bag, made);
        if (inserted < 0) {
            return fail(Cmd29ReplyBody.ERROR_INVENTORY_FULL, slot, "主背包满了");
        }
        return new Outcome(0, slot, inserted, prize.id(), made.addInfo(),
                made.endurance(), made.itemAttr(), made.upgrade(), ItemListType.LIST_MAIN,
                avatarTail(prize), prize.name() + " × " + made.addInfo());
    }
}
