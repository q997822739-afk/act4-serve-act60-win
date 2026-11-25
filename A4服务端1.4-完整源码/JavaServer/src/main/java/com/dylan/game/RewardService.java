package com.dylan.game;

import java.util.List;
import java.util.Random;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Cmd16SelectDungeonRequest;
import com.dylan.protocol.body.Cmd49PlayResultRequest;
import com.dylan.protocol.body.Cmd49ReplyBody;
import com.dylan.protocol.body.Cmd72ReplyBody;
import com.dylan.protocol.body.Cmd73ReplyBody;
import com.dylan.protocol.body.Cmd74SelectCardReply;
import com.dylan.protocol.body.Cmd74SelectCardRequest;
import com.dylan.protocol.body.Cmd75ReplyBody;
import com.dylan.protocol.body.Noti34PlayResultBody;
import com.dylan.protocol.body.Noti35ClearRewardBody;

public final class RewardService {
    private static final Random RANDOM = new Random();

    private RewardService() {
    }

    public static void setPlayResult(GameSession s, byte[] plain) {
        Cmd49PlayResultRequest report = Cmd49PlayResultRequest.parse(plain);

        byte[] reply = Cmd49ReplyBody.success();
        s.replyNow(Cmd49ReplyBody.PACKET_ID, reply);

        if (!ServerConfig.CLEAR_REWARD) {
            return;
        }
        // A4 官方教程里 CMD 49 静默忽略（日志「教程结算请求已静默忽略，等待最终模块 31 回城」），
        // 回城走 CMD 153 模块 31。
        var dungeon = s.dungeon();
        if (ServerConfig.A4 && dungeon != null
                && dungeon.dungeonIndex() == Cmd16SelectDungeonRequest.TUTORIAL_DUNGEON_INDEX) {
            return;
        }
        Party party = requireParty(s, Cmd49PlayResultRequest.PACKET_ID);
        if (party == null) {
            return;
        }
        ClearState state = party.clearState();
        state.reportedRank(report.rank());
        if (!state.advance(ClearState.PLAY_RESULT)) {
            return;
        }
        sendResult(party, s);
    }

    private static Party requireParty(GameSession s, int packetId) {
        Party party = s.party().current();
        return party;
    }

    private static void sendResult(Party party, GameSession initiator) {
        List<GameSession> members = party.view().members();
        for (GameSession member : members) {
            member.runSummary().stopClock();
        }
        DungeonRunSummary run = initiator.runSummary();
        ClearState state = party.clearState();
        state.rank(ClearRankRules.evaluate(run));

        if (ServerConfig.PLAY_RESULT) {
            byte[] playResult = Noti34PlayResultBody.withRank(state.reportedRank()).toBytes();
            for (GameSession member : members) {
                member.run(() -> {
                    member.send(Noti34PlayResultBody.PACKET_ID, playResult);
                    member.flush();
                });
            }
        }

        rollCardsForParty(party, initiator);
        for (GameSession member : members) {
            member.run(() -> {
                ClearExpBonus bonus = grantClearExperience(member, state.rank());
                sendClearReward(party, member, bonus);
                recordDungeonClear(member, state.rank());
                member.flush();
            });
        }
        state.advance(ClearState.REWARD_SENT);
        scheduleClearDungeonReward(party);
        initiator.flush();
    }

    private static void recordDungeonClear(GameSession s, ClearRank rank) {
        var run = s.dungeon();
        if (run == null) {
            return;
        }
        if (run.dungeonIndex() == Cmd16SelectDungeonRequest.TUTORIAL_DUNGEON_INDEX) {
            return;
        }
        s.dungeonClear().recordClear(run.dungeonIndex(), run.difficulty(), rank);
    }

    private static ClearExpBonus grantClearExperience(GameSession s, ClearRank rank) {
        long gained = s.runSummary().experienceGained();
        if (gained <= 0) {
            return ClearExpBonus.zero();
        }
        ClearExpBonus bonus = ClearExpBonus.compute(s, gained, rank);
        long extra = bonus.extraTotal();
        if (extra <= 0) {
            return bonus;
        }
        int amount = (int) Math.min(Integer.MAX_VALUE, extra);
        ExperienceService.grant(s, amount, amount);
        return bonus;
    }

