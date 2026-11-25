package com.dylan.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntPredicate;

import com.dylan.protocol.body.Cmd28DisjointItemRequest;
import com.dylan.protocol.body.Cmd28ReplyBody;
import com.dylan.protocol.body.ItemListType;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.DisjointTable;
import com.dylan.pvf.script.ItemScript;
import com.dylan.store.blob.InvenItem;
import com.dylan.store.blob.ItemArray;

public final class DisjointService {

    private final GameSession session;

    private final Random random;

    public DisjointService(GameSession session) {
        this(session, new Random());
    }

    DisjointService(GameSession session, Random random) {
        this.session = session;
        this.random = random;
    }

    public record Outcome(int error, int slot, int itemSpace, long itemId,
                          List<Cmd28ReplyBody.Product> products, String reason) {

        public static Outcome rejected(int error, String reason) {
            return new Outcome(error, 0, 0, 0, List.of(), reason);
        }

        public boolean ok() {
            return error == 0;
        }
    }

    public Outcome disjoint(Cmd28DisjointItemRequest request) {
        InventoryService inventory = session.inventory();

        ItemArray container = inventory.containerOf(request.itemSpace());
        if (container == null) {
            return Outcome.rejected(Cmd28ReplyBody.ERROR_NO_SUCH_ITEM,
                    "容器 " + request.itemSpace() + " 在这一版没有可读的格子数组");
        }
        int capacity = inventory.capacityOf(request.itemSpace());
        if (request.slot() < 0 || request.slot() >= capacity
                || request.slot() >= container.slots()) {
            return Outcome.rejected(Cmd28ReplyBody.ERROR_NO_SUCH_ITEM,
                    "容器 " + request.itemSpace() + " 的槽 " + request.slot()
                            + " 越界（容量 " + capacity + "）");
        }
        InvenItem target = container.get(request.slot());

        GameScripts scripts = session.scripts();
        ItemScript script = target.isEmpty() || scripts == null
                ? null : scripts.item((int) target.itemId()).orElse(null);

        int error = checkItemState(target, script);
        if (error != 0) {
            return Outcome.rejected(error, "check_error " + error
                    + "：容器 " + request.itemSpace() + " 的槽 " + request.slot() + " 上是 "
                    + (target.isEmpty() ? "空的" : target.itemId()
                            + "（ITEM_TYPE " + target.itemType()
                            + (script == null ? "，不在物品表里"
                                    : "，" + script.name()
                                            + "，attachType " + script.attachType()) + "）"));
        }

        DisjointTable table = scripts.disjointTable();
        if (table == null) {
            return Outcome.rejected(Cmd28ReplyBody.ERROR_NO_SUCH_ITEM,
                    DisjointTable.PATH + " 没读出来");
        }
        ItemArray bag = inventory.containerOf(ItemListType.LIST_MAIN);
        if (bag == null) {
            return Outcome.rejected(Cmd28ReplyBody.ERROR_NO_SUCH_ITEM, "主背包还没装载");
        }
        if (!roomForProducts(bag, script, table)) {
            return Outcome.rejected(Cmd28ReplyBody.ERROR_BAG_FULL, "背包放不下分解产物");
        }

        long basePrice = (long) (script.shopBasePrice() * 1.1);
        long sellPrice = scripts.priceTable().sellPrice(basePrice, false, 0, 0);
        var results = table.resultOf(sellPrice, script.grade(), script.rarity(), random);

        container.set(request.slot(), InvenItem.EMPTY);

        List<Cmd28ReplyBody.Product> products = new ArrayList<>(results.size());
        for (var p : results) {
            ItemScript productScript = scripts.item(p.itemId()).orElse(null);
            if (productScript == null) {
                continue;
            }
            int slot = InventoryService.grant(bag, productScript, p.count());
            if (slot < 0) {
                continue;
            }
            bag.get(slot).stackCount();
            products.add(new Cmd28ReplyBody.Product(slot, p.itemId(), p.count()));
        }
        inventory.markItemsDirty();

        return new Outcome(0, request.slot(), request.itemSpace(), target.itemId(),
                products, "");
    }

    static int checkItemState(InvenItem target, ItemScript script) {
        if (target.itemType() == InventoryService.CREATURE_ITEM_TYPE) {
            return Cmd28ReplyBody.ERROR_NO_SUCH_ITEM;
        }
        if (!target.equipable() || target.isEmpty() || target.itemId() == 0) {
            return Cmd28ReplyBody.ERROR_NO_SUCH_ITEM;
        }
        if (script == null) {
            return Cmd28ReplyBody.ERROR_NO_SUCH_ITEM;
        }
        if (script.attachType() == ItemScript.ATTACH_TRADE_DELETE) {
            return Cmd28ReplyBody.ERROR_NO_SUCH_ITEM;
        }
        return 0;
    }

    private boolean roomForProducts(ItemArray bag, ItemScript script, DisjointTable table) {
        GameScripts scripts = session.scripts();
        List<Integer> predicted = table.predictResult(script.rarity());
        Map<BagLayout.Section, Integer> need = new HashMap<>();
        int stacked = 0;
        for (int itemId : predicted) {
            ItemScript product = scripts == null ? null : scripts.item(itemId).orElse(null);
            if (hasStackable(bag, itemId, product)) {
                stacked++;
                continue;
            }
            need.merge(BagLayout.of(product), 1, Integer::sum);
        }
        IntPredicate occupied = s -> s >= bag.slots() || !bag.get(s).isEmpty();
        if (BagLayout.fits(need, occupied)) {
            return true;
        }
        return false;
    }

    private static boolean hasStackable(ItemArray bag, int itemId, ItemScript product) {
        for (BagLayout.Section section : BagLayout.stackSections(product)) {
            for (int slot = section.firstSlot();
                 slot <= section.lastSlot() && slot < bag.slots(); slot++) {
                InvenItem cur = bag.get(slot);
                if (!cur.isEmpty() && cur.itemId() == itemId) {
                    return true;
                }
            }
        }
        return false;
    }
}
