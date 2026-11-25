package com.dylan.game;

import java.util.Random;

import com.dylan.game.drop.EquipGradeGenerator;
import com.dylan.protocol.body.Cmd84ReplyBody;
import com.dylan.protocol.body.Cmd84ResetItemAttrRequest;
import com.dylan.protocol.body.EquipmentSlot;
import com.dylan.protocol.body.ItemListType;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.ItemScript;
import com.dylan.pvf.script.ResealTable;
import com.dylan.store.blob.InvenItem;
import com.dylan.store.blob.ItemArray;

public final class ResetItemAttrService {

    private static final int[] RESEAL_ITEMS = {14, 690_000_113};

    private static final int[] RESEAL_ONESHOT_ITEMS = {2_675_422};

    private static final int[] RESET_GRADE_ITEMS = {15, 897, 2_675_022, 690_000_114};

    private static final int RESEAL_COUNT_CAP = 7;

    private static final int ITEM_TYPE_EQUIPMENT = 1;

    private static final int ATTACH_TYPE_SEALING = ItemScript.ATTACH_SEALING;

    private static final int ATTACH_STATE_SEALED = 1;

    private static final int REGRADE_PROBE =
            Integer.getInteger("s1a5.regradeProbe", 0);

    private static final java.util.concurrent.atomic.AtomicInteger PROBE_COUNTER =
            new java.util.concurrent.atomic.AtomicInteger();

    private final GameSession session;

    private final Random random;

    public ResetItemAttrService(GameSession session) {
        this(session, new Random());
    }

    ResetItemAttrService(GameSession session, Random random) {
        this.session = session;
        this.random = random;
    }

    public record Outcome(int error, int kind, int slot, int itemId,
                          int boxSlot, InvenItem boxTemplate, long boxLeft, int cost,
                          boolean bagChanged, String detail) {

        public static Outcome rejected(int error, String detail) {
            return new Outcome(error, 0, 0, 0, 0, InvenItem.EMPTY, 0, 0, false, detail);
        }

        public boolean ok() {
            return error == 0;
        }

        public int boxItemId() {
            return (int) boxTemplate.itemId();
        }
    }