    private static void rollCardsForParty(Party party, GameSession initiator) {
        ClearState state = party.clearState();
        for (int slot = 0; slot < ClearState.SLOT_COUNT; slot++) {
            GameSession member = party.memberAt(slot);
            if (member == null) {
                continue;
            }
            state.cardsOf(slot, rollCards(initiator, member));
        }
    }

    private static void sendClearReward(Party party, GameSession s, ClearExpBonus bonus) {
        ClearState state = party.clearState();
        var groupA = new java.util.ArrayList<List<Noti35ClearRewardBody.SlotEntry>>(
                Noti35ClearRewardBody.SLOT_COUNT);
        for (int slot = 0; slot < Noti35ClearRewardBody.SLOT_COUNT; slot++) {
            groupA.add(makeRewardItemInfo(party, slot));
        }

        var card = state.cardsOf(party.slotOf(s));
        int goldCardCost = card == null ? 0 : card.goldCardCost();
        var body = ServerConfig.REWARD_PROBE
                ? Noti35ClearRewardBody.probe(bonus, groupA)
                : Noti35ClearRewardBody.of(bonus, groupA, goldCardCost);
        int hellPercent = HellPartyService.hellPartyValuePercent(party, s);
        body = body.withPartyOnly(hellPercent);
        byte[] bytes = body.toBytes();
        s.send(Noti35ClearRewardBody.PACKET_ID, bytes);
    }

    private static List<Noti35ClearRewardBody.SlotEntry> makeRewardItemInfo(Party party,
                                                                           int slot) {
        ClearState state = party.clearState();
        ClearRewardRules.Cards card = state.cardsOf(slot);
        GameSession owner = party.memberAt(slot);
        if (card == null || owner == null) {
            return List.of();
        }
        var item = card.freeItem();
        if (item != null && !canLand(owner, item, "免费牌")) {
            card = card.withoutFreeItem();
            state.cardsOf(slot, card);
            item = null;
        }
        var entries = new java.util.ArrayList<Noti35ClearRewardBody.SlotEntry>(2);
        entries.add(new Noti35ClearRewardBody.SlotEntry(GOLD_ENTRY_ITEM_ID,
                (int) Math.min(card.freeGold(), Integer.MAX_VALUE)));
        if (item != null) {
            entries.add(new Noti35ClearRewardBody.SlotEntry(item.itemId(), item.count()));
        }
        return List.copyOf(entries);
    }

    private static boolean canLand(GameSession s, ClearRewardRules.CardItem item, String what) {
        var script = s.scripts() == null ? null : s.scripts().item(item.itemId()).orElse(null);
        if (script == null) {
            s.fail(what + "上的物品 " + item.itemId() + " 查不到脚本 —— 翻牌池和物品库不同步");
            return false;
        }
        if (InventoryService.tryAddItem(s.inventory().inventory(), script, item.count()).ok()) {
            return true;
        }
        return false;
    }

    public static final int GOLD_ENTRY_ITEM_ID = 0;

    private static ClearRewardRules.Cards rollCards(GameSession context, GameSession owner) {
        var scripts = context.scripts();
        var run = context.dungeon();
        if (scripts == null || run == null) {
            return ClearRewardRules.Cards.none();
        }
        var ctx = ClearRewardRules.Context.of(run.basisLevel(), run.difficulty(), 1,
                context.runSummary());
        var card = ClearRewardRules.roll(ctx, scripts.clearReward(),
                scripts.goldDropTable(), scripts.clearRewardPool(),
                itemId -> scripts.item(itemId).orElse(null), RANDOM,
                com.dylan.game.event.EventService.isRunning(
                        com.dylan.game.event.EventNo.CLEAR_REWARD_CARD, owner));
        return card;
    }

    private static void replyToParty(Party party, int packetId, byte[] body, String why) {
        List<GameSession> members = party.view().members();
        for (GameSession member : members) {
            member.run(() -> {
                member.reply(packetId, body);
                member.flush();
            });
        }
    }

    public static void scoreScrollState(GameSession s) {
        Party party = requireParty(s, Cmd72ReplyBody.PACKET_ID);
        if (party == null) {
            return;
        }
        if (party.clearState().state() > ClearState.REWARD_SENT) {
            return;
        }
        openScoreScroll(party, "玩家点了「翻牌」");
        s.flush();
    }

