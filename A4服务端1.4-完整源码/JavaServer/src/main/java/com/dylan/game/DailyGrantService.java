package com.dylan.game;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Cmd220ShopCoinEventReply;
import com.dylan.protocol.body.ItemListType;
import com.dylan.protocol.body.Noti191ShopCoinEventBody;
import com.dylan.pvf.script.ItemScript;
import com.dylan.store.GameStore;

import java.time.Instant;
import java.time.ZoneId;

public final class DailyGrantService {
    private static final int COIN_ITEM_ID = 1;

    private static final int DEFAULT_COIN_PER_DAY = 1;

    private DailyGrantService() {
    }

    public static void onSelectCharacter(GameSession s, int characNo) {
        topUpTicket(s, characNo, false, "选角");
        sendOpenflag(s, characNo, true, "选角");
    }

    private static void sendOpenflag(GameSession s, int characNo, boolean always, String why) {
        if (characNo <= 0) {
            return;
        }
        var inv = s.inventory();
        if (inv == null || inv.inventory() == null) {
            return;
        }
        var row = s.character();
        int level = row == null ? 1 : row.lev();
        boolean claimable = claimable(s, characNo, level);
        if (!claimable && !always) {
            return;
        }
        s.send(Noti191ShopCoinEventBody.PACKET_ID,
                (claimable ? Noti191ShopCoinEventBody.claimable()
                           : Noti191ShopCoinEventBody.done()).toBytes());
    }

    private static boolean claimable(GameSession s, int characNo, int level) {
        if (alreadyClaimed(s.store(), characNo)) {
            return false;
        }
        if (ServerConfig.A4) {
            return true;   // A4 官方：只看今天领没领（假人有 57 个复活币，进世界 NOTI 178 仍是 00 可领）
        }
        var inv = s.inventory();
        return inv.inventory().coin() < wantCoin(s, level);
    }

