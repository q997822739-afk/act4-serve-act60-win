package com.dylan.game;

import com.dylan.protocol.body.Cmd19MoveItemSpaceRequest;
import com.dylan.protocol.body.Cmd19ReplyBody;
import com.dylan.protocol.body.ItemGainReply;
import com.dylan.protocol.body.ItemListType;
import com.dylan.protocol.body.TradePackets;
import com.dylan.store.blob.InvenItem;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家间交易（A4 CMD 10/11 kind 1、CMD 19 进出 list 4、CMD 26；NOTI 7/8/15/16/17/18）。
 * 协议见 07_协议检修/社交/交易.md，流程照 A21 ItemTradeHandler / ItemTradeSession。
 *
 * 和摆摊一样东西不离开背包：交易栏只记"背包第几格、几件"，两边都确认（state 3）时在 {@link StallService#LOCK}
 * 下核对 + 一次性交换；任何一步对不上整笔取消，取消什么都不用退。
 */
public final class TradeService {
    private TradeService() {
    }

    static final int STATE_UNLOCK = 0;
    static final int STATE_LOCK = 1;
    static final int STATE_CLOSE = 2;
    static final int STATE_CONFIRM = 3;
    static final int STATE_CONFIRM_CANCEL = 4;

    /** 交易栏一格：主背包 bagSlot 的 itemId × count；bagSlot = SLOT_GOLD 时 count 是金额。 */
    record Offer(int bagSlot, int itemId, long count) {
    }

    static final class Trade {
        final GameSession[] side = new GameSession[2];
        final int[] state = new int[2];
        @SuppressWarnings("unchecked")
        final Map<Integer, Offer>[] offers = new Map[]{new TreeMap<>(), new TreeMap<>()};
        boolean done;

        int sideOf(GameSession s) {
            return side[0] == s ? 0 : 1;
        }
    }

    /** 被邀请人 → 邀请人（还没接受）。 */
    private static final Map<GameSession, GameSession> INVITES = new ConcurrentHashMap<>();
    private static final Map<GameSession, Trade> TRADES = new ConcurrentHashMap<>();

    public static boolean trading(GameSession s) {
        return TRADES.containsKey(s);
    }

    // ---- CMD 10 kind 1：邀请 ----
    public static void request(GameSession s, TradePackets.PeerRequest req) {
        GameSession target = SessionRegistry.findInWorld(req.uid());
        if (target == null || target == s || !target.hasCharacter()) {
            s.reply(10, TradePackets.fail(TradePackets.E_GONE, TradePackets.KIND_TRADE));
            return;
        }
        if (TRADES.containsKey(target) || TRADES.containsKey(s)) {
            s.reply(10, TradePackets.fail(TradePackets.E_BUSY_TRADE, TradePackets.KIND_TRADE));
            return;
        }
        INVITES.put(target, s);
        s.reply(10, TradePackets.ok());
        target.sendNow(TradePackets.NOTI_REQUEST_PEER, TradePackets.invite(s.uniqueId(), req.peer()));
    }

    // ---- CMD 11 kind 1：接受 / 拒绝 ----
    public static void respond(GameSession s, TradePackets.PeerRequest req) {
        GameSession inviter = INVITES.remove(s);
        if (inviter == null || inviter.uniqueId() != req.uid() || !inviter.isActive()) {
            s.reply(11, TradePackets.fail(TradePackets.E_GONE, TradePackets.KIND_TRADE));
            return;
        }
        if (req.refuse() != 0) {
            inviter.replyNow(11, TradePackets.fail(TradePackets.E_REFUSED, TradePackets.KIND_TRADE));
            return;
        }
        if (TRADES.containsKey(inviter) || TRADES.containsKey(s)) {
            s.reply(11, TradePackets.fail(TradePackets.E_BUSY_TRADE, TradePackets.KIND_TRADE));
            return;
        }
        Trade t = new Trade();
        t.side[0] = inviter;
        t.side[1] = s;
        TRADES.put(inviter, t);
        TRADES.put(s, t);
        s.reply(11, TradePackets.acceptedReply(inviter.uniqueId()));
        inviter.sendNow(TradePackets.NOTI_RESPONSE_PEER, TradePackets.acceptedNoti(s.uniqueId()));
    }

    /**
     * 交易栏格号：0 = 金币，物品从 3 起到 26（和主背包一样，0–2 是货币位）。
     * 客户端放物品（DNF.exe 00476F2C..00476F6F）只在本地查 3..0x1A 有没有空格，然后 CMD 19 的 dst 恒写 0，
     * 由服务端分配格号，客户端照 REPLY 19 的 dst 放。以前从 1 分配，1 / 2 撞货币位，客户端放不上去。
     */
    static final int FIRST_ITEM_SLOT = 3;
    static final int LAST_ITEM_SLOT = 0x1A;

    // ---- CMD 19 进出交易栏（list 4）。不是交易中的 CMD 19 返回 false，照常走背包 ----
    public static boolean move(GameSession s, Cmd19MoveItemSpaceRequest req) {
        boolean in = req.dstType() == ItemListType.LIST_TRADE && req.srcType() == ItemListType.LIST_MAIN;
        boolean out = req.srcType() == ItemListType.LIST_TRADE;
        if (!in && !out) {
            return false;
        }
        Trade t = TRADES.get(s);
        if (t == null) {
            s.reply(19, Cmd19ReplyBody.rejected(Cmd19ReplyBody.ERROR_CANNOT_MOVE, req.srcType(), req.dstType()));
            return true;
        }
        int me = t.sideOf(s);
        GameSession other = t.side[1 - me];
        byte[] toOther;
        int count;
        int srcSlot = req.srcSlot();
        int dstSlot = req.dstSlot();
        synchronized (StallService.LOCK) {
            Map<Integer, Offer> mine = t.offers[me];
            if (t.done || t.state[me] != STATE_UNLOCK) {
                s.reply(19, Cmd19ReplyBody.rejected(Cmd19ReplyBody.ERROR_CANNOT_MOVE, req.srcType(), req.dstType()));
                return true;
            }
            if (in) {
                long want = Math.max(1, req.count() & 0xFFFF_FFFFL);
                if (req.srcSlot() == BagLayout.SLOT_GOLD) {
                    Offer old = mine.get(0);
                    long total = (old == null ? 0 : old.count()) + want;
                    if (total > s.inventory().money()) {
                        s.reply(19, Cmd19ReplyBody.rejected(Cmd19ReplyBody.ERROR_CANNOT_MOVE, req.srcType(), req.dstType()));
                        return true;
                    }
                    mine.put(0, new Offer(BagLayout.SLOT_GOLD, 0, total));
                    dstSlot = 0;
                    count = (int) want;
                    toOther = TradePackets.item(0, 0, total, 0, 0, 0);
                } else {
                    var bag = s.inventory().containerOf(ItemListType.LIST_MAIN);
                    if (BagLayout.isCurrencySlot(srcSlot) || srcSlot < 0 || srcSlot >= bag.slots()) {
                        s.reply(19, Cmd19ReplyBody.rejected(Cmd19ReplyBody.ERROR_CANNOT_MOVE, req.srcType(), req.dstType()));
                        return true;
                    }
                    InvenItem item = bag.get(srcSlot);
                    long offered = mine.values().stream().filter(o -> o.bagSlot() == srcSlot).mapToLong(Offer::count).sum();
                    long have = item.isEmpty() ? 0 : (item.equipable() ? 1 : item.stackCount());
                    long take = item.equipable() ? 1 : Math.min(want, have - offered);
                    int free = -1;
                    for (int i = FIRST_ITEM_SLOT; i <= LAST_ITEM_SLOT && free < 0; i++) {
                        if (!mine.containsKey(i)) {
                            free = i;
                        }
                    }
                    if (item.isEmpty() || take <= 0 || offered >= have || free < 0
                            || (req.srcItemId() != 0 && req.srcItemId() != item.itemId())) {
                        s.reply(19, Cmd19ReplyBody.rejected(Cmd19ReplyBody.ERROR_CANNOT_MOVE, req.srcType(), req.dstType()));
                        return true;
                    }
                    mine.put(free, new Offer(srcSlot, (int) item.itemId(), take));
                    dstSlot = free;
                    count = (int) take;
                    toOther = TradePackets.item(free, (int) item.itemId(), take, item.itemAttr(),
                            item.endurance(), (int) item.enchant());
                }
            } else {
                Offer o = mine.remove(srcSlot);
                if (o == null) {
                    s.reply(19, Cmd19ReplyBody.rejected(Cmd19ReplyBody.ERROR_CANNOT_MOVE, req.srcType(), req.dstType()));
                    return true;
                }
                count = (int) o.count();
                dstSlot = o.bagSlot();
                toOther = TradePackets.cleared(srcSlot);
            }
        }
        s.reply(19, Cmd19ReplyBody.accepted(req.srcType(), srcSlot, count, req.dstType(), dstSlot));
        other.sendNow(TradePackets.NOTI_CHANGE_ITEM, toOther);
        return true;
    }

    // ---- CMD 26 锁定 / 确认 / 取消 ----
    public static void state(GameSession s, int state) {
        Trade t = TRADES.get(s);
        if (t == null) {
            if (com.dylan.net.ServerConfig.A4) {
                // 依据：A4 官方实抓（sweep_town CMD 26，城里包体全 0）回 REPLY 26 `00 11`，A5 原来静默；
                // catalog_cmd「命令 26：已拒绝未实现的物品交易状态，物品保持不变」。
                // 客户端 00476AA0 的锁定/最终确认都发 CMD 26，收不到回包就一直等。只补回包，不改交易的任何状态。
                s.reply(TradePackets.PACKET_ID_STATE, TradePackets.stateRejected());
                s.flush();
            }
            return;
        }
        int me = t.sideOf(s);
        if (state == STATE_CLOSE || state == STATE_CONFIRM_CANCEL) {
            cancel(s, "客户端取消（state " + state + "）");
            return;
        }
        boolean commit;
        synchronized (StallService.LOCK) {
            if (t.done || (state != STATE_LOCK && state != STATE_UNLOCK && state != STATE_CONFIRM)) {
                return;
            }
            if (state == STATE_CONFIRM && (t.state[0] < STATE_LOCK || t.state[1] < STATE_LOCK)) {
                return;
            }
            t.state[me] = state;
            commit = t.state[0] == STATE_CONFIRM && t.state[1] == STATE_CONFIRM;
            if (state == STATE_UNLOCK && t.state[1 - me] == STATE_CONFIRM) {
                // 一边解锁 → 对方已按的最终确认作废，退回锁定
                t.state[1 - me] = STATE_LOCK;
            }
        }
        if (commit) {
            finish(t);
            return;
        }
        if (state != STATE_CONFIRM) {
            byte[] noti = TradePackets.state(s.uniqueId(), state);
            t.side[0].sendNow(TradePackets.NOTI_STATE, noti);
            t.side[1].sendNow(TradePackets.NOTI_STATE, noti);
        }
    }

    /** 两边都 state 3：核对 → 在背包副本上试放 → 真交换。任何一步不行整笔取消。 */
    private static void finish(Trade t) {
        boolean ok = false;
        java.util.List<ItemGainReply.Line> to0 = java.util.List.of();
        java.util.List<ItemGainReply.Line> to1 = java.util.List.of();
        synchronized (StallService.LOCK) {
            if (t.done) {
                return;
            }
            t.done = true;
            ok = check(t, 0) && check(t, 1) && fits(t, 0) && fits(t, 1);
            if (ok) {
                java.util.List<InvenItem> moving0 = takeOut(t, 0);
                java.util.List<InvenItem> moving1 = takeOut(t, 1);
                long gold0 = gold(t, 0);
                long gold1 = gold(t, 1);
                GameSession a = t.side[0];
                GameSession b = t.side[1];
                a.inventory().setMoney(a.inventory().money() - gold0 + gold1);
                b.inventory().setMoney(b.inventory().money() - gold1 + gold0);
                for (InvenItem it : moving0) {
                    b.inventory().insertExisting(b.inventory().containerOf(ItemListType.LIST_MAIN), it);
                }
                for (InvenItem it : moving1) {
                    a.inventory().insertExisting(a.inventory().containerOf(ItemListType.LIST_MAIN), it);
                }
                a.inventory().save();
                b.inventory().save();
                // moving0 是 0 交给 1 的，弹窗给接收方。金币不进这个窗口（NOTI 13 会刷金币）。
                to1 = gains(moving0);
                to0 = gains(moving1);
            }
        }
        end(t, ok, to0, to1);
    }

    private static java.util.List<ItemGainReply.Line> gains(java.util.List<InvenItem> items) {
        java.util.List<ItemGainReply.Line> lines = new java.util.ArrayList<>(items.size());
        for (InvenItem it : items) {
            int count = it.equipable() ? 1 : (int) Math.min(Math.max(it.stackCount(), 1), Integer.MAX_VALUE);
            lines.add(new ItemGainReply.Line((int) it.itemId(), count));
        }
        return lines;
    }

    private static long gold(Trade t, int side) {
        Offer g = t.offers[side].get(0);
        return g == null ? 0 : g.count();
    }

    /** 交易栏里记的东西现在还在背包里、金币够。 */
    private static boolean check(Trade t, int side) {
        GameSession s = t.side[side];
        if (!s.isActive() || gold(t, side) > s.inventory().money()) {
            return false;
        }
        var bag = s.inventory().containerOf(ItemListType.LIST_MAIN);
        Map<Integer, Long> perSlot = new TreeMap<>();
        for (Offer o : t.offers[side].values()) {
            if (o.bagSlot() != BagLayout.SLOT_GOLD) {
                perSlot.merge(o.bagSlot(), o.count(), Long::sum);
                InvenItem it = bag.get(o.bagSlot());
                if (it.isEmpty() || it.itemId() != o.itemId()) {
                    return false;
                }
            }
        }
        for (var e : perSlot.entrySet()) {
            InvenItem it = bag.get(e.getKey());
            if ((it.equipable() ? 1 : it.stackCount()) < e.getValue()) {
                return false;
            }
        }
        long other = gold(t, 1 - side);
        return s.inventory().money() - gold(t, side) + other <= 0xFFFF_FFFFL;
    }

    /** 对方给的东西放得进 side 的背包（在副本上试放）。 */
    private static boolean fits(Trade t, int side) {
        GameSession s = t.side[side];
        var real = s.inventory().containerOf(ItemListType.LIST_MAIN);
        var copy = com.dylan.store.blob.ItemArray.empty(real.slots());
        for (int i = 0; i < real.slots(); i++) {
            copy.set(i, real.get(i));
        }
        var srcBag = t.side[1 - side].inventory().containerOf(ItemListType.LIST_MAIN);
        for (Offer o : t.offers[1 - side].values()) {
            if (o.bagSlot() == BagLayout.SLOT_GOLD) {
                continue;
            }
            InvenItem it = srcBag.get(o.bagSlot());
            InvenItem moving = it.equipable() ? it : it.withAddInfo(o.count());
            if (s.inventory().insertExisting(copy, moving) < 0) {
                return false;
            }
        }
        return true;
    }

    /** 从 side 的背包扣掉交易栏里的东西，返回要给对方的物品。 */
    private static java.util.List<InvenItem> takeOut(Trade t, int side) {
        GameSession s = t.side[side];
        var bag = s.inventory().containerOf(ItemListType.LIST_MAIN);
        java.util.List<InvenItem> out = new java.util.ArrayList<>();
        for (Offer o : t.offers[side].values()) {
            if (o.bagSlot() == BagLayout.SLOT_GOLD) {
                continue;
            }
            InvenItem it = bag.get(o.bagSlot());
            out.add(it.equipable() ? it : it.withAddInfo(o.count()));
            s.inventory().deleteItem(ItemListType.LIST_MAIN, o.bagSlot(), o.itemId(), (int) o.count());
        }
        return out;
    }

    /** 取消：断线 / 换区域 / 进副本 / 回选角 / 客户端关窗口都走这里；也清掉挂着的邀请。 */
    public static void cancel(GameSession s, String why) {
        INVITES.remove(s);
        INVITES.values().removeIf(v -> v == s);
        Trade t = TRADES.get(s);
        if (t == null) {
            return;
        }
        synchronized (StallService.LOCK) {
            if (t.done) {
                return;
            }
            t.done = true;
        }
        end(t, false, java.util.List.of(), java.util.List.of());
    }

    /** NOTI 18 成交 / NOTI 16 取消（n = 0）→ 重发主背包（含金币），两边都发。成交后再补 REPLY 171 获得窗。 */
    private static void end(Trade t, boolean committed,
                            java.util.List<ItemGainReply.Line> to0,
                            java.util.List<ItemGainReply.Line> to1) {
        TRADES.remove(t.side[0], t);
        TRADES.remove(t.side[1], t);
        java.util.List<java.util.List<ItemGainReply.Line>> gained = java.util.List.of(to0, to1);
        for (int i = 0; i < t.side.length; i++) {
            GameSession s = t.side[i];
            if (!s.isActive()) {
                continue;
            }
            s.sendNow(committed ? TradePackets.NOTI_FINISH : TradePackets.NOTI_CANCEL, TradePackets.closed());
            s.inventory().sendItemList(ItemListType.LIST_MAIN, committed ? "交易成交" : "交易取消");
            java.util.List<ItemGainReply.Line> lines = gained.get(i);
            if (committed && lines != null && !lines.isEmpty()) {
                s.replyNow(ItemGainReply.PACKET_ID, ItemGainReply.of(lines));
            }
            s.flush();
        }
    }
}