    private static void openScoreScroll(Party party, String why) {
        replyToParty(party, Cmd72ReplyBody.PACKET_ID, Cmd72ReplyBody.success(),
                "SCORE_SCROLL_STATE：翻牌页打开（" + why + "）");
        party.clearState().advance(ClearState.SCORE_SCROLL);
        scheduleScoreScroll(party);
    }

    public static void cardSelectRightState(GameSession s) {
        Party party = requireParty(s, Cmd73ReplyBody.PACKET_ID);
        if (party == null) {
            return;
        }
        if (party.clearState().state() > ClearState.SCORE_SCROLL) {
            return;
        }
        grantCardSelectRight(party, "玩家打开了翻牌页");
        s.flush();
    }

    private static void grantCardSelectRight(Party party, String why) {
        int[] rights = new int[Cmd73ReplyBody.SLOT_COUNT];
        java.util.Arrays.fill(rights, Cmd73ReplyBody.EMPTY_SLOT);
        var detail = new StringBuilder();
        for (int slot = 0; slot < Cmd73ReplyBody.SLOT_COUNT; slot++) {
            GameSession member = party.memberAt(slot);
            if (member == null) {
                continue;
            }
            boolean hasFatigue = member.fatigue().checkFatigue();
            rights[slot] = hasFatigue ? Cmd73ReplyBody.GRANTED : Cmd73ReplyBody.FATIGUE_SPENT;
            detail.append(detail.isEmpty() ? "" : "，").append("槽 ").append(slot)
                    .append("=角色 ").append(member.characNo())
                    .append(hasFatigue ? "（可以翻）" : "（★ 疲劳用满，全队都翻不了）");
        }
        replyToParty(party, Cmd73ReplyBody.PACKET_ID, Cmd73ReplyBody.of(rights),
                "CARD_SELECT_RIGHT_STATE（" + why + "）");
        party.clearState().advance(ClearState.CARD_SELECT);
        scheduleCardSelectRight(party);
        scheduleCardSelect(party);
    }

    public static void selectCard(GameSession s, byte[] plain) {
        Cmd74SelectCardRequest request = Cmd74SelectCardRequest.parse(plain);
        if (!request.validType()) {
            return;
        }
        Party party = requireParty(s, Cmd74SelectCardRequest.PACKET_ID);
        if (party == null) {
            return;
        }
        ClearState state = party.clearState();
        if (state.state() > ClearState.CARD_SELECT) {
            return;
        }
        if (state.state() < ClearState.CARD_SELECT) {
            if (ServerConfig.A4) {
                // 依据：A4 官方实抓（pair_probe - dungeon 74:00 和 sweep_dungeon CMD 74，还没进结算、翻牌布局没生成
                // 就发 CMD 74 全 0）= REPLY 74 `00 04`；catalog_cmd「命令 74：翻牌布局未生成、已翻牌、请求无效或
                // 当前不在副本中」。
                // A5 原来会直接拿掉 0 号免费牌并回 13 字节 eachCardInfo（01 00 ff 00 ff…），客户端以为已经翻到牌。
                // 改成「不该发就不发」：不 takeCard、不回 eachCardInfo，只回官方那条失败码。
                // （确认过不是「金币不足仍给牌」：dungeonClear 要每人免费 + 金卡都选完才发东西，这条走不到发牌。）
                s.reply(Cmd74SelectCardReply.PACKET_ID,
                        Cmd74SelectCardReply.rejected(Cmd74SelectCardReply.ERROR_BAD_STATE));
                s.flush();
                return;
            }
        }
        if (!state.isCompleteCardSelectOfMembers(ClearState.CARD_TYPE_BOTH, validMembers(party))) {
            if (!setCardNumber(party, s, request.cardType(), request.cardNo())) {
                s.flush();
                return;
            }
            sendEachCardInfo(party);
        }

        if (state.isCompleteCardSelectOfMembers(ClearState.CARD_TYPE_BOTH, validMembers(party))) {
            dungeonClear(party);
        }
        s.flush();
    }

    private static java.util.function.IntPredicate validMembers(Party party) {
        return slot -> {
            GameSession member = party.memberAt(slot);
            return member != null && member.hasCharacter();
        };
    }

    private static void insertClearTimer(Party party, int message, long seconds) {
        TimerQueue.insertPartyTimer(party, message, seconds, 0);
    }

