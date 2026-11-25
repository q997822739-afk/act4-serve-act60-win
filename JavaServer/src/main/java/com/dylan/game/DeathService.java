package com.dylan.game;

import com.dylan.protocol.body.Cmd43DieCharacterRequest;
import com.dylan.protocol.body.Cmd43ReplyBody;
import com.dylan.protocol.body.Cmd44ReplyBody;
import com.dylan.protocol.body.Cmd44UseCoinRequest;
import com.dylan.protocol.body.Noti32CharacLiveBody;
import com.dylan.protocol.body.ReviveError;

import java.util.List;

public final class DeathService {

    private DeathService() {
    }

    private static final int DUNGEON_FAIL_DELAY_SECONDS = 10;

    public static void dieCharacter(GameSession s, Cmd43DieCharacterRequest request) {
        if (!s.hasCharacter()) {
            return;
        }
        if (!inDungeonState(s)) {
            return;
        }

        Party party = s.party().current();
        if (party == null) {
            return;
        }
        dieUser(party, s);
    }

    private static void dieUser(Party party, GameSession s) {
        Party.View view = party.view();
        int slot = view.slots().indexOf(s);
        if (slot < 0) {
            reject43(s, Cmd43ReplyBody.ERROR_NOT_IN_PARTY,
                    "角色 " + s.characNo() + " 不在队伍 " + party.index() + " 里");
            return;
        }
        if (!party.aliveAt(slot)) {
            reject43(s, Cmd43ReplyBody.ERROR_ALREADY_DEAD,
                    "角色 " + s.characNo() + " 本来就是倒下的");
            return;
        }

        setCharacLive(party, s, Noti32CharacLiveBody.STATE_DEAD,
                "角色 " + s.characNo() + " 在副本里倒下");

        if (!party.checkAllMemberDie()) {
            return;
        }
        DeathTower tower = s.deathTower();
        if (tower != null) {
            DeathTowerService.afterDieUser(s, party, tower);
            return;
        }
        DungeonInstance dungeon = party.dungeon();
        if (dungeon == null || dungeon.field() == null) {
            return;
        }
        if (dungeon.field().isCleared()) {
            return;
        }
        sendDungeonFailMsg(party, "队伍 " + party.index() + " 全员倒下");
    }

    private static void sendDungeonFailMsg(Party party, String why) {
        int key = party.genTimerKey(TimerMessage.DUNGEON_FAIL);
        TimerQueue.insertTimer(TimerQueue.OBJ_PARTY, party.index(), TimerMessage.DUNGEON_FAIL,
                DUNGEON_FAIL_DELAY_SECONDS, key, 0);
    }

    static {
        TimerQueue.registerParty(TimerMessage.DUNGEON_FAIL,
                (party, key, param) -> partyDungeonFail(party, key, "全队阵亡倒计时到点"));
    }

    static void partyDungeonFail(Party party, int key, String why) {
        if (!party.checkTimerKey(TimerMessage.DUNGEON_FAIL, key)) {
            return;
        }
        DungeonInstance dungeon = party.dungeon();
        if (dungeon == null || dungeon.field() == null) {
            return;
        }
        if (dungeon.field().isCleared()) {
            return;
        }
        if (!party.checkAllMemberDie()) {
            return;
        }

        List<GameSession> members = party.view().members();
        if (members.isEmpty()) {
            return;
        }
        GameSession leader = party.view().manager();
        GameSession host = leader != null ? leader : members.get(0);
        dungeonFailPenalty(party, members);
        host.run(() -> TownService.returnToTown(host, "副本失败（全队阵亡）", true));
    }

    private static void dungeonFailPenalty(Party party, List<GameSession> members) {
        for (GameSession member : members) {
            member.run(() -> {
                if (member.stamina().penalty("副本失败（全队阵亡）")) {
                    member.flush();
                }
            });
        }
    }

    public static void useCoin(GameSession s, Cmd44UseCoinRequest request) {
        if (!s.hasCharacter()) {
            return;
        }
        if (!inDungeonState(s)) {
            reject44(s, ReviveError.WRONG_STATE, "人不在副本状态里");
            return;
        }
        Party party = s.party().current();
        if (party == null) {
            return;
        }
        DungeonInstance dungeon = party.dungeon();
        if (dungeon == null) {
            return;
        }

        boolean free = checkFreeRevivalCondition(party, dungeon);
        if (!free) {
            if (s.inventory().coinTotal() <= 0) {
                reject44(s, ReviveError.NO_COIN, "身上一枚复活币都没有");
                return;
            }
            if (!checkUnLimitUsedCoinCondition(dungeon)) {
                String blocked = checkLimitUsedCoinCondition(party, dungeon);
                if (blocked != null) {
                    reject44(s, ReviveError.COIN_LIMIT, blocked);
                    return;
                }
            }
        }

        int error = reviveUser(party, s, request.targetUserId());
        if (error != 0) {
            reject44(s, error, "ReviveUser 返回 " + error);
            return;
        }

        if (!free) {
            s.inventory().useCoin("复活（CMD " + Cmd44UseCoinRequest.PACKET_ID + "）");
            party.increaseUsedCoinCount();
        }

        byte[] body = Cmd44ReplyBody.success(request.targetUserId());
        s.reply(Cmd44ReplyBody.PACKET_ID, body);
        if (!free && com.dylan.net.ServerConfig.A4) {
            // A4 官方实抓（pair_probe dungeon 43 → 44）：REPLY 44 后紧跟 NOTI 14 刷主背包货币格 1（复活币余数）。
            s.inventory().sendItemUpdate(com.dylan.protocol.body.ItemListType.LIST_MAIN,
                    "用掉 1 枚复活币后刷新货币格", BagLayout.SLOT_COIN);
        }
        s.flush();
    }