    private static boolean alreadyClaimed(GameStore store, int characNo) {
        if (store == null) {
            return false;
        }
        try {
            Instant last = store.dailyGrant()
                    .lastDailyGrant(characNo, COIN_ITEM_ID).orElse(Instant.EPOCH);
            return !periodStart().isAfter(last);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * A4 的「领取免费复活币」是 CMD 190 / REPLY 190 / NOTI 178，与 ACT5 的 CMD 220 / REPLY 220 / NOTI 191
     * 同一套包体（REPLY {@code 01 <给了几个>}，NOTI 1 字节 opencoin：0 可领、1 已领）。官方满级假人实抓：
     * <pre>
     *   第 1 次：REPLY 190 {01 01} → NOTI 13/0 整个主背包 → NOTI 192 {00 00} → NOTI 178 {01}
     *   第 2 次：只有 NOTI 178 {01}（不回 REPLY 190）
     * </pre>
     * A4 没有这条 handler 时客户端一直等 REPLY 190，商城界面卡死。
     */
    public static final int A4_CMD_CLAIM = 190;
    public static final int A4_NOTI_OPENFLAG = 178;

    public static void claim(GameSession s) {
        int characNo = s.characNo();
        if (characNo <= 0 || s.inventory() == null || s.inventory().inventory() == null) {
            if (ServerConfig.A4) {
                s.send(A4_NOTI_OPENFLAG, Noti191ShopCoinEventBody.done().toBytes());
                s.flush();
            }
            return;
        }
        var inv = s.inventory();
        int want = wantCoin(s, 1);
        long have = inv.inventory().coin();
        // ACT5：补到 want 个。A4 官方：每天直接 +want 个（假人身上已有 57 个，领完 58）
        int give = ServerConfig.A4 ? want : topUpAmount(have, want);

        if (give <= 0 || alreadyClaimed(s.store(), characNo)) {
            markGranted(s.store(), characNo, COIN_ITEM_ID, periodStart());
            if (ServerConfig.A4) {
                s.send(A4_NOTI_OPENFLAG, Noti191ShopCoinEventBody.done().toBytes());
                s.flush();
                return;
            }
            s.reply(Cmd220ShopCoinEventReply.PACKET_ID,
                    Cmd220ShopCoinEventReply.nothing().toBytes());
            return;
        }
        if (give >= Cmd220ShopCoinEventReply.GIVE_LIMIT) {
            return;
        }

        inv.setCoins(ServerConfig.A4 ? have + give : want, inv.inventory().payCoin(), inv.inventory().eventCoin());
        inv.save();
        markGranted(s.store(), characNo, COIN_ITEM_ID, periodStart());
        if (ServerConfig.A4) {
            s.reply(A4_CMD_CLAIM, new Cmd220ShopCoinEventReply(give).toBytes());
            inv.sendItemList(ItemListType.LIST_MAIN, "免费复活币");
            s.send(A4_NOTI_OPENFLAG, Noti191ShopCoinEventBody.done().toBytes());
            s.flush();
            return;
        }
        s.reply(Cmd220ShopCoinEventReply.PACKET_ID,
                new Cmd220ShopCoinEventReply(give).toBytes());
        inv.sendItemUpdate(ItemListType.LIST_MAIN, "免费复活币", BagLayout.SLOT_COIN);
        s.flush();
    }

    /** A4 进世界时 NOTI 2/0 之后发一次 NOTI 178（官方：今天没领过 00，领过 01）。 */
    static void sendA4Openflag(GameSession s) {
        var row = s.character();
        int level = row == null ? 1 : row.lev();
        boolean claimable = s.characNo() > 0 && s.inventory() != null && s.inventory().inventory() != null
                && claimable(s, s.characNo(), level);
        s.send(A4_NOTI_OPENFLAG, (claimable ? Noti191ShopCoinEventBody.claimable()
                : Noti191ShopCoinEventBody.done()).toBytes());
    }

    static int wantCoin(GameSession s, int level) {
        int byEvent = coinNoPerDay(s, level);
        return byEvent > 0 ? byEvent : DEFAULT_COIN_PER_DAY;
    }

    static int coinNoPerDay(GameSession s, int level) {
        var state = com.dylan.game.event.EventService.get(
                com.dylan.game.event.EventNo.COIN_PER_DAY);
        if (state == null || !state.running()) {
            return 0;
        }
        if (level <= 17) {
            return state.byteParam(0);
        }
        if (level > 26) {
            return state.byteParam(2);
        }
        return state.byteParam(1);
    }

    private static void topUpTicket(GameSession s, int characNo, boolean sendUpdate,
                                    String why) {
        if (!ServerConfig.DAILY_GRANT) {
            return;
        }
        int itemId = ServerConfig.DAILY_TICKET_ITEM;
        int cap = ServerConfig.DAILY_TICKET_CAP;
        if (itemId <= 0 || cap <= 0 || characNo <= 0) {
            return;
        }
        GameStore store = s.store();
        var inv = s.inventory();
        if (inv == null || inv.inventory() == null) {
            return;
        }

        Instant period = periodStart();
        if (store != null) {
            Instant last;
            try {
                last = store.dailyGrant().lastDailyGrant(characNo, itemId).orElse(Instant.EPOCH);
            } catch (RuntimeException e) {
                return;
            }
            if (!period.isAfter(last)) {
                return;
            }
        }

        long have = InventoryService.countOf(inv.inventory().inventory(), itemId);
        int need = topUpAmount(have, cap);
        if (need <= 0) {
            markGranted(store, characNo, itemId, period);
            return;
        }

        ItemScript script = s.scripts() == null ? null
                : s.scripts().item(itemId).orElse(null);
        if (script == null) {
            return;
        }
        InventoryService.Landing landing = inv.addItem(script, need);
        if (!landing.ok()) {
            return;
        }
        int slot = landing.slot();
        inv.save();
        markGranted(store, characNo, itemId, period);
        if (sendUpdate) {
            inv.sendItemUpdate(landing.itemSpace(), "每日发放 " + itemId, slot);
            ChatService.system(s, "每日的" + (script.name().isEmpty() ? "道具" : script.name())
                    + "已经放进背包了（" + (have + need) + "/" + cap + "）");
            s.flush();
        }
    }

    static int topUpAmount(long have, int cap) {
        return (int) Math.max(0, cap - Math.max(0, have));
    }

    private static void markGranted(GameStore store, int characNo, int itemId, Instant period) {
        if (store == null) {
            return;
        }
        try {
            store.dailyGrant().saveDailyGrant(characNo, itemId, period);
        } catch (RuntimeException e) {
        }
    }

    static Instant periodStart() {
        return FatigueService.periodStartFor(Instant.now(), ZoneId.systemDefault(),
                ServerConfig.FATIGUE_RESET_HOUR);
    }
}
