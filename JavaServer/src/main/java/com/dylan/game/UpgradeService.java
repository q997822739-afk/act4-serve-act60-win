package com.dylan.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.dylan.protocol.body.Cmd83ReplyBody;
import com.dylan.protocol.body.Cmd83UpgradeItemRequest;
import com.dylan.protocol.body.ItemListType;
import com.dylan.protocol.body.Noti86Body;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.DisjointTable;
import com.dylan.pvf.script.ItemScript;
import com.dylan.pvf.script.UpgradeTable;
import com.dylan.store.blob.InvenItem;
import com.dylan.store.blob.ItemArray;

public final class UpgradeService {
    public static final int MAX_UPGRADE_FOR_INFO = 30;

    private static final java.util.Map<Integer, Integer> TICKET_TARGET_UPGRADE =
            java.util.Map.of(7273, 7, 7465, 7, 7274, 10, 7275, 12);

    private static final int TICKET_SUCCESS_RATE = 100;

    private static final int TICKET_RATE_SCALE = 100;

    private final GameSession session;

    private final Random random;

    public UpgradeService(GameSession session) {
        this(session, new Random());
    }

    UpgradeService(GameSession session, Random random) {
        this.session = session;
        this.random = random;
    }

    public record Outcome(int error, int result, int slot, int itemId,
                          int upgradeBefore, int upgradeAfter,
                          int materialSlot, long materialLeft, long cost,
                          List<Cmd83ReplyBody.Fragment> fragments, String reason) {
        public static Outcome rejected(int error, String reason) {
            return new Outcome(error, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), reason);
        }

        public boolean ok() {
            return error == 0;
        }

