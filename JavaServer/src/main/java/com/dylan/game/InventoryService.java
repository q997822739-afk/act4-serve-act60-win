package com.dylan.game;

import com.dylan.game.drop.EquipGradeGenerator;
import com.dylan.protocol.body.Cmd18ReplyBody;
import com.dylan.protocol.body.Cmd19MoveItemSpaceRequest;
import com.dylan.protocol.body.Cmd19ReplyBody;
import com.dylan.protocol.body.Cmd47ReplyBody;
import com.dylan.protocol.body.EquipmentSlot;
import com.dylan.protocol.body.ItemListType;
import com.dylan.protocol.body.Noti13ItemListBody;
import com.dylan.protocol.body.Noti14UpdateItemListBody;
import com.dylan.protocol.body.Noti2CharacterEntry;
import com.dylan.protocol.body.Noti2PartyEntry;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.ItemScript;
import com.dylan.store.CharacterInventory;
import com.dylan.store.GameStore;
import com.dylan.store.blob.InvenItem;
import com.dylan.store.blob.ItemArray;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InventoryService {
    private final GameSession session;

    private void countHack(int site, int parameter2, String detail) {
        if (session != null) {
            session.hack().count(HackType.MEMORY_MODIFIED, site, parameter2, detail);
        }
    }

    private int characNo;

    private CharacterInventory items;

    private com.dylan.store.CharacterCargo cargo;

    private final List<com.dylan.store.AvatarItemRow> avatarItems = new ArrayList<>();

    private final List<Creature> creatureItems = new ArrayList<>();

    private boolean itemsDirty;
    private boolean cargoDirty;

    private final Map<Integer, java.util.SortedSet<Integer>> emptiedSlots = new LinkedHashMap<>();

    public InventoryService(GameSession session) {
        this.session = session;
    }

    private InventoryService() {
        this.session = null;
    }

    public static List<Noti13ItemListBody> itemLists(CharacterInventory inv, long winPoint,
                                                     int cargoCapacity, GameScripts scripts) {
        if (inv == null) {
            return List.of(
                    Noti13ItemListBody.empty(ItemListType.LIST_MAIN),
                    Noti13ItemListBody.empty(ItemListType.LIST_AVATAR),
                    Noti13ItemListBody.cargo(cargoCapacity, List.of()),
                    Noti13ItemListBody.empty(ItemListType.LIST_CREATURE));
        }
        return List.of(
                Noti13ItemListBody.of(ItemListType.LIST_MAIN, mainBag(inv, winPoint, scripts)),
                Noti13ItemListBody.empty(ItemListType.LIST_AVATAR),
                Noti13ItemListBody.cargo(cargoCapacity, List.of()),
                Noti13ItemListBody.of(ItemListType.LIST_CREATURE,
                        plainItems(inv.creature(), 0, scripts)));
    }

    public static List<Noti13ItemListBody.Item> mainBag(CharacterInventory inv, long winPoint,
                                                        GameScripts scripts) {
        List<Noti13ItemListBody.Item> out = new ArrayList<>();
        out.add(currency(BagLayout.SLOT_GOLD, inv.money()));
        long coin = inv.coin() + inv.payCoin() + inv.eventCoin();
        // A4 官方点券格（slot 1）为 0 时整条不发：新号会话 347 次 NOTI 13/0 的槽位集合只有 {0,2,105}，
        // 有点券时才出现 1（sweep/replay 里 slot 1 = 10 / 100 / 10000000）。A5 原来无条件发，
        // 每次进世界都比官方多 18 字节（57 → 75），客户端读到 3 条就停。
        if (!com.dylan.net.ServerConfig.A4 || coin > 0) {
            out.add(currency(BagLayout.SLOT_COIN, coin));
        }
        out.add(currency(BagLayout.SLOT_WIN_POINT, winPoint));
        out.addAll(plainItems(inv.inventory(), BagLayout.FIRST_ITEM_SLOT, scripts));
        return out;
    }

    private static Noti13ItemListBody.Item currency(int slot, long amount) {
        long clamped = Math.max(0, Math.min(amount, 0xFFFF_FFFFL));
        return new Noti13ItemListBody.Item(slot, slot, (int) clamped, 0, 0, 0, 0, 0, 0);
    }

    public static List<Noti13ItemListBody.Item> plainItems(ItemArray array, int firstSlot,
                                                           GameScripts scripts) {
        List<Noti13ItemListBody.Item> out = new ArrayList<>();
        for (int slot = firstSlot; slot < array.slots(); slot++) {
            InvenItem item = array.get(slot);
            if (item.isEmpty()) {
                continue;
            }
            if (slot > 0xFFFF || item.itemId() > 0xFFFF_FFFFL) {
                continue;
            }
            out.add(toWire(slot, item));
        }
        return out;
    }

    public static Noti13ItemListBody.Item toWire(int slot, InvenItem item) {
        return new Noti13ItemListBody.Item(
                slot,
                (int) item.itemId(),
                (int) item.addInfo(),
                item.itemAttr(),
                item.endurance(),
                sealed(item) ? Noti13ItemListBody.SEAL_SEALED : Noti13ItemListBody.SEAL_UNSEALED,
                (int) item.enchant(),
                item.amplify().visibleAbilityType() & 0xFF,
                item.amplify().visibleAbilityValue());
    }

    /**
     * NOTI 13 封印字节。ACT5：attachType 非 0 就算封印。A4 官方只对 [sealing] / [sealing trade]
     * （attachType 3 / 4）发 1：官方全部实抓 512 种物品，attach 3 的 15 种全是 1，attach 1（[trade]，
     * 4183 每日券、时装等 473 种）和 attach 0 全是 0。
     */
    private static boolean sealed(InvenItem item) {
        if (!com.dylan.net.ServerConfig.A4) {
            return item.attachType() != 0;
        }
        return item.attachType() == com.dylan.pvf.script.ItemScript.ATTACH_SEALING
                || item.attachType() == com.dylan.pvf.script.ItemScript.ATTACH_SEALING_TRADE;
    }

    public static InvenItem newItem(ItemScript script, int count) {
        if (isCurrencyPlaceholder(script.id())) {
            throw new IllegalArgumentException("物品 " + script.id() + "（" + script.path()
                    + "）是虚拟货币格的占位脚本，不是实物，发不出来。"
                    + "金币走 setMoney、复活币走 coin/pay_coin/event_coin 三列、"
                    + "决斗胜点走 pvp_result.win_point");
        }
        int stack = Math.max(1, count);
        int type = itemTypeOf(script);
        long addInfo;
        if (script.isStackableItem()) {
            addInfo = stack;
        } else if (type == EQUIPMENT_ITEM_TYPE) {
            addInfo = EquipGradeGenerator.hasGrade(script) ? newRandomCode() : 0;
        } else {
            addInfo = 0;
        }
        return InvenItem.of(script.id(), type, addInfo)
                .withEndurance(script.durability())
                .withAttachType(script.attachType());
    }

    static long newRandomCode() {
        return EquipGradeGenerator.generate(java.util.concurrent.ThreadLocalRandom.current());
    }

    public static int itemTypeOf(ItemScript script) {
        if (script.isAvatar()) {
            return AVATAR_ITEM_TYPE;
        }
        if (script.isStackableItem()) {
            return script.stackableType() == ItemScript.STACKABLE_CREATURE
                    || script.stackableType() == ItemScript.STACKABLE_FEED
                    ? CREATURE_STACKABLE_ITEM_TYPE : DEFAULT_ITEM_TYPE;
        }
        if (script.equipmentSlot() == EquipmentSlot.CREATURE) {
            return CREATURE_ITEM_TYPE;
        }
        if (script.equipmentSlot() >= EquipmentSlot.FIRST_ARTIFACT
                && script.equipmentSlot() < EquipmentSlot.COUNT) {
            return CREATURE_ARTIFACT_ITEM_TYPE;
        }
        return EQUIPMENT_ITEM_TYPE;
    }

    static final int EQUIPMENT_ITEM_TYPE = 1;

    static final int CREATURE_ITEM_TYPE = 5;

    private static final int CREATURE_ARTIFACT_ITEM_TYPE = 6;

    private static final int CREATURE_STACKABLE_ITEM_TYPE = 7;

    private static final int AVATAR_ITEM_TYPE = 8;

    private static final int DEFAULT_ITEM_TYPE = 0;

    public static int grant(ItemArray array, ItemScript script, int count) {
        if (script == null) {
            throw new IllegalArgumentException("要放的物品没有脚本，客户端也认不出它");
        }
        int stack = Math.max(1, count);
        if (script.isStackableItem()) {
            int stacked = stackInto(array, script, script.id(), stack);
            if (stacked >= 0) {
                return stacked;
            }
        }
        if (stack > stackLimitOf(script)) {
            return -1;
        }
        int slot = BagLayout.allocate(script, s -> s >= array.slots() || !array.get(s).isEmpty());
        if (slot < 0) {
            return -1;
        }
        array.set(slot, newItem(script, stack));
        return slot;
    }

    private static int stackInto(ItemArray array, ItemScript script, long itemId, long incoming) {
        long limit = stackLimitOf(script);
        for (BagLayout.Section section : BagLayout.stackSections(script)) {
            for (int slot = section.firstSlot();
                 slot <= section.lastSlot() && slot < array.slots(); slot++) {
                InvenItem cur = array.get(slot);
                if (!cur.isEmpty() && cur.itemId() == itemId
                        && cur.addInfo() + incoming <= limit) {
                    array.set(slot, cur.withAddInfo(cur.addInfo() + incoming));
                    return slot;
                }
            }
        }
        return -1;
    }

    public static com.dylan.store.CreatureItemRow grantCreature(
            GameStore store, int characNo, CharacterInventory inv, ItemScript script) {
        if (script == null) {
            throw new IllegalArgumentException("要放的宠物没有脚本，客户端也认不出它");
        }
        ItemArray bag = inv.creature();
        int slot = CreatureBagLayout.firstFree(bag, CREATURE_ITEM_TYPE);
        if (slot < 0) {
            return null;
        }
        com.dylan.store.CreatureItemRow row = store != null
                ? store.creature().insertCreatureItem(characNo, slot, script.id(),
                        script.creatureType())
                : com.dylan.store.CreatureItemRow.ofNew(characNo, slot, script.id(),
                        script.creatureType());
        bag.set(slot, InvenItem.of(script.id(), itemTypeOf(script), row.uiId()));
        return row;
    }

    public int grantCreature(ItemScript script) {
        var row = grantCreature(session == null ? null : session.store(), characNo, items, script);
        if (row == null) {
            return -1;
        }
        addCreature(CreatureService.load(session, row));
        markItemsDirty();
        return row.slot();
    }

    public record Landing(int itemSpace, int slot,
                          com.dylan.store.CreatureItemRow creatureRow) {
        public Landing(int itemSpace, int slot) {
            this(itemSpace, slot, null);
        }

        public boolean ok() {
            return slot >= 0;
        }

        public static Landing full() {
            return new Landing(ItemListType.LIST_MAIN, -1);
        }
    }

    public static Landing addItem(GameStore store, int characNo, CharacterInventory inv,
                                  ItemScript script, int count) {
        if (script == null) {
            throw new IllegalArgumentException("要发的物品没有脚本，客户端也认不出它");
        }
        if (inv == null) {
            return Landing.full();
        }
        int type = itemTypeOf(script);
        if (!isCreatureItemType(type)) {
            return new Landing(ItemListType.LIST_MAIN, grant(inv.inventory(), script, count));
        }
        if (type == CREATURE_ITEM_TYPE) {
            var row = grantCreature(store, characNo, inv, script);
            return new Landing(ItemListType.LIST_CREATURE, row == null ? -1 : row.slot(), row);
        }
        int slot = switch (type) {
            case CREATURE_ARTIFACT_ITEM_TYPE -> insertIntoCreatureBag(inv.creature(), script, 1,
                    false);
            default -> insertIntoCreatureBag(inv.creature(), script, Math.max(1, count), true);
        };
        return new Landing(ItemListType.LIST_CREATURE, slot);
    }

    public static Landing addItem(GameSession session, GameStore store, int characNo,
                                  CharacterInventory inv, ItemScript script, int count) {
        Landing landing = addItem(store, characNo, inv, script, count);
        if (landing.creatureRow() != null && session != null && session.inventory() != null
                && session.inventory().inventory() == inv) {
            session.inventory().addCreature(CreatureService.load(session, landing.creatureRow()));
        }
        if (landing.ok() && session != null && inv != null) {
            ItemArray landed = containerIn(inv, landing.itemSpace());
            if (landed != null && landing.slot() >= 0 && landing.slot() < landed.slots()) {
                ItemHistoryRecorder.record(session, landing.itemSpace(), landing.slot(),
                        landed.get(landing.slot()), count);
            }
        }
        return landing;
    }

    private static ItemArray containerIn(CharacterInventory inv, int itemSpace) {
        return switch (itemSpace) {
            case ItemListType.LIST_MAIN -> inv.inventory();
            case ItemListType.LIST_EQUIPPED -> inv.equipslot();
            case ItemListType.LIST_CREATURE -> inv.creature();
            default -> null;
        };
    }

    public static Landing tryAddItem(CharacterInventory inv, ItemScript script, int count) {
        if (script == null) {
            throw new IllegalArgumentException("要试放的物品没有脚本，客户端也认不出它");
        }
        if (inv == null) {
            return Landing.full();
        }
        int type = itemTypeOf(script);
        if (!isCreatureItemType(type)) {
            ItemArray main = inv.inventory();
            if (main == null) {
                return Landing.full();
            }
            ItemArray copy = ItemArray.decode(main.encode(), main.slots());
            return new Landing(ItemListType.LIST_MAIN, grant(copy, script, count));
        }
        int want = type == CREATURE_STACKABLE_ITEM_TYPE ? Math.max(1, count) : 1;
        return new Landing(ItemListType.LIST_CREATURE,
                tryCreatureSlot(inv.creature(), script, want));
    }

    public Landing addItem(ItemScript script, int count) {
        Landing landing = addItem(session == null ? null : session.store(), characNo, items,
                script, count);
        if (landing.creatureRow() != null) {
            addCreature(CreatureService.load(session, landing.creatureRow()));
        }
        if (landing.ok()) {
            markItemsDirty();
            ItemArray landed = containerOf(landing.itemSpace());
            if (landed != null && landing.slot() < landed.slots()) {
                ItemHistoryRecorder.record(session, landing.itemSpace(), landing.slot(),
                        landed.get(landing.slot()), count);
            }
        }
        return landing;
    }

    public static boolean isCreatureItemType(int itemType) {
        return itemType == CREATURE_ITEM_TYPE
                || itemType == CREATURE_ARTIFACT_ITEM_TYPE
                || itemType == CREATURE_STACKABLE_ITEM_TYPE;
    }

    static int insertIntoCreatureBag(ItemArray bag, ItemScript script, int count,
                                     boolean stack) {
        int itemType = itemTypeOf(script);
        if (stack && script.isStackableItem()) {
            long limit = stackLimitOf(script);
            int exist = CreatureBagLayout.firstOf(bag, itemType, script.id());
            if (exist >= 0) {
                InvenItem cur = bag.get(exist);
                if (cur.addInfo() + count > limit) {
                    return -1;
                }
                bag.set(exist, cur.withAddInfo(cur.addInfo() + count));
                return exist;
            }
        }
        int slot = CreatureBagLayout.firstFree(bag, itemType);
        if (slot < 0) {
            return -1;
        }
        bag.set(slot, newItem(script, count));
        return slot;
    }

    public static int tryCreatureSlot(ItemArray bag, ItemScript script, int count) {
        if (bag == null || script == null) {
            return -1;
        }
        ItemArray copy = ItemArray.decode(bag.encode(), bag.slots());
        return insertIntoCreatureBag(copy, script, Math.max(1, count),
                itemTypeOf(script) == CREATURE_STACKABLE_ITEM_TYPE);
    }

    static int stackLimitOf(ItemScript script) {
        return script.stackLimit() > 0 ? script.stackLimit() : NO_STACK_LIMIT;
    }

    static final int NO_STACK_LIMIT = Integer.MAX_VALUE;

    public static boolean isCurrencyPlaceholder(long itemId) {
        return itemId >= BagLayout.SLOT_GOLD && itemId <= BagLayout.SLOT_WIN_POINT;
    }

    public static final int MATERIAL_MONEY = 0;

    public static final int MATERIAL_WIN_POINT = 2;

    public static boolean isCurrencyMaterial(int itemId) {
        return itemId == MATERIAL_MONEY || itemId == MATERIAL_WIN_POINT;
    }

    public long countMaterial(int itemId) {
        if (itemId == MATERIAL_MONEY) {
            return money();
        }
        if (itemId == MATERIAL_WIN_POINT) {
            return session == null ? 0 : session.winPoint();
        }
        ItemArray bag = containerOf(ItemListType.LIST_MAIN);
        return bag == null ? 0 : countOf(bag, itemId);
    }

    public List<Taken> consumeMaterial(ItemArray bag, int itemId, int count) {
        if (itemId == MATERIAL_MONEY) {
            if (money() < count) {
                return List.of();
            }
            setMoney(money() - count);
            return List.of(new Taken(BagLayout.SLOT_GOLD, count));
        }
        if (itemId == MATERIAL_WIN_POINT) {
            if (session == null || session.winPoint() < count) {
                return List.of();
            }
            session.winPoint(session.winPoint() - count);
            GameStore store = session.store();
            if (store != null && session.characNo() > 0
                    && !store.pvp().setWinPoint(session.characNo(), session.winPoint())) {
                throw new IllegalStateException("charac_no=" + session.characNo()
                        + " 在 pvp_result 里没有行，胜点扣不下去（建角色流程漏了 insertPvp）");
            }
            return List.of(new Taken(BagLayout.SLOT_WIN_POINT, count));
        }
        return consume(bag, itemId, count);
    }

    public static long countOf(ItemArray array, int itemId) {
        long total = 0;
        for (int slot = BagLayout.FIRST_ITEM_SLOT; slot < array.slots(); slot++) {
            InvenItem item = array.get(slot);
            if (!item.isEmpty() && item.itemId() == itemId) {
                total += item.stackCount();
            }
        }
        return total;
    }

    public static List<Taken> consume(ItemArray array, int itemId, int count) {
        List<Taken> taken = new java.util.ArrayList<>();
        int remaining = count;
        for (int slot = BagLayout.FIRST_ITEM_SLOT; slot < array.slots() && remaining > 0; slot++) {
            InvenItem item = array.get(slot);
            if (item.isEmpty() || item.itemId() != itemId) {
                continue;
            }
            long have = item.stackCount();
            int take = (int) Math.min(have, remaining);
            remaining -= take;
            if (take >= have) {
                array.set(slot, InvenItem.EMPTY);
            } else {
                array.set(slot, item.withAddInfo(have - take));
            }
            taken.add(new Taken(slot, take));
        }
        return taken;
    }

    public record Taken(int slot, int count) {}

    public static boolean canFitAll(ItemArray array, List<Stack> handedIn, List<Stack> incoming,
                                    java.util.function.IntFunction<ItemScript> scripts) {
        if (incoming.isEmpty()) {
            return true;
        }
        ItemArray copy = ItemArray.decode(array.encode(), array.slots());
        for (Stack stack : handedIn) {
            if (isCurrencyMaterial(stack.itemId())) {
                continue;
            }
            consume(copy, stack.itemId(), stack.count());
        }
        for (Stack stack : incoming) {
            if (isCurrencyMaterial(stack.itemId())) {
                continue;
            }
            ItemScript script = scripts.apply(stack.itemId());
            if (script == null) {
                continue;
            }
            if (grant(copy, script, stack.count()) < 0) {
                return false;
            }
        }
        return true;
    }

    public record Stack(int itemId, int count) {}

    public void load(int characNo) {
        this.characNo = characNo;
        this.itemsDirty = false;
        this.cargoDirty = false;
        GameStore store = session == null ? null : session.store();
        avatarItems.clear();
        creatureItems.clear();
        if (store == null) {
            items = null;
            cargo = null;
            return;
        }
        items = store.inventory().loadInventory(characNo).orElse(null);
        avatarItems.addAll(store.avatar().loadAvatarItems(characNo));
        for (var row : store.creature().loadCreatureItems(characNo)) {
            creatureItems.add(CreatureService.load(session, row));
        }
        applyWornAvatars();
        equipWornCreature();
        cargo = store.inventory().loadCargo(characNo)
                .orElseGet(() -> com.dylan.store.CharacterCargo.empty(characNo));
    }

    void attach(int characNo, CharacterInventory items,
                List<com.dylan.store.AvatarItemRow> avatarRows) {
        attach(characNo, items, avatarRows, List.of());
    }

    void attach(int characNo, CharacterInventory items,
                List<com.dylan.store.AvatarItemRow> avatarRows,
                List<com.dylan.store.CreatureItemRow> creatureRows) {
        this.characNo = characNo;
        this.items = items;
        this.avatarItems.clear();
        this.avatarItems.addAll(avatarRows);
        this.creatureItems.clear();
        for (var row : creatureRows) {
            this.creatureItems.add(CreatureService.load(session, row));
        }
        applyWornAvatars();
        equipWornCreature();
        relocateCreatureBag();
    }

    private void relocateCreatureBag() {
        if (items == null) {
            return;
        }
        int moved = CreatureBagLayout.relocate(items.creature());
        if (moved > 0) {
            markItemsDirty();
        }
    }

    private void equipWornCreature() {
        Creature worn = equippedCreature();
        if (worn != null) {
            CreatureService.equip(session, worn);
        }
    }

    public void clear() {
        characNo = 0;
        items = null;
        cargo = null;
        avatarItems.clear();
        creatureItems.clear();
        itemsDirty = false;
        cargoDirty = false;
    }

    public void save() {
        GameStore store = session == null ? null : session.store();
        if (store == null || characNo == 0) {
            return;
        }
        if (!inDeathTower()) {
            removeTowerItems("落库前（官方 SaveInventory 的 RemoveFPItem）");
        }
        CreatureService.saveAll(session);
        try {
            if (itemsDirty && items != null) {
                store.inventory().saveInventory(items);
                itemsDirty = false;
            }
            if (cargoDirty && cargo != null) {
                store.inventory().saveCargo(cargo);
                cargoDirty = false;
            }
        } catch (RuntimeException e) {
            if (session != null) {
                session.fail("保存角色 " + characNo + " 的背包失败", e);
            }
        }
    }

    public int removeTowerItems(String why) {
        if (items == null) {
            return 0;
        }
        int removed = sweepTowerItems(items.equipslot(), 0)
                + sweepTowerItems(items.inventory(), BagLayout.FIRST_ITEM_SLOT);
        if (removed > 0) {
            markItemsDirty();
        }
        return removed;
    }

    private boolean inDeathTower() {
        return session != null && session.deathTower() != null;
    }

    private static int sweepTowerItems(ItemArray array, int firstSlot) {
        int removed = 0;
        for (int slot = firstSlot; slot < array.slots(); slot++) {
            InvenItem item = array.get(slot);
            if (item.isEmpty()) {
                continue;
            }
            long id = item.itemId();
            if (id >= TOWER_ITEM_FIRST && id <= TOWER_ITEM_LAST) {
                array.set(slot, InvenItem.EMPTY);
                removed++;
            }
        }
        return removed;
    }

    public static final long TOWER_ITEM_FIRST = 6500;

    public static final long TOWER_ITEM_LAST = 6999;

    public void markItemsDirty() {
        itemsDirty = true;
        if (session != null) {
            CharacterService.refreshSnapshots(session, "背包 / 装备栏内容变了");
        }
    }

    public void markCargoDirty() {
        cargoDirty = true;
    }

    public int cargoCapacity() {
        return cargo == null ? 0 : cargo.usableSlots();
    }

    public boolean upgradeCargo(int newCapacity) {
        if (cargo == null) {
            return false;
        }
        int current = cargo.usableSlots();
        if (current == 0 || newCapacity <= current
                || newCapacity > com.dylan.store.blob.ItemArray.CARGO_SLOTS) {
            return false;
        }
        cargo = new com.dylan.store.CharacterCargo(cargo.characNo(), newCapacity,
                cargo.items());
        markCargoDirty();
        save();
        return true;
    }

    public CharacterInventory inventory() {
        return items;
    }

    public com.dylan.store.CharacterCargo cargo() {
        return cargo;
    }

    public long money() {
        return items == null ? 0 : items.money();
    }

    public long coinTotal() {
        return items == null ? 0 : items.coin() + items.payCoin() + items.eventCoin();
    }

    public void setMoney(long value) {
        if (items == null) {
            return;
        }
        long before = items.money();
        long clamped = Math.clamp(value, 0, 0xFFFF_FFFFL);
        items = new CharacterInventory(items.characNo(), clamped, items.coin(), items.payCoin(),
                items.eventCoin(), items.equipslot(), items.inventory(), items.creature());
        itemsDirty = true;
        ItemHistoryRecorder.recordMoney(session, clamped - before, clamped);
    }

    public void setCoins(long coin, long payCoin, long eventCoin) {
        if (items == null) {
            return;
        }
        long beforeCoin = items.coin();
        long beforePay = items.payCoin();
        long beforeEvent = items.eventCoin();
        long afterCoin = Math.clamp(coin, 0, 0xFFFF_FFFFL);
        long afterPay = Math.clamp(payCoin, 0, 0xFFFF_FFFFL);
        long afterEvent = Math.clamp(eventCoin, 0, 0xFFFF_FFFFL);
        items = new CharacterInventory(items.characNo(), items.money(),
                afterCoin, afterPay, afterEvent,
                items.equipslot(), items.inventory(), items.creature());
        itemsDirty = true;
        ItemHistoryRecorder.recordCoin(session, ItemHistoryRecorder.COIN_COL_COIN,
                afterCoin - beforeCoin, afterCoin);
        ItemHistoryRecorder.recordCoin(session, ItemHistoryRecorder.COIN_COL_PAY,
                afterPay - beforePay, afterPay);
        ItemHistoryRecorder.recordCoin(session, ItemHistoryRecorder.COIN_COL_EVENT,
                afterEvent - beforeEvent, afterEvent);
    }

    public String useCoin(String why) {
        if (items == null) {
            return null;
        }
        String which;
        if (items.coin() > 0) {
            setCoins(items.coin() - 1, items.payCoin(), items.eventCoin());
            which = "coin";
        } else if (items.eventCoin() > 0) {
            setCoins(items.coin(), items.payCoin(), items.eventCoin() - 1);
            which = "event_coin";
        } else if (items.payCoin() > 0) {
            setCoins(items.coin(), items.payCoin() - 1, items.eventCoin());
            which = "pay_coin";
        } else {
            setCoins(0, 0, 0);
            return null;
        }
        save();
        return which;
    }

    public ItemArray containerOf(int listType) {
        if (items == null) {
            return null;
        }
        return switch (listType) {
            case ItemListType.LIST_MAIN -> items.inventory();
            case ItemListType.LIST_EQUIPPED -> items.equipslot();
            case ItemListType.LIST_CREATURE -> items.creature();
            case ItemListType.LIST_CARGO -> cargo == null ? null : cargo.items();
            default -> null;
        };
    }

    public int capacityOf(int listType) {
        if (listType == ItemListType.LIST_AVATAR) {
            return items == null ? 0 : AVATAR_SLOTS;
        }
        ItemArray array = containerOf(listType);
        if (array == null) {
            return 0;
        }
        return switch (listType) {
            case ItemListType.LIST_MAIN -> Math.min(BagLayout.LAST_TAB_SLOT + 1, array.slots());
            case ItemListType.LIST_CARGO -> cargo.usableSlots();
            case ItemListType.LIST_CREATURE ->
                    Math.min(CreatureBagLayout.LAST_SLOT + 1, array.slots());
            default -> array.slots();
        };
    }

    public int itemIdAt(int listType, int slot) {
        if (listType == ItemListType.LIST_AVATAR) {
            var row = slot < 0 ? null : avatarRowInBag(slot);
            return row == null ? 0 : row.itemId();
        }
        ItemArray array = containerOf(listType);
        if (array == null || slot < 0 || slot >= array.slots()) {
            return 0;
        }
        return (int) array.get(slot).itemId();
    }

    public List<Noti13ItemListBody> allLists() {
        return allLists(session == null ? 0 : session.winPoint());
    }

    public List<Noti13ItemListBody> allLists(long winPoint) {
        if (items == null) {
            return itemLists(null, winPoint, cargo == null ? 0 : cargo.usableSlots(),
                    session == null ? null : session.scripts());
        }
        GameScripts scripts = session == null ? null : session.scripts();
        return List.of(
                Noti13ItemListBody.of(ItemListType.LIST_MAIN, mainBagOf(winPoint, scripts)),
                Noti13ItemListBody.of(ItemListType.LIST_AVATAR, avatarBagItems()),
                Noti13ItemListBody.cargo(cargo == null ? 0 : cargo.usableSlots(),
                        cargo == null ? List.of()
                                : plainItems(cargo.items(), 0, scripts)),
                Noti13ItemListBody.of(ItemListType.LIST_CREATURE,
                        plainItems(items.creature(), 0, scripts)));
    }

    public List<Noti13ItemListBody.Item> avatarBagItems() {
        var now = java.time.Instant.now();
        List<Noti13ItemListBody.Item> out = new ArrayList<>(avatarItems.size());
        for (var row : avatarItems) {
            if (row.slot() < AVATAR_BAG_SLOT_BASE) {
                continue;
            }
            out.add(avatarBagItem(row, now));
        }
        return out;
    }

    private static Noti13ItemListBody.Item avatarBagItem(
            com.dylan.store.AvatarItemRow row, java.time.Instant now) {
        return new Noti13ItemListBody.Item(row.slot() - AVATAR_BAG_SLOT_BASE, row.itemId(),
                (int) row.remainingSeconds(now),
                0,
                row.abilityNo(),
                // A4 官方装扮栏封印位恒 0（商城刚买的时装 NOTI 13/1、14 实抓）
                com.dylan.net.ServerConfig.A4
                        ? Noti13ItemListBody.SEAL_UNSEALED : Noti13ItemListBody.SEAL_SEALED,
                0, 0, 0);
    }

    public static final int AVATAR_BAG_SLOT_BASE = 10;

    private com.dylan.store.AvatarItemRow avatarRowInBag(int bagSlot) {
        return avatarRowAt(bagSlot + AVATAR_BAG_SLOT_BASE);
    }

    public int clearAvatarBag(String why) {
        GameStore store = session == null ? null : session.store();
        List<com.dylan.store.AvatarItemRow> doomed = new ArrayList<>();
        for (var row : avatarItems) {
            if (row.slot() >= AVATAR_BAG_SLOT_BASE) {
                doomed.add(row);
            }
        }
        if (doomed.isEmpty()) {
            return 0;
        }
        for (var row : doomed) {
            if (store != null && row.uiId() != com.dylan.store.AvatarItemRow.NOT_PERSISTED) {
                store.avatar().deleteAvatarItem(row.uiId());
            }
            avatarItems.remove(row);
        }
        return doomed.size();
    }

    public com.dylan.store.AvatarItemRow avatarBagRowAt(int bagSlot) {
        return avatarRowInBag(bagSlot);
    }

    private com.dylan.store.AvatarItemRow avatarRowWorn(int equipSlot) {
        return avatarRowAt(equipSlot);
    }

    private com.dylan.store.AvatarItemRow avatarRowAt(int slot) {
        for (var row : avatarItems) {
            if (row.slot() == slot) {
                return row;
            }
        }
        return null;
    }

    private void moveAvatarRow(com.dylan.store.AvatarItemRow row, int newSlot) {
        GameStore store = session == null ? null : session.store();
        if (store != null && row.uiId() != com.dylan.store.AvatarItemRow.NOT_PERSISTED
                && !store.avatar().moveAvatarItem(row.uiId(), newSlot)) {
            throw new com.dylan.store.StoreException(
                    "装扮栏第 " + row.uiId() + " 行挪到槽 " + newSlot + " 时没有影响任何行");
        }
        for (int i = 0; i < avatarItems.size(); i++) {
            if (avatarItems.get(i) == row) {
                avatarItems.set(i, row.withSlot(newSlot));
                return;
            }
        }
        throw new IllegalStateException("要挪的那一行不在会话的装扮栏里：ui_id " + row.uiId());
    }

    private void applyWornAvatars() {
        if (items == null) {
            return;
        }
        ItemArray worn = items.equipslot();
        for (int slot = 0; slot <= EquipmentSlot.SKIN_AVATAR && slot < worn.slots(); slot++) {
            worn.set(slot, InvenItem.EMPTY);
        }
        for (var row : avatarItems) {
            if (row.slot() >= AVATAR_BAG_SLOT_BASE) {
                continue;
            }
            if (row.slot() >= worn.slots()) {
                continue;
            }
            worn.set(row.slot(), avatarInvenItem(row));
        }
    }

    private static InvenItem avatarInvenItem(com.dylan.store.AvatarItemRow row) {
        return InvenItem.of(row.itemId(), ItemTypes.AVATAR, row.uiId())
                .withEndurance(row.abilityNo());
    }

    public static final int AVATAR_SLOTS = 105;

    public int firstFreeAvatarSlot() {
        var used = new java.util.HashSet<Integer>();
        for (var row : avatarItems) {
            used.add(row.slot());
        }
        for (int slot = 0; slot < AVATAR_SLOTS; slot++) {
            if (!used.contains(slot + AVATAR_BAG_SLOT_BASE)) {
                return slot;
            }
        }
        return -1;
    }

    public int freeAvatarSlots() {
        var used = new java.util.HashSet<Integer>();
        for (var row : avatarItems) {
            used.add(row.slot());
        }
        int free = 0;
        for (int slot = 0; slot < AVATAR_SLOTS; slot++) {
            if (!used.contains(slot + AVATAR_BAG_SLOT_BASE)) {
                free++;
            }
        }
        return free;
    }

    public int grantAvatarItem(int itemId, int days, int abilityNo, long commodityNo) {
        GameStore store = session == null ? null : session.store();
        if (store == null || characNo == 0) {
            return -1;
        }
        int slot = firstFreeAvatarSlot();
        if (slot < 0) {
            return -1;
        }
        var row = store.avatar().insertAvatarItem(characNo, slot + AVATAR_BAG_SLOT_BASE, itemId, days,
                abilityNo, commodityNo, AVATAR_OBTAIN_FROM_CASH_SHOP);
        avatarItems.add(row);
        return slot;
    }

    public static final int AVATAR_OBTAIN_FROM_CASH_SHOP = 0;

    public static final int AVATAR_OBTAIN_FROM_COMPOUND = 0;

    public static final int AVATAR_STAT_CONSUMED = 1;

    public void consumeAvatarRow(com.dylan.store.AvatarItemRow row) {
        GameStore store = session == null ? null : session.store();
        if (store != null && row.uiId() != com.dylan.store.AvatarItemRow.NOT_PERSISTED
                && !store.avatar().changeAvatarStat(row.uiId(), characNo, AVATAR_STAT_CONSUMED, row.slot())) {
            throw new com.dylan.store.StoreException(
                    "把时装 ui_id=" + row.uiId() + " 标记成已消耗时没有影响任何行");
        }
        for (int i = 0; i < avatarItems.size(); i++) {
            if (avatarItems.get(i) == row) {
                avatarItems.remove(i);
                return;
            }
        }
        throw new IllegalStateException("要消耗的那一行不在会话的装扮栏里：ui_id " + row.uiId());
    }

    public int grantCompoundAvatar(int itemId, java.time.Instant expireAt, int abilityNo,
                                   String ipgAgencyNo) {
        GameStore store = session == null ? null : session.store();
        if (store == null || characNo == 0) {
            return -1;
        }
        int slot = firstFreeAvatarSlot();
        if (slot < 0) {
            return -1;
        }
        var row = store.avatar().insertAvatarItemAt(characNo, slot + AVATAR_BAG_SLOT_BASE, itemId, expireAt,
                abilityNo, ipgAgencyNo, AVATAR_OBTAIN_FROM_COMPOUND);
        avatarItems.add(row);
        return slot;
    }

    public int receiveAvatarItem(com.dylan.store.AvatarItemRow row) {
        int slot = firstFreeAvatarSlot();
        if (slot < 0) {
            return -1;
        }
        int stored = slot + AVATAR_BAG_SLOT_BASE;
        GameStore store = session == null ? null : session.store();
        if (store != null && row.uiId() != com.dylan.store.AvatarItemRow.NOT_PERSISTED
                && !store.avatar().moveAvatarItem(row.uiId(), stored)) {
            throw new com.dylan.store.StoreException(
                    "邮件里的时装 ui_id=" + row.uiId() + " 挪到装扮栏槽 " + stored + " 时没有影响任何行");
        }
        avatarItems.add(row.withSlot(stored).withCharacNo(characNo));
        return slot;
    }

    private List<Noti13ItemListBody.Item> mainBagOf(long winPoint, GameScripts scripts) {
        List<Noti13ItemListBody.Item> out = new ArrayList<>();
        out.add(currency(BagLayout.SLOT_GOLD, money()));
        long coin = coinTotal();
        // 同 mainBag：A4 点券为 0 时不发 slot 1（官方 347 次新号 NOTI 13/0 没有这一条）。
        if (!com.dylan.net.ServerConfig.A4 || coin > 0) {
            out.add(currency(BagLayout.SLOT_COIN, coin));
        }
        out.add(currency(BagLayout.SLOT_WIN_POINT, winPoint));
        out.addAll(plainItems(items.inventory(), BagLayout.FIRST_ITEM_SLOT, scripts));
        return out;
    }

    public void sendItemList(int listType, String why) {
        if (session == null) {
            return;
        }
        GameScripts scripts = session.scripts();
        Noti13ItemListBody body = switch (listType) {
            case ItemListType.LIST_MAIN -> items == null
                    ? Noti13ItemListBody.empty(ItemListType.LIST_MAIN)
                    : Noti13ItemListBody.of(ItemListType.LIST_MAIN,
                            mainBagOf(session == null ? 0 : session.winPoint(), scripts));
            case ItemListType.LIST_CARGO -> Noti13ItemListBody.cargo(
                    cargo == null ? 0 : cargo.usableSlots(),
                    cargo == null ? List.of() : plainItems(cargo.items(), 0, scripts));
            case ItemListType.LIST_CREATURE -> items == null
                    ? Noti13ItemListBody.empty(ItemListType.LIST_CREATURE)
                    : Noti13ItemListBody.of(ItemListType.LIST_CREATURE,
                            plainItems(items.creature(), 0, scripts));
            case ItemListType.LIST_AVATAR ->
                    Noti13ItemListBody.of(ItemListType.LIST_AVATAR, avatarBagItems());
            default -> Noti13ItemListBody.empty(listType);
        };
        byte[] bytes = body.toBytes();
        session.send(Noti13ItemListBody.PACKET_ID, bytes);
        // A4：NOTI 13 新建的物品对象锁状态为 0，官方每次整栏重发后都跟一条 NOTI 192 锁列表（ItemLockService.sendLockList）
        if (com.dylan.net.ServerConfig.A4 && listType != ItemListType.LIST_AVATAR && session.hasCharacter()) {
            session.itemLock().sendLockList(why);
        }
    }

    public void sendItemUpdate(int listType, String why, int... slots) {
        if (session == null || slots == null || slots.length == 0) {
            return;
        }
        List<Noti13ItemListBody.Item> items = new ArrayList<>(slots.length);
        for (int slot : slots) {
            Noti13ItemListBody.Item item = itemAt(listType, slot);
            if (item == null) {
                continue;
            }
            items.add(item);
        }
        if (items.isEmpty()) {
            return;
        }
        var body = new Noti14UpdateItemListBody(listType, items);
        byte[] bytes = body.toBytes();
        session.send(Noti14UpdateItemListBody.PACKET_ID, bytes);
        if (com.dylan.net.ServerConfig.A4 && session.hasCharacter()) {
            for (int slot : slots) {
                if (session.itemLock().isLocked(listType, slot)) {
                    // NOTI 14 可能重建那格的物品对象，锁着的格子补一条锁列表
                    session.itemLock().sendLockList(why);
                    break;
                }
            }
        }
    }

    private void markSlotEmptied(int listType, int slot) {
        if (session == null || slot < 0
                || listType == ItemListType.LIST_EQUIPPED
                || listType == ItemListType.LIST_AVATAR) {
            return;
        }
        emptiedSlots.computeIfAbsent(listType, k -> new java.util.TreeSet<>()).add(slot);
    }

    public void flushEmptiedSlots() {
        if (emptiedSlots.isEmpty()) {
            return;
        }
        var pending = new LinkedHashMap<>(emptiedSlots);
        emptiedSlots.clear();
        if (session == null || !session.isActive()) {
            return;
        }
        for (var entry : pending.entrySet()) {
            int[] slots = entry.getValue().stream().mapToInt(Integer::intValue).toArray();
            sendItemUpdate(entry.getKey(), "这几格已经扣到 0 了，让客户端把格子清掉", slots);
        }
    }

    public void sendItemRebuild(int listType, String why, int slot) {
        if (session == null) {
            return;
        }
        Noti13ItemListBody.Item item = itemAt(listType, slot);
        if (item == null) {
            return;
        }
        var cleared = new Noti13ItemListBody.Item(slot,
                Noti14UpdateItemListBody.EMPTY_SLOT_ITEM_ID,
                0, 0, 0, Noti13ItemListBody.SEAL_UNSEALED, 0, 0, 0);
        byte[] clearBytes = new Noti14UpdateItemListBody(listType, List.of(cleared)).toBytes();
        session.send(Noti14UpdateItemListBody.PACKET_ID, clearBytes);

        byte[] bytes = new Noti14UpdateItemListBody(listType, List.of(item)).toBytes();
        session.send(Noti14UpdateItemListBody.PACKET_ID, bytes);
    }

    public Noti13ItemListBody.Item itemAt(int listType, int slot) {
        if (slot < 0) {
            return null;
        }
        if (listType == ItemListType.LIST_AVATAR) {
            var row = avatarRowInBag(slot);
            if (row == null) {
                return null;
            }
            return avatarBagItem(row, java.time.Instant.now());
        }
        if (listType == ItemListType.LIST_MAIN && BagLayout.isCurrencySlot(slot)) {
            return currency(slot, currencyAt(slot));
        }
        ItemArray array = containerOf(listType);
        if (array == null || slot >= array.slots()) {
            return null;
        }
        InvenItem item = array.get(slot);
        if (item.isEmpty()) {
            return new Noti13ItemListBody.Item(slot,
                    Noti14UpdateItemListBody.EMPTY_SLOT_ITEM_ID,
                    0, 0, 0, Noti13ItemListBody.SEAL_UNSEALED, 0, 0, 0);
        }
        return toWire(slot, item);
    }

    private long currencyAt(int slot) {
        if (slot == BagLayout.SLOT_GOLD) {
            return money();
        }
        if (slot == BagLayout.SLOT_COIN) {
            return coinTotal();
        }
        return session == null ? 0 : session.winPoint();
    }

    public List<Noti2CharacterEntry.Equipment> equipmentList() {
        if (items == null) {
            return List.of();
        }
        return equipmentListOf(items.equipslot());
    }

    public List<Noti2PartyEntry.Equipment> detailedEquipmentList() {
        if (items == null) {
            return List.of();
        }
        if (session.scripts() == null) {
            return detailedEquipmentListOf(items.equipslot(), (slot, itemId) -> false);
        }
        return detailedEquipmentListOf(items.equipslot(), session.scripts());
    }

    List<Noti2PartyEntry.Equipment> detailedEquipmentList(AvatarTailRule avatarTail) {
        if (items == null) {
            return List.of();
        }
        return detailedEquipmentListOf(items.equipslot(), avatarTail);
    }

    public boolean hasEquippedAvatar() {
        if (items == null) {
            return false;
        }
        ItemArray worn = items.equipslot();
        for (int slot = 0; slot <= EquipmentSlot.SKIN_AVATAR && slot < worn.slots(); slot++) {
            if (!worn.get(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private InvenItem equippedCreatureSlot() {
        if (items == null) {
            return InvenItem.EMPTY;
        }
        ItemArray equipslot = items.equipslot();
        if (EquipmentSlot.CREATURE >= equipslot.slots()) {
            return InvenItem.EMPTY;
        }
        return equipslot.get(EquipmentSlot.CREATURE);
    }

    public long equippedCreatureItemId() {
        return equippedCreatureSlot().itemId();
    }

    public Creature equippedCreature() {
        InvenItem worn = equippedCreatureSlot();
        return worn.isEmpty() ? null : findCreature(worn.addInfo());
    }

    public String equippedCreatureName() {
        var worn = equippedCreature();
        return worn == null || worn.row().name() == null ? "" : worn.row().name();
    }

    public boolean equippedCreatureAlive() {
        var worn = equippedCreature();
        return worn == null || !worn.dead(CreatureService.nowSecs());
    }

    public Creature findCreature(long uiId) {
        if (uiId == 0) {
            return null;
        }
        for (var creature : creatureItems) {
            if (creature.uiId() == uiId) {
                return creature;
            }
        }
        return null;
    }

    public List<Creature> creatures() {
        return List.copyOf(creatureItems);
    }

    public com.dylan.protocol.body.Noti105CreatureItemListBody creatureItemList() {
        long now = CreatureService.nowSecs();
        List<com.dylan.protocol.body.Noti105CreatureItemListBody.Item> out = new ArrayList<>();
        for (var creature : creatureItems) {
            var row = creature.row();
            if (row.creatureType() != com.dylan.store.CreatureItemRow.TYPE_CREATURE) {
                continue;
            }
            out.add(new com.dylan.protocol.body.Noti105CreatureItemListBody.Item(
                    row.uiId(), creature.stomachNow(now), row.exp(), creature.level(),
                    row.name() == null ? "" : row.name(), row.noCharge()));
        }
        return new com.dylan.protocol.body.Noti105CreatureItemListBody(out);
    }

    public static List<Noti2PartyEntry.Equipment> detailedEquipmentListOf(
            ItemArray equipslot, com.dylan.pvf.GameScripts scripts) {
        if (scripts == null) {
            throw new IllegalStateException(
                    "detailedEquipmentListOf 没拿到 pvf：装扮尾巴判不出来，"
                            + "发出去就是 NOTI 2 子类型 1 整包错位（见 Equipment#avatarTail）");
        }
        return detailedEquipmentListOf(equipslot,
                (slot, itemId) -> carriesAvatarTail(slot, itemId, scripts));
    }

    @FunctionalInterface
    interface AvatarTailRule {
        boolean applies(int slot, int itemId);
    }

    static List<Noti2PartyEntry.Equipment> detailedEquipmentListOf(
            ItemArray equipslot, AvatarTailRule avatarTail) {
        List<Noti2PartyEntry.Equipment> out = new ArrayList<>();
        for (int slot = 0; slot < equipslot.slots(); slot++) {
            InvenItem item = equipslot.get(slot);
            if (item.isEmpty()) {
                continue;
            }
            if (slot > 0xFF || item.itemId() > 0xFFFF_FFFFL) {
                continue;
            }
            int addInfo = slot > EquipmentSlot.SKIN_AVATAR ? (int) item.addInfo() : 0;
            out.add(new Noti2PartyEntry.Equipment(
                    slot,
                    (int) item.itemId(),
                    addInfo,
                    item.itemAttr(),
                    item.endurance(),
                    0,
                    (int) item.enchant(),
                    item.amplify().abilityType(),
                    item.amplify().abilityValue(),
                    avatarTail.applies(slot, (int) item.itemId())));
        }
        return out;
    }

    private static boolean carriesAvatarTail(int slot, int itemId,
                                             com.dylan.pvf.GameScripts scripts) {
        var script = scripts.item(itemId).orElse(null);
        Noti2PartyEntry.isAvatarSlot(slot);
        if (script == null) {
            return false;
        }
        boolean byScript = Noti2PartyEntry.isAvatarSlot(script.equipmentSlot());
        return byScript;
    }

    public static List<Noti2CharacterEntry.Equipment> equipmentListOf(ItemArray equipslot) {
        return equipmentListOf(equipslot, false);
    }

    /**
     * {@code characterList} = 选角列表 NOTI 2/2。A4 那里装备项 flags 后的 u32 是物品 addInfo（官方库 item_count：
     * field_scan equip9.item_count 3 → 2/2 +27 = 03；满级假人武器 / 称号 01），时装和 NOTI 2/0 都是 0。
     * ACT5 这个位置是 clearAvatarItemId，A5 一律 0。
     */
    public static List<Noti2CharacterEntry.Equipment> equipmentListOf(ItemArray equipslot, boolean characterList) {
        List<Noti2CharacterEntry.Equipment> out = new ArrayList<>();
        int count = Math.min(EquipmentSlot.BASIC_LIST_COUNT, equipslot.slots());
        boolean withAddInfo = characterList && com.dylan.net.ServerConfig.A4;
        for (int slot = 0; slot < count; slot++) {
            InvenItem item = equipslot.get(slot);
            if (item.isEmpty()) {
                continue;
            }
            if (slot >= Noti2CharacterEntry.Equipment.SLOT_MAX
                    || item.itemId() > 0xFFFF_FFFFL) {
                continue;
            }
            int third = withAddInfo && slot > EquipmentSlot.SKIN_AVATAR ? (int) item.addInfo() : 0;
            out.add(Noti2CharacterEntry.Equipment.of(slot, (int) item.itemId(),
                    item.upgrade(), item.isAmplified(), third, (int) item.enchant()));
        }
        return out;
    }

    public boolean sort(int listType) {
        ItemArray array = containerOf(listType);
        if (array == null) {
            return false;
        }
        switch (listType) {
            case ItemListType.LIST_MAIN -> {
                compact(array, BagLayout.QUICK.firstSlot(), BagLayout.QUICK.lastSlot());
                for (BagLayout.Section s : BagLayout.TABS) {
                    compact(array, s.firstSlot(), s.lastSlot());
                }
                markItemsDirty();
                logTabLanding(array);
            }
            case ItemListType.LIST_CREATURE -> {
                for (CreatureBagLayout.Section s : CreatureBagLayout.SECTIONS) {
                    compact(array, s.firstSlot(), s.lastSlot());
                }
                markItemsDirty();
            }
            case ItemListType.LIST_CARGO -> {
                Cargo.sort(array);
                markCargoDirty();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void logTabLanding(ItemArray array) {
        int landing = -1;
        for (int slot = 0; slot < array.slots(); slot++) {
            if (!array.get(slot).isEmpty() && BagLayout.tabAt(slot) != null) {
                landing = slot;
            }
        }
        if (landing < 0) {
            return;
        }
        BagLayout.tabAt(landing);
    }

    private static void compact(ItemArray array, int from, int to) {
        int limit = Math.min(to, array.slots() - 1);
        int write = from;
        for (int slot = from; slot <= limit; slot++) {
            InvenItem item = array.get(slot);
            if (item.isEmpty()) {
                continue;
            }
            if (write != slot) {
                array.set(write, item);
                array.set(slot, InvenItem.EMPTY);
            }
            write++;
        }
    }

    public record MoveOutcome(boolean ok, int errorCode, int count, int dstSlot, String reason) {
        static MoveOutcome reject(String reason) {
            return new MoveOutcome(false, Cmd19ReplyBody.ERROR_CANNOT_MOVE, 0, 0, reason);
        }

        static MoveOutcome done(int count, int dstSlot, String what) {
            return new MoveOutcome(true, 0, count, dstSlot, what);
        }
    }

    public MoveOutcome move(Cmd19MoveItemSpaceRequest req) {
        ItemArray src = containerOf(req.srcType());
        ItemArray dst = containerOf(req.dstType());
        boolean srcOk = src != null || req.srcType() == ItemListType.LIST_AVATAR;
        boolean dstOk = dst != null || req.dstType() == ItemListType.LIST_AVATAR;
        if (!srcOk || !dstOk) {
            return MoveOutcome.reject("容器 " + (srcOk ? req.dstType() : req.srcType())
                    + " 这一版没有数据源");
        }
        boolean avatarInvolved = req.srcType() == ItemListType.LIST_AVATAR
                || req.dstType() == ItemListType.LIST_AVATAR;
        if (avatarInvolved && items == null) {
            return MoveOutcome.reject("这条连接没有背包数据，动不了装扮栏");
        }
        boolean cargoInvolved = req.srcType() == ItemListType.LIST_CARGO
                || req.dstType() == ItemListType.LIST_CARGO;
        if (!cargoInvolved
                && (!validSlot(req.srcType(), req.srcSlot())
                        || !validSlot(req.dstType(), req.dstSlot()))) {
            return MoveOutcome.reject("槽号越界：源 " + req.srcSlot() + " / 目标 " + req.dstSlot()
                    + "（容量 " + capacityOf(req.srcType()) + " / " + capacityOf(req.dstType())
                    + "）");
        }
        if (req.srcType() == req.dstType() && req.srcSlot() == req.dstSlot()) {
            return MoveOutcome.reject("源和目标是同一格");
        }
        if (req.srcType() != ItemListType.LIST_CARGO && req.dstType() != ItemListType.LIST_CARGO
                && !agreesOnBothEnds(req)) {
            countHack(HackAnalyzer.Site.MOVE_ITEM_MISMATCH, req.srcSlot(),
                    "容器 " + req.srcType() + " 第 " + req.srcSlot() + " 格 → 容器 "
                            + req.dstType() + " 第 " + req.dstSlot() + " 格：两端看法不一致");
            return MoveOutcome.reject("两端对这两格的看法不一致");
        }

        if (req.srcType() == ItemListType.LIST_MAIN && req.dstType() == ItemListType.LIST_MAIN) {
            return swapWithinMainBag(req);
        }
        if (req.srcType() == ItemListType.LIST_CARGO && req.dstType() == ItemListType.LIST_CARGO) {
            if (!Cargo.moveItem(src, cargo.usableSlots(), req.srcSlot(), req.dstSlot())) {
                return MoveOutcome.reject("金库槽 " + req.srcSlot() + " ←→ " + req.dstSlot()
                        + " 换不了（容量 " + cargo.usableSlots() + " 格，或两格都空）");
            }
            markCargoDirty();
            return MoveOutcome.done(req.count(), req.dstSlot(), "金库内对调");
        }
        if (req.srcType() == ItemListType.LIST_MAIN && req.dstType() == ItemListType.LIST_CARGO) {
            return depositToCargo(req);
        }
        if (req.srcType() == ItemListType.LIST_CARGO && req.dstType() == ItemListType.LIST_MAIN) {
            return withdrawFromCargo(req);
        }
        if (req.srcType() == ItemListType.LIST_CREATURE
                && req.dstType() == ItemListType.LIST_CREATURE) {
            return swapCreature(req.srcSlot(), ItemListType.LIST_CREATURE,
                    req.dstSlot(), ItemListType.LIST_CREATURE, req.count(), req.dstSlot(),
                    "宠物栏内对调");
        }
        if (req.srcType() == ItemListType.LIST_CREATURE
                && req.dstType() == ItemListType.LIST_EQUIPPED) {
            return swapCreature(req.srcSlot(), ItemListType.LIST_CREATURE,
                    req.dstSlot(), ItemListType.LIST_EQUIPPED, req.count(), req.dstSlot(),
                    "带上宠物");
        }
        if (req.srcType() == ItemListType.LIST_EQUIPPED
                && req.dstType() == ItemListType.LIST_CREATURE) {
            return swapCreature(req.dstSlot(), ItemListType.LIST_CREATURE,
                    req.srcSlot(), ItemListType.LIST_EQUIPPED, req.count(), req.dstSlot(),
                    "取下宠物");
        }
        if (req.srcType() == ItemListType.LIST_MAIN
                && req.dstType() == ItemListType.LIST_EQUIPPED) {
            return equip(req.srcSlot(), req.dstSlot(), req.count());
        }
        if (req.srcType() == ItemListType.LIST_EQUIPPED
                && req.dstType() == ItemListType.LIST_MAIN) {
            return unequip(req.srcSlot(), req.dstSlot(), req.count());
        }
        if (req.srcType() == ItemListType.LIST_AVATAR
                && req.dstType() == ItemListType.LIST_EQUIPPED) {
            return wearAvatar(req.srcSlot(), req.dstSlot(), req.count());
        }
        if (req.srcType() == ItemListType.LIST_EQUIPPED
                && req.dstType() == ItemListType.LIST_AVATAR) {
            return takeOffAvatar(req.srcSlot(), req.dstSlot(), req.count());
        }
        if (req.srcType() == ItemListType.LIST_AVATAR
                && req.dstType() == ItemListType.LIST_AVATAR) {
            return swapWithinAvatarBag(req.srcSlot(), req.dstSlot(), req.count());
        }
        if (isForbiddenByOfficial(req.srcType(), req.dstType())) {
            return MoveOutcome.reject("官方 move_item 就不允许 容器" + req.srcType()
                    + " → 容器" + req.dstType() + "，这是照抄的拒绝");
        }
        return MoveOutcome.reject("容器" + req.srcType() + " → 容器" + req.dstType()
                + " 官方允许，但这一版还没做");
    }

    private MoveOutcome depositToCargo(Cmd19MoveItemSpaceRequest req) {
        ItemArray bag = items.inventory();
        ItemArray box = cargo.items();
        int capacity = cargo.usableSlots();
        int srcSlot = req.srcSlot();
        if (srcSlot < 0 || srcSlot >= bag.slots() || BagLayout.isCurrencySlot(srcSlot)) {
            return MoveOutcome.reject("主背包槽 " + srcSlot + " 不是一个能拿的格子");
        }
        InvenItem item = bag.get(srcSlot);
        if (item.isEmpty()) {
            return MoveOutcome.reject("主背包槽 " + srcSlot + " 是空的");
        }
        GameScripts scripts = session == null ? null : session.scripts();
        if (scripts != null && scripts.item((int) item.itemId()).isEmpty()) {
            return new MoveOutcome(false, Cmd19ReplyBody.ERROR_CANNOT_STORE, 0, 0,
                    "物品 " + item.itemId() + " 在 pvf 里查不到");
        }
        if (item.itemId() > CARGO_FORBIDDEN_FIRST - 1 && item.itemId() <= CARGO_FORBIDDEN_LAST) {
            return new MoveOutcome(false, Cmd19ReplyBody.ERROR_CANNOT_STORE, 0, 0,
                    "物品 " + item.itemId() + " 落在官方写死的 " + CARGO_FORBIDDEN_FIRST + ".."
                            + CARGO_FORBIDDEN_LAST + " 那一段里，不许入库");
        }

        int dst = req.dstSlot();
        long count = req.count();
        InvenItem moving = item;
        if (item.itemType() == EQUIPMENT_ITEM_TYPE) {
            if (!Cargo.checkSlotEmpty(box, capacity, dst)) {
                dst = Cargo.getEmptySlot(box, capacity);
                if (dst < 0) {
                    return new MoveOutcome(false, Cmd19ReplyBody.ERROR_CARGO_FULL, 0, 0,
                            "金库满了（" + capacity + " 格）");
                }
            }
            count = 1;
        } else {
            if (item.itemType() == CARGO_FORBIDDEN_ITEM_TYPE) {
                return new MoveOutcome(false, Cmd19ReplyBody.ERROR_CANNOT_STORE, 0, 0,
                        "ITEM_TYPE 4 官方不许入库");
            }
            if (item.addInfo() < count || count <= 0) {
                return MoveOutcome.reject("要存 " + count + " 件，那一格只有 " + item.addInfo());
            }
            moving = item.withAddInfo(count);
            int exist = Cargo.checkItemExist(box, item.itemId());
            if (exist >= 0) {
                dst = exist;
            } else if (!Cargo.checkSlotEmpty(box, capacity, dst)) {
                dst = Cargo.getEmptySlot(box, capacity);
                if (dst < 0) {
                    return new MoveOutcome(false, Cmd19ReplyBody.ERROR_CARGO_FULL, 0, 0,
                            "金库满了（" + capacity + " 格）");
                }
            }
        }

        int inserted = Cargo.tryInsert(box, capacity, moving, dst, itemScriptLookup());
        if (inserted < 0) {
            return new MoveOutcome(false, Cmd19ReplyBody.ERROR_CARGO_FULL, 0, 0,
                    "金库第 " + dst + " 格放不下（" + inserted + "）");
        }
        int taken = deleteItem(ItemListType.LIST_MAIN, srcSlot, (int) item.itemId(), (int) count);
        if (taken < 0) {
            return MoveOutcome.reject("从主背包槽 " + srcSlot + " 扣不下来（错误码 " + (-taken) + "）");
        }
        int landed = Cargo.insert(box, capacity, moving, inserted, itemScriptLookup());
        markCargoDirty();
        markItemsDirty();
        return MoveOutcome.done((int) count, landed,
                "存进金库：主背包槽 " + srcSlot + " 的物品 " + item.itemId() + " × " + count
                        + " → 金库槽 " + landed);
    }

    private MoveOutcome withdrawFromCargo(Cmd19MoveItemSpaceRequest req) {
        ItemArray bag = items.inventory();
        ItemArray box = cargo.items();
        int capacity = cargo.usableSlots();
        int srcSlot = req.srcSlot();
        if (!Cargo.isOperatorable(capacity, srcSlot) || srcSlot >= box.slots()) {
            return MoveOutcome.reject("金库槽 " + srcSlot + " 不能操作（容量 " + capacity + " 格）");
        }
        InvenItem item = box.get(srcSlot);
        if (item.isEmpty()) {
            return MoveOutcome.reject("金库槽 " + srcSlot + " 是空的");
        }

        long count = req.count();
        InvenItem moving = item;
        if (item.itemType() != EQUIPMENT_ITEM_TYPE) {
            if (count <= 0 || count > item.addInfo()) {
                return MoveOutcome.reject("要取 " + count + " 件，那一格只有 " + item.addInfo());
            }
            moving = item.withAddInfo(count);
        } else {
            count = 1;
        }

        if (tryInsertExisting(bag, moving) < 0) {
            return MoveOutcome.reject("背包放不下物品 " + item.itemId() + " × " + count);
        }
        if (!Cargo.deleteItem(box, capacity, srcSlot, count)) {
            return MoveOutcome.reject("从金库槽 " + srcSlot + " 扣不下来");
        }
        int landed = insertExisting(bag, moving);
        markCargoDirty();
        markItemsDirty();
        return MoveOutcome.done((int) count, landed,
                "从金库取出：金库槽 " + srcSlot + " 的物品 " + item.itemId() + " × " + count
                        + " → 主背包槽 " + landed);
    }

    public int insertExisting(ItemArray array, InvenItem item) {
        GameScripts scripts = session == null ? null : session.scripts();
        ItemScript script = scripts == null ? null : scripts.item((int) item.itemId()).orElse(null);
        if (script != null && script.isStackableItem()) {
            int stacked = stackInto(array, script, item.itemId(), item.addInfo());
            if (stacked >= 0) {
                return stacked;
            }
        }
        if (script != null && item.addInfo() > stackLimitOf(script)) {
            return -1;
        }
        int slot = BagLayout.allocate(script, s -> s >= array.slots() || !array.get(s).isEmpty());
        if (slot < 0) {
            return -1;
        }
        array.set(slot, item);
        recordInsert(array, slot, item);
        return slot;
    }

    private void recordInsert(ItemArray array, int slot, InvenItem item) {
        int itemSpace = itemSpaceIn(array);
        if (itemSpace >= 0) {
            ItemHistoryRecorder.record(session, itemSpace, slot, item, item.stackCount());
        }
    }

    private int itemSpaceIn(ItemArray array) {
        if (array == null || items == null) {
            return -1;
        }
        if (array == items.inventory()) {
            return ItemListType.LIST_MAIN;
        }
        if (array == items.equipslot()) {
            return ItemListType.LIST_EQUIPPED;
        }
        if (array == items.creature()) {
            return ItemListType.LIST_CREATURE;
        }
        if (cargo != null && array == cargo.items()) {
            return ItemListType.LIST_CARGO;
        }
        return -1;
    }

    public int tryInsertExisting(ItemArray array, InvenItem item) {
        ItemArray copy = ItemArray.decode(array.encode(), array.slots());
        return insertExisting(copy, item);
    }

    private java.util.function.IntFunction<ItemScript> itemScriptLookup() {
        GameScripts scripts = session == null ? null : session.scripts();
        if (scripts == null) {
            return null;
        }
        return id -> scripts.item(id).orElse(null);
    }

    private static final long CARGO_FORBIDDEN_FIRST = TOWER_ITEM_FIRST;

    private static final long CARGO_FORBIDDEN_LAST = TOWER_ITEM_LAST;

    private static final int CARGO_FORBIDDEN_ITEM_TYPE = 4;

    private MoveOutcome equip(int bagSlot, int equipSlot, int count) {
        ItemArray bag = items.inventory();
        ItemArray worn = items.equipslot();
        InvenItem incoming = bag.get(bagSlot);
        InvenItem outgoing = worn.get(equipSlot);

        if (!checkEquipable(incoming, outgoing)) {
            return MoveOutcome.reject("装备栏只放得下装备和时装（两格都空也不行）");
        }
        if (!incoming.isEmpty() && !incoming.amplify().identified()) {
            return MoveOutcome.reject("背包里那件的增幅还没鉴定");
        }
        if (!outgoing.isEmpty() && !outgoing.amplify().identified()) {
            return MoveOutcome.reject("身上那件的增幅还没鉴定");
        }
        if (incoming.itemType() == ItemTypes.AVATAR) {
            return MoveOutcome.reject("主背包槽 " + bagSlot + " 上是时装，时装只能从装扮栏穿");
        }
        String why = checkWearable(incoming, equipSlot);
        if (why != null) {
            return MoveOutcome.reject(why);
        }
        if (!fitsSection(BagLayout.tabAt(bagSlot), outgoing)) {
            return MoveOutcome.reject("换下来的那件放不进背包槽 " + bagSlot + " 所在的页");
        }
        swapAcross(bag, bagSlot, worn, equipSlot);
        unsealWorn(worn, equipSlot);
        markItemsDirty();
        return MoveOutcome.done(count, equipSlot,
                (incoming.isEmpty() ? "脱下" : "穿上") + EquipmentSlot.name(equipSlot)
                        + "（背包槽 " + bagSlot + "）");
    }

    private MoveOutcome unequip(int equipSlot, int bagSlot, int count) {
        ItemArray bag = items.inventory();
        ItemArray worn = items.equipslot();
        InvenItem outgoing = worn.get(equipSlot);
        InvenItem incoming = bag.get(bagSlot);

        if (!checkEquipable(outgoing, incoming)) {
            return MoveOutcome.reject("装备栏只放得下装备和时装（两格都空也不行）");
        }
        BagLayout.Section section = BagLayout.tabAt(bagSlot);
        boolean placeable = BagLayout.QUICK.contains(bagSlot) || section == BagLayout.TAB1;
        if (!placeable) {
            return MoveOutcome.reject("脱下来的装备只能放快捷栏或装备页，槽 " + bagSlot
                    + " 在" + (section == null ? "别处" : section.label()));
        }
        if (!outgoing.isEmpty() && !outgoing.amplify().identified()) {
            return MoveOutcome.reject("身上那件的增幅还没鉴定");
        }
        if (!incoming.isEmpty() && !incoming.amplify().identified()) {
            return MoveOutcome.reject("背包里那件的增幅还没鉴定");
        }
        if (incoming.itemType() == ItemTypes.AVATAR) {
            return MoveOutcome.reject("主背包槽 " + bagSlot + " 上是时装，时装只能从装扮栏穿");
        }
        if (outgoing.itemType() == ItemTypes.AVATAR) {
            return MoveOutcome.reject("身上那件是时装，只能脱回装扮栏（容器 "
                    + ItemListType.LIST_AVATAR + "）");
        }
        String why = checkWearable(incoming, equipSlot);
        if (why != null) {
            return MoveOutcome.reject(why);
        }
        swapAcross(bag, bagSlot, worn, equipSlot);
        unsealWorn(worn, equipSlot);
        markItemsDirty();
        return MoveOutcome.done(count, bagSlot,
                "脱下" + EquipmentSlot.name(equipSlot) + "（进背包槽 " + bagSlot + "）");
    }

    public record CreatureOutcome(boolean ok, int errorCode, String detail) {
        static CreatureOutcome fail(int errorCode, String detail) {
            return new CreatureOutcome(false, errorCode, detail);
        }

        static CreatureOutcome done(String detail) {
            return new CreatureOutcome(true, 0, detail);
        }
    }

    public CreatureOutcome renameCreature(int slot, int itemSpace, String name) {
        var worn = equippedCreature();
        if (worn == null) {
            return CreatureOutcome.fail(
                    com.dylan.protocol.body.Cmd103ReplyBody.ERROR_NO_CREATURE,
                    "没有带着宠物");
        }
        String rejected = NameFilter.rejectReason(name);
        if (rejected != null) {
            return CreatureOutcome.fail(
                    com.dylan.protocol.body.Cmd103ReplyBody.ERROR_BAD_NAME, rejected);
        }
        String trimmed = truncateName(name);
        ItemArray container = containerOf(itemSpace);
        if (container == null || slot < 0 || slot >= container.slots()) {
            return CreatureOutcome.fail(
                    com.dylan.protocol.body.Cmd103ReplyBody.ERROR_NO_ITEM,
                    "容器 " + itemSpace + " 槽 " + slot + " 取不到东西");
        }
        InvenItem token = container.get(slot);
        if (token.isEmpty()) {
            return CreatureOutcome.fail(
                    com.dylan.protocol.body.Cmd103ReplyBody.ERROR_NO_ITEM,
                    "容器 " + itemSpace + " 槽 " + slot + " 是空的");
        }
        consumeOne(container, slot, token);
        worn.setRow(worn.row().withName(trimmed));
        GameStore store = session == null ? null : session.store();
        if (store != null && worn.uiId() != com.dylan.store.CreatureItemRow.NOT_PERSISTED) {
            store.creature().renameCreatureItem(worn.uiId(), trimmed);
        }
        markItemsDirty();
        return CreatureOutcome.done("宠物 " + worn.row().itemId() + " 改名「" + trimmed
                + "」，用掉容器" + itemSpace + " 槽 " + slot + " 的 " + token.itemId());
    }

    public CreatureOutcome hatchCreature(int itemSpace, int slot) {
        ItemArray container = containerOf(itemSpace);
        if (container == null || slot < 0 || slot >= container.slots()) {
            return CreatureOutcome.fail(
                    com.dylan.protocol.body.Cmd105ReplyBody.ERROR_NOT_FOUND,
                    "容器 " + itemSpace + " 槽 " + slot + " 取不到东西");
        }
        InvenItem egg = container.get(slot);
        var creature = egg.isEmpty() ? null : findCreature(egg.addInfo());
        if (creature == null) {
            return CreatureOutcome.fail(
                    com.dylan.protocol.body.Cmd105ReplyBody.ERROR_NOT_FOUND,
                    "容器 " + itemSpace + " 槽 " + slot + " 上那件东西在 creature_items 里没有行");
        }
        var row = creature.row();
        if (row.creatureType() != 0) {
            return CreatureOutcome.fail(
                    com.dylan.protocol.body.Cmd105ReplyBody.ERROR_NOT_EGG,
                    "creature_type=" + row.creatureType() + "，这不是蛋");
        }
        GameScripts scripts = session == null ? null : session.scripts();
        ItemScript script = scripts == null ? null
                : scripts.item((int) egg.itemId()).orElse(null);
        int hatched = script == null ? 0 : script.hatchItemId();
        if (hatched <= 0) {
            return CreatureOutcome.fail(
                    com.dylan.protocol.body.Cmd105ReplyBody.ERROR_HATCH_FAILED,
                    "物品 " + egg.itemId() + " 没有 [output index]，孵不出东西");
        }
        container.set(slot, egg.withAttachType(0).withItemId(hatched));
        replaceCreature(creature, CreatureService.hatched(session, creature,
                row.withHatched(hatched)));
        GameStore store = session == null ? null : session.store();
        if (store != null && row.uiId() != com.dylan.store.CreatureItemRow.NOT_PERSISTED) {
            store.creature().hatchCreatureItem(row.uiId(), hatched);
        }
        markItemsDirty();
        return CreatureOutcome.done("蛋 " + egg.itemId() + " → 宠物 " + hatched
                + "（容器" + itemSpace + " 槽 " + slot + "，ui_id=" + row.uiId() + "）");
    }

    private static String truncateName(String name) {
        if (name == null) {
            return "";
        }
        byte[] raw = name.getBytes(com.dylan.protocol.io.PacketWriter.DEFAULT_CHARSET);
        if (raw.length <= com.dylan.store.CreatureItemRow.NAME_MAX) {
            return name;
        }
        return new String(raw, 0, com.dylan.store.CreatureItemRow.NAME_MAX,
                com.dylan.protocol.io.PacketWriter.DEFAULT_CHARSET);
    }

    private void consumeOne(ItemArray container, int slot, InvenItem item) {
        long left = item.stackCount() - 1;
        container.set(slot, left > 0 ? item.withAddInfo(left) : InvenItem.EMPTY);
    }

    private MoveOutcome swapCreature(int slotA, int typeA, int slotB, int typeB,
                                     int count, int replyDstSlot, String what) {
        ItemArray arrayA = containerOf(typeA);
        ItemArray arrayB = containerOf(typeB);
        if (arrayA == null || arrayB == null) {
            return MoveOutcome.reject("宠物栏或装备栏没有数据源");
        }
        InvenItem itemA = arrayA.get(slotA);
        InvenItem itemB = arrayB.get(slotB);
        if (itemA.isEmpty() && itemB.isEmpty()) {
            return MoveOutcome.reject("两格都是空的");
        }
        boolean artifact = itemA.itemType() == ItemTypes.CREATURE_ARTIFACT
                || itemB.itemType() == ItemTypes.CREATURE_ARTIFACT;
        boolean toEquip = typeA == ItemListType.LIST_EQUIPPED
                || typeB == ItemListType.LIST_EQUIPPED;
        if (artifact) {
            MoveOutcome denied = swapArtifact(arrayA, slotA, typeA, arrayB, slotB, typeB);
            if (denied != null) {
                return denied;
            }
        }
        var creA = itemA.isEmpty() ? null : findCreature(itemA.addInfo());
        var creB = itemB.isEmpty() ? null : findCreature(itemB.addInfo());

        if (itemA.itemType() == ItemTypes.CREATURE || itemB.itemType() == ItemTypes.CREATURE) {
            if (!creatureSlotInScope(slotA) || !creatureSlotInScope(slotB)) {
                return MoveOutcome.reject("宠物槽号越界（0.." + CREATURE_SLOT_SCOPE + "）："
                        + slotA + " / " + slotB);
            }
            boolean aIsEquipSlot = typeA == ItemListType.LIST_EQUIPPED
                    && slotA == EquipmentSlot.CREATURE;
            boolean bIsEquipSlot = typeB == ItemListType.LIST_EQUIPPED
                    && slotB == EquipmentSlot.CREATURE;
            if (!aIsEquipSlot && !bIsEquipSlot && toEquip) {
                return MoveOutcome.reject("宠物只能放在装备栏第 " + EquipmentSlot.CREATURE
                        + " 格（" + EquipmentSlot.name(EquipmentSlot.CREATURE) + "）");
            }
            if (aIsEquipSlot && !equipCreatureItem(creA, creB)) {
                return MoveOutcome.reject("换不上：只有 creature_type=1 的宠物本体能上身");
            }
            if (!aIsEquipSlot && bIsEquipSlot && !equipCreatureItem(creB, creA)) {
                return MoveOutcome.reject("换不上：只有 creature_type=1 的宠物本体能上身");
            }
        }

        moveCreatureRow(creA, slotB, typeB);
        moveCreatureRow(creB, slotA, typeA);
        swapAcross(arrayA, slotA, arrayB, slotB);
        markItemsDirty();
        return MoveOutcome.done(count, replyDstSlot, what + "：槽 " + slotA + "（容器" + typeA
                + "）←→ 槽 " + slotB + "（容器" + typeB + "）");
    }

    private MoveOutcome swapArtifact(ItemArray arrayA, int slotA, int typeA,
                                     ItemArray arrayB, int slotB, int typeB) {
        boolean aIsEquip = typeA == ItemListType.LIST_EQUIPPED;
        boolean bIsEquip = typeB == ItemListType.LIST_EQUIPPED;
        if (!aIsEquip && !bIsEquip) {
            return null;
        }
        boolean aIsArtifactSlot = aIsEquip && isArtifactEquipSlot(slotA);
        boolean bIsArtifactSlot = bIsEquip && isArtifactEquipSlot(slotB);
        if (!aIsArtifactSlot && !bIsArtifactSlot) {
            return MoveOutcome.reject("神器只能放在装备栏第 " + EquipmentSlot.FIRST_ARTIFACT
                    + ".." + (EquipmentSlot.COUNT - 1) + " 格（"
                    + EquipmentSlot.name(EquipmentSlot.FIRST_ARTIFACT) + " 那三格）");
        }
        ItemArray bagArray = aIsArtifactSlot ? arrayB : arrayA;
        int bagSlot = aIsArtifactSlot ? slotB : slotA;
        InvenItem incoming = bagArray.get(bagSlot);
        if (!incoming.isEmpty()) {
            GameScripts scripts = session == null ? null : session.scripts();
            ItemScript script = scripts == null
                    ? null : scripts.item((int) incoming.itemId()).orElse(null);
            if (script == null) {
                return MoveOutcome.reject("物品 " + incoming.itemId() + " 不在物品表里");
            }
            if (script.isStackableItem()) {
                return MoveOutcome.reject("可堆叠物品当不了神器");
            }
            bagArray.set(bagSlot, incoming.withAttachType(0));
        }
        return null;
    }

    private static boolean isArtifactEquipSlot(int slot) {
        return slot >= EquipmentSlot.FIRST_ARTIFACT && slot < EquipmentSlot.COUNT;
    }

    private boolean equipCreatureItem(Creature down, Creature up) {
        if (down != null
                && down.row().creatureType() != com.dylan.store.CreatureItemRow.TYPE_CREATURE) {
            return false;
        }
        if (down != null) {
            CreatureService.dismantle(session, down);
        }
        if (up == null) {
            return true;
        }
        if (up.row().creatureType() != com.dylan.store.CreatureItemRow.TYPE_CREATURE) {
            return false;
        }
        CreatureService.equip(session, up);
        return true;
    }

    private void moveCreatureRow(Creature creature, int slot, int listType) {
        if (creature == null) {
            return;
        }
        int encoded = listType == ItemListType.LIST_EQUIPPED
                ? com.dylan.store.CreatureItemRow.ofEquipSlot(slot)
                : slot;
        creature.setRow(creature.row().withSlot(encoded));
        GameStore store = session == null ? null : session.store();
        if (store != null
                && creature.uiId() != com.dylan.store.CreatureItemRow.NOT_PERSISTED) {
            store.creature().moveCreatureItem(creature.uiId(), encoded);
        }
    }

    void replaceCreature(Creature old, Creature updated) {
        for (int i = 0; i < creatureItems.size(); i++) {
            if (creatureItems.get(i).uiId() == updated.uiId()) {
                creatureItems.set(i, updated);
                return;
            }
        }
        creatureItems.remove(old);
        creatureItems.add(updated);
    }

    void addCreature(Creature creature) {
        creatureItems.add(creature);
    }

    private static boolean creatureSlotInScope(int slot) {
        return slot >= 0 && slot < CREATURE_SLOT_SCOPE;
    }

    private static final int CREATURE_SLOT_SCOPE = CreatureBagLayout.PET.lastSlot() + 1;

    private MoveOutcome wearAvatar(int bagSlot, int equipSlot, int count) {
        ItemArray worn = items.equipslot();
        if (equipSlot < 0 || equipSlot >= worn.slots()) {
            return MoveOutcome.reject("装备槽 " + equipSlot + " 越界（装备栏 "
                    + worn.slots() + " 格）");
        }
        var incomingRow = avatarRowInBag(bagSlot);
        InvenItem incoming = incomingRow == null ? InvenItem.EMPTY : avatarInvenItem(incomingRow);
        InvenItem outgoing = worn.get(equipSlot);
        if (!checkEquipable(incoming, outgoing)) {
            return MoveOutcome.reject("装备栏只放得下装备和时装（两格都空也不行）");
        }
        if (!outgoing.isEmpty() && outgoing.itemType() != ItemTypes.AVATAR) {
            return MoveOutcome.reject(EquipmentSlot.name(equipSlot)
                    + "上是装备不是时装，换不进装扮栏");
        }
        String why = checkWearable(incoming, equipSlot);
        if (why != null) {
            return MoveOutcome.reject(why);
        }
        why = checkWearable(outgoing, equipSlot);
        if (why != null) {
            return MoveOutcome.reject(why);
        }
        var outgoingRow = outgoing.isEmpty() ? null : avatarRowWorn(equipSlot);
        if (!outgoing.isEmpty() && outgoingRow == null) {
            return MoveOutcome.reject("身上那件时装没有对应的 user_items 行");
        }
        swapAvatarRows(incomingRow, outgoingRow, equipSlot, bagSlot + AVATAR_BAG_SLOT_BASE);
        worn.set(equipSlot, incoming);
        markItemsDirty();
        return MoveOutcome.done(count, equipSlot,
                (incoming.isEmpty() ? "脱下" : "穿上时装 " + incoming.itemId() + " → ")
                        + EquipmentSlot.name(equipSlot) + "（装扮栏槽 " + bagSlot + "）");
    }

    private MoveOutcome takeOffAvatar(int equipSlot, int bagSlot, int count) {
        ItemArray worn = items.equipslot();
        if (equipSlot < 0 || equipSlot >= worn.slots()) {
            return MoveOutcome.reject("装备槽 " + equipSlot + " 越界（装备栏 "
                    + worn.slots() + " 格）");
        }
        InvenItem outgoing = worn.get(equipSlot);
        var incomingRow = avatarRowInBag(bagSlot);
        InvenItem incoming = incomingRow == null ? InvenItem.EMPTY : avatarInvenItem(incomingRow);
        if (!checkEquipable(outgoing, incoming)) {
            return MoveOutcome.reject("装备栏只放得下装备和时装（两格都空也不行）");
        }
        if (!outgoing.isEmpty() && outgoing.itemType() != ItemTypes.AVATAR) {
            return MoveOutcome.reject(EquipmentSlot.name(equipSlot)
                    + "上是装备不是时装，脱不进装扮栏");
        }
        String why = checkWearable(incoming, equipSlot);
        if (why != null) {
            return MoveOutcome.reject(why);
        }
        var outgoingRow = outgoing.isEmpty() ? null : avatarRowWorn(equipSlot);
        if (!outgoing.isEmpty() && outgoingRow == null) {
            return MoveOutcome.reject("身上那件时装没有对应的 user_items 行");
        }
        swapAvatarRows(incomingRow, outgoingRow, equipSlot, bagSlot + AVATAR_BAG_SLOT_BASE);
        worn.set(equipSlot, incoming);
        markItemsDirty();
        return MoveOutcome.done(count, bagSlot,
                "脱下" + EquipmentSlot.name(equipSlot) + "的时装 " + outgoing.itemId()
                        + "（进装扮栏槽 " + bagSlot + "）");
    }

    private MoveOutcome swapWithinAvatarBag(int srcSlot, int dstSlot, int count) {
        var srcRow = avatarRowInBag(srcSlot);
        var dstRow = avatarRowInBag(dstSlot);
        if (srcRow == null && dstRow == null) {
            return MoveOutcome.reject("装扮栏槽 " + srcSlot + " 和 " + dstSlot + " 都是空的");
        }
        swapAvatarRows(srcRow, dstRow,
                dstSlot + AVATAR_BAG_SLOT_BASE, srcSlot + AVATAR_BAG_SLOT_BASE);
        return MoveOutcome.done(count, dstSlot,
                "装扮栏内对调 槽" + srcSlot + " ←→ 槽" + dstSlot);
    }

    private void swapAvatarRows(com.dylan.store.AvatarItemRow srcRow,
                                com.dylan.store.AvatarItemRow dstRow,
                                int srcTo, int dstTo) {
        if (srcRow != null) {
            moveAvatarRow(srcRow, srcTo);
        }
        if (dstRow != null) {
            moveAvatarRow(dstRow, dstTo);
        }
    }

    private static boolean checkEquipable(InvenItem a, InvenItem b) {
        if (!a.isEmpty() && a.itemType() != ItemTypes.EQUIPMENT && a.itemType() != ItemTypes.AVATAR) {
            return false;
        }
        if (!b.isEmpty() && b.itemType() != ItemTypes.EQUIPMENT && b.itemType() != ItemTypes.AVATAR) {
            return false;
        }
        return !a.isEmpty() || !b.isEmpty();
    }

    private static final class ItemTypes {
        static final int EQUIPMENT = 1;
        static final int CREATURE = 5;
        static final int CREATURE_ARTIFACT = 6;
        static final int AVATAR = 8;
    }

    private String checkWearable(InvenItem item, int equipSlot) {
        if (item.isEmpty()) {
            return null;
        }
        GameScripts scripts = session == null ? null : session.scripts();
        if (scripts == null) {
            return null;
        }
        ItemScript script = scripts.item((int) item.itemId()).orElse(null);
        if (script == null) {
            return "物品 " + item.itemId() + " 在 pvf 里查不到";
        }
        if (script.equipmentSlot() != equipSlot) {
            return "物品 " + item.itemId() + " 是" + EquipmentSlot.name(script.equipmentSlot())
                    + "，穿不到" + EquipmentSlot.name(equipSlot);
        }
        var row = session.character();
        if (row != null && !script.usableByJob(row.job())) {
            return "职业 " + row.job() + " 用不了物品 " + item.itemId();
        }
        if (row != null) {
            int over = session.premium().overEquipableLevel(equipSlot);
            if (script.minimumLevel() > session.level() + over) {
                return "等级不够：要 " + script.minimumLevel() + "，现在 " + session.level()
                        + (over > 0 ? "（会员越级额度 +" + over + "）" : "");
            }
        }
        if (script.needsSkill() && !hasSkill(script.requiredSkill())) {
            return "要先学会技能 " + script.requiredSkill();
        }
        return null;
    }

    private boolean hasSkill(int skillId) {
        var slots = session == null ? null : session.skills().slots();
        if (slots == null) {
            return true;
        }
        for (int slot = 0; slot < com.dylan.store.blob.SkillSlots.SLOTS; slot++) {
            if (slots.skillIndexAt(slot) == skillId) {
                return true;
            }
        }
        return false;
    }

    public record UseItemOutcome(int error, boolean consumed, long remaining, String detail) {
        public boolean ok() {
            return error == 0;
        }

        static UseItemOutcome fail(int error, String detail) {
            return new UseItemOutcome(error, false, 0, detail);
        }
    }

    public UseItemOutcome useItem(int slot, int count, int itemSpace) {
        if (count < 0) {
            return UseItemOutcome.fail(Cmd47ReplyBody.ERROR_NOT_ENOUGH, "件数 " + count + " 是负的");
        }
        ItemArray container = containerOf(itemSpace);
        if (container == null) {
            return UseItemOutcome.fail(Cmd47ReplyBody.ERROR_BAD_SLOT, "容器 " + itemSpace + " 不认识");
        }
        boolean creatureBag = itemSpace == ItemListType.LIST_CREATURE;

        if (itemSpace == ItemListType.LIST_MAIN && !usableMainBagSlot(slot)) {
            return UseItemOutcome.fail(Cmd47ReplyBody.ERROR_BAD_SLOT,
                    "槽 " + slot + " 不是能用东西的格子（要在 " + BagLayout.FIRST_ITEM_SLOT
                            + ".." + BagLayout.LAST_TAB_SLOT + " 之间）");
        }
        if (slot < 0 || slot >= container.slots()) {
            return UseItemOutcome.fail(Cmd47ReplyBody.ERROR_BAD_SLOT,
                    "容器 " + itemSpace + " 只有 " + container.slots() + " 格，取不到槽 " + slot);
        }

        InvenItem item = container.get(slot);
        if (item.isEmpty()) {
            return UseItemOutcome.fail(Cmd47ReplyBody.ERROR_NOT_CONSUMABLE,
                    "容器 " + itemSpace + " 槽 " + slot + " 是空的");
        }

        if (!creatureBag) {
            GameScripts scripts = session == null ? null : session.scripts();
            ItemScript script = scripts == null
                    ? null : scripts.item((int) item.itemId()).orElse(null);
            if (script == null) {
                return UseItemOutcome.fail(Cmd47ReplyBody.ERROR_NOT_ENOUGH,
                        "物品 " + item.itemId() + " 不在物品表里");
            }
            if (script.isUnlimitedWaste()) {
                return new UseItemOutcome(0, false, item.stackCount(),
                        "物品 " + item.itemId() + " 是 [unlimited waste]，用了不减");
            }
        }

        int creatureError = CreatureService.useItem(session, item.itemId());
        if (creatureError != 0) {
            return UseItemOutcome.fail(creatureError,
                    "物品 " + item.itemId() + " 是宠物用品，但宠物那边拒绝了（码 "
                            + creatureError + "）");
        }

        long have = item.stackCount();
        if (have < count) {
            container.set(slot, InvenItem.EMPTY);
            markDirtyFor(itemSpace);
            markSlotEmptied(itemSpace, slot);
            return new UseItemOutcome(Cmd47ReplyBody.ERROR_NOT_ENOUGH, true, 0,
                    "槽 " + slot + " 只有 " + have + " 件，用不了 " + count + " 件");
        }
        long left = have - count;
        container.set(slot, left > 0 ? item.withAddInfo(left) : InvenItem.EMPTY);
        markDirtyFor(itemSpace);
        if (left <= 0) {
            markSlotEmptied(itemSpace, slot);
        }
        return new UseItemOutcome(0, true, left,
                "物品 " + item.itemId() + " 用掉 " + count + " 件，还剩 " + left);
    }

    private static boolean usableMainBagSlot(int slot) {
        return slot >= BagLayout.FIRST_ITEM_SLOT && slot <= BagLayout.LAST_TAB_SLOT;
    }

    private void markDirtyFor(int itemSpace) {
        if (itemSpace == ItemListType.LIST_CARGO) {
            markCargoDirty();
        } else {
            markItemsDirty();
        }
    }

    public int deleteItem(int listType, int slot, int itemId, int amount) {
        ItemArray array = containerOf(listType);
        if (array == null || slot < 0 || slot >= array.slots()) {
            return -Cmd18ReplyBody.ERROR_SILENT;
        }
        if (listType == ItemListType.LIST_MAIN && BagLayout.isCurrencySlot(slot)) {
            return -Cmd18ReplyBody.ERROR_SILENT;
        }
        if (listType == ItemListType.LIST_EQUIPPED && slot <= EquipmentSlot.SKIN_AVATAR) {
            return -Cmd18ReplyBody.ERROR_CANNOT_DELETE;
        }
        InvenItem item = array.get(slot);
        if (item.isEmpty()) {
            return -Cmd19ReplyBody.ERROR_CANNOT_MOVE;
        }
        if (itemId != 0 && item.itemId() != itemId) {
            countHack(HackAnalyzer.Site.DELETE_ITEM_MISMATCH, itemId,
                    "要删容器 " + listType + " 第 " + slot + " 格的物品 " + itemId
                            + "，服务端那一格是 " + item.itemId());
            return -Cmd19ReplyBody.ERROR_CANNOT_MOVE;
        }
        boolean wholeSlot = item.itemType() == EQUIPMENT_ITEM_TYPE;
        long have = item.stackCount();
        int take = wholeSlot ? 1 : (int) Math.min(have, Math.max(1, amount));
        ItemHistoryRecorder.record(session, listType, slot, item, -take);
        if (wholeSlot || take >= have) {
            array.set(slot, InvenItem.EMPTY);
            markSlotEmptied(listType, slot);
        } else {
            array.set(slot, item.withAddInfo(have - take));
        }
        if (listType == ItemListType.LIST_CARGO) {
            markCargoDirty();
        } else {
            markItemsDirty();
        }
        return take;
    }

    public int decreaseDurability(int equipSlot) {
        if (items == null || equipSlot < 0 || equipSlot >= items.equipslot().slots()) {
            return -1;
        }
        InvenItem item = items.equipslot().get(equipSlot);
        if (item.isEmpty()) {
            return -1;
        }
        int before = item.endurance();
        if (before <= 0) {
            return 0;
        }
        items.equipslot().set(equipSlot, item.withEndurance(before - 1));
        markItemsDirty();
        return before - 1;
    }

    static void unsealWorn(ItemArray worn, int equipSlot) {
        InvenItem item = worn.get(equipSlot);
        worn.set(equipSlot, item.withAttachType(0));
    }

    private static void swapAcross(ItemArray a, int slotA, ItemArray b, int slotB) {
        InvenItem tmp = a.get(slotA);
        a.set(slotA, b.get(slotB));
        b.set(slotB, tmp);
    }

    private static boolean isForbiddenByOfficial(int srcType, int dstType) {
        return (srcType == ItemListType.LIST_MAIN && dstType == ItemListType.LIST_AVATAR)
                || (srcType == ItemListType.LIST_AVATAR && dstType == ItemListType.LIST_MAIN)
                || (srcType == ItemListType.LIST_MAIN && dstType == ItemListType.LIST_CREATURE)
                || (srcType == ItemListType.LIST_CREATURE
                        && dstType == ItemListType.LIST_MAIN);
    }

    private MoveOutcome swapWithinMainBag(Cmd19MoveItemSpaceRequest req) {
        ItemArray bag = items.inventory();
        if (BagLayout.isCurrencySlot(req.srcSlot()) || BagLayout.isCurrencySlot(req.dstSlot())) {
            return MoveOutcome.reject("虚拟货币格（0/1/2）拖不动");
        }
        BagLayout.Section srcSection = BagLayout.tabAt(req.srcSlot());
        BagLayout.Section dstSection = BagLayout.tabAt(req.dstSlot());
        if (srcSection != null && dstSection != null && srcSection != dstSection) {
            return MoveOutcome.reject("不许跨段：槽 " + req.srcSlot() + " 在" + srcSection.label()
                    + "、槽 " + req.dstSlot() + " 在" + dstSection.label());
        }
        InvenItem srcItem = bag.get(req.srcSlot());
        InvenItem dstItem = bag.get(req.dstSlot());
        if (!fitsSection(dstSection, srcItem) || !fitsSection(srcSection, dstItem)) {
            return MoveOutcome.reject("物品和落点的页签对不上");
        }
        swapSlots(bag, req.srcSlot(), req.dstSlot());
        markItemsDirty();
        return MoveOutcome.done(req.count(), req.dstSlot(),
                "背包内对调 槽" + req.srcSlot() + " ←→ 槽" + req.dstSlot());
    }

    private boolean fitsSection(BagLayout.Section section, InvenItem item) {
        if (section == null || item.isEmpty()) {
            return true;
        }
        GameScripts scripts = session == null ? null : session.scripts();
        if (scripts == null) {
            return true;
        }
        return scripts.item((int) item.itemId())
                .map(script -> BagLayout.of(script) == section)
                .orElse(true);
    }

    private boolean validSlot(int listType, int slot) {
        return slot >= 0 && slot < capacityOf(listType);
    }

    private boolean agreesOnBothEnds(Cmd19MoveItemSpaceRequest req) {
        return agreesOnSlot(req.srcType(), req.srcSlot(), req.srcItemId())
                && agreesOnSlot(req.dstType(), req.dstSlot(), req.dstItemId());
    }

    private boolean agreesOnSlot(int listType, int slot, int itemId) {
        if (itemId == 0) {
            return true;
        }
        int actual = itemIdAt(listType, slot);
        if (actual == itemId) {
            return true;
        }
        return false;
    }

    private static void swapSlots(ItemArray array, int a, int b) {
        InvenItem tmp = array.get(a);
        array.set(a, array.get(b));
        array.set(b, tmp);
    }
}
