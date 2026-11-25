package com.dylan.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.dylan.protocol.body.Cmd170ReplyBody;
import com.dylan.protocol.body.ItemListType;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.BoosterInfo;
import com.dylan.pvf.script.ItemScript;
import com.dylan.store.CharacterRow;
import com.dylan.store.GameStore;
import com.dylan.store.blob.InvenItem;
import com.dylan.store.blob.ItemArray;

public final class BoosterService {

    public static final int PAY_COIN_ITEM_ID = 1;

    public static final int REQUIRED_FREE_CREATURE_SLOTS = 1;

    private static final int CONSUME_COUNT = 1;

    private final GameSession session;
    private final Random random;

    public BoosterService(GameSession session) {
        this(session, new Random());
    }

    BoosterService(GameSession session, Random random) {
        this.session = session;
        this.random = random;
    }

    public record Prize(Cmd170ReplyBody.Entry entry, String detail) {}

    public record Result(int error, String fatal, int slot, long remain, List<Prize> prizes) {

        public Result {
            prizes = List.copyOf(prizes);
        }

        public boolean ok() {
            return error == 0 && fatal == null;
        }

        public List<Cmd170ReplyBody.Entry> entries() {
            var out = new ArrayList<Cmd170ReplyBody.Entry>(prizes.size());
            for (Prize p : prizes) {
                out.add(p.entry());
            }
            return out;
        }
    }

    public Result use(int slot) {
        InventoryService inventory = session.inventory();
        ItemArray bag = inventory.containerOf(ItemListType.LIST_MAIN);
        if (bag == null || slot < 0 || slot >= bag.slots()) {
            return fatal("槽号 " + slot + " 越界（主背包 "
                    + (bag == null ? "没有数据源" : bag.slots() + " 格") + "）", slot);
        }
        InvenItem held = bag.get(slot);
        GameScripts scripts = session.scripts();
        ItemScript script = held.isEmpty() || scripts == null
                ? null : scripts.item((int) held.itemId()).orElse(null);
        if (script == null) {
            return fatal("槽 " + slot + " 是"
                    + (held.isEmpty() ? "空的" : held.itemId() + "，在 pvf 里查不到"), slot);
        }
        if (!script.isBoosterItem()) {
            return fatal("槽 " + slot + " 的 " + script.id() + "「" + script.name()
                    + "」不是礼包（[stackable type] " + script.stackableType() + "）", slot);
        }

        int itemId = (int) held.itemId();
        if (session.inDungeon()) {
            return reject(Cmd170ReplyBody.ERROR_CANNOT_USE, slot, "不在城镇里");
        }
        CharacterRow row = session.character();
        if (row == null) {
            return fatal("还没选角色", slot);
        }
        if (!script.usableByJob(row.job())) {
            return reject(Cmd170ReplyBody.ERROR_WRONG_JOB, slot,
                    script.name() + " 职业 " + row.job() + " 用不了（掩码 "
                            + Integer.toBinaryString(script.usableJobs()) + "）");
        }
        if (script.minimumLevel() > session.level()) {
            return reject(Cmd170ReplyBody.ERROR_CANNOT_USE, slot,
                    script.name() + " 要 " + script.minimumLevel() + " 级，现在 "
                            + session.level() + " 级");
        }
        ItemArray pen = inventory.containerOf(ItemListType.LIST_CREATURE);
        if (emptySlots(pen) < REQUIRED_FREE_CREATURE_SLOTS) {
            return reject(Cmd170ReplyBody.ERROR_NOT_ENOUGH_SPACE, slot,
                    "宠物栏一个空格都没有（官方 check_empty_count(ITEM_TYPE 5, 1)）");
        }
        if (session.consumables().checkCoolTimeItem(itemId)) {
            return reject(Cmd170ReplyBody.ERROR_CANNOT_USE, slot, script.name() + " 还在冷却中");
        }

        BoosterInfo info = script.booster();
        List<BoosterInfo.Entry> drawn = info.draw(random);

        var resolved = new ArrayList<ItemScript>(drawn.size());
        int avatarNeeded = 0;
        int creatureNeeded = 0;
        for (BoosterInfo.Entry e : drawn) {
            if (e.itemId() == PAY_COIN_ITEM_ID) {
                resolved.add(null);
                continue;
            }
            ItemScript prize = scripts.item(e.itemId()).orElse(null);
            resolved.add(prize);
            if (prize == null) {
                continue;
            }
            if (prize.isAvatar()) {
                avatarNeeded++;
            } else if (prize.isCreature()
                    || InventoryService.isCreatureItemType(InventoryService.itemTypeOf(prize))) {
                creatureNeeded++;
            }
        }
        if (avatarNeeded > inventory.freeAvatarSlots()) {
            return reject(Cmd170ReplyBody.ERROR_NOT_ENOUGH_SPACE, slot,
                    "装扮栏要 " + avatarNeeded + " 格，只有 " + inventory.freeAvatarSlots() + " 格");
        }
        if (creatureNeeded > emptySlots(pen)) {
            return reject(Cmd170ReplyBody.ERROR_NOT_ENOUGH_SPACE, slot,
                    "宠物栏要 " + creatureNeeded + " 格，只有 " + emptySlots(pen) + " 格");
        }

        int taken = inventory.deleteItem(ItemListType.LIST_MAIN, slot, itemId, CONSUME_COUNT);
        if (taken <= 0) {
            return fatal("槽 " + slot + " 的 " + itemId + " 扣不掉", slot);
        }
        session.consumables().processContinuousItem(itemId, script);
        inventory.sendItemUpdate(ItemListType.LIST_MAIN, "拆礼包扣掉一个", slot);

        var prizes = new ArrayList<Prize>(drawn.size());
        for (int i = 0; i < drawn.size(); i++) {
            BoosterInfo.Entry e = drawn.get(i);
            if (e.itemId() == PAY_COIN_ITEM_ID) {
                prizes.add(givePayCoin(e));
                continue;
            }
            ItemScript prize = resolved.get(i);
            if (prize == null) {
                continue;
            }
            prizes.add(give(script, e, prize));
        }
        inventory.save();

        InvenItem after = bag.get(slot);
        long remain = after.isEmpty() || after.itemId() != itemId ? 0 : after.addInfo();
        return new Result(0, null, slot, remain, prizes);
    }