    static {
        TimerQueue.registerParty(TimerMessage.CLEAR_DUNGEON_REWARD,
                RewardService::onClearDungeonRewardTimer);
        TimerQueue.registerParty(TimerMessage.CLEAR_DUNGEON_SCORE_SCROLL,
                RewardService::onScoreScrollTimer);
        TimerQueue.registerParty(TimerMessage.CARD_SELECT,
                RewardService::onCardSelectTimer);
        TimerQueue.registerParty(TimerMessage.CLEAR_DUNGEON_CARD_SELECT_RIGHT,
                RewardService::onCardSelectRightTimer);
        TimerQueue.registerParty(TimerMessage.DUNGEON_CLEAR,
                RewardService::onDungeonClearTimer);
        TimerQueue.registerParty(TimerMessage.EPLP_RETURN_VILLAGE,
                RewardService::onEplpReturnVillageTimer);
        TimerQueue.registerParty(TimerMessage.EPLP_COMMAND_WAIT,
                RewardService::onEplpCommandWaitTimer);
    }

    private static void scheduleClearDungeonReward(Party party) {
        insertClearTimer(party, TimerMessage.CLEAR_DUNGEON_REWARD,
                CLEAR_DUNGEON_REWARD_TIMEOUT_SEC);
    }

    private static void onClearDungeonRewardTimer(Party party, int key, int param) {
        if (!party.checkTimerKey(TimerMessage.CLEAR_DUNGEON_REWARD, key)) {
            return;
        }
        if (party.clearState().state() != ClearState.REWARD_SENT) {
            return;
        }
        openScoreScroll(party, "官方 TimerClearDungeonReward 超时");
    }

    private static void scheduleScoreScroll(Party party) {
        insertClearTimer(party, TimerMessage.CLEAR_DUNGEON_SCORE_SCROLL, SCORE_SCROLL_TIMEOUT_SEC);
    }

    private static void onScoreScrollTimer(Party party, int key, int param) {
        if (!party.checkTimerKey(TimerMessage.CLEAR_DUNGEON_SCORE_SCROLL, key)) {
            return;
        }
        if (party.clearState().state() != ClearState.SCORE_SCROLL) {
            return;
        }
        grantCardSelectRight(party, "官方 TimerClearDungeonScoreScoll 超时");
        scheduleScoreScroll(party);
    }

    private static void scheduleCardSelect(Party party) {
        insertClearTimer(party, TimerMessage.CARD_SELECT, CARD_SELECT_TIMEOUT_SEC);
    }

    private static void onCardSelectTimer(Party party, int key, int param) {
        if (!party.checkTimerKey(TimerMessage.CARD_SELECT, key)) {
            return;
        }
        ClearState state = party.clearState();
        if (state.state() == ClearState.NONE || state.state() >= ClearState.DONE) {
            return;
        }
        if (!state.isCompleteCardSelectOfMembers(Cmd74SelectCardReply.CARD_TYPE_FREE,
                validMembers(party))) {
            state.completeCardSelect(validMembers(party));
            sendEachCardInfo(party);
        }
        dungeonClear(party);
    }

    private static void scheduleCardSelectRight(Party party) {
        insertClearTimer(party, TimerMessage.CLEAR_DUNGEON_CARD_SELECT_RIGHT,
                CARD_SELECT_RIGHT_TIMEOUT_SEC);
    }

    private static void onCardSelectRightTimer(Party party, int key, int param) {
        if (!party.checkTimerKey(TimerMessage.CLEAR_DUNGEON_CARD_SELECT_RIGHT, key)) {
            return;
        }
        if (party.clearState().state() != ClearState.CARD_SELECT) {
            return;
        }
        party.clearState().advance(ClearState.DONE);
        scheduleDungeonClear(party);
    }

    private static void scheduleDungeonClear(Party party) {
        insertClearTimer(party, TimerMessage.DUNGEON_CLEAR, DUNGEON_CLEAR_TIMEOUT_SEC);
    }

    private static void onDungeonClearTimer(Party party, int key, int param) {
        if (!party.checkTimerKey(TimerMessage.DUNGEON_CLEAR, key)) {
            return;
        }
        dungeonClear(party);
    }

    private static final long CARD_SELECT_TIMEOUT_SEC = 4;

    private static final long CLEAR_DUNGEON_REWARD_TIMEOUT_SEC = 30;

