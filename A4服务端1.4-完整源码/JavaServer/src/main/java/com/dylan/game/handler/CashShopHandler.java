package com.dylan.game.handler;

import com.dylan.net.ServerConfig;
import com.dylan.game.CashShopService;
import com.dylan.game.CommandHandler;
import com.dylan.game.DailyGrantService;
import com.dylan.game.GameSession;
import com.dylan.protocol.body.CashShopError;
import com.dylan.protocol.body.Cmd220ShopCoinEventReply;
import com.dylan.protocol.body.Cmd67BuyCeraShopItemRequest;
import com.dylan.protocol.body.Cmd67ReplyBody;
import com.dylan.protocol.body.Cmd68ReplyBody;
import com.dylan.protocol.body.ItemListType;
import com.dylan.protocol.body.Noti53CeraBody;

import java.time.Instant;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class CashShopHandler implements CommandHandler {

    public static final int CMD_CERA = 66;

    private static final AtomicInteger TICKET_COUNTER = new AtomicInteger();

    private static final int TICKET_COUNTER_MAX = 0xC34F;

    private static final Random RANDOM = new Random();

    @Override
    public Set<Integer> handles() {
        return ServerConfig.A4
                ? Set.of(CMD_CERA, Cmd68ReplyBody.PACKET_ID, Cmd67BuyCeraShopItemRequest.PACKET_ID,
                        Cmd220ShopCoinEventReply.PACKET_ID, DailyGrantService.A4_CMD_CLAIM)
                : Set.of(CMD_CERA, Cmd68ReplyBody.PACKET_ID, Cmd67BuyCeraShopItemRequest.PACKET_ID,
                        Cmd220ShopCoinEventReply.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        switch (packetId) {
            case CMD_CERA -> sendCera(session);
            case Cmd68ReplyBody.PACKET_ID -> genCeraTicket(session);
            case Cmd67BuyCeraShopItemRequest.PACKET_ID -> buy(session, plain);
            case Cmd220ShopCoinEventReply.PACKET_ID, DailyGrantService.A4_CMD_CLAIM ->
                    DailyGrantService.claim(session);
            default -> throw new IllegalStateException("不该路由到这里：CMD " + packetId);
        }
    }

    private void buy(GameSession session, byte[] plain) {
        Cmd67BuyCeraShopItemRequest request;
        try {
            request = Cmd67BuyCeraShopItemRequest.parse(plain);
        } catch (RuntimeException e) {
            session.reply(Cmd67BuyCeraShopItemRequest.PACKET_ID, ServerConfig.A4
                    ? Cmd67ReplyBody.a4Failure(CashShopError.BAD_REQUEST,
                            com.dylan.pvf.script.CashShopCatalog.CATEGORY_UNKNOWN, Cmd67ReplyBody.NO_ITEM)
                    : Cmd67ReplyBody.failure(CashShopError.BAD_REQUEST));
            return;
        }

        if (!ServerConfig.CASH_SHOP) {
            rejectAll(session, request, CashShopError.NOT_PURCHASABLE);
            return;
        }
        if (request.gift()) {
            rejectAll(session, request, CashShopError.NOT_PURCHASABLE);
            return;
        }

        var shop = session.cashShop();
        int total = request.lines().size();
        long balance = shop.balance();
        int ok = 0;
        int seq = 0;

        for (var line : request.lines()) {
            var outcome = shop.buy(line);
            balance = outcome.balance();
            seq++;

            if (ServerConfig.A4) {
                // A4 官方：失败只回一条 21 字节（seq/total 0）然后停；成功每件一条，最后一件后补 NOTI 53。
                // 顺序照官方：REPLY 67 → NOTI 53 → 物品通知。
                if (!outcome.ok()) {
                    session.reply(Cmd67BuyCeraShopItemRequest.PACKET_ID,
                            Cmd67ReplyBody.a4Failure(outcome.error(), outcome.category(),
                                    line.commodityNo()));
                    session.send(Noti53CeraBody.PACKET_ID, Noti53CeraBody.of(balance).toBytes());
                    return;
                }
                int count = outcome.entry() == null ? 1 : outcome.entry().count();
                session.reply(Cmd67BuyCeraShopItemRequest.PACKET_ID,
                        Cmd67ReplyBody.a4Success(outcome.category(), line.commodityNo(),
                                outcome.itemId(), count, seq, total, outcome.bundle()));
                if (seq == total) {
                    session.send(Noti53CeraBody.PACKET_ID, Noti53CeraBody.of(balance).toBytes());
                }
                sendTouched(session, outcome);
                continue;
            }

            if (seq == total) {
                session.send(Noti53CeraBody.PACKET_ID, Noti53CeraBody.of(balance).toBytes());
                sendCurrencySlots(session);
            }

            if (outcome.ok()) {
                ok++;
                sendTouched(session, outcome);
                session.reply(Cmd67BuyCeraShopItemRequest.PACKET_ID,
                        Cmd67ReplyBody.success(outcome.category(), line.commodityNo(),
                                outcome.itemId(), seq, total, request.gift(),
                                outcome.bundle()));
            } else {
                session.reply(Cmd67BuyCeraShopItemRequest.PACKET_ID,
                        Cmd67ReplyBody.failure(outcome.error(), outcome.category(),
                                line.commodityNo(), request.gift(), seq, total));
            }
        }
    }

    private void sendTouched(GameSession session, CashShopService.Outcome outcome) {
        if (!outcome.touchedLists().isEmpty()) {
            for (int itemSpace : outcome.touchedLists()) {
                session.inventory().sendItemList(itemSpace, "商城成交（礼包拆开）");
            }
            return;
        }
        if (!outcome.hasList()) {
            return;
        }
        if (!ServerConfig.ITEM_UPDATE_INCREMENTAL || !outcome.hasSlot() || ServerConfig.A4) {
            // A4 官方商城成交后重发整个主背包，不是单格 NOTI 14：scen_off 里 shop L798 / L1171
            // 两次成交后各一条 NOTI 13/0 165B（9 条：3 货币 + 6 物品）+ NOTI 192；
            // A5 原来只发 21B NOTI 14，客户端背包里新买的东西要等下次整栏刷新才出现。
            session.inventory().sendItemList(outcome.listType(), "商城成交");
            return;
        }
        session.inventory().sendItemUpdate(outcome.listType(), "商城成交", outcome.slot());
    }

    private void sendCurrencySlots(GameSession session) {
        if (!ServerConfig.ITEM_UPDATE_INCREMENTAL) {
            return;
        }
        session.inventory().sendItemUpdate(ItemListType.LIST_MAIN, "商城成交收尾（金币 / 胜点）",
                CashShopService.CURRENCY_GOLD, CashShopService.CURRENCY_WIN_POINT);
    }

    private void rejectAll(GameSession session, Cmd67BuyCeraShopItemRequest request,
                           CashShopError error) {
        int total = request.lines().size();
        int seq = 0;
        // A4 官方整单失败只回一条 21 字节（seq/total 0），不像 ACT5 每个商品一条（sweep_shop 实抓）
        if (ServerConfig.A4 && total > 0) {
            session.reply(Cmd67BuyCeraShopItemRequest.PACKET_ID,
                    Cmd67ReplyBody.a4Failure(error, com.dylan.pvf.script.CashShopCatalog.CATEGORY_UNKNOWN,
                            request.lines().get(0).commodityNo()));
            return;
        }
        for (var line : request.lines()) {
            seq++;
            session.reply(Cmd67BuyCeraShopItemRequest.PACKET_ID,
                    Cmd67ReplyBody.failure(error,
                            com.dylan.pvf.script.CashShopCatalog.CATEGORY_UNKNOWN,
                            line.commodityNo(), request.gift(), seq, total));
        }
    }

    private void sendCera(GameSession session) {
        long cera = session.cera();
        byte[] body = Noti53CeraBody.of(cera).toBytes();
        session.send(Noti53CeraBody.PACKET_ID, body);
    }

    private void genCeraTicket(GameSession session) {
        // 依据：A4 官方 Act4Opcode 68 = 黑钻免费开通 + pair_probe 实抓，见 freeBlackDiamond
        if (ServerConfig.A4) {
            freeBlackDiamond(session);
            return;
        }
        if (!session.hasCharacter()) {
            session.reply(Cmd68ReplyBody.PACKET_ID, Cmd68ReplyBody.rejected(0));
            return;
        }
        String ticket = nextTicket();
        int check = RANDOM.nextInt(Cmd68ReplyBody.MAX_CHECK + 1);
        session.reply(Cmd68ReplyBody.PACKET_ID, new Cmd68ReplyBody(ticket, check).toBytes());
    }

    /**
     * A4 CMD 68 = 黑钻免费开通（官方 Act4Opcode 68，日志模板「命令 68：黑钻免费开通 … 到期={3}」），
     * 不是 ACT5 的点券券（GEN_CERATICKET）。原来回 ACT5 票据格式 {@code 01 u32 len 字符串 u32}：客户端成功分支
     * （00418709）只读一个 u32 就开窗口 0x3F，把字符串长度当到期显示，服务端又什么都没开，玩家看到「开通」后卡住。
     *
     * <p>依据：A4 官方实抓（pair_probe town 68，满级假人，连点两次逐字节）：
     * REPLY 68 {@code 00} → NOTI 53 点券 → 每个生效会员一条 NOTI 66（黑钻类型 0x1D 剩 2592000 秒）→ NOTI 2/0 本人
     * → NOTI 24 区域名单 → NOTI 12 系统消息「[黑钻会员]的有效时间还剩30天」（userId = 本人连接号）
     * → NOTI 36 疲劳（只有新开通、疲劳上限变了才有，第二次连点没有）。
     */
    private void freeBlackDiamond(GameSession session) {
        if (!session.hasCharacter()) {
            session.reply(Cmd68ReplyBody.PACKET_ID, Cmd68ReplyBody.rejected(0));
            return;
        }
        var premium = session.premium();
        Boolean fresh = premium.grantFreeBlackDiamond();
        session.reply(Cmd68ReplyBody.PACKET_ID, Cmd68ReplyBody.rejected(0));
        if (fresh == null) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        session.send(Noti53CeraBody.PACKET_ID, Noti53CeraBody.of(session.cashShop().balance()).toBytes());
        for (var p : premium.wirePremiums(now).entrySet()) {
            if (p.getKey() > com.dylan.protocol.body.Noti66SpecialItemBody.MAX_PREMIUM_TYPE) {
                continue;
            }
            session.send(com.dylan.protocol.body.Noti66SpecialItemBody.PACKET_ID,
                    com.dylan.protocol.body.Noti66SpecialItemBody.premium(
                            com.dylan.game.A4WorldEntry.LOGIN_PREMIUM_KIND, p.getKey(), p.getValue()).toBytes());
        }
        com.dylan.game.CharacterService.resendOwnCharacterInfo(session, "黑钻开通");
        com.dylan.game.NeighborService.resendRoster(session, "黑钻开通");
        var row = premium.rows().get(com.dylan.game.PremiumService.PREMIUM_BLACK_DIAMOND);
        long days = row == null ? 0 : (row.remaining(now) + 86399L) / 86400L;
        String text = "[黑钻会员]的有效时间还剩" + days + "天";
        session.sendNow(com.dylan.protocol.body.Noti12MessageBody.PACKET_ID,
                com.dylan.protocol.body.Noti12MessageBody.from(
                        com.dylan.protocol.body.ChatType.SYSTEM, session.uniqueId(), text).toBytes());
        session.fatigue().refreshPremiumCap();
        session.flush();
    }

    private static String nextTicket() {
        int count = TICKET_COUNTER.updateAndGet(v -> v >= TICKET_COUNTER_MAX ? 1 : v + 1);
        return String.format("%02d%02d%010d%05d",
                1, ServerConfig.MY_CHANNEL % 100, Instant.now().getEpochSecond(), count);
    }
}