    private Prize givePayCoin(BoosterInfo.Entry e) {
        InventoryService inventory = session.inventory();
        var items = inventory.inventory();
        if (items != null) {
            inventory.setCoins(items.coin(), items.payCoin() + e.count(), items.eventCoin());
            inventory.sendItemUpdate(ItemListType.LIST_MAIN, "拆礼包给了复活币",
                    BagLayout.SLOT_COIN);
        }
        return new Prize(new Cmd170ReplyBody.Entry(PAY_COIN_ITEM_ID, e.count()),
                "复活币 × " + e.count());
    }

    private Prize give(ItemScript box, BoosterInfo.Entry e, ItemScript prize) {
        InventoryService inventory = session.inventory();
        int count = Math.max(1, e.count());
        if (prize.isAvatar()) {
            int days = avatarPeriodOf(prize, e.periodIndex());
            int at = inventory.grantAvatarItem(prize.id(), days, e.abilityNo(), 0);
            if (at >= 0) {
                inventory.sendItemUpdate(ItemListType.LIST_AVATAR, "拆礼包拆出时装", at);
            }
            return new Prize(new Cmd170ReplyBody.Entry(prize.id(), count),
                    prize.name() + "（时装）");
        }
        if (prize.isCreature()) {
            int at = inventory.grantCreature(prize);
            if (at >= 0) {
                inventory.sendItemUpdate(ItemListType.LIST_CREATURE, "拆礼包拆出宠物", at);
            }
            return new Prize(new Cmd170ReplyBody.Entry(prize.id(), count),
                    prize.name() + "（宠物）");
        }
        InventoryService.Landing landing = inventory.addItem(prize, count);
        if (!landing.ok()) {
            return mail(box, prize, count);
        }
        inventory.sendItemUpdate(landing.itemSpace(), "拆礼包拆出 " + prize.id(),
                landing.slot());
        return new Prize(new Cmd170ReplyBody.Entry(prize.id(), count),
                prize.name() + " × " + count);
    }

    private Prize mail(ItemScript box, ItemScript prize, int count) {
        GameStore store = session.store();
        if (store == null) {
            return new Prize(new Cmd170ReplyBody.Entry(prize.id(), count),
                    prize.name() + "（放不下且寄不出）");
        }
        MailService.deliver(store, session.characNo(), box.name(),
                prize.id(), count, 0, true);
        return new Prize(new Cmd170ReplyBody.Entry(prize.id(), count),
                prize.name() + " × " + count + "（已寄到邮箱）");
    }

    private static int avatarPeriodOf(ItemScript prize, int index) {
        var types = prize.avatarTypes();
        if (index < 0 || index >= types.size()) {
            return prize.usablePeriod();
        }
        return types.get(index).days();
    }

    private static int emptySlots(ItemArray array) {
        if (array == null) {
            return 0;
        }
        int free = 0;
        for (int i = 0; i < array.slots(); i++) {
            if (array.get(i).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    private Result reject(int error, int slot, String why) {
        return new Result(error, null, slot, 0, List.of());
    }

    private static Result fatal(String why, int slot) {
        return new Result(0, why, slot, 0, List.of());
    }
}