    private static final long SCORE_SCROLL_TIMEOUT_SEC = 30;

    private static final long CARD_SELECT_RIGHT_TIMEOUT_SEC = 30;

    private static final long DUNGEON_CLEAR_TIMEOUT_SEC = 5;

    private static boolean setCardNumber(Party party, GameSession s, int cardType, int cardNo) {
        ClearState state = party.clearState();
        int slot = party.slotOf(s);
        if (slot < 0) {
            return false;
        }
        if (cardNo < 0 || cardNo >= ClearState.SLOT_COUNT) {
            return false;
        }
        if (state.haveAlreadySelectedCard(slot, cardType)) {
            return false;
        }
        if (!state.isCardFree(cardType, cardNo)) {
            return false;
        }
        if (!checkGoldCardSelectable(party, s, slot, cardType)) {
            return false;
        }
        state.takeCard(cardType, cardNo, slot);
        if (cardType == Cmd74SelectCardReply.CARD_TYPE_GOLD) {
            reserveGoldCardItem(party, s, slot);
        }
        return true;
    }

    private static boolean checkGoldCardSelectable(Party party, GameSession s, int slot,
                                                   int cardType) {
        if (cardType != Cmd74SelectCardReply.CARD_TYPE_GOLD) {
            return true;
        }
        var card = party.clearState().cardsOf(slot);
        int cost = card == null ? 0 : card.goldCardCost();
        if (cost <= 0) {
            return true;
        }
        if (s.store() == null || s.characNo() < 0) {
            return false;
        }
        var inv = s.inventory().inventory();
        if (inv == null) {
            s.fail("角色 " + s.characNo() + " 在 inventory 表里没有行，金卡付不了钱");
            return false;
        }
        if (inv.money() < cost) {
            return false;
        }
        long before = inv.money();
        s.inventory().setMoney(before - cost);
        s.inventory().save();
        CharacterService.resendItemLists(s, "金卡手续费");
        return true;
    }

    private static void reserveGoldCardItem(Party party, GameSession s, int slot) {
        ClearState state = party.clearState();
        var card = state.cardsOf(slot);
        if (card == null || card.goldCardItem() == null) {
            return;
        }
        if (canLand(s, card.goldCardItem(), "金卡")) {
            return;
        }
        state.cardsOf(slot, card.withoutGoldCardItem());
    }

    private static void sendEachCardInfo(Party party) {
        ClearState state = party.clearState();
        int[] freeOwners = state.freeOwners();
        int[] goldOwners = state.goldOwners();
        var goldItems = new Cmd74SelectCardReply.Entry[Cmd74SelectCardReply.SLOT_COUNT];
        for (int i = 0; i < goldOwners.length; i++) {
            if (goldOwners[i] == Cmd74SelectCardReply.NOBODY) {
                continue;
            }
            var card = state.cardsOf(goldOwners[i]);
            if (card != null && card.goldCardItem() != null) {
                goldItems[i] = new Cmd74SelectCardReply.Entry(card.goldCardItem().itemId(),
                        card.goldCardItem().count());
            }
        }
        byte[] body = Cmd74SelectCardReply.eachCardInfo(freeOwners, goldOwners, goldItems)
                .toBytes();
        replyToParty(party, Cmd74SelectCardReply.PACKET_ID, body,
                "SELECT_CARD：桌面 免费=" + java.util.Arrays.toString(freeOwners)
                        + " 金卡=" + java.util.Arrays.toString(goldOwners));
    }

    private static void dungeonClear(Party party) {
        ClearState state = party.clearState();
        state.advance(ClearState.DONE);
        party.eplpState(Party.EPLP_CLEARED);
        scheduleEplpCommandWait(party);
        var cards = state.takeCards();
        for (int slot = 0; slot < cards.length; slot++) {
            var card = cards[slot];
            GameSession owner = party.memberAt(slot);
            if (card == null || owner == null) {
                continue;
            }
            boolean goldTaken = state.tookGoldCard(slot);
            owner.run(() -> {
                putItemIntoUser(owner, card, goldTaken);
                owner.flush();
            });
        }
    }

    private static void scheduleEplpCommandWait(Party party) {
        TimerQueue.insertPartyTimer(party, TimerMessage.EPLP_COMMAND_WAIT,
                EPLP_COMMAND_WAIT_SEC, 0);
    }