        public boolean destroyed() {
            return error == 0 && result == Cmd83ReplyBody.UPGRADE_DESTROYED;
        }
    }

    record Requirement(int materialItemId, int materialCount, long cost) {}

    public Outcome upgrade(Cmd83UpgradeItemRequest request) {
        InventoryService inventory = session.inventory();
        ItemArray bag = inventory.containerOf(ItemListType.LIST_MAIN);
        if (bag == null) {
            return Outcome.rejected(Cmd83ReplyBody.ERROR_NO_SUCH_ITEM, "主背包还没装载");
        }
        GameScripts scripts = session.scripts();
        UpgradeTable table = scripts == null ? null : scripts.upgradeTable();
        if (table == null) {
            return Outcome.rejected(Cmd83ReplyBody.ERROR_MAX_LEVEL,
                    UpgradeTable.PATH + " 没读出来");
        }

        if (request.slot() < 0 || request.slot() >= bag.slots()) {
            return Outcome.rejected(Cmd83ReplyBody.ERROR_NO_SUCH_ITEM,
                    "装备槽 " + request.slot() + " 越界");
        }
        InvenItem target = bag.get(request.slot());
        if (target.isEmpty()) {
            return Outcome.rejected(Cmd83ReplyBody.ERROR_NO_SUCH_ITEM,
                    "槽 " + request.slot() + " 是空的");
        }
        if (target.itemId() != request.itemId()) {
            return Outcome.rejected(Cmd83ReplyBody.ERROR_NO_SUCH_ITEM,
                    "槽 " + request.slot() + " 上是 " + target.itemId()
                            + "，客户端报的是 " + request.itemId());
        }

        if (request.materialSlot() < 0 || request.materialSlot() >= bag.slots()) {
            return Outcome.rejected(Cmd83ReplyBody.ERROR_BAD_MATERIAL,
                    "材料槽 " + request.materialSlot() + " 越界");
        }
        InvenItem material = bag.get(request.materialSlot());
        if (material.isEmpty()) {
            return Outcome.rejected(Cmd83ReplyBody.ERROR_BAD_MATERIAL,
                    "材料槽 " + request.materialSlot() + " 是空的");
        }

        ItemScript script = scripts.item((int) target.itemId()).orElse(null);
        if (script == null) {
            return Outcome.rejected(Cmd83ReplyBody.ERROR_NO_SUCH_ITEM,
                    "物品 " + target.itemId() + " 不在物品表里");
        }

        if (request.isAmplify()) {
            return Outcome.rejected(Cmd83ReplyBody.ERROR_AMPLIFY_MISMATCH,
                    "请求带了增幅标志 " + request.amplify() + "，这一版没有增幅系统");
        }
        if (target.isAmplified()) {
            return Outcome.rejected(Cmd83ReplyBody.ERROR_AMPLIFY_MISMATCH,
                    "槽 " + request.slot() + " 上是增幅装备，普通强化走不了它");
        }

        int before = target.upgrade();
        if (before > MAX_UPGRADE_FOR_INFO) {
            return Outcome.rejected(Cmd83ReplyBody.ERROR_MAX_LEVEL,
                    "已经是 +" + before + "，再加一次会绕回 +0");
        }
        UpgradeTable.Step step = table.stepFrom(before);
        if (step == null) {
            return Outcome.rejected(Cmd83ReplyBody.ERROR_MAX_LEVEL,
                    "+" + before + " 已经是 [table] 的上限 +" + table.maxUpgrade());
        }
        Requirement need = new Requirement(step.materialItemId(), step.materialCount(),
                reduceUpgradePay(table.cost(script.grade(), script.equipmentSlot(),
                        script.rarity()), target.isAmplified()));

        int ticketUpgrade = ticketUpgradeOf(target, material);
        if (ticketUpgrade != 0) {
            need = new Requirement((int) material.itemId(), 1, 0);
        }

        int penalty = table.penaltyTypeOf(before, script.rarity(), step);
        boolean roomForFragments = penalty != UpgradeTable.PENALTY_DESTROY
                || roomForFragments(bag, script);
        int error = checkCondition(target, material, script, table, need,
                inventory.money(), roomForFragments);
        if (error != 0) {
            return Outcome.rejected(error, "check_error " + error
                    + "：材料槽 " + request.materialSlot() + " 放的是 " + material.itemId()
                    + " ×" + Math.max(1L, material.addInfo())
                    + "，这一单要 " + need.materialItemId() + " ×" + need.materialCount()
                    + " + " + need.cost() + " 金币（身上 " + inventory.money()
                    + "），装备 +" + before + " 耐久 " + target.endurance()
                    + "/" + script.freshDurability());
        }

        boolean success = ticketUpgrade != 0 ? ticketRoll() : roll(step);

        if (inventory.money() < need.cost()) {
            return Outcome.rejected(Cmd83ReplyBody.ERROR_NOT_ENOUGH_GOLD,
                    "扣 " + need.cost() + " 金币失败");
        }
        inventory.setMoney(inventory.money() - need.cost());
        long materialLeft = consumeMaterial(bag, request.materialSlot(), need.materialCount());
        if (materialLeft < 0) {
            return Outcome.rejected(Cmd83ReplyBody.ERROR_DELETE_FAILED,
                    "材料槽 " + request.materialSlot() + " 扣不出 " + need.materialCount() + " 个");
        }
        inventory.markItemsDirty();

        if (ticketUpgrade != 0) {
            return procTicket(bag, request, target, table, ticketUpgrade, before, success,
                    materialLeft, need);
        }

        notice(success, (int) target.itemId(), noticeLevelOf(success, before), table);

        if (success) {
            bag.set(request.slot(), target.withUpgrade(before + 1));
            return new Outcome(0, Cmd83ReplyBody.UPGRADE_SUCCESS, request.slot(),
                    (int) target.itemId(), before, before + 1,
                    request.materialSlot(), materialLeft, need.cost(), List.of(), "");
        }

        return procPenalty(bag, request, target, script, step, penalty, before,
                materialLeft, need.cost());
    }

    static int ticketUpgradeOf(InvenItem target, InvenItem material) {
        if (target.isAmplified()) {
            return 0;
        }
        return TICKET_TARGET_UPGRADE.getOrDefault((int) material.itemId(), 0);
    }

    private Outcome procTicket(ItemArray bag, Cmd83UpgradeItemRequest request, InvenItem target,
                               UpgradeTable table, int ticketUpgrade, int before,
                               boolean success, long materialLeft, Requirement need) {
        if (!success) {
            return new Outcome(0, Cmd83ReplyBody.UPGRADE_FAILED, request.slot(),
                    (int) target.itemId(), before, before,
                    request.materialSlot(), materialLeft, need.cost(), List.of(), "");
        }
        bag.set(request.slot(), target.withUpgrade(ticketUpgrade));
        notice(true, (int) target.itemId(), ticketUpgrade, table);
        return new Outcome(0, Cmd83ReplyBody.UPGRADE_SUCCESS, request.slot(),
                (int) target.itemId(), before, ticketUpgrade,
                request.materialSlot(), materialLeft, need.cost(), List.of(), "");
    }

    private long reduceUpgradePay(long base, boolean amplified) {
        if (base <= 0 || amplified) {
            return base;
        }
        if (!com.dylan.game.event.EventService.isRunning(
                com.dylan.game.event.EventNo.REDUCE_UPGRADE_PAY, session)) {
            return base;
        }
        int rate = com.dylan.game.event.EventService.param1(
                com.dylan.game.event.EventNo.REDUCE_UPGRADE_PAY);
        if (rate <= 0) {
            return base;
        }
        long cost = Math.max(0, base - rate * base / 100);
        return cost;
    }

    static int checkCondition(InvenItem target, InvenItem material, ItemScript script,
                              UpgradeTable table, Requirement need,
                              long money, boolean roomForFragments) {
        if (material.itemId() != need.materialItemId()) {
            return Cmd83ReplyBody.ERROR_BAD_MATERIAL;
        }
        if (Math.max(1L, material.addInfo()) < need.materialCount()) {
            return Cmd83ReplyBody.ERROR_BAD_MATERIAL;
        }

        if (money < need.cost()) {
            return Cmd83ReplyBody.ERROR_NOT_ENOUGH_GOLD;
        }

        if (table.stepFrom(target.upgrade()) == null) {
            return Cmd83ReplyBody.ERROR_MAX_LEVEL;
        }

        if (target.itemType() != InventoryService.EQUIPMENT_ITEM_TYPE) {
            return Cmd83ReplyBody.ERROR_NOT_UPGRADABLE;
        }
        if (!table.upgradable(script.equipmentSlot())) {
            return Cmd83ReplyBody.ERROR_NOT_UPGRADABLE;
        }

        if (target.endurance() != script.freshDurability()) {
            return Cmd83ReplyBody.ERROR_NOT_FULL_ENDURANCE;
        }

        int rarityMax = table.maxUpgradeLevel(script.rarity());
        if (rarityMax != UpgradeTable.NOT_CONFIGURED && target.upgrade() >= rarityMax) {
            return Cmd83ReplyBody.ERROR_RARITY_MAX_LEVEL;
        }

        if (!roomForFragments) {
            return Cmd83ReplyBody.ERROR_BAG_FULL;
        }
        return 0;
    }

    private boolean roomForFragments(ItemArray bag, ItemScript script) {
        GameScripts scripts = session.scripts();
        DisjointTable disjoint = scripts == null ? null : scripts.disjointTable();
        if (disjoint == null) {
            return true;
        }
        int needed = disjoint.predictResultItemTypeCount(script.rarity()) + 1;
        java.util.function.IntPredicate occupied =
                s -> s >= bag.slots() || !bag.get(s).isEmpty();
        if (BagLayout.fits(java.util.Map.of(BagLayout.TAB3, needed), occupied)) {
            return true;
        }
        return false;
    }

    boolean roll(UpgradeTable.Step step) {
        if (gmMode == GmMode.ALWAYS_SUCCESS) {
            return true;
        }
        if (gmMode == GmMode.ALWAYS_FAIL) {
            return false;
        }
        int failProbability = step.failProbability();
        if (failProbability <= 0) {
            return true;
        }
        return random.nextInt(UpgradeTable.FAIL_PROBABILITY_SCALE) >= failProbability;
    }

    private boolean ticketRoll() {
        if (gmMode == GmMode.ALWAYS_SUCCESS) {
            return true;
        }
        if (gmMode == GmMode.ALWAYS_FAIL) {
            return false;
        }
        return random.nextInt(TICKET_RATE_SCALE) < TICKET_SUCCESS_RATE;
    }

    public enum GmMode {
        RANDOM("按概率"),

        ALWAYS_SUCCESS("必成"),

        ALWAYS_FAIL("必败");

        private final String display;

        GmMode(String display) {
            this.display = display;
        }

        public String display() {
            return display;
        }
    }

    private GmMode gmMode = GmMode.RANDOM;

    public GmMode gmMode() {
        return gmMode;
    }

    public void gmMode(GmMode mode) {
        this.gmMode = mode == null ? GmMode.RANDOM : mode;
    }

    private Outcome procPenalty(ItemArray bag, Cmd83UpgradeItemRequest request,
                                InvenItem target, ItemScript script, UpgradeTable.Step step,
                                int penalty, int before, long materialLeft, long cost) {
        if (penalty == UpgradeTable.PENALTY_NONE) {
            return new Outcome(0, Cmd83ReplyBody.UPGRADE_FAILED, request.slot(),
                    (int) target.itemId(), before, before,
                    request.materialSlot(), materialLeft, cost, List.of(), "");
        }

        if (penalty == UpgradeTable.PENALTY_DEGRADE) {
            int after = step.degradedLevel(before);
            bag.set(request.slot(), target.withUpgrade(after));
            return new Outcome(0, Cmd83ReplyBody.UPGRADE_DEGRADED, request.slot(),
                    (int) target.itemId(), before, after,
                    request.materialSlot(), materialLeft, cost, List.of(), "");
        }

        bag.set(request.slot(), InvenItem.EMPTY);
        List<Cmd83ReplyBody.Fragment> fragments = grantFragments(bag, target, script, before);
        return new Outcome(0, Cmd83ReplyBody.UPGRADE_DESTROYED, request.slot(),
                (int) target.itemId(), before, 0,
                request.materialSlot(), materialLeft, cost, fragments, "");
    }

    private List<Cmd83ReplyBody.Fragment> grantFragments(ItemArray bag, InvenItem target,
                                                         ItemScript script, int upgradeBefore) {
        GameScripts scripts = session.scripts();
        DisjointTable disjoint = scripts == null ? null : scripts.disjointTable();
        if (disjoint == null) {
            return List.of();
        }
        long basePrice = (long) (script.shopBasePrice() * 1.1);
        long sellPrice = scripts.priceTable().sellPrice(basePrice, false, 0, 0);
        var products = new ArrayList<>(
                disjoint.resultOf(sellPrice, script.grade(), script.rarity(), random));

        UpgradeTable table = scripts.upgradeTable();
        if (table != null) {
            var bonus = table.additionalDisjointBonus(script.grade(), script.rarity(),
                    upgradeBefore);
            if (bonus != null) {
                products.add(new DisjointTable.Product(bonus.itemId(), bonus.count()));
            }
        }

        List<Cmd83ReplyBody.Fragment> out = new ArrayList<>(products.size());
        for (var p : products) {
            ItemScript fragmentScript = scripts.item(p.itemId()).orElse(null);
            if (fragmentScript == null) {
                continue;
            }
            int slot = InventoryService.grant(bag, fragmentScript, p.count());
            if (slot < 0) {
                continue;
            }
            Math.max(1L, bag.get(slot).addInfo());
            out.add(new Cmd83ReplyBody.Fragment(slot, p.itemId(), p.count()));
        }
        return out;
    }

    static int noticeLevelOf(boolean success, int upgradeBefore) {
        return success ? upgradeBefore + 1 : upgradeBefore;
    }

    private void notice(boolean success, int itemId, int upgrade, UpgradeTable table) {
        if (upgrade < table.noticeLevel()) {
            return;
        }
        byte[] body = new Noti86Body(success, session.uniqueId(), itemId, upgrade).toBytes();
        int sent = 0;
        for (GameSession other : SessionRegistry.playing()) {
            other.send(Noti86Body.PACKET_ID, body);
            sent++;
        }
    }

    private static long consumeMaterial(ItemArray bag, int slot, int count) {
        InvenItem material = bag.get(slot);
        if (material.isEmpty()) {
            return -1;
        }
        long have = Math.max(1L, material.addInfo());
        if (have < count) {
            return -1;
        }
        long left = have - count;
        bag.set(slot, left <= 0 ? InvenItem.EMPTY : material.withAddInfo(left));
        return left;
    }
}
