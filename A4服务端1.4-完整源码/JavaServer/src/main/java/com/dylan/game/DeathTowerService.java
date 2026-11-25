package com.dylan.game;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Cmd16ReplyBody;
import com.dylan.protocol.body.Cmd16SelectDungeonRequest;
import com.dylan.protocol.body.Cmd169DeathTowerStageRequest;
import com.dylan.protocol.body.Cmd48MoveMapRequest;
import com.dylan.protocol.body.Cmd48ReplyBody;
import com.dylan.protocol.body.Noti154DeathTowerInfoBody;
import com.dylan.protocol.body.Noti155StartDeathTowerMapBody;
import com.dylan.protocol.body.Noti157DeathTowerRewardBody;
import com.dylan.protocol.body.Noti158DeathTowerEplpBody;
import com.dylan.pvf.script.DungeonScript;
import com.dylan.pvf.script.MapScript;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class DeathTowerService {
    private DeathTowerService() {
    }

    private static final int ERROR_INTERNAL = 1;

    private static final int ERROR_LEVEL = 14;

    private static final int ERROR_NO_TICKET = AncientDungeonService.ERROR_NO_TICKET;

    private static final int ERROR_NO_MAP = 21;

    private static final int ERROR_NO_DUNGEON = 22;

    private static final int PREPARE_FINISH_SECONDS = 5;

    private static final int REWARD_SECONDS = 1;

    private static final int EPLP_SECONDS = 5;

    private static final int EPLP_TIMEOUT_SECONDS = 60;

    public static boolean selectDungeon(Party party, GameSession initiator,
                                        DungeonScript dungeon, Cmd16SelectDungeonRequest req) {
        List<GameSession> members = party.view().members();

        if (dungeon.tower().maxStage() <= 0 || dungeon.tower().floorMaps().isEmpty()) {
            fail(initiator, ERROR_NO_DUNGEON, 0);
            return false;
        }

        int blocking = AncientDungeonService.checkEnter(party, dungeon);
        if (blocking != AncientDungeonService.PASSED) {
            party.view().at(blocking);
            fail(initiator, ERROR_NO_TICKET, blocking);
            ChatService.system(initiator, MSG_NO_TICKET);
            return false;
        }

        for (int slot = 0; slot < Party.MAX_MEMBERS; slot++) {
            GameSession member = party.view().at(slot);
            if (member != null && member.level() < dungeon.minimumLevel()) {
                fail(initiator, ERROR_LEVEL, 0);
                return false;
            }
        }

        DeathTower tower = new DeathTower(dungeon);
        int mapIndex = tower.stageMap(1);
        if (mapIndex < 0) {
            fail(initiator, ERROR_NO_MAP, 0);
            return false;
        }
        List<RoomMonster> monsters = consistMap(initiator, dungeon, mapIndex, 1);
        if (monsters == null) {
            fail(initiator, ERROR_INTERNAL, 0);
            return false;
        }
        tower.resetStage(mapIndex, monsters);
        tower.makeStartMemberInfo(snapshot(party));

        if (!AncientDungeonService.useItems(party, dungeon)) {
            fail(initiator, ERROR_INTERNAL, 0);
            return false;
        }

        DungeonRun run = new DungeonRun(dungeon.id(), req.difficulty(), 0,
                0, 0, 0, 0, 0, 0, mapIndex, dungeon.basisLevel(), true,
                dungeon.coinLimit());
        party.resetUsedCoin();
        party.setAllAlive();
        party.startDeathTower(run, tower.room());
        if (DeathTowerRegistry.create(tower, party) == null) {
            fail(initiator, ERROR_INTERNAL, 0);
            party.endDungeon();
            return false;
        }
        tower.towerState(DeathTower.STATE_PLAYING);

        byte[] info = new Noti154DeathTowerInfoBody(dungeon.id(), tower.endStage()).toBytes();
        byte[] stage = stagePacket(tower);

        DungeonService.armLoadingBarrier(party, "进塔 " + dungeon.id() + " 第 1 层");
        party.clearState().reset();
        for (GameSession member : members) {
            member.run(() -> {
                member.send(Noti154DeathTowerInfoBody.PACKET_ID, info);
                member.sendNow(Noti155StartDeathTowerMapBody.PACKET_ID, stage);
                member.runSummary().begin(member.exp());
                member.runSummary().countRoom(monsters.size());
            });
        }
        return true;
    }

    private static final String MSG_NO_TICKET = "没有进入的必需品";

    private static List<DeathTower.MemberInfo> snapshot(Party party) {
        List<DeathTower.MemberInfo> out = new ArrayList<>(Party.MAX_MEMBERS);
        for (int slot = 0; slot < Party.MAX_MEMBERS; slot++) {
            GameSession member = party.view().at(slot);
            if (member == null) {
                continue;
            }
            var row = member.character();
            out.add(new DeathTower.MemberInfo(member.characNo(),
                    member.characName() == null ? "" : member.characName(),
                    member.level(),
                    row == null ? 0 : row.job(),
                    row == null ? 0 : Math.max(0, row.growType())));
        }
        return out;
    }

    private static List<RoomMonster> consistMap(GameSession any, DungeonScript dungeon,
                                                int mapIndex, int stage) {
        if (!ServerConfig.SPAWN_MONSTERS) {
            return List.of();
        }
        try {
            var scripts = any.scripts();
            MapScript map = scripts == null ? null : scripts.map(mapIndex).orElse(null);
            if (map == null) {
                return List.of();
            }
            List<RoomMonster> monsters = new SpawnService(scripts).spawnTowerStage(map,
                    dungeon.basisLevel(), ThreadLocalRandom.current(),
                    ServerConfig.SEQ_ID_BASE);
            return monsters;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static byte[] stagePacket(DeathTower tower) {
        List<Noti155StartDeathTowerMapBody.Monster> wire = new ArrayList<>();
        for (RoomMonster m : tower.room().living()) {
            wire.add(m.toTowerMonster());
        }
        byte[] body = Noti155StartDeathTowerMapBody
                .floor(tower.currentStage(), tower.stageMapIndex(), wire).toBytes();
        return body;
    }

    private static void fail(GameSession s, int errorCode, int detail) {
        byte[] body = Cmd16ReplyBody.needsExtraByte(errorCode)
                ? Cmd16ReplyBody.failureWithDetail(errorCode, detail)
                : Cmd16ReplyBody.failure(errorCode);
        s.replyNow(Cmd16ReplyBody.PACKET_ID, body);
    }

    public static void stageCommand(GameSession s, byte[] plain) {
        Cmd169DeathTowerStageRequest req = Cmd169DeathTowerStageRequest.parse(plain);

        Party party = s.party().current();
        DeathTower tower = s.deathTower();
        if (tower == null) {
            return;
        }
        if (!party.isManager(s)) {
            return;
        }
        switch (req.command()) {
            case Cmd169DeathTowerStageRequest.CMD_START_STAGE -> {
                if (!tower.stageState(DeathTower.STAGE_PLAYING)) {
                    return;
                }
                if (party.checkAllMemberDie()) {
                    return;
                }
                tower.onStartStage(System.currentTimeMillis());
            }
            case Cmd169DeathTowerStageRequest.CMD_FINISH_STAGE -> {
                if (!tower.stageState(DeathTower.STAGE_CLEARED)) {
                    return;
                }
                tower.onFinishStage(System.currentTimeMillis());
            }
            default -> {
            }
        }
        s.flush();
    }

    public static boolean moveMap(GameSession s, Party party, DeathTower tower,
                                  Cmd48MoveMapRequest req) {
        if (party.checkAllMemberDie()) {
            rejectMove(party, ERROR_MEMBER_DIE);
            return true;
        }
        if (tower.stageState() <= DeathTower.STAGE_PLAYING) {
            rejectMove(party, Cmd48ReplyBody.ERROR_NOT_NOW);
            return true;
        }
        if (!tower.moveNextStage()) {
            rejectMove(party, ERROR_MEMBER_DIE);
            return true;
        }

        int stage = tower.currentStage();
        int mapIndex = tower.stageMap(stage);
        if (mapIndex < 0) {
            s.fail("塔 " + tower.dungeonIndex() + " 的第 " + stage
                    + " 层没有地图（官方 handleMoveMap 在这里抛错误码 " + ERROR_NO_MAP + "）");
            return true;
        }
        List<RoomMonster> monsters = consistMap(s, tower.dungeon(), mapIndex, stage);
        if (monsters == null) {
            s.fail("塔 " + tower.dungeonIndex() + " 第 " + stage + " 层的图 " + mapIndex
                    + " 读不出来（官方 handleMoveMap 在这里抛错误码 " + ERROR_INTERNAL + "）");
            return true;
        }
        tower.resetStage(mapIndex, monsters);
        party.currentRoom(tower.room());

        byte[] stagePacket = stagePacket(tower);
        List<GameSession> members = party.view().members();
        int living = monsters.size();
        DungeonService.armLoadingBarrier(party, "上到塔的第 " + stage + " 层");
        for (GameSession member : members) {
            member.run(() -> {
                member.runSummary().countRoom(living);
                member.sendNow(Noti155StartDeathTowerMapBody.PACKET_ID, stagePacket);
            });
        }
        return true;
    }

    private static final int ERROR_MEMBER_DIE = 22;

    private static void rejectMove(Party party, int errorCode) {
        byte[] body = Cmd48ReplyBody.rejected(errorCode);
        for (GameSession member : party.view().members()) {
            member.replyNow(Cmd48ReplyBody.PACKET_ID, body);
        }
    }

    public static void afterKillMonster(GameSession s, Party party, DeathTower tower) {
        if (!tower.checkClearStage()) {
            return;
        }
        tower.onFinishStage(System.currentTimeMillis());
        if (tower.currentStage() >= tower.endStage()) {
            armPrepareFinish(tower);
        }
    }

    public static List<com.dylan.protocol.body.Noti38Body.Drop> rollTowerDrops(
            GameSession s, Party party, DeathTower tower, RoomMonster monster) {
        if (!tower.dungeon().tower().itemDrop()) {
            return List.of();
        }
        com.dylan.pvf.GameScripts scripts = s.scripts();
        if (scripts == null) {
            return List.of();
        }
        List<Integer> itemIds = towerDropIds(scripts, monster);
        if (itemIds.isEmpty()) {
            return List.of();
        }
        int copies = Math.max(1, party == null ? 1 : party.memberCount());
        List<com.dylan.protocol.body.Noti38Body.Drop> wire = new ArrayList<>();
        for (int itemId : itemIds) {
            if (scripts.item(itemId).isEmpty()) {
                continue;
            }
            for (int i = 0; i < copies; i++) {
                var item = com.dylan.game.drop.GeneratedItem.item(itemId, 1, 0);
                int itemNo = s.room().drop(item, monster.seqId());
                wire.add(com.dylan.protocol.body.Noti38Body.Drop.of(itemNo, itemId, 1, 0));
            }
        }
        return wire;
    }

    private static List<Integer> towerDropIds(com.dylan.pvf.GameScripts scripts,
                                              RoomMonster monster) {
        var random = ThreadLocalRandom.current();
        List<Integer> out = new ArrayList<>();
        if (monster.type() <= MONSTER_ROLE_MAX) {
            var script = scripts.monster(monster.monsterId()).orElse(null);
            if (script == null || script.deathTowerItems().isEmpty()) {
                return out;
            }
            int id = com.dylan.pvf.script.MonsterScript.dropItemIndex(
                    random.nextInt(DROP_DENOMINATOR), script.deathTowerItems());
            if (id != com.dylan.pvf.script.MonsterScript.NO_DROP) {
                out.add(id);
            }
            return out;
        }
        var apc = scripts.aiCharacter(monster.monsterId()).orElse(null);
        if (apc == null) {
            return out;
        }
        for (var entry : apc.deathTowerItems()) {
            if (entry.weight() > random.nextInt(DROP_DENOMINATOR)) {
                out.add(entry.itemId());
            }
        }
        return out;
    }

    private static final int MONSTER_ROLE_MAX = 4;

    private static final int DROP_DENOMINATOR = 10_000;

    public static void afterDieUser(GameSession s, Party party, DeathTower tower) {
        if (!party.checkAllMemberDie()) {
            return;
        }
        tower.onFinishStage(System.currentTimeMillis());
        armPrepareFinish(tower);
    }

    public static void onLeaveUser(GameSession member, DeathTower tower) {
        member.deathTowerIndex(DeathTowerRegistry.NOT_IN_TOWER);
        member.inventory().removeTowerItems(
                "离开死亡之塔 " + tower.dungeonIndex() + "（官方 onLeaveUser 的 RemoveFPItem）");
        DeathTowerRegistry.destroyIfEmpty(tower);
    }

    private static void armPrepareFinish(DeathTower tower) {
        insertTowerTimer(tower, TimerMessage.DEATH_TOWER_PREPARE_FINISH, PREPARE_FINISH_SECONDS);
    }

    private static void insertTowerTimer(DeathTower tower, int message, int seconds) {
        TimerQueue.insertDeathTowerTimer(tower, message, seconds, message);
    }

    static {
        TimerQueue.registerDeathTower(TimerMessage.DEATH_TOWER_PREPARE_FINISH,
                (tower, key, param) -> onTowerTimer(tower, key,
                        TimerMessage.DEATH_TOWER_PREPARE_FINISH,
                        DeathTowerService::onPrepareFinishTimer));
        TimerQueue.registerDeathTower(TimerMessage.DEATH_TOWER_REWARD,
                (tower, key, param) -> onTowerTimer(tower, key, TimerMessage.DEATH_TOWER_REWARD,
                        DeathTowerService::onRewardTimer));
        TimerQueue.registerDeathTower(TimerMessage.DEATH_TOWER_EPLP,
                (tower, key, param) -> onTowerTimer(tower, key, TimerMessage.DEATH_TOWER_EPLP,
                        DeathTowerService::onEplpTimer));
        TimerQueue.registerDeathTower(TimerMessage.DEATH_TOWER_EPLP_TIMEOUT,
                (tower, key, param) -> onTowerTimer(tower, key,
                        TimerMessage.DEATH_TOWER_EPLP_TIMEOUT,
                        DeathTowerService::onEplpTimeout));
    }

    private static void onTowerTimer(DeathTower tower, int key, int message,
                                     java.util.function.BiConsumer<Party, DeathTower> body) {
        Party party = tower.party();
        if (party == null) {
            return;
        }
        if (!party.checkTimerKey(message, key)) {
            return;
        }
        body.accept(party, tower);
    }

    private static void onPrepareFinishTimer(Party party, DeathTower tower) {
        if (tower.towerState() <= 1 || party.memberCount() <= 0) {
            return;
        }
        if (party.checkAllMemberDie()) {
            beginTowerClearProcess(party, tower, false);
        } else if (tower.currentStage() >= tower.endStage()) {
            beginTowerClearProcess(party, tower, true);
        }
    }

    private static void beginTowerClearProcess(Party party, DeathTower tower, boolean cleared) {
        if (tower.towerState() > DeathTower.STATE_PLAYING) {
            return;
        }
        long playTimeMs = cleared ? tower.playTimeMs() : tower.lastStageTimeMs();
        tower.towerState(DeathTower.STATE_CLEARING);

        String memberInfo = DeathTowerRankingService.encode(tower.startMembers());
        for (int slot = 0; slot < Party.MAX_MEMBERS; slot++) {
            GameSession member = party.view().at(slot);
            if (member == null) {
                continue;
            }
            int mySlot = slot;
            member.run(() -> {
                int recordBonus = DeathTowerRankingService.settleMember(member, tower,
                        memberInfo, playTimeMs, cleared);
                DeathTowerRewardRules.generateOne(member, tower, mySlot, recordBonus, playTimeMs);
                member.flush();
            });
        }

        insertTowerTimer(tower, TimerMessage.DEATH_TOWER_REWARD, REWARD_SECONDS);
    }

    private static void onRewardTimer(Party party, DeathTower tower) {
        if (tower.towerState() <= DeathTower.STATE_PLAYING) {
            return;
        }
        var all = tower.allRewardItems();
        for (int slot = 0; slot < Party.MAX_MEMBERS; slot++) {
            GameSession member = party.view().at(slot);
            if (member == null) {
                continue;
            }
            int mySlot = slot;
            member.run(() -> {
                byte[] body = Noti157DeathTowerRewardBody
                        .of(tower.rewardExp(mySlot), all).toBytes();
                member.sendNow(Noti157DeathTowerRewardBody.PACKET_ID, body);
            });
        }
        tower.towerState(DeathTower.STATE_REWARDED);
        insertTowerTimer(tower, TimerMessage.DEATH_TOWER_EPLP, EPLP_SECONDS);
    }

    private static void onEplpTimer(Party party, DeathTower tower) {
        if (tower.towerState() <= DeathTower.STATE_CLEARING) {
            return;
        }
        boolean canRetry = everyoneHasTicket(tower, party);
        byte[] eplp = new Noti158DeathTowerEplpBody(canRetry).toBytes();
        for (GameSession member : party.view().members()) {
            member.run(() -> member.sendNow(Noti158DeathTowerEplpBody.PACKET_ID, eplp));
        }
        DeathTowerRewardRules.grant(party, tower);

        tower.towerState(DeathTower.STATE_EPLP);
        party.eplpState(Party.EPLP_CLEARED);
        insertTowerTimer(tower, TimerMessage.DEATH_TOWER_EPLP_TIMEOUT, EPLP_TIMEOUT_SECONDS);
    }

    private static void onEplpTimeout(Party party, DeathTower tower) {
        if (tower.towerState() != DeathTower.STATE_EPLP) {
            return;
        }
        finishDeathTower(party, tower, "结算菜单超时");
    }

    public static void finishDeathTower(Party party, DeathTower tower, String why) {
        tower.towerState(DeathTower.STATE_NONE);
        GameSession any = party.view().manager();
        if (any == null) {
            List<GameSession> members = party.view().members();
            if (members.isEmpty()) {
                DeathTowerRegistry.destroy(tower);
                return;
            }
            any = members.get(0);
        }
        GameSession leader = any;
        leader.run(() -> {
            TownService.returnToTown(leader, "死亡之塔收尾：" + why, true);
            DeathTowerRegistry.destroy(tower);
            leader.flush();
        });
    }

    private static boolean everyoneHasTicket(DeathTower tower, Party party) {
        return AncientDungeonService.everyoneHasTicket(party, tower.dungeon());
    }
}