    private static void onEplpCommandWaitTimer(Party party, int key, int param) {
        if (!party.checkTimerKey(TimerMessage.EPLP_COMMAND_WAIT, key)) {
            return;
        }
        if (!shouldReturnToTownOnTimeout(party)) {
            return;
        }
        returnPartyToVillage(party, "结算超时（官方 TimerEPLPCommandWait）");
    }

    private static boolean returnPartyToVillage(Party party, String why) {
        Party.View view = party.view();
        GameSession entry = view.manager();
        if (entry == null) {
            List<GameSession> members = view.members();
            entry = members.isEmpty() ? null : members.get(0);
        }
        if (entry == null) {
            return false;
        }
        GameSession runner = entry;
        runner.run(() -> {
            TownService.returnToTown(runner, why, true);
            runner.flush();
        });
        return true;
    }

    static boolean shouldReturnToTownOnTimeout(Party party) {
        if (party == null) {
            return false;
        }
        if (party.selectedEplpCmd() == Party.EPLP_CMD_STAY) {
            return false;
        }
        if (party.eplpState() != Party.EPLP_CLEARED) {
            return false;
        }
        return true;
    }

    private static final long EPLP_COMMAND_WAIT_SEC = 60;

    private static void putItemIntoUser(GameSession s, ClearRewardRules.Cards card,
                                        boolean goldCardTaken) {
        if (!s.hasCharacter()) {
            return;
        }
        if (s.store() == null || s.characNo() < 0) {
            return;
        }
        var inv = s.inventory().inventory();
        if (inv == null) {
            s.fail("角色 " + s.characNo() + " 在 inventory 表里没有行，结算奖励发不下去");
            return;
        }
        var granted = new StringBuilder();
        long moneyBefore = inv.money();
        long moneyAfter = card.freeGold() > 0
                ? Math.min(0xFFFFFFFFL, moneyBefore + card.freeGold())
                : moneyBefore;
        if (card.freeGold() > 0) {
            granted.append(card.freeGold()).append(" 金币（")
                    .append(moneyBefore).append(" → ").append(moneyAfter).append('）');
        }
        grantCardItem(s, inv, card.freeItem(), "免费牌", granted);
        if (goldCardTaken) {
            grantCardItem(s, inv, card.goldCardItem(), "金卡", granted);
        }
        s.inventory().setMoney(moneyAfter);
        s.inventory().save();
        if (!granted.isEmpty()) {
            CharacterService.resendItemLists(s, "通关结算奖励入账");
        }
    }

    private static void grantCardItem(GameSession s, com.dylan.store.CharacterInventory inv,
                                      ClearRewardRules.CardItem item, String what,
                                      StringBuilder granted) {
        if (item == null) {
            return;
        }
        var script = s.scripts() == null ? null : s.scripts().item(item.itemId()).orElse(null);
        if (script == null) {
            s.fail("结算奖励里的物品 " + item.itemId() + " 查不到脚本 —— 翻牌池和物品库不同步");
            return;
        }
        var landing = InventoryService.addItem(s, s.store(), s.characNo(), inv, script,
                item.count());
        if (!landing.ok()) {
            return;
        }
        granted.append(granted.isEmpty() ? "" : " + ").append(what).append(' ')
                .append(script.name().isEmpty() ? "（脚本里没有名字）" : script.name())
                .append('(').append(item.itemId()).append(") x").append(item.count())
                .append(" 进容器 ").append(landing.itemSpace())
                .append(" 第 ").append(landing.slot()).append(" 格");
    }