    private static int reviveUser(Party party, GameSession payer, int targetUserId) {
        if (party.state() != DungeonInstance.STATE_IN_DUNGEON) {
            return ReviveError.WRONG_STATE;
        }
        Party.View view = party.view();
        int slot = view.slotOfUserId(targetUserId);
        if (slot < 0) {
            return ReviveError.NO_SUCH_MEMBER;
        }
        if (party.aliveAt(slot)) {
            return ReviveError.NOT_DEAD;
        }
        GameSession target = view.at(slot);
        if (target == null) {
            return ReviveError.NO_SUCH_MEMBER;
        }
        setCharacLive(party, target, Noti32CharacLiveBody.STATE_ALIVE,
                "角色 " + target.characNo() + " 被复活币扶起来了");
        return 0;
    }

    private static void setCharacLive(Party party, GameSession who, int state, String why) {
        int slot = party.setCharacLive(who, state);
        if (slot < 0) {
            return;
        }
        Noti32CharacLiveBody noti = new Noti32CharacLiveBody(who.uniqueId(), state);
        byte[] body = noti.toBytes();
        List<GameSession> members = party.view().members();
        for (GameSession member : members) {
            member.sendNow(Noti32CharacLiveBody.PACKET_ID, body);
        }
    }

    private static boolean checkFreeRevivalCondition(Party party, DungeonInstance dungeon) {
        if (party.quickParty()) {
            return true;
        }
        int difficulty = dungeon.run().difficulty();
        if (dungeon.run().coinQuota().freeRevival(difficulty)) {
            return true;
        }
        return false;
    }

    private static boolean checkUnLimitUsedCoinCondition(DungeonInstance dungeon) {
        return dungeon.run().coinQuota().dimensionPossible() > 0
                && com.dylan.game.event.EventService.isRunning(DIMENSION_ACTIVATION_EVENT);
    }

    private static String checkLimitUsedCoinCondition(Party party, DungeonInstance dungeon) {
        int used = party.usedCoinCount();
        var quota = dungeon.run().coinQuota();
        int difficulty = dungeon.run().difficulty();
        int dungeonIndex = dungeon.run().dungeonIndex();

        int memberLimit = quota.partyMemberCoinLimit();
        if (memberLimit != com.dylan.pvf.script.DungeonScript.CoinQuota.MEMBER_LIMIT_NONE
                && used >= memberLimit) {
            return "队伍 " + party.index() + " 本局已经用了 " + used + " 次，副本 "
                    + dungeonIndex + " 的 [party member coin limit] 是 " + memberLimit;
        }
        Integer byDifficulty = quota.limitCoinDiff(difficulty);
        if (byDifficulty != null && byDifficulty != DungeonRun.COIN_LIMIT_NONE
                && used >= byDifficulty) {
            return "队伍 " + party.index() + " 本局已经用了 " + used + " 次，副本 "
                    + dungeonIndex + " 的 [coin info] 在难度 " + difficulty
                    + " 那一档是 " + byDifficulty;
        }
        int limit = dungeon.run().coinLimit();
        if (limit != DungeonRun.COIN_LIMIT_NONE && used >= limit) {
            return "队伍 " + party.index() + " 本局已经用了 " + used + " 次，副本 "
                    + dungeonIndex + " 的 [coin limit] 是 " + limit;
        }
        int hellLimit = quota.hellCoinLimit();
        if (hellLimit == DungeonRun.COIN_LIMIT_NONE
                || checkHellCoinLimint(party, dungeon, hellLimit)) {
            return null;
        }
        return "队伍 " + party.index() + " 本局已经用了 " + used
                + " 次，深渊组还没清完，副本 " + dungeonIndex
                + " 的 [hell coin limit] 是 " + hellLimit;
    }

    private static boolean checkHellCoinLimint(Party party, DungeonInstance dungeon,
                                               int hellLimit) {
        if (!HellPartyService.HELL_DUNGEON_ENABLED) {
            return true;
        }
        if (party.dungeonType()
                != com.dylan.protocol.body.Cmd16SelectDungeonRequest.DUNGEON_TYPE_HELL_PARTY) {
            return true;
        }
        var field = dungeon.field();
        if (field != null && field.killedAllHellGroups()) {
            return true;
        }
        return party.usedCoinCount() < hellLimit;
    }

    private static final int DIMENSION_ACTIVATION_EVENT = 94;

    private static boolean inDungeonState(GameSession s) {
        SessionRegistry.Presence p = SessionRegistry.presenceOf(s);
        return p != null && !p.inTown();
    }

    private static void reject43(GameSession s, int errorCode, String why) {
        byte[] body = Cmd43ReplyBody.rejected(errorCode);
        s.reply(Cmd43ReplyBody.PACKET_ID, body);
        s.flush();
    }

    private static void reject44(GameSession s, int errorCode, String why) {
        byte[] body = Cmd44ReplyBody.rejected(errorCode);
        s.reply(Cmd44ReplyBody.PACKET_ID, body);
        s.flush();
    }
}