    public Outcome execute(Cmd84ResetItemAttrRequest request) {
        InventoryService inventory = session.inventory();
        ItemArray bag = inventory.containerOf(ItemListType.LIST_MAIN);
        if (bag == null) {
            return Outcome.rejected(Cmd84ReplyBody.ERROR_NO_SUCH_ITEM, "主背包还没装载");
        }
        GameScripts scripts = session.scripts();
        if (scripts == null) {
            return Outcome.rejected(Cmd84ReplyBody.ERROR_NO_SUCH_ITEM, "脚本还没装载");
        }

        if (request.slot() < 0 || request.slot() >= bag.slots()) {
            return Outcome.rejected(Cmd84ReplyBody.ERROR_NO_SUCH_ITEM,
                    "装备槽 " + request.slot() + " 越界");
        }
        InvenItem target = bag.get(request.slot());
        if (target.isEmpty()) {
            return Outcome.rejected(Cmd84ReplyBody.ERROR_NO_SUCH_ITEM,
                    "槽 " + request.slot() + " 是空的");
        }
        if (target.itemId() != request.itemId()) {
            return Outcome.rejected(Cmd84ReplyBody.ERROR_NO_SUCH_ITEM,
                    "槽 " + request.slot() + " 上是 " + target.itemId()
                            + "，客户端报的是 " + request.itemId());
        }
        if (target.itemType() != ITEM_TYPE_EQUIPMENT) {
            return Outcome.rejected(Cmd84ReplyBody.ERROR_NOT_APPLICABLE,
                    "槽 " + request.slot() + " 上那件的 itemType 是 " + target.itemType()
                            + "，不是装备");
        }
        ItemScript script = scripts.item((int) target.itemId()).orElse(null);
        if (script == null) {
            return Outcome.rejected(Cmd84ReplyBody.ERROR_NO_SUCH_ITEM,
                    "物品 " + target.itemId() + " 不在物品表里");
        }
        if (!adjustable(script.equipmentSlot())) {
            return Outcome.rejected(Cmd84ReplyBody.ERROR_NOT_APPLICABLE,
                    "物品 " + target.itemId() + " 的部位 " + script.equipmentSlot()
                            + " 不在可调整的 " + EquipmentSlot.WEAPON + ".."
                            + (EquipmentSlot.CREATURE - 1) + " 里");
        }

        if (request.boxSlot() < 0 || request.boxSlot() >= bag.slots()) {
            return Outcome.rejected(Cmd84ReplyBody.ERROR_NO_ITEM,
                    "道具槽 " + request.boxSlot() + " 越界");
        }
        InvenItem box = bag.get(request.boxSlot());
        if (box.isEmpty()) {
            return Outcome.rejected(Cmd84ReplyBody.ERROR_NO_ITEM,
                    "道具槽 " + request.boxSlot() + " 是空的");
        }

        int boxItemId = (int) box.itemId();
        int kind;
        int cost;
        if (contains(RESET_GRADE_ITEMS, boxItemId)) {
            kind = Cmd84ReplyBody.KIND_RESET_GRADE;
            cost = 1;
        } else if (contains(RESEAL_ITEMS, boxItemId)) {
            kind = Cmd84ReplyBody.KIND_RESEAL;
            if (target.reSealCount() > ResealTable.MAX_RESEAL_COUNT) {
                return Outcome.rejected(Cmd84ReplyBody.ERROR_RESEAL_COUNT_MAX,
                        "槽 " + request.slot() + " 上那件已经封过 " + target.reSealCount() + " 次");
            }
            if (script.equipmentSlot() == EquipmentSlot.TITLE) {
                return Outcome.rejected(Cmd84ReplyBody.ERROR_NOT_APPLICABLE, "称号不能重新封装");
            }
            ResealTable table = scripts.resealTable();
            if (table == null) {
                return Outcome.rejected(Cmd84ReplyBody.ERROR_NO_ITEM,
                        ResealTable.PATH + " 没读出来");
            }
            cost = table.resealCost(script.rarity(), script.grade(), target.reSealCount());
            if (cost < 0) {
                return Outcome.rejected(Cmd84ReplyBody.ERROR_NOT_APPLICABLE,
                        ResealTable.PATH + " 算不出价：稀有度 " + script.rarity()
                                + " 等级 " + script.grade() + " 已封 " + target.reSealCount()
                                + " 次 → " + cost);
            }
            if (script.attachType() != ATTACH_TYPE_SEALING) {
                return Outcome.rejected(Cmd84ReplyBody.ERROR_NOT_APPLICABLE,
                        "物品 " + target.itemId() + " 的 [attach type] 是 "
                                + script.attachType() + "，不是封装类");
            }
        } else if (contains(RESEAL_ONESHOT_ITEMS, boxItemId)) {
            kind = Cmd84ReplyBody.KIND_RESEAL;
            if (script.equipmentSlot() == EquipmentSlot.TITLE) {
                return Outcome.rejected(Cmd84ReplyBody.ERROR_NOT_APPLICABLE, "称号不能重新封装");
            }
            if (script.attachType() != ATTACH_TYPE_SEALING) {
                return Outcome.rejected(Cmd84ReplyBody.ERROR_NOT_APPLICABLE,
                        "物品 " + target.itemId() + " 的 [attach type] 是 "
                                + script.attachType() + "，不是封装类");
            }
            ResealTable table = scripts.resealTable();
            if (table == null) {
                return Outcome.rejected(Cmd84ReplyBody.ERROR_NO_ITEM,
                        ResealTable.PATH + " 没读出来");
            }
            cost = table.oneshotResealCost(script.rarity(), script.grade());
            if (cost < 0) {
                return Outcome.rejected(Cmd84ReplyBody.ERROR_NOT_APPLICABLE,
                        ResealTable.PATH + " 算不出一口价：稀有度 " + script.rarity()
                                + " 等级 " + script.grade() + " → " + cost);
            }
        } else {
            return Outcome.rejected(Cmd84ReplyBody.ERROR_NO_ITEM,
                    "道具槽 " + request.boxSlot() + " 上是 " + boxItemId
                            + "，这条命令不认得它");
        }

        if (box.addInfo() < cost) {
            return Outcome.rejected(Cmd84ReplyBody.ERROR_NOT_ENOUGH_ITEM,
                    "道具槽 " + request.boxSlot() + " 只有 " + box.addInfo()
                            + " 个，要 " + cost + " 个");
        }

        long boxLeft = consume(bag, request.boxSlot(), cost);
        if (boxLeft < 0) {
            return Outcome.rejected(Cmd84ReplyBody.ERROR_NO_ITEM,
                    "道具槽 " + request.boxSlot() + " 扣不出 " + cost + " 个");
        }
        inventory.markItemsDirty();

        if (kind == Cmd84ReplyBody.KIND_RESEAL) {
            if (target.attachType() != 0) {
                return new Outcome(Cmd84ReplyBody.ERROR_ALREADY_SEALED, kind, request.slot(),
                        (int) target.itemId(), request.boxSlot(), box, boxLeft, cost, true,
                        "槽 " + request.slot() + " 上那件已经封着了");
            }
            target = target.withAttachType(ATTACH_STATE_SEALED)
                    .withReSealCount(Math.min(target.reSealCount() + 1, RESEAL_COUNT_CAP));
        } else {
            target = target.withAddInfo(nextGradeValue());
        }
        bag.set(request.slot(), target);

        return new Outcome(0, kind, request.slot(), (int) target.itemId(),
                request.boxSlot(), box, boxLeft, cost, true, "");
    }