    public static void eplpCommand(GameSession s, byte[] plain) {
        if (plain == null || plain.length < 2) {
            s.reply(Cmd75ReplyBody.PACKET_ID, Cmd75ReplyBody.rejected());
            s.flush();
            return;
        }
        int phase = plain[0] & 0xFF;
        int button = plain[1] & 0xFF;
        Party party = s.party().current();
        if (party == null) {
            return;
        }
        if (!party.isManager(s)) {
            broadcastEplp(s, Cmd75ReplyBody.error(Cmd75ReplyBody.ERROR_NOT_THE_LEADER, button),
                    "不是队长");
            s.flush();
            return;
        }
        if (!checkEplpState(s, party)) {
            if (ServerConfig.A4) {
                // 依据：A4 官方实抓（pair_probe - dungeon 75:00 / sweep_dungeon 刚进本还没结算就发 CMD 75 全 0）
                // = REPLY 75 `01 00 00`，即原样回显 phase 0 + button 0（catalog_cmd「命令 75：Eplp state={0} option={1}」）。
                // 原版 A5 不在结算状态就静默丢，客户端等回包。官方对任何 phase/button 都回显，这里照抄；
                // 只补回包，结算/回城的状态判断和定时器都没动。
                broadcastEplp(s, Cmd75ReplyBody.echo(phase, button), "还没到结算，照官方原样回显");
                s.flush();
            }
            return;
        }
        broadcastEplp(s, Cmd75ReplyBody.echo(phase, button), "确认回显");

        if (phase != Cmd75ReplyBody.PHASE_CONFIRM) {
            s.flush();
            return;
        }
        if (button == Cmd75ReplyBody.BUTTON_STAY) {
            party.selectedEplpCmd(Party.EPLP_CMD_STAY);
            s.flush();
            return;
        }
        if (button > Cmd75ReplyBody.BUTTON_STAY) {
            broadcastEplp(s, Cmd75ReplyBody.error(Cmd75ReplyBody.ERROR_UNKNOWN_BUTTON, button),
                    "没见过的按钮号");
        }
        DeathTower tower = s.deathTower();
        if (tower != null) {
            party.eplpState(Party.EPLP_COMMAND_SELECTED);
            DeathTowerService.finishDeathTower(party, tower,
                    "玩家点了「" + buttonName(button) + "」");
            s.flush();
            return;
        }
        dungeonClear(party);
        party.eplpState(Party.EPLP_COMMAND_SELECTED);
        var run = s.dungeon();
        boolean immediate = run != null
                && run.dungeonIndex() == Cmd16SelectDungeonRequest.TUTORIAL_DUNGEON_INDEX;
        long delay = button == Cmd75ReplyBody.BUTTON_TOWN
                ? EPLP_DELAY_TOWN_SEC : EPLP_DELAY_OTHER_SEC;
        s.flush();
        if (immediate) {
            returnToVillage(party, button);
            return;
        }
        int key = party.genTimerKey(TimerMessage.EPLP_RETURN_VILLAGE);
        TimerQueue.insertTimer(TimerQueue.OBJ_PARTY, party.index(), TimerMessage.EPLP_RETURN_VILLAGE, delay, key, button);
    }

    private static void onEplpReturnVillageTimer(Party party, int key, int button) {
        if (!party.checkTimerKey(TimerMessage.EPLP_RETURN_VILLAGE, key)) {
            return;
        }
        returnToVillage(party, button);
    }

    private static boolean checkEplpState(GameSession s, Party party) {
        if (party.eplpState() == Party.EPLP_CLEARED) {
            return true;
        }
        var run = s.dungeon();
        return run != null
                && run.dungeonIndex() == Cmd16SelectDungeonRequest.TUTORIAL_DUNGEON_INDEX;
    }

    private static void returnToVillage(Party party, int button) {
        if (!returnPartyToVillage(party, "结算界面点了「" + buttonName(button) + "」")) {
            return;
        }
    }

    private static final long EPLP_DELAY_TOWN_SEC = 1;

    private static final long EPLP_DELAY_OTHER_SEC = 3;

    private static void broadcastEplp(GameSession s, byte[] body, String why) {
        Party party = s.party().current();
        if (party == null) {
            s.reply(Cmd75ReplyBody.PACKET_ID, body);
            return;
        }
        replyToParty(party, Cmd75ReplyBody.PACKET_ID, body, "EPLP_COMMAND：" + why);
    }

    public static final int CMD_CHARACTER_STATISTIC = 133;

    public static void characterStatistic(GameSession s, byte[] plain) {
    }

    private static String buttonName(int button) {
        return switch (button) {
            case Cmd75ReplyBody.BUTTON_TOWN -> "返回城镇（实机验过）";
            case Cmd75ReplyBody.BUTTON_RESTART -> "再来一次（按顺序推的，待验）";
            case Cmd75ReplyBody.BUTTON_OTHER_DUNGEON -> "选择其他地下城（按顺序推的，待验）";
            case Cmd75ReplyBody.BUTTON_STAY -> "留在这儿（官方 v10==3，只记一笔不动人）";
            default -> "? 没见过的按钮号";
        };
    }
}
