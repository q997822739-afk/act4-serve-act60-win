package com.dylan.game;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Cmd15ReplyBody;
import com.dylan.protocol.body.Cmd16ReplyBody;
import com.dylan.protocol.body.Cmd16SelectDungeonRequest;
import com.dylan.protocol.body.Cmd45ReplyBody;
import com.dylan.protocol.body.Cmd48MoveMapRequest;
import com.dylan.protocol.body.Cmd48ReplyBody;
import com.dylan.protocol.body.Cmd204DungeonEventStoryPauseRequest;
import com.dylan.protocol.body.Noti183DungeonEventStoryPauseBody;
import com.dylan.protocol.body.Noti23Body;
import com.dylan.protocol.body.Noti31Body;
import com.dylan.protocol.body.Noti24Body;
import com.dylan.protocol.body.Noti26Body;
import com.dylan.protocol.body.Noti27Body;
import com.dylan.protocol.body.Noti28Body;
import com.dylan.protocol.body.Noti29Body;
import com.dylan.protocol.body.Noti2Body;
import com.dylan.protocol.body.Noti2PartyEntry;
import com.dylan.protocol.body.Noti3UserStateBody;
import com.dylan.protocol.body.Noti30Body;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class DungeonService {

    public static final int CMD_FINISH_LOADING = 40;

    private DungeonService() {
    }

    public static void startGame(GameSession s) {
        if (!ServerConfig.DUNGEON_ENTRY) {
            return;
        }
        if (s.inDungeon()) {
            return;
        }
        if (!checkPvpChannel(s)) {
            return;
        }

        // 城镇队伍（PartyInviteService）和副本用的 Party 原来是两套。队长点进本只给自己建了单人 Party，
        // 队员还在城门口，客户端就把他挡在副本外面。这里把城镇队员并进同一支非单人 Party，后面的
        // NOTI 27/28/29、加载屏障都按这支队伍发。位置互相同步仍要 CMD 2 的 UDP，见联机清单。
        if (ServerConfig.A4) {
            List<GameSession> town = PartyInviteService.members(s);
            if (town.size() >= 2) {
                if (town.get(0) != s) {
                    ChatService.system(s, MSG_ONLY_LEADER_SELECTS);
                    s.flush();
                    return;
                }
                if (!townReady(s, town)) {
                    return;
                }
                Party party = adoptTownParty(town);
                gameStart(party, s);
                return;
            }
        }

        Party party = s.party().current();
        if (party == null) {
            party = PartyRegistry.createSinglePlay(s, defaultSingleTitle(s));
        } else if (!checkMemberArea(s, party)) {
            return;
        }

        gameStart(party, s);
    }

    /** 城镇队员都在同一城镇区域才进本。有人在副本里或别的区域就整队不进，避免把别人从原副本里拽出来。 */
    private static boolean townReady(GameSession s, List<GameSession> town) {
        int townId = -1;
        int area = -1;
        for (GameSession member : town) {
            SessionRegistry.Presence presence = SessionRegistry.presenceOf(member);
            if (presence == null || !presence.inTown()) {
                s.replyNow(Cmd15ReplyBody.PACKET_ID, Cmd15ReplyBody.failure(ERROR_MEMBER_AREA));
                ChatService.system(s, MSG_MEMBER_NOT_HERE);
                s.flush();
                return false;
            }
            if (townId < 0) {
                townId = presence.town();
                area = presence.area();
            } else if (presence.town() != townId || presence.area() != area) {
                s.replyNow(Cmd15ReplyBody.PACKET_ID, Cmd15ReplyBody.failure(ERROR_MEMBER_AREA));
                ChatService.system(s, MSG_MEMBER_NOT_HERE);
                s.flush();
                return false;
            }
        }
        return true;
    }

    private static Party adoptTownParty(List<GameSession> town) {
        GameSession leader = town.get(0);
        Party existing = PartyRegistry.partyOf(leader);
        if (existing != null && !existing.singlePlay()
                && existing.state() == DungeonInstance.STATE_TOWN
                && sameMembers(existing, town)) {
            for (GameSession member : town) {
                PartyRegistry.bind(member, existing);
            }
            return existing;
        }
        for (GameSession member : town) {
            Party old = PartyRegistry.partyOf(member);
            if (old == null || old.state() != DungeonInstance.STATE_TOWN) {
                continue;
            }
            member.party().onLeaveDungeon("并入城镇队伍进本");
        }
        String name = leader.characName();
        Party party = PartyRegistry.create(leader,
                (name == null || name.isEmpty() ? "队伍" : name), Party.MAX_MEMBERS);
        for (int i = 1; i < town.size(); i++) {
            GameSession member = town.get(i);
            int joined = party.join(member);
            if (joined == Party.JOIN_OK || joined == Party.JOIN_ALREADY_IN) {
                PartyRegistry.bind(member, party);
            }
        }
        return party;
    }

    private static boolean sameMembers(Party party, List<GameSession> town) {
        List<GameSession> now = party.view().members();
        if (now.size() != town.size()) {
            return false;
        }
        for (int i = 0; i < town.size(); i++) {
            if (now.get(i) != town.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static String defaultSingleTitle(GameSession s) {
        String name = s.characName();
        return (name == null || name.isEmpty() ? String.valueOf(s.characNo()) : name) + "（单人）";
    }

    private static boolean checkMemberArea(GameSession s, Party party) {
        Party.View view = party.view();
        int town = -1;
        int area = -1;
        for (GameSession member : view.members()) {
            SessionRegistry.Presence presence = SessionRegistry.presenceOf(member);
            if (presence == null) {
                continue;
            }
            if (town < 0) {
                town = presence.town();
                area = presence.area();
            } else if (presence.town() != town || presence.area() != area) {
                s.replyNow(Cmd15ReplyBody.PACKET_ID, Cmd15ReplyBody.failure(ERROR_MEMBER_AREA));
                ChatService.system(s, MSG_MEMBER_NOT_HERE);
                return false;
            }
        }
        return true;
    }

    private static boolean checkPvpChannel(GameSession s) {
        // 按连接判（登录回包频道类型）。进程里同时开 7002/7003/7004 三个频道，进程级静态量会串。
        if (!s.pvpChannel()) {
            return true;
        }
        s.replyNow(Cmd15ReplyBody.PACKET_ID, Cmd15ReplyBody.failure(ERROR_PVP_CHANNEL));
        return false;
    }

    private static final int ERROR_PVP_CHANNEL = 8;

    private static final int ERROR_MEMBER_AREA = 21;

    private static final String MSG_MEMBER_NOT_HERE = "对方不在城镇内";

    private static void gameStart(Party party, GameSession initiator) {
        Party.View view = party.view();
        List<GameSession> members = view.members();

        party.setAllAlive();

        java.util.Map<GameSession, byte[]> rosters = new java.util.HashMap<>();
        for (GameSession member : members) {
            byte[] mine = rosterPacketFor(view, member);
            if (mine != null) {
                rosters.put(member, mine);
            }
        }

        for (GameSession member : members) {
            member.run(() -> {
                member.rememberTownPosition();
                sendUserArea(member);
            });
        }
        announceEnterDungeon(members, party);
        byte[] startGame = startGameBody(initiator);
        for (GameSession member : members) {
            member.run(() -> {
                byte[] mine = rosters.get(member);
                if (mine != null) {
                    member.send(Noti2Body.PACKET_ID, mine);
                }
                sendUdpHost(member, view.hostSlot());
                openDungeonSelect(member, startGame,
                        "应 CMD 15（队伍 " + party.index() + "）");
                member.flush();
            });
        }
    }

    private static void announceEnterDungeon(List<GameSession> members, Party party) {
        List<Noti3UserStateBody.Entry> entries = new java.util.ArrayList<>(members.size());
        for (GameSession member : members) {
            if (!member.hasCharacter()) {
                continue;
            }
            entries.add(new Noti3UserStateBody.Entry(member.uniqueId(),
                    Noti3UserStateBody.STATE_IN_DUNGEON));
        }
        if (entries.isEmpty()) {
            return;
        }
        NeighborService.announceUserState(new Noti3UserStateBody(entries),
                "队伍 " + party.index() + " 进副本");
    }

    private static void sendUserArea(GameSession s) {
        if (!ServerConfig.USER_AREA) {
            return;
        }
        if (!s.hasCharacter()) {
            return;
        }
        boolean fromClient = s.hasPosition();
        int x = fromClient ? s.lastX() : ServerConfig.USER_AREA_X;
        int y = fromClient ? s.lastY() : ServerConfig.USER_AREA_Y;
        byte[] area = Noti23Body.enterDungeon(s.uniqueId(), s.townId(), x, y,
                Noti24Body.DIRECTION_RIGHT, ServerConfig.VISIBLE_VALUES).toBytes();
        s.send(Noti23Body.PACKET_ID, area);

        // 城镇互见：同区域的人收到他进了副本区域 255，客户端把他移出城镇场景
        SessionRegistry.Presence before = SessionRegistry.presenceOf(s);
        if (before != null && before.inTown()) {
            NeighborService.leaveArea(s, before.town(), before.area(),
                    Noti23Body.enterDungeon(s.uniqueId(), before.town(), x, y, Noti24Body.DIRECTION_RIGHT,
                            NeighborService.A4_VISIBLE));
        }
        SessionRegistry.enterDungeon(s);
    }

    private static byte[] rosterPacketFor(Party.View view, GameSession recipient) {
        if (!ServerConfig.ROSTER_BEFORE_START_GAME) {
            return null;
        }
        List<GameSession> ordered = new java.util.ArrayList<>(view.members());
        if (recipient != null && ordered.remove(recipient)) {
            ordered.add(recipient);
        }
        List<Noti2PartyEntry> entries = new java.util.ArrayList<>(Party.MAX_MEMBERS);
        for (GameSession member : ordered) {
            SessionRegistry.Presence presence = SessionRegistry.presenceOf(member);
            if (presence == null || presence.detail() == null) {
                continue;
            }
            entries.add(presence.detail());
        }
        if (entries.isEmpty()) {
            return null;
        }
        byte[] roster = Noti2Body.partyRoster(entries).toBytes();
        logRosterLayout(entries, roster);
        return roster;
    }

    private static void logRosterLayout(List<Noti2PartyEntry> entries, byte[] roster) {
        int at = 3;
        StringBuilder layout = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            int len = entries.get(i).toBytes(Noti2Body.SUBTYPE_PARTY_ROSTER).length;
            layout.append(layout.isEmpty() ? "" : "；")
                    .append('#').append(i)
                    .append(" userId=").append(entries.get(i).userId())
                    .append(" 偏移 ").append(at).append("..").append(at + len)
                    .append("（").append(len).append(" 字节）");
            at += len;
        }
    }

    private static void sendUdpHost(GameSession member, int hostSlot) {
        if (!ServerConfig.UDP_HOST) {
            return;
        }
        byte[] body = new Noti26Body(hostSlot).toBytes();
        member.send(Noti26Body.PACKET_ID, body);
    }

    private static void openDungeonSelect(GameSession s, byte[] body, String why) {
        if (s.dungeonSelectOpened()) {
            return;
        }
        s.dungeonSelectOpened(true);
        if (ServerConfig.A4) {
            // A4 官方打开选副本时在 NOTI 27 前重发一次 NOTI 5 副本权限全表（cmd_sweep / 回放 CMD 15）。
            s.dungeonClear().sendFullList("打开选副本");
        }
        s.sendNow(Noti27Body.PACKET_ID, body);
    }

    private static byte[] startGameBody(GameSession initiator) {
        Party party = initiator.party().current();
        Noti27Body body = party == null
                ? Noti27Body.noAbyssBlockers()
                : HellPartyService.startGame(party, initiator);
        return body.toBytes();
    }

    public static void selectDungeon(GameSession s, byte[] plain) {
        Cmd16SelectDungeonRequest req = Cmd16SelectDungeonRequest.parse(plain);

        if (!ServerConfig.DUNGEON_ENTRY) {
            return;
        }

        int error = checkError(s, req);
        if (error != CHECK_OK) {
            if (error == CHECK_SILENT) {
                return;
            }
            s.replyNow(Cmd16ReplyBody.PACKET_ID, Cmd16ReplyBody.failure(error & 0xFF));
            return;
        }

        Party party = s.party().current();
        if (party == null) {
            return;
        }
        var script = s.scripts() == null ? null
                : s.scripts().dungeon(req.dungeonIndex()).orElse(null);
        if (script != null && script.isTower()) {
            DeathTowerService.selectDungeon(party, s, script, req);
            s.flush();
            return;
        }

        if (!checkDungeonDifficulty(s, req)) {
            return;
        }
        if (!checkMemberEligible(s, party, script, req)) {
            return;
        }
        if (!checkMemberFatigue(s, party, req)) {
            return;
        }
        if (!HellPartyService.checkHavePassItem(party, s, req.dungeonType())) {
            return;
        }
        if (!checkRequiredItems(s, party, script)) {
            return;
        }
        byte[] body = Cmd16ReplyBody.success();
        s.replyNow(Cmd16ReplyBody.PACKET_ID, body);

        enterDungeon(party, s, req);
    }

    private static final int CHECK_OK = 0;

    private static final int CHECK_SILENT = 0x7FFFFFFF;

    private static int checkError(GameSession s, Cmd16SelectDungeonRequest req) {
        if (!s.dungeonSelectOpened()) {
            if (ServerConfig.A4) {
                // 依据：A4 官方实抓（sweep_town CMD 16，包体 10270000…，城里没打开选副本界面）= REPLY 16 `00 04`；
                // 同一份抓包里已经进了本再发 10000 号本也是 `00 04`（sweep_dungeon d10000），4 = 无效/不可进入的地下城。
                // 客户端 00411F60 读 u8 结果 + u8 错误码，失败时按错误码显示文本并解锁「选择地下城」按钮；
                // 原版 A5 这里 CHECK_SILENT 静默丢，A4 客户端会一直等回包。
                // 只补回包，校验条件一个字没动（开了选副本界面之后照旧走原来的分支）。
                return 4;
            }
            return CHECK_SILENT;
        }
        if (req.dungeonType() > Cmd16SelectDungeonRequest.DUNGEON_TYPE_NO_PARTY_CHECK
                || req.difficulty() > 0x7F) {
            return -1;
        }
        if (req.quickParty() >= 2) {
            return -2;
        }
        if (req.dungeonType() > 0 && req.quickParty() > 0) {
            return CHECK_SILENT;
        }
        if (req.dungeonType() == Cmd16SelectDungeonRequest.DUNGEON_TYPE_NO_PARTY_CHECK) {
            return CHECK_OK;
        }
        Party party = s.party().current();
        if (party == null) {
            return CHECK_SILENT;
        }
        if (!party.isManager(s)) {
            ChatService.system(s, MSG_ONLY_LEADER_SELECTS);
            s.flush();
            return CHECK_SILENT;
        }
        if (party.state() != DungeonInstance.STATE_TOWN) {
            return CHECK_SILENT;
        }
        return CHECK_OK;
    }

    private static final String MSG_ONLY_LEADER_SELECTS = "只有队长能选择地下城";

    /**
     * A4 普通副本的 .dgn {@code [required item] 物品 数量 是否消耗}（毕勒马克 3209、王的遗迹 / 千年迷宫 4083 …）。
     * 原版 A5 只在死亡之塔查；官方实抓（probe_ticket.py）进本时扣 1 张、NOTI 29 前发 NOTI 14，
     * 没票回 REPLY 16 {@code 00 11 <槽>}（客户端 00411F60：0x11 = 文本 0x2DA 没有入场道具）。
     */
    private static boolean checkRequiredItems(GameSession s, Party party,
                                              com.dylan.pvf.script.DungeonScript script) {
        if (!ServerConfig.A4 || script == null) {
            return true;
        }
        int blocking = AncientDungeonService.checkEnter(party, script);
        if (blocking == AncientDungeonService.PASSED) {
            return true;
        }
        s.replyNow(Cmd16ReplyBody.PACKET_ID, Cmd16ReplyBody.failureWithDetail(
                AncientDungeonService.ERROR_NO_TICKET, blocking));
        return false;
    }

    private static void spendFatigueOnEnter(GameSession s, Cmd16SelectDungeonRequest req) {
        if (req.isTutorial()) {
            return;
        }
        s.fatigue().fatigueUp(ServerConfig.FATIGUE_PER_ROOM, "进副本 " + req.dungeonIndex());
    }

    private static boolean checkMemberFatigue(GameSession s, Party party,
                                              Cmd16SelectDungeonRequest req) {
        if (req.isTutorial()) {
            return true;
        }
        Party.View view = party.view();
        for (int slot = 0; slot < Party.MAX_MEMBERS; slot++) {
            GameSession member = view.at(slot);
            if (member == null || member.fatigue().checkFatigue()) {
                continue;
            }
            member.fatigue();
            s.replyNow(Cmd16ReplyBody.PACKET_ID, Cmd16ReplyBody.failure(ERROR_MEMBER_FATIGUE));
            if (member != s) {
                ChatService.system(s, "队员" + describe(member) + "的疲劳值不足");
            }
            return false;
        }
        return true;
    }

    private static String describe(GameSession member) {
        String name = member.characName();
        return name == null || name.isEmpty() ? "#" + member.characNo() : "「" + name + "」";
    }

    private static final int ERROR_MEMBER_FATIGUE = 22;

    private static boolean checkDungeonDifficulty(GameSession s,
                                                  Cmd16SelectDungeonRequest req) {
        if (req.difficulty() == 0) {
            return true;
        }
        int cleared = s.dungeonClear().difficultyOf(req.dungeonIndex());
        if (cleared != 0
                && (cleared >= req.difficulty() || req.difficulty() == UNCHECKED_DIFFICULTY)) {
            return true;
        }
        s.replyNow(Cmd16ReplyBody.PACKET_ID, Cmd16ReplyBody.failure(ERROR_DUNGEON_DIFFICULTY));
        return false;
    }

    private static final int ERROR_DUNGEON_DIFFICULTY = 19;

    /**
     * 组队进本时逐个查队员能不能进（原来只查队长：客户端选本界面只按队长自己的等级 / 权限 / 任务过滤）。
     * 依据都在 PVF：.dgn {@code [minimum required level]}；NOTI 5 的通关难度（同 {@link #checkDungeonDifficulty}）；
     * 世界地图 .wdm {@code [dungeon]} 每行「副本号 任务号」= 接了或做完这个任务才列出这个副本（-1 不要求，
     * 同一副本多行任一满足即可，如 granfloris2 的 9 号要 116 或 4）；选深渊时 .wdm {@code [hell quest]} 每人都要做完
     * （客户端 NOTI 27 标志 0 时提示文本 5431，但服务端原来不拦）。
     * 失败回 REPLY 16 {@code 00 13}（00411B82：文本 727「处于无法选择状态」），再用系统消息说是谁、差什么。
     */
    private static boolean checkMemberEligible(GameSession s, Party party,
                                               com.dylan.pvf.script.DungeonScript script,
                                               Cmd16SelectDungeonRequest req) {
        if (!ServerConfig.A4 || req.isTutorial()) {
            return true;
        }
        var worldMap = HellPartyService.worldMapAt(s);
        List<com.dylan.pvf.script.WorldMapScript.Dungeon> rows = worldMap
                .map(m -> m.dungeons().stream()
                        .filter(d -> d.dungeonIndex() == req.dungeonIndex()).toList())
                .orElse(List.of());
        boolean questGated = !rows.isEmpty() && rows.stream().allMatch(d -> d.questIndex() >= 0);
        for (GameSession member : party.view().members()) {
            String why = null;
            if (script != null && member.level() < script.minimumLevel()) {
                why = "等级不足（需要 Lv" + script.minimumLevel() + "）";
            } else if (!difficultyOpen(member, req)) {
                why = "还没有开放这个难度";
            } else if (questGated && rows.stream().noneMatch(d -> questSatisfied(member, d))) {
                why = "没有接受或完成进入该地下城所需的任务";
            }
            if (why != null) {
                rejectMember(s, member, why);
                return false;
            }
        }
        if (req.isHellParty() && worldMap.isPresent() && worldMap.get().isInHellDungeon()) {
            List<Integer> blockers = HellPartyService.checkClearQuestHellParty(party, worldMap.get());
            if (!blockers.isEmpty()) {
                GameSession member = party.view().at(blockers.get(0));
                rejectMember(s, member == null ? s : member, "没有完成深渊地下城任务");
                return false;
            }
        }
        return true;
    }

    private static boolean difficultyOpen(GameSession member, Cmd16SelectDungeonRequest req) {
        if (req.difficulty() == 0) {
            return true;
        }
        int cleared = member.dungeonClear().difficultyOf(req.dungeonIndex());
        return cleared != 0
                && (cleared >= req.difficulty() || req.difficulty() == UNCHECKED_DIFFICULTY);
    }

    private static boolean questSatisfied(GameSession member,
                                          com.dylan.pvf.script.WorldMapScript.Dungeon row) {
        var quests = member.quests();
        if (quests == null) {
            return false;
        }
        return quests.isRunning(row.questIndex())
                || (!row.inProgress() && quests.cleared().isCleared(row.questIndex()));
    }

    private static void rejectMember(GameSession leader, GameSession member, String why) {
        leader.replyNow(Cmd16ReplyBody.PACKET_ID, Cmd16ReplyBody.failure(ERROR_DUNGEON_DIFFICULTY));
        String text = (member == leader ? "你" : "队员" + describe(member)) + why + "，无法进入地下城";
        ChatService.system(leader, text);
        leader.flush();
        if (member != leader) {
            member.run(() -> {
                ChatService.system(member, "你" + why + "，队伍无法进入地下城");
                member.flush();
            });
        }
    }

    private static final int UNCHECKED_DIFFICULTY = 4;

    private static DungeonRun.PartyQuests partyQuests(Party party, int dungeonType) {
        List<GameSession> members = party.view().members();
        return new DungeonRun.PartyQuests() {
            @Override
            public boolean checkQuestConnection(int questId, int questType) {
                for (GameSession member : members) {
                    var quests = member.quests();
                    if (quests == null) {
                        continue;
                    }
                    boolean hit = questType
                            == com.dylan.pvf.script.DungeonScript.QuestConnection.TYPE_CLEARED
                            ? quests.cleared().isCleared(questId)
                            : quests.isRunning(questId);
                    if (hit) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public int memberCount() {
                return members.size();
            }

            @Override
            public int questStageMap(int dungeonIndex, int difficulty,
                                     java.util.random.RandomGenerator random) {
                if (dungeonType
                        == com.dylan.protocol.body.Cmd16SelectDungeonRequest
                                .DUNGEON_TYPE_HELL_PARTY) {
                    return DungeonRun.NO_QUEST_STAGE_MAP;
                }
                for (GameSession member : members) {
                    int map = rescueQuestMap(member, dungeonIndex, difficulty, random);
                    if (map > 0) {
                        return map;
                    }
                }
                return DungeonRun.NO_QUEST_STAGE_MAP;
            }
        };
    }

    private static int rescueQuestMap(GameSession member, int dungeonIndex, int difficulty,
                                      java.util.random.RandomGenerator random) {
        com.dylan.pvf.GameScripts scripts = member.scripts();
        if (scripts == null) {
            return DungeonRun.NO_QUEST_STAGE_MAP;
        }
        var hit = QuestService.rescueQuestMap(member.quests(), scripts::quest,
                dungeonIndex, difficulty, random);
        if (hit.isNone()) {
            return DungeonRun.NO_QUEST_STAGE_MAP;
        }
        return hit.mapIndex();
    }

    private static void enterDungeon(Party party, GameSession initiator,
                                     Cmd16SelectDungeonRequest req) {
        List<GameSession> members = party.view().members();
        party.resetUsedCoin();
        party.dungeonType(req.dungeonType());
        party.quickParty(req.quickParty() == Cmd16SelectDungeonRequest.QUICK_PARTY_ON);

        com.dylan.pvf.script.DungeonScript dungeonScript = initiator.scripts() == null ? null
                : initiator.scripts().dungeon(req.dungeonIndex()).orElse(null);
        boolean randomAppear = HellPartyService.checkRandomAppearHellDungeon(
                party, initiator, dungeonScript, req.dungeonType(), req.quickParty());
        HellPartyService.HellPartyEntry hell = HellPartyService.selectDungeon(
                party, initiator, dungeonScript, req.dungeonType(), randomAppear,
                ThreadLocalRandom.current());
        if (hell == null) {
            initiator.fail("深渊派对难度掷不出来（" + com.dylan.pvf.script.HellPartyTable.PATH
                    + " 的 [difficulty] 表是空的？），官方在这里整个进本失败");
            return;
        }

        DungeonRun run = DungeonRun.plan(initiator.scripts(), req.dungeonIndex(),
                req.difficulty(), ServerConfig.FALLBACK_MAP_INDEX, ThreadLocalRandom.current(),
                partyQuests(party, req.dungeonType()), hell.appeared());
        if (!run.fromScripts()) {
            initiator.fail("副本 " + run.dungeonIndex() + " 没能从 pvf 里读出来"
                    + "（pvf 关着 / 不在清单 / 地图池是空的 / 所有迷宫都要任务而队伍一个都没接），"
                    + "不发兜底副本");
            return;
        }

        byte[] info = run.dungeonInfo().toBytes();

        BattleField field = openBattleField(party, initiator, run, hell);
        if (field == null) {
            return;
        }
        int mode = ServerConfig.START_MAP_MODE;
        BattleField.SpawnOutcome spawned = spawnFor(initiator,
                field.startRoomSpawnRequest(run.mapIndex()));
        List<RoomMonster> monsters = spawned.monsters();
        DungeonRoom startRoom = DungeonRoom.of(monsters);
        startRoom.resetHellGroups(spawned.hellGroups());
        BattleField.lockIntoObjects(startRoom, spawned);
        Noti29Body startMap = run.startMap(mode, monsters, ServerConfig.ENTITY_EXTRA_FLAG,
                field.hellPartyDifficulty(), field.hellPartyAppeared(),
                ObjectDropService.wireOf(startRoom));
        field.enterStartRoom(run.mapIndex(), startRoom);
        party.startDungeon(run, field, startRoom);
        for (GameSession member : party.view().members()) {
            member.run(() -> CreatureService.turnStomach(member, true));
        }
        if (!consumeSealDoorItems(party, initiator, req)) {
            return;
        }
        if (ServerConfig.A4 && dungeonScript != null && !dungeonScript.isTower()) {
            // 门票：selectDungeon 里 checkRequiredItems 已查过每人都够
            AncientDungeonService.useItems(party, dungeonScript);
        }
        byte[] map = startMap.toBytes();
        int roomCount = startRoomClearable(monsters);
        armLoadingBarrier(party, "进副本 " + run.dungeonIndex());
        byte[] startGame = startGameBody(initiator);
        party.clearState().reset();
        for (GameSession member : members) {
            member.run(() -> {
                openDungeonSelect(member, startGame,
                        "兜底：没经过 CMD 15 就直接发了 CMD 16");
                member.send(Noti28Body.PACKET_ID, info);
                member.runSummary().begin(member.exp());
                member.runSummary().countRoom(roomCount);
                member.sendNow(Noti29Body.PACKET_ID, map);
                spendFatigueOnEnter(member, req);
            });
        }

    }

    private static int startRoomClearable(List<RoomMonster> monsters) {
        int n = 0;
        for (RoomMonster m : monsters) {
            if (m.countsForRoomClear()) {
                n++;
            }
        }
        return n;
    }

    private static boolean consumeSealDoorItems(Party party, GameSession initiator,
                                                Cmd16SelectDungeonRequest req) {
        if (!req.isHellParty() || !party.hellPassChecked()) {
            return true;
        }
        var worldMap = HellPartyService.worldMapOf(party, initiator);
        if (worldMap.isEmpty() || !worldMap.get().isInHellDungeon()) {
            return true;
        }
        if (HellPartyService.useSealDoorItems(party, initiator)) {
            return true;
        }
        return false;
    }

    private static BattleField openBattleField(Party party, GameSession initiator,
                                               DungeonRun run,
                                               HellPartyService.HellPartyEntry hell) {
        try {
            return BattleField.of(initiator.scripts(), run, ThreadLocalRandom.current(), hell);
        } catch (RuntimeException e) {
            initiator.fail("副本 " + run.dungeonIndex() + " 的场地建不起来，换房间会整段错乱", e);
            return null;
        }
    }

    public static void moveMap(GameSession s, byte[] plain) {
        Cmd48MoveMapRequest req = Cmd48MoveMapRequest.parse(plain);

        Party inTower = s.party().current();
        DeathTower tower = s.deathTower();
        if (tower != null) {
            DeathTowerService.moveMap(s, inTower, tower, req);
            s.flush();
            return;
        }

        BattleField field = s.battleField();
        if (!s.inDungeon() || field == null) {
            reject(s, Cmd48ReplyBody.ERROR_NOT_NOW);
            return;
        }
        if (field.isCleared()) {
            reject(s, Cmd48ReplyBody.ERROR_NOT_NOW);
            return;
        }

        if (req.roomX() >= field.maze().width() || req.roomY() >= field.maze().height()) {
            s.flush();
            return;
        }

        Party party = s.party().current();
        if (party == null) {
            reject(s, Cmd48ReplyBody.ERROR_NOT_NOW);
            return;
        }

        BattleField.MoveResult outcome = field.moveMap(req.roomX(), req.roomY(),
                request -> spawnFor(s, request));
        if (!outcome.ok()) {
            reject(s, outcome.error());
            return;
        }

        party.currentRoom(outcome.room());
        // 换房走通：官方回 1 字节 01（sweep_dungeon / scen_off 每次成功换房都有 REPLY 48 01）。
        s.reply(Cmd48ReplyBody.PACKET_ID, Cmd48ReplyBody.accepted());
        List<GameSession> members = party.view().members();
        byte[] room = roomPacket(req.roomX(), req.roomY(), outcome, field, party.index(),
                members.size());
        int living = outcome.room().clearableLivingCount();
        armLoadingBarrier(party, "换到房间 (" + req.roomX() + "," + req.roomY() + ")");
        for (GameSession member : members) {
            member.run(() -> {
                if (!outcome.revisit()) {
                    member.runSummary().countRoom(living);
                }
                member.sendNow(Noti29Body.PACKET_ID, room);
                spendFatigueOnMoveMap(member, outcome);
            });
        }
    }

    private static void spendFatigueOnMoveMap(GameSession s, BattleField.MoveResult outcome) {
        if (outcome.revisit()) {
            return;
        }
        DungeonRun run = s.dungeon();
        if (run != null
                && run.dungeonIndex() == Cmd16SelectDungeonRequest.TUTORIAL_DUNGEON_INDEX) {
            return;
        }
        s.fatigue().fatigueUp(ServerConfig.FATIGUE_PER_ROOM, "换房间");
    }

    private static BattleField.SpawnOutcome spawnFor(GameSession s,
                                                     BattleField.SpawnRequest req) {
        if (!ServerConfig.SPAWN_MONSTERS) {
            return BattleField.SpawnOutcome.of(List.of());
        }
        SpawnService spawner = new SpawnService(s.scripts());
        var random = ThreadLocalRandom.current();
        List<RoomMonster> monsters = spawner.spawn(req.mapIndex(), req.basisLevel(), random,
                ServerConfig.SEQ_ID_BASE, req.conditionMonsterIds());
        var map = s.scripts() == null ? null : s.scripts().map(req.mapIndex()).orElse(null);
        monsters = SpawnService.addPassiveMonsters(map, monsters, random);
        java.util.Map<Integer, Integer> hellGroups = new java.util.LinkedHashMap<>();
        if (req.hellDifficulty() != HellPartyService.DIFFICULTY_NONE && s.scripts() != null) {
            monsters = spawner.addHellPartyMonsters(map, monsters, req.basisLevel(),
                    req.hellDifficulty(), random, hellGroups);
        }
        monsters = spawner.addAiCharacters(map, monsters);
        monsters = SpawnService.addAdditionalChampions(monsters, req.championCount(), random);
        monsters = SpawnService.addEventMonsters(map, monsters, req.eventMonsters(),
                req.basisLevel());
        monsters = sandbag(monsters);
        return new BattleField.SpawnOutcome(monsters, hellGroups,
                objectDropsFor(s, req, map, random));
    }

    /** {@code -Ds1a5.sandbag=怪物号:等级[:只数]}：见 {@link ServerConfig#SANDBAG}。 */
    private static List<RoomMonster> sandbag(List<RoomMonster> monsters) {
        String spec = ServerConfig.SANDBAG;
        java.nio.file.Path file = java.nio.file.Path.of("sandbag.txt");
        if (java.nio.file.Files.isRegularFile(file)) {
            // 工作目录有 sandbag.txt 就以它为准，每次刷怪现读：换沙袋不用重启服务端
            try {
                spec = java.nio.file.Files.readString(file).trim();
            } catch (java.io.IOException e) {
                System.out.println("[沙袋] 读 sandbag.txt 失败：" + e.getMessage());
            }
        }
        if (spec.isBlank() || monsters.isEmpty()) {
            return monsters;
        }
        String[] p = spec.split(":");
        int id = Integer.parseInt(p[0].trim());
        int level = p.length > 1 ? Integer.parseInt(p[1].trim()) : monsters.get(0).level();
        int count = p.length > 2 ? Integer.parseInt(p[2].trim()) : 1;
        RoomMonster first = monsters.get(0);
        List<RoomMonster> out = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(new RoomMonster(i, first.seqId() + i, id, level, 0,
                    first.x() + 40 * i, first.y(), 0, 0));
        }
        return out;
    }

    private static List<ObjectDropService.ObjectDrop> objectDropsFor(
            GameSession s, BattleField.SpawnRequest req,
            com.dylan.pvf.script.MapScript map, java.util.random.RandomGenerator random) {
        if (s.scripts() == null || req.dungeonIndex() == BattleField.SpawnRequest.NO_DUNGEON) {
            return List.of();
        }
        var dungeon = s.scripts().dungeon(req.dungeonIndex()).orElse(null);
        if (dungeon == null) {
            return List.of();
        }
        return new ObjectDropService(s.scripts())
                .fill(map, dungeon, req.basisLevel(), req.dungeonDiff(), random);
    }

    private static byte[] roomPacket(int roomX, int roomY, BattleField.MoveResult outcome,
                                     BattleField field, int partyIndex, int memberCount) {
        int hellDifficulty = field.hellPartyDifficulty();
        int hellAppeared = field.hellPartyAppeared() ? 1 : 0;
        Noti29Body body;
        if (outcome.revisit()) {
            body = Noti29Body.visitedRoom(roomX, roomY, hellDifficulty);
        } else {
            List<Noti29Body.Entity> entities = outcome.room().living().stream()
                    .map(m -> m.toEntity(ServerConfig.ENTITY_EXTRA_FLAG)).toList();
            body = new Noti29Body(roomX, roomY, 0, hellDifficulty, outcome.mode(),
                    outcome.mapIndex(), entities, ObjectDropService.wireOf(outcome.room()),
                    hellAppeared);
        }
        byte[] bytes = body.toBytes();
        return bytes;
    }

    private static void reject(GameSession s, int errorCode) {
        byte[] body = Cmd48ReplyBody.rejected(errorCode);
        s.replyNow(Cmd48ReplyBody.PACKET_ID, body);
    }

    public static void finishLoading(GameSession s) {
        // A4 官方收到 CMD 40 无条件回 1 字节 01（sweep_town / sweep_dungeon / scen_off / replay_off 全一致）。
        // A5 原来只有全员到齐才继续、且从不回包；城里发也会回 01（官方随后还发 NOTI 30/36，那是另一条逻辑）。
        s.reply(40, new byte[]{1});
        if (!ServerConfig.DUNGEON_ENTRY) {
            return;
        }
        Party party = s.party().current();
        if (party == null) {
            return;
        }
        if (!party.finishLoading(s)) {
            return;
        }
        if (!party.checkAllMemberLoading(false)) {
            return;
        }
        startMap(party, "队伍 " + party.index() + " 全员报完了 CMD " + CMD_FINISH_LOADING);
    }

    private static void startMap(Party party, String why) {
        List<GameSession> recipients = party.membersLoaded();
        byte[] body = Noti30Body.toBytes();
        for (GameSession member : recipients) {
            member.sendNow(Noti30Body.PACKET_ID, body);
        }
    }

    private static final int SINGLE_MAP_LOADING_WAIT_TIMEOUT = 120;

    private static final int PARTY_MAP_LOADING_WAIT_TIMEOUT = 90;

    static void armLoadingBarrier(Party party, String why) {
        party.resetFinishLoadingVar();
        int key = party.armLoadingTimer();
        int members = party.memberCount();
        int seconds = members == 1 ? SINGLE_MAP_LOADING_WAIT_TIMEOUT
                : PARTY_MAP_LOADING_WAIT_TIMEOUT;
        TimerQueue.insertTimer(TimerQueue.OBJ_PARTY, party.index(), TimerMessage.MAP_LOADING, seconds, key, 0);
    }

    static {
        TimerQueue.registerParty(TimerMessage.MAP_LOADING,
                (party, key, param) -> mapLoadForcedFinish(party, key, "载入屏障超时"));
    }

    private static void mapLoadForcedFinish(Party party, int key, String why) {
        if (!party.checkTimerKey(TimerMessage.MAP_LOADING, key)) {
            return;
        }
        if (party.gameStarted()) {
            return;
        }
        List<GameSession> stragglers = party.membersNotLoaded();
        if (stragglers.isEmpty()) {
            return;
        }
        for (GameSession straggler : stragglers) {
            straggler.run(() -> TownService.returnToTown(straggler, "载入超时", false));
        }
        if (party.checkAllMemberLoading(true)) {
            startMap(party, "队伍 " + party.index() + " 载入超时，报完的那几个先开始");
        }
    }

    public static void prepareDungeonClear(GameSession s) {
        BattleField field = s.battleField();
        if (field == null || !s.inDungeon()) {
            return;
        }
        if (s.room().clearableLivingCount() > 0) {
            return;
        }
        BattleField.RoomState state = field.current();
        int mapIndex = state == null ? 0 : state.mapIndex();
        onRoomCleared(s, mapIndex);
        boolean byCondition = field.reportClearCondition(
                com.dylan.pvf.script.DungeonScript.TYPE_CLEAR_MAP, mapIndex);
        if (!byCondition && !field.atEndPoint()) {
            return;
        }
        clearDungeon(s, field);
    }

    public static void reportKillClearCondition(GameSession s, RoomMonster monster) {
        BattleField field = s.battleField();
        if (field == null || monster == null || field.clearConditions().isEmpty()) {
            return;
        }
        int type = ClearConditions.conditionTypeOf(monster.type());
        if (!field.reportClearCondition(type, monster.monsterId())) {
            return;
        }
        clearDungeon(s, field);
    }

    private static void onRoomCleared(GameSession s, int mapIndex) {
        for (GameSession member : partyMembers(s)) {
            member.run(() -> {
                member.lastClearedMapIndex(mapIndex);
                int spent = member.fatigue().takeLastSpentFatigue();
                CreatureService.gainExpOnDungeonClear(member, spent);
            });
        }
    }

    private static List<GameSession> partyMembers(GameSession s) {
        Party party = s.party().current();
        return party == null ? List.of(s) : party.view().members();
    }

    private static void clearDungeon(GameSession s, BattleField field) {
        if (!field.markCleared()) {
            return;
        }
        List<GameSession> members = partyMembers(s);
        Party clearedParty = s.party().current();
        if (clearedParty != null) {
            HellPartyService.onClearDungeon(clearedParty, s, field);
        }
        byte[] body = Noti31Body.toBytes();
        for (GameSession member : members) {
            member.run(() -> {
                member.runSummary().stopClock();
                QuestDropService.onDungeonClear(member,
                        java.util.concurrent.ThreadLocalRandom.current());
                member.sendNow(Noti31Body.PACKET_ID, body);
            });
        }
    }

    public static void giveUpGame(GameSession s) {

        byte[] body = Cmd45ReplyBody.success();
        s.reply(Cmd45ReplyBody.PACKET_ID, body);

        if (!s.inDungeon()) {
            s.flush();
            return;
        }
        if (giveupPenaltyFlag(s)) {
            s.stamina().penalty("CMD " + Cmd45ReplyBody.PACKET_ID + " 放弃副本");
        }
        TownService.returnToTown(s, "玩家放弃副本");
    }

    static boolean giveupPenaltyFlag(GameSession s) {
        DungeonRun run = s.dungeon();
        if (run != null
                && run.dungeonIndex() == Cmd16SelectDungeonRequest.TUTORIAL_DUNGEON_INDEX) {
            return false;
        }
        Party party = s.party().current();
        int eplp = party == null ? Party.EPLP_NONE : party.eplpState();
        if (eplp != Party.EPLP_PLAYING) {
            return false;
        }
        return true;
    }

    public static void dungeonEventStoryPause(GameSession s, byte[] plain) {
        Cmd204DungeonEventStoryPauseRequest req = Cmd204DungeonEventStoryPauseRequest.parse(plain);

        if (!s.inDungeon()) {
            return;
        }
        Party party = s.party().current();
        if (party == null) {
            return;
        }

        byte[] body = new Noti183DungeonEventStoryPauseBody(s.uniqueId(), req.flag()).toBytes();
        List<GameSession> recipients = party.view().members();
        for (GameSession member : recipients) {
            member.sendNow(Noti183DungeonEventStoryPauseBody.PACKET_ID, body);
        }
    }

    public static final int CMD_HELLPARTY_START = 250;

    public static void hellPartyStart(GameSession s) {
        if (!s.inDungeon()) {
            s.fail("人不在副本里却收到 CMD " + CMD_HELLPARTY_START + " HELLPARTY_START"
                    + "（深渊之门开启通知）—— 官方 dispatch_sig 那道 get_state == 5 不过就是断连");
            return;
        }
    }

    public static final int CMD_BACK_2_VILLAGE = 142;

    public static void backToVillage(GameSession s) {
        if (!s.inDungeon() && !s.dungeonSelectOpened()) {
            return;
        }
        boolean leader = s.party().mayLeadTheParty();
        TownService.returnToTown(s,
                (s.inDungeon() ? "玩家在副本里点了返回城镇" : "玩家在副本选择界面点了返回城镇")
                        + (leader ? "（队长，带全队）" : "（队员，只有他自己走 = 退队）"),
                leader);
    }
}