    private long nextGradeValue() {
        if (REGRADE_PROBE > 0) {
            int index = Math.floorMod(PROBE_COUNTER.getAndIncrement(), REGRADE_PROBE);
            long value = index < EquipGradeGenerator.GRADE_CODES.length
                    ? EquipGradeGenerator.GRADE_CODES[index]
                    : index;
            return value;
        }
        return EquipGradeGenerator.generate(random);
    }

    static boolean adjustable(int equipmentSlot) {
        return equipmentSlot >= EquipmentSlot.WEAPON && equipmentSlot < EquipmentSlot.CREATURE;
    }

    public int returnPart(Outcome outcome) {
        int rate = session.premium().returnItemRate(outcome.boxItemId());
        if (rate == 0) {
            return 0;
        }
        int back = rate * outcome.cost() / 100;
        if (back <= 0) {
            return 0;
        }
        InventoryService inventory = session.inventory();
        ItemArray bag = inventory.containerOf(ItemListType.LIST_MAIN);
        if (bag == null) {
            return 0;
        }
        int slot = inventory.insertExisting(bag, outcome.boxTemplate().withAddInfo(back));
        if (slot < 0) {
            return 0;
        }
        inventory.markItemsDirty();
        inventory.sendItemUpdate(ItemListType.LIST_MAIN, "会员返还的道具", slot);
        return back;
    }

    private static long consume(ItemArray bag, int slot, int count) {
        InvenItem box = bag.get(slot);
        if (box.isEmpty() || box.addInfo() < count) {
            return -1;
        }
        long left = box.addInfo() - count;
        bag.set(slot, left <= 0 ? InvenItem.EMPTY : box.withAddInfo(left));
        return left;
    }

    private static boolean contains(int[] ids, int itemId) {
        for (int id : ids) {
            if (id == itemId) {
                return true;
            }
        }
        return false;
    }
}
