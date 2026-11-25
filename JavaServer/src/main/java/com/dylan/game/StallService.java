package com.dylan.game;

import com.dylan.protocol.body.ItemGainReply;
import com.dylan.protocol.body.ItemListType;
import com.dylan.protocol.body.Noti13ItemListBody;
import com.dylan.protocol.body.PrivateStorePackets;
import com.dylan.protocol.body.PrivateStorePackets.Shelf;
import com.dylan.protocol.body.PrivateStorePackets.StoreView;
import com.dylan.store.blob.InvenItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 摆摊（个人商店，A4 CMD 85–90 / NOTI 78–84）。官方 DF60ACT4 和 A21 都没实现，协议全部来自 A4 客户端读写包函数，
 * 见 07_协议检修/社交/摆摊.md。
 *
 * 物品不离开卖家背包：货架只记"背包第几格、卖几件、单价"，成交时再从卖家那格扣、给买家加，
 * 摊主断线 / 收摊什么都不用退。跨会话改两个人的背包，全部成交走 {@link #LOCK}。
 */
public final class StallService {
    private StallService() {
    }

    static final Object LOCK = new Object();

    /** 错误码（客户端 0088AFD0 / 0088B0A0 / 0088B200 / 0088B290 的文本）。 */
    static final int E_RETRY = 1;
    static final int E_BAG_FULL = 4;
    static final int E_NO_GOLD = 10;
    static final int E_NO_ITEM = 17;
    static final int E_ALREADY_OPEN = 20;
    static final int E_NO_STORE = 58;
    static final int E_BAD_STORE = 57;
    /** 「此区域无法开设个人商店」（0088B0A0 开摊 88 的 62）。 */
    static final int E_AREA = 62;
    /** 「在物品已锁定状态下，您无法进行当前操作」（0088B2D0 cmp 0x97 → 文本 0xA4F8）。 */
    static final int E_ITEM_LOCKED = 151;

    static final class Stall {
        final GameSession seller;
        final int town, area, x, y;
        volatile String title = "";
        volatile boolean open;
        final Map<Integer, Entry> shelf = new TreeMap<>();
        final Set<GameSession> viewers = ConcurrentHashMap.newKeySet();

        Stall(GameSession seller, int town, int area, int x, int y) {
            this.seller = seller;
            this.town = town;
            this.area = area;
            this.x = x;
            this.y = y;
        }

        StoreView view() {
            return new StoreView(seller.uniqueId(), title, open, town, area, x, y);
        }
    }

    /** 货架一格：卖家主背包 bagSlot 那格的 itemId，剩 count 件，单价 price。 */
    record Entry(int bagSlot, int itemId, int count, long price) {
    }

    private static final Map<GameSession, Stall> STALLS = new ConcurrentHashMap<>();
    private static final Map<GameSession, Stall> VIEWING = new ConcurrentHashMap<>();

    // ---- CMD 88 开摊 ----
    public static void create(GameSession s, PrivateStorePackets.CreateRequest req) {
        if (!s.hasCharacter()) {
            s.reply(88, PrivateStorePackets.fail(E_RETRY));
            return;
        }
        if (STALLS.containsKey(s)) {
            s.reply(88, PrivateStorePackets.fail(E_ALREADY_OPEN));
            return;
        }
        int area = req.area() == 0xFF ? s.areaId() : req.area();
        if (!stallAllowed(s, req.village(), area)) {
            s.reply(88, PrivateStorePackets.fail(E_AREA));
            return;
        }
        Stall st = new Stall(s, req.village(), area, req.x(), req.y());
        STALLS.put(s, st);
        s.reply(88, PrivateStorePackets.ok());
        byte[] noti = PrivateStorePackets.notiCreate(s.uniqueId(), st.town, st.area, st.x, st.y);
        for (GameSession o : SessionRegistry.inArea(st.town, st.area, s)) {
            o.sendNow(PrivateStorePackets.NOTI_CREATE, noti);
        }
    }

    /**
     * 照台服 CPrivateStore::CreatePrivateStore / IsAreaNearEntranceDungeon：决斗频道、区域类型 4
     * （.twn `[dungeon gate]` 城门口）不能开摊，回 0x3E。A4 另有 `[gate]` = 赛丽亚房间（每人一间、别人看不见，
     * SessionRegistry.inArea），摆了也没人能买，一并拒掉。客户端发来的村子 / 区域和服务端记的都要过。
     */
    private static boolean stallAllowed(GameSession s, int village, int area) {
        if (s.pvpChannel()) {
            return false;
        }
        var towns = s.scripts() == null ? null : s.scripts().towns();
        if (towns == null) {
            return true;
        }
        return areaAllowsStall(towns, village, area) && areaAllowsStall(towns, s.townId(), s.areaId());
    }

    private static boolean areaAllowsStall(com.dylan.pvf.script.TownTable towns, int town, int area) {
        return towns.area(town, area)
                .map(a -> !a.isGate() && !a.isDungeonGate())
                .orElse(true);
    }

    // ---- CMD 90 开张 ----
    public static void display(GameSession s, PrivateStorePackets.CompleteDisplayRequest req) {
        Stall st = STALLS.get(s);
        if (st == null) {
            s.reply(90, PrivateStorePackets.fail(E_NO_STORE));
            return;
        }
        Map<Integer, Entry> shelf = new TreeMap<>();
        for (var e : req.items()) {
            if (e.itemSpace() != ItemListType.LIST_MAIN || e.price() <= 0
                    || BagLayout.isCurrencySlot(e.invenSlot())
                    || e.invenSlot() < 0 || e.invenSlot() >= s.inventory().containerOf(ItemListType.LIST_MAIN).slots()) {
                failDisplay(s, E_NO_ITEM);
                return;
            }
            InvenItem item = s.inventory().containerOf(ItemListType.LIST_MAIN).get(e.invenSlot());
            if (s.itemLock().isLocked(item)) {
                // 锁定中、解锁倒计时中都算锁着（ItemLockService.isLocked），和交易 / 邮寄 / 出售一样不让上架
                failDisplay(s, E_ITEM_LOCKED);
                return;
            }
            int have = item.isEmpty() ? 0 : (item.equipable() ? 1 : (int) item.stackCount());
            int count = item.equipable() ? 1 : e.count();
            if (have <= 0 || count <= 0 || count > have) {
                failDisplay(s, E_NO_ITEM);
                return;
            }
            shelf.put(e.shelfIndex(), new Entry(e.invenSlot(), (int) item.itemId(), count, e.price()));
        }
        String title = com.dylan.protocol.body.Noti12MessageBody.fit(req.title(), PrivateStorePackets.TITLE_MAX_BYTES);
        synchronized (LOCK) {
            st.shelf.clear();
            st.shelf.putAll(shelf);
            st.title = title;
            st.open = true;
        }
        s.reply(90, PrivateStorePackets.ok());
        byte[] noti = PrivateStorePackets.notiDisplay(s.uniqueId(), title);
        for (GameSession o : SessionRegistry.inArea(st.town, st.area, s)) {
            o.sendNow(PrivateStorePackets.NOTI_DISPLAY, noti);
        }
    }

    /** 开张失败客户端先关摆货窗口（0041A406），货架上那几件在窗口里就丢了，和收摊一样重发背包。 */
    private static void failDisplay(GameSession s, int error) {
        s.reply(90, PrivateStorePackets.fail(error));
        restoreBag(s, "开张失败", true);
    }

    // ---- CMD 89 收摊 ----
    public static void remove(GameSession s) {
        Stall st = close(s, false);
        if (st == null) {
            s.reply(89, PrivateStorePackets.fail(E_NO_STORE));
            return;
        }
        s.reply(89, PrivateStorePackets.ok());
        restoreBag(s, st, "收摊");
    }

    /**
     * 摆货时客户端把物品从背包窗口挪到货架窗口（本地挪，服务端背包没动），收摊关窗口时不会放回去。
     * 2026-10-28 实测：撤销摊位后背包里东西全部不见，小退才回来。收摊后整栏重发主背包（NOTI 13/0 + 锁列表）。
     */
    static final String MSG_RETURNED = "个人商店的物品已返还到物品栏";

    static void restoreBag(GameSession s, Stall closed, String why) {
        // 货架卖空后客户端自己发 CMD 89（0088B7CF），那时没东西可还，不提示
        restoreBag(s, why, !closed.open || !closed.shelf.isEmpty());
    }

    private static void restoreBag(GameSession s, String why, boolean notify) {
        if (s.isActive() && s.hasCharacter()) {
            s.inventory().sendItemList(ItemListType.LIST_MAIN, why);
            if (notify) {
                // 客户端文本表没有这句（dnf.str 1321–1355 都是失败提示），走系统消息
                ChatService.system(s, MSG_RETURNED);
            }
            s.flush();
        }
    }

    /**
     * 收摊：同区域的人收 NOTI 79，正在看的买家收 NOTI 80（"个人商店已被取消"）。
     * 断线 / 换区域 / 进副本 / 回选角也走这里（forced = true，不回 REPLY）。
     */
    public static Stall close(GameSession s, boolean forced) {
        Stall st = STALLS.remove(s);
        stopViewing(s);
        if (st == null) {
            return null;
        }
        for (GameSession v : st.viewers) {
            VIEWING.remove(v, st);
            v.sendNow(PrivateStorePackets.NOTI_FORCE_REMOVE, new byte[0]);
        }
        byte[] noti = PrivateStorePackets.notiRemove(s.uniqueId());
        for (GameSession o : SessionRegistry.inArea(st.town, st.area, s)) {
            o.sendNow(PrivateStorePackets.NOTI_REMOVE, noti);
        }
        return st;
    }

    private static void stopViewing(GameSession s) {
        Stall seen = VIEWING.remove(s);
        if (seen != null) {
            seen.viewers.remove(s);
        }
    }

    /** 进区域时补发这个区域里别人的摊（NOTI 84；客户端要求角色和头像已存在，所以在外观 NOTI 2/0 之后发）。 */
    public static void sendAreaStalls(GameSession s, int town, int area) {
        List<StoreView> views = new ArrayList<>();
        for (Stall st : STALLS.values()) {
            if (st.seller != s && st.town == town && st.area == area && st.seller.isActive()) {
                views.add(st.view());
            }
        }
        if (!views.isEmpty()) {
            s.sendNow(PrivateStorePackets.NOTI_LIST, PrivateStorePackets.notiList(views));
        }
    }

    // ---- CMD 86 进摊 / CMD 87 离开 ----
    public static void enter(GameSession s, int sellerUid) {
        GameSession seller = SessionRegistry.findInWorld(sellerUid);
        Stall st = seller == null ? null : STALLS.get(seller);
        if (st == null || !st.open || seller == s) {
            // REPLY 86 失败分支只关等待框（0041A1F0），不读错误码
            s.reply(86, new byte[]{0});
            return;
        }
        stopViewing(s);
        List<Shelf> shelves = new ArrayList<>();
        synchronized (LOCK) {
            for (var e : st.shelf.entrySet()) {
                InvenItem item = seller.inventory().containerOf(ItemListType.LIST_MAIN).get(e.getValue().bagSlot());
                shelves.add(new Shelf(e.getKey(), e.getValue().price(), e.getValue().itemId(),
                        e.getValue().count(), item.itemAttr(), item.endurance(), (int) item.enchant()));
            }
            st.viewers.add(s);
            VIEWING.put(s, st);
        }
        s.reply(86, PrivateStorePackets.replyEnter(sellerUid, shelves));
    }

    public static void exit(GameSession s) {
        stopViewing(s);
        s.reply(87, PrivateStorePackets.ok());
    }

    // ---- CMD 85 买 ----
    public static void buy(GameSession s, PrivateStorePackets.BuyRequest req) {
        Stall st = VIEWING.get(s);
        if (st == null || STALLS.get(st.seller) != st || !st.open) {
            s.reply(85, PrivateStorePackets.fail(E_NO_STORE));
            return;
        }
        GameSession seller = st.seller;
        int sold;
        int itemId;
        long price;
        Noti13ItemListBody.Item landed;
        int bagSlot;
        synchronized (LOCK) {
            Entry e = st.shelf.get(req.shelfIndex());
            if (e == null) {
                s.reply(85, PrivateStorePackets.fail(E_NO_ITEM));
                return;
            }
            var sellerBag = seller.inventory().containerOf(ItemListType.LIST_MAIN);
            InvenItem item = sellerBag.get(e.bagSlot());
            if (item.isEmpty() || item.itemId() != e.itemId()) {
                st.shelf.remove(req.shelfIndex());
                s.reply(85, PrivateStorePackets.fail(E_NO_ITEM));
                return;
            }
            int have = item.equipable() ? 1 : (int) Math.min(item.stackCount(), e.count());
            sold = item.equipable() ? 1 : Math.min(Math.max(1, req.count()), have);
            price = e.price() * sold;
            if (s.inventory().money() < price) {
                s.reply(85, PrivateStorePackets.fail(E_NO_GOLD));
                return;
            }
            if (seller.inventory().money() + price > 0xFFFF_FFFFL) {
                s.reply(85, PrivateStorePackets.fail(22));
                return;
            }
            InvenItem bought = item.equipable() ? item : item.withAddInfo(sold);
            int slot = s.inventory().insertExisting(s.inventory().containerOf(ItemListType.LIST_MAIN), bought);
            if (slot < 0) {
                s.reply(85, PrivateStorePackets.fail(E_BAG_FULL));
                return;
            }
            seller.inventory().deleteItem(ItemListType.LIST_MAIN, e.bagSlot(), e.itemId(), sold);
            s.inventory().setMoney(s.inventory().money() - price);
            seller.inventory().setMoney(seller.inventory().money() + price);
            int left = e.count() - sold;
            if (left <= 0) {
                st.shelf.remove(req.shelfIndex());
            } else {
                st.shelf.put(req.shelfIndex(), new Entry(e.bagSlot(), e.itemId(), left, e.price()));
            }
            itemId = e.itemId();
            bagSlot = e.bagSlot();
            landed = s.inventory().itemAt(ItemListType.LIST_MAIN, slot);
            s.inventory().save();
            seller.inventory().save();
        }
        s.reply(85, PrivateStorePackets.replyBought(s.inventory().money(), s.winPoint(), s.cera(),
                landed.slot(), itemId, sold, landed.itemAttr(), landed.durability(), landed.enchant()));
        // REPLY 85 只入包。获得窗是 REPLY 171（DNF.exe 0041B5D9），见 ItemGainReply。
        s.reply(ItemGainReply.PACKET_ID, ItemGainReply.of(java.util.List.of(
                new ItemGainReply.Line(itemId, sold))));
        s.flush();
        // 卖家：NOTI 82 扣货架 + 改金币（货架空了客户端自己发 CMD 89），再刷那一格背包
        seller.sendNow(PrivateStorePackets.NOTI_SOLD, PrivateStorePackets.notiSold(seller.inventory().money(),
                itemId, req.shelfIndex(), sold, s.characName(), price, 0));
        seller.inventory().sendItemUpdate(ItemListType.LIST_MAIN, "摆摊卖出", bagSlot);
        seller.flush();
        byte[] dec = PrivateStorePackets.notiItem(req.shelfIndex(), sold);
        for (GameSession v : st.viewers) {
            if (v != s) {
                v.sendNow(PrivateStorePackets.NOTI_ITEM, dec);
            }
        }
    }
}
