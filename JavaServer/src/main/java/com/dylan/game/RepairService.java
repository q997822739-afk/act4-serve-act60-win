package com.dylan.game;

import java.util.ArrayList;
import java.util.List;

import com.dylan.protocol.body.Cmd25RepairEquipRequest;
import com.dylan.protocol.body.Cmd25ReplyBody;
import com.dylan.protocol.body.EquipmentSlot;
import com.dylan.protocol.body.ItemListType;
import com.dylan.pvf.script.ItemScript;
import com.dylan.store.blob.InvenItem;
import com.dylan.store.blob.ItemArray;

public final class RepairService {

    private RepairService() {
    }

    public static final int REPAIR_ALL_BAG_FIRST = BagLayout.QUICK_FIRST;

    public static final int REPAIR_ALL_BAG_LAST = BagLayout.QUICK_LAST;

    public static final int REPAIR_ALL_EQUIP_FIRST = EquipmentSlot.WEAPON;

    public static final int REPAIR_ALL_EQUIP_LAST = EquipmentSlot.CREATURE - 1;

    public record Target(int itemSpace, int slot, int itemId, int before, int after, long cost) {
    }

    public record Outcome(int error, String reason, List<Target> targets, long cost, long gold) {

        public boolean ok() {
            return error == 0;
        }

        static Outcome rejected(int error, String reason) {
            return new Outcome(error, reason, List.of(), 0, 0);
        }
    }

    public static Outcome repair(GameSession session, Cmd25RepairEquipRequest req) {
        if (!session.hasCharacter()) {
            return Outcome.rejected(Cmd25ReplyBody.ERROR_BUSY, "还没选角色");
        }
        if (session.scripts() == null) {
            return Outcome.rejected(Cmd25ReplyBody.ERROR_INVALID, "没有物品脚本，算不出修理费");
        }

        List<Target> targets = new ArrayList<>();
        if (req.isRepairAll()) {
            collectAll(session, targets);
        } else {
            String why = collectOne(session, req.itemSpace(), req.slot(), targets);
            if (why != null) {
                return Outcome.rejected(Cmd25ReplyBody.ERROR_INVALID, why);
            }
        }

        long total = 0;
        for (Target t : targets) {
            total += t.cost();
        }

        InventoryService inventory = session.inventory();
        long money = inventory.money();
        if (money < total) {
            return Outcome.rejected(Cmd25ReplyBody.ERROR_NOT_ENOUGH_MONEY,
                    "要 " + total + " 金币，身上只有 " + money);
        }
        inventory.setMoney(money - total);

        for (Target t : targets) {
            ItemArray array = inventory.containerOf(t.itemSpace());
            if (array == null) {
                continue;
            }
            array.set(t.slot(), array.get(t.slot()).withEndurance(t.after()));
            if (t.itemSpace() == ItemListType.LIST_CARGO) {
                inventory.markCargoDirty();
            } else {
                inventory.markItemsDirty();
            }
        }

        return new Outcome(0, "", List.copyOf(targets), total, inventory.money());
    }

    private static String collectOne(GameSession session, int itemSpace, int slot,
                                     List<Target> out) {
        if (itemSpace != ItemListType.LIST_MAIN && itemSpace != ItemListType.LIST_CARGO
                && itemSpace != ItemListType.LIST_EQUIPPED) {
            return "容器 " + itemSpace + " 上不能修东西";
        }
        ItemArray array = session.inventory().containerOf(itemSpace);
        if (array == null || slot < 0 || slot >= array.slots()) {
            return "容器 " + itemSpace + " 没有第 " + slot + " 格";
        }
        InvenItem item = array.get(slot);
        ItemScript script = repairableScript(session, item);
        if (script == null) {
            return "容器 " + itemSpace + " 第 " + slot + " 格上没有能修的装备";
        }
        out.add(targetOf(session, item, script, itemSpace, slot));
        return null;
    }

    private static void collectAll(GameSession session, List<Target> out) {
        ItemArray worn = session.inventory().containerOf(ItemListType.LIST_EQUIPPED);
        if (worn != null) {
            int last = Math.min(REPAIR_ALL_EQUIP_LAST, worn.slots() - 1);
            for (int slot = REPAIR_ALL_EQUIP_FIRST; slot <= last; slot++) {
                collectIfRepairable(session, worn.get(slot), ItemListType.LIST_EQUIPPED, slot, out);
            }
        }
        ItemArray bag = session.inventory().containerOf(ItemListType.LIST_MAIN);
        if (bag != null) {
            int last = Math.min(REPAIR_ALL_BAG_LAST, bag.slots() - 1);
            for (int slot = REPAIR_ALL_BAG_FIRST; slot <= last; slot++) {
                collectIfRepairable(session, bag.get(slot), ItemListType.LIST_MAIN, slot, out);
            }
        }
    }

    private static void collectIfRepairable(GameSession session, InvenItem item, int itemSpace,
                                            int slot, List<Target> out) {
        ItemScript script = repairableScript(session, item);
        if (script == null) {
            return;
        }
        if (item.endurance() >= script.durability()) {
            return;
        }
        out.add(targetOf(session, item, script, itemSpace, slot));
    }

    private static ItemScript repairableScript(GameSession session, InvenItem item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        ItemScript script = session.scripts().item((int) item.itemId()).orElse(null);
        if (script == null || script.durability() <= 0) {
            return null;
        }
        return script;
    }

    private static Target targetOf(GameSession session, InvenItem item, ItemScript script,
                                   int itemSpace, int slot) {
        double rate = session.scripts().upgradeTable() == null
                ? 1.0 : session.scripts().upgradeTable().repairCostRate(item.upgrade());
        long cost = RepairCost.of(script, item.endurance(), rate);
        return new Target(itemSpace, slot, (int) item.itemId(), item.endurance(),
                script.durability(), cost);
    }
}
