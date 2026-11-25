package com.dylan.game;

import com.dylan.net.OutboundCmdReply;
import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Cmd1ReplyBody;
import com.dylan.protocol.body.Cmd4ReplyBody;
import com.dylan.protocol.body.Cmd4SelectCharacterRequest;
import com.dylan.protocol.body.Cmd5CreateCharacterRequest;
import com.dylan.protocol.body.Cmd6DeleteCharacterRequest;
import com.dylan.protocol.body.Cmd146QueryCharacInfoRequest;
import com.dylan.protocol.body.Cmd146ReplyBody;
import com.dylan.protocol.body.Cmd7ReplyBody;
import com.dylan.protocol.body.CombatStats;
import com.dylan.protocol.body.EquipmentSlot;
import com.dylan.protocol.body.Noti13ItemListBody;
import com.dylan.protocol.body.Noti19SkillInfoBody;
import com.dylan.protocol.body.Noti2Body;
import com.dylan.protocol.body.Noti2CharacterEntry;
import com.dylan.protocol.body.Noti2OwnCharacterEntry;
import com.dylan.protocol.body.Noti2PartyEntry;
import com.dylan.store.AccountStore;
import com.dylan.store.CharacterRow;
import com.dylan.store.CharacterStore;
import com.dylan.store.DuplicateNameException;

import java.util.List;
import java.util.Optional;

public final class CharacterService {
    private static final int NEW_CHARACTER_SEX = 1;

    private static final int CREATE_FAILED_CODE = 18;

    private static int coinOnCreateCharac() {
        if (com.dylan.game.event.EventService.isRunning(
                com.dylan.game.event.EventNo.COIN_ON_CHAR_CREATE)) {
            return com.dylan.game.event.EventService.param1(
                    com.dylan.game.event.EventNo.COIN_ON_CHAR_CREATE);
        }
        // A4 官方新角色没有复活币：NOTI 13/0 只有槽 0（金币）和槽 2（胜点），session_replay 23 份全是这样
        return ServerConfig.A4 ? 0 : com.dylan.store.CharacterStore.DEFAULT_COIN_ON_CREATE;
    }

    private CharacterService() {
    }

    public static void resolveAccount(GameSession s) {
        if (s.store() == null) {
            return;
        }
        String userId = s.loginUserId();
        // 令牌模式下账号只能从网关注册，服务端不再自动建号
        boolean autoCreate = ServerConfig.DB_AUTO_CREATE_ACCOUNT && !s.loginTicketed()
                && !ServerConfig.LOGIN_TOKEN_MODE;
        s.account(autoCreate
                ? s.store().account().findOrCreateAccount(userId, ServerConfig.DB_DEFAULT_PASSWORD)
                : s.store().account().findAccount(userId).orElse(null));
        if (s.account() != null) {
            GmManager.refresh(s.store(), s.account().mId());
            TutorialService.loadSkipable(s);
        }
    }

    public static boolean kickDuplicateLogin(GameSession s) {
        if (s.account() == null) {
            return false;
        }
        GameSession previous = SessionRegistry.findByAccountId(s.account().mId(), s);
        if (previous == null) {
            return false;
        }

        previous.run(() -> {
            if (previous.isActive()) {
                previous.replyNow(Cmd1ReplyBody.PACKET_ID,
                        Cmd1ReplyBody.rejected(ERROR_DUPLICATE_LOGIN));
                previous.fail("同一个账号在别处登录（照官方的断连信号）");
            }
        });

        s.replyNow(Cmd1ReplyBody.PACKET_ID, Cmd1ReplyBody.rejected(ERROR_DUPLICATE_LOGIN));
        return true;
    }

    public static final int ERROR_DUPLICATE_LOGIN = 0x12;

    public static void sendLoginReply(GameSession s) {
        byte[] body;
        String source;
        if (!ServerConfig.LOGIN_REPLY_HEX.isBlank()) {
            body = parseHex(ServerConfig.LOGIN_REPLY_HEX);
            source = "手工 -DloginReplyHex";
        } else if (ServerConfig.LOGIN_REJECT_CODE != 0) {
            body = Cmd1ReplyBody.rejected(ServerConfig.LOGIN_REJECT_CODE);
            source = "拒绝，错误码 " + ServerConfig.LOGIN_REJECT_CODE;
        } else if (ServerConfig.A4) {
            // A4 登录回包：地址 + 两个中转端口 + 7 个 0，频道类型按端口 1→2 / 6→3 / 51→4（probe_login.py 逐字节一致）
            int kind = ServerConfig.LOGIN_CHANNEL_KIND >= 0 ? ServerConfig.LOGIN_CHANNEL_KIND
                    : ServerConfig.a4ChannelKindForPort(s.localPort());
            s.channelKind(kind);
            GameChannel.setType(kind);
            String host = ServerConfig.PEER_UDP_HOST.isBlank() ? "127.0.0.1" : ServerConfig.PEER_UDP_HOST;
            // 这三个字段是 P2P 中转参数（见 Cmd1ReplyBody.a4Accepted 注释）。客户端组队时拿它做
            // 第 3 个候选端点 + 中转登记身份；官方服只发 0/网关端口，所以外网一直走不通。
            int relayTcp = ServerConfig.ENTRANCE_PORT;
            int relayUdp = UdpPartyRelay.ENABLED && UdpPartyRelay.port() > 0
                    ? UdpPartyRelay.port() : relayTcp;
            int identity = ServerConfig.LOGIN_UDP_IDENTITY && s.account() != null
                    ? (int) s.account().mId() : 0;
            body = Cmd1ReplyBody.a4Accepted(kind, host, relayUdp, relayTcp, identity);
            source = "A4 登录回包，频道类型 " + kind + "，中转 " + host + " udp " + relayUdp
                    + " tcp " + relayTcp + "，身份 " + identity;
        } else {
            int channelKind = loginChannelKind();
            s.channelKind(channelKind);
            GameChannel.setType(channelKind);
            Cmd1ReplyBody reply = Cmd1ReplyBody.accepted();
            body = new Cmd1ReplyBody(reply.result(), reply.unknown1(), reply.unknown2(),
                    channelKind, reply.pcRoom(), reply.unknown5(),
                    reply.text(), reply.unknown7(), reply.unknown8(), reply.unknown9(),
                    reply.unknown10(), reply.unknown11(), reply.unknown12(),
                    reply.unknown13(), reply.unknown14(), reply.unknown15(),
                    reply.unknown16()).toBytes();
            source = "Cmd1ReplyBody.accepted，频道类型 " + channelKind
                    + "（" + GameChannel.describe(channelKind) + "）";
        }
        s.replyNow(Cmd1ReplyBody.PACKET_ID, body);
    }

    static int loginChannelKind() {
        if (ServerConfig.LOGIN_CHANNEL_KIND >= 0) {
            return ServerConfig.LOGIN_CHANNEL_KIND;
        }
        int type = GameChannel.channelType();
        return type < 0 ? Cmd1ReplyBody.CHANNEL_KIND_NORMAL : type;
    }

    private static byte[] parseHex(String hex) {
        String clean = hex.replaceAll("[^0-9a-fA-F]", "");
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public static void sendCharacterList(GameSession s, String why) {
        List<Noti2CharacterEntry> characters = currentCharacters(s);
        byte[] body = Noti2Body.characterList(characters).toBytes();
        s.sendNow(Noti2Body.PACKET_ID, body);
    }

    public static void sendOtherUserInfo(GameSession s,
                                         com.dylan.protocol.body.Cmd8GetUserInfoRequest request) {
        GameSession target = SessionRegistry.findInWorld(request.targetUserId());
        SessionRegistry.Presence presence =
                target == null ? null : SessionRegistry.presenceOf(target);
        if (presence == null) {
            return;
        }
        int kind = request.kind();
        com.dylan.protocol.body.Noti2Entry entry = switch (kind) {
            case Noti2Body.SUBTYPE_OWN_CHARACTER -> presence.identity();
            case Noti2Body.SUBTYPE_PARTY_ROSTER,
                 Noti2PartyEntry.SUBTYPE_OTHER_USER -> presence.detail();
            default -> null;
        };
        if (entry == null) {
            return;
        }
        byte[] body = new Noti2Body(kind, List.of(entry)).toBytes();
        s.sendNow(Noti2Body.PACKET_ID, body);
    }

    public static List<Noti2CharacterEntry> currentCharacters(GameSession s) {
        List<CharacterRow> rows = currentCharacterRows(s);
        if (rows == null) {
            return s.memoryCharacters();
        }
        List<Noti2CharacterEntry> out = new java.util.ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            out.add(toEntry(s, i, rows.get(i)));
        }
        return out;
    }

    public static List<CharacterRow> currentCharacterRows(GameSession s) {
        if (s.store() == null || s.account() == null) {
            return null;
        }
        return s.store().character().charactersOf(s.account().mId(), s.account().slot());
    }

    private static Noti2CharacterEntry toEntry(GameSession s, int listIndex, CharacterRow row) {
        List<Noti2CharacterEntry.Equipment> worn = List.of();
        if (s.store() != null) {
            worn = s.store().inventory().loadInventory(row.characNo())
                    .map(inv -> InventoryService.equipmentListOf(inv.equipslot(), true))
                    .orElse(List.of());
        }
        return new Noti2CharacterEntry(listIndex, row.characName(), 0,
                row.job(), row.growType(), row.lev(), 0,
                Noti2CharacterEntry.STATUS_NORMAL, worn, 0,
                (int) row.guildId(), 0, 0, 0, 0, 0, 0);
    }

    public static void selectCharacter(GameSession s, byte[] plain) {
        if (plain == null) {
            s.fail("CMD " + Cmd4SelectCharacterRequest.PACKET_ID + " SELECT_CHARACTER 的包体解不开");
            return;
        }
        Cmd4SelectCharacterRequest req = Cmd4SelectCharacterRequest.parse(plain);

        List<CharacterRow> rows = currentCharacterRows(s);
        if (rows == null) {
            selectFromMemory(s, req);
            return;
        }
        if (req.listIndex() >= rows.size()) {
            replySelectFailure(s, "列表序号越界");
            return;
        }

        CharacterRow row = rows.get(req.listIndex());

        long regTime = s.account() == null ? 0L : s.account().regDate();
        long createTime = s.store() == null ? 0L
                : s.store().character().characterCreateTime(row.characNo()).orElse(0L);
        s.characCreateTime((int) createTime);
        int uniqueId = s.uniqueId();
        if (uniqueId == UniqueIdAllocator.UNASSIGNED) {
            s.fail("选角时这条连接还没有 uniqueId —— SessionRegistry.enter 没走到");
            return;
        }
        int village = s.store() == null ? ServerConfig.LANDING_TOWN_ID
                : s.store().character().characterVillage(row.characNo()).orElse(ServerConfig.LANDING_TOWN_ID);
        // 决斗频道（登录回包频道类型 4/5/6/7）落地：原版 A5 就留了 PVP_VILLAGE=2（赫顿玛尔），
        // 决斗场大厅在那座城的酒馆里（map/hendonmyre/hendon_pub.map，pub 区）。存档城镇是 1 的话
        // 客户端在 kind 4 频道加载不出大厅 → 一直停加载（黑屏，2026-10-28 实机）。
        // 官方成品服频道 51 只发存档城镇（它的假人正好存档在 2 号城），不能当依据。
        var landing = s.pvpChannel() ? TownService.pvpLanding(s, village) : TownService.landingFor(s, village);
        int townId = landing.town();
        // 一条连接一行：端口 / 账号 / 角色 / 频道类型 / 落到哪个 town/area。排查「决斗场频道黑屏」用。
        System.out.println("[会话 " + s.localPort() + "] 选角 " + row.characName()
                + " (uid " + uniqueId + ", acc " + (s.account() == null ? 0 : s.account().mId()) + ")"
                + " 频道类型 " + GameChannel.channelType()
                + "(" + GameChannel.describe(GameChannel.channelType()) + ")"
                + " 存档城镇 " + village + " → 落地 town " + landing.town()
                + "/area " + landing.area() + " (" + landing.x() + "," + landing.y() + ")"
                + (s.pvpChannel() ? "（决斗频道，模式 " + TownService.pvpLandingMode() + "）" : ""));

        // 已经确定要进这个角色了：**先**向外部工具登记"服务端持有它"，再开始把整块数据 load 进
        // 内存。顺序很重要 —— 宁可早登记让 GM 暂时不许写（fail-safe），也不能晚登记，让 GM 在
        // 服务端已把数据拿进内存后写进来（那样存档回写会盖掉，是真丢数据）。见 PlayerPresence。
        PlayerPresence.enter(s.store(), row.characNo(), row.characName(),
                s.account() == null ? 0L : s.account().mId());

        TutorialService.load(s, row.characNo());
        s.character(row);
        QuestService.load(s, row.characNo());
        s.premium().load(s.account() == null ? 0 : s.account().mId());
        s.fatigue().load(row.characNo());
        s.stamina().load(row.characNo());
        s.dungeonClear().load(s.account() == null ? 0 : s.account().mId());
        s.inventory().load(row.characNo());
        s.itemLock().load(row.characNo());
        DailyGrantService.onSelectCharacter(s, row.characNo());
        s.skills().load(row.characNo(), row.job());
        s.expertJob().load(row.characNo());
        s.expertJob().repairFromClearedQuests();
        s.mail().load(row.characNo());
        EmotionService.load(s, row.characNo());
        loadCurrencies(s, row.characNo());

        Cmd4ReplyBody reply = buildSelectReply(s, regTime, createTime, uniqueId, townId);
        // A4 选角回包布局不同（u32 角色号开头、3 个任务槽、尾部教程位图），数据仍是上面 A5 算的，见 toA4Bytes
        byte[] body = ServerConfig.A4 ? reply.toA4Bytes(row.characNo()) : reply.toBytes();
        TownService.landAt(s, landing, "选角落地，角色上次在城镇 " + village);
        s.characNo(row.characNo());
        s.characName(row.characName());
        s.stats(combatStats(s, row));
        ExperienceService.load(s, row.characNo());
        writeSelectReply(s, body);
        A4WorldEntry.afterSelectReply(s);

        s.consumables().load(row.characNo());

        enterGameWorld(s, row, uniqueId);

        // A4 官方 NOTI 136 只在 CMD 37 之后发一次（TownService.reportPosition 那里），选角时不发。
        if (!reply.entersTown() && !ServerConfig.A4) {
            TownService.sendEnterGameWorldComplete(s);
        }
    }

    private static Cmd4ReplyBody buildSelectReply(GameSession s, long regTime, long createTime,
                                                  int uniqueId, int townId) {
        return withPremiums(s, withCompletedQuests(s, withCera(s,
                withQuests(s, withFatigue(s, shapeOf(s, regTime, createTime, uniqueId, townId))))))
                .withTutorialSkipable(s.tutorialSkipable());
    }

    private static Cmd4ReplyBody withPremiums(GameSession s, Cmd4ReplyBody reply) {
        long now = java.time.Instant.now().getEpochSecond();
        List<Cmd4ReplyBody.Premium> premiums = new java.util.ArrayList<>();
        for (var wire : s.premium().wirePremiums(now).entrySet()) {
            if (wire.getKey() >= Cmd4ReplyBody.MAX_PREMIUM_TYPE) {
                continue;
            }
            premiums.add(new Cmd4ReplyBody.Premium(wire.getKey(), wire.getValue()));
        }
        if (premiums.isEmpty()) {
            return reply;
        }
        return reply.withPremiums(premiums);
    }

    private static void loadCurrencies(GameSession s, int characNo) {
        if (s.store() == null) {
            return;
        }
        s.winPoint(s.store().pvp().winPoint(characNo));
        if (s.account() != null) {
            s.cera(s.store().cera().cera(s.account().mId()));
        }
    }

    private static Cmd4ReplyBody withCera(GameSession s, Cmd4ReplyBody reply) {
        return s.cera() == 0 ? reply : reply.withCera(s.cera());
    }

    private static Cmd4ReplyBody withCompletedQuests(GameSession s, Cmd4ReplyBody reply) {
        if (ServerConfig.CLEAR_QUEST_ALL) {
            var all = Cmd4ReplyBody.CompletedQuestSet.allCategories();
            return reply.withCompletedQuestSets(all);
        }
        var sets = QuestService.completionSets(s);
        if (sets.isEmpty()) {
            return reply;
        }
        return reply.withCompletedQuestSets(sets);
    }

    private static Cmd4ReplyBody withQuests(GameSession s, Cmd4ReplyBody reply) {
        var slots = QuestService.activeQuestSlots(s.quests());
        if (slots.isEmpty()) {
            return reply;
        }
        return reply.withActiveQuests(slots);
    }

    private static Cmd4ReplyBody shapeOf(GameSession s, long regTime, long createTime,
                                         int uniqueId, int townId) {
        if (!ServerConfig.TUTORIAL_DONE) {
            return ServerConfig.PRISTINE_TOWN_ID
                    ? Cmd4ReplyBody.pristineInTown(regTime, createTime, uniqueId, townId)
                    : Cmd4ReplyBody.minimal(regTime, createTime, uniqueId);
        }
        if (ServerConfig.TUTORIAL_PERSIST) {
            s.tutorials().get(Cmd4ReplyBody.TUTORIAL_ENTER_TOWN);
            return Cmd4ReplyBody.withTutorials(regTime, createTime, uniqueId, townId,
                    s.tutorials().completed());
        }
        return Cmd4ReplyBody.enteringTown(regTime, createTime, uniqueId, townId);
    }

    private static Cmd4ReplyBody withFatigue(GameSession s, Cmd4ReplyBody reply) {
        var f = s.fatigue().snapshot();
        int used = f.totalFatigue();
        if (ServerConfig.FATIGUE_PROBE_USED >= 0) {
            used = Math.min(ServerConfig.FATIGUE_PROBE_USED, f.totalMaxFatigue());
        }
        return reply.withFatigue(used, f.totalMaxFatigue(), f.usedFatigue())
                .withFatigueExtras(FATIGUE_BATTERY_NOT_IMPLEMENTED, f.grownUpBuff());
    }

    private static final int FATIGUE_BATTERY_NOT_IMPLEMENTED = 0;

    private static void selectFromMemory(GameSession s, Cmd4SelectCharacterRequest req) {
        if (req.listIndex() >= s.memoryCharacters().size()) {
            replySelectFailure(s, "列表序号越界");
            return;
        }
        int uniqueId = s.uniqueId();
        var landing = TownService.landingFor(s, ServerConfig.LANDING_TOWN_ID);
        byte[] body = (ServerConfig.TUTORIAL_DONE
                ? Cmd4ReplyBody.enteringTown(0, 0, uniqueId, landing.town())
                : Cmd4ReplyBody.minimal(0, 0, uniqueId)).toBytes();
        TownService.landAt(s, landing, "选角落地（数据库关着）");
        writeSelectReply(s, body);
    }

    private static void replySelectFailure(GameSession s, String why) {
        writeSelectReply(s, Cmd4ReplyBody.failure());
    }

    private static void writeSelectReply(GameSession s, byte[] body) {
        s.markSelectAnswered();
        byte[] out = body;
        switch (ServerConfig.SELECT_REPLY) {
            case "normal" -> {
            }
            case "none" -> {
                return;
            }
            case "failure" -> {
                out = Cmd4ReplyBody.failure();
            }
            default -> {
            }
        }
        s.write(OutboundCmdReply.of(Cmd4ReplyBody.PACKET_ID, out));
        s.flush();
    }

    private static void enterGameWorld(GameSession s, CharacterRow row, int uniqueId) {
        if (!"normal".equals(ServerConfig.SELECT_REPLY)) {
            return;
        }
        if (!ServerConfig.INSERT_GAME_WORLD) {
            return;
        }

        s.gameOption().sendToClient();
        // 换频道是新连接。旧连接还在名单里的话，邀请框会看到同一个人两次。
        retireSameCharacter(s, row.characNo());

        for (Noti13ItemListBody list : itemLists(s, row)) {
            sendCreatureItemListBefore(s, list.listType(), "进世界");
            byte[] body = list.toBytes();
            s.send(Noti13ItemListBody.PACKET_ID, body);
        }

        // A4 也要发：官方每次进世界在 NOTI 13/0 之后跟一条 NOTI 192（空表 00 00，scen_off 6 次全有）。
        // A4 的锁列表就是 192（sendLockList 内部按 ServerConfig.A4 换号），原来这里写反了，
        // 进世界不发 192 → 客户端没有锁表，重登后已锁物品的锁图标全丢。
        s.itemLock().sendLockList("进世界");

        SessionRegistry.reachWorld(s, new SessionRegistry.Presence(
                uniqueId, row.characNo(), row.characName(), null,
                s.townId(), s.areaId(), s.landingX(), s.landingY(), s.lastDirection(),
                true, TownService.isPrivateArea(s, s.townId(), s.areaId()),
                s.account() == null ? 0 : (int) s.account().mId(), null,
                row.sex()));
        sendOwnCharacterInfo(s, row, uniqueId, true, "进世界");
        A4WorldEntry.afterOwnCharacter(s);

        Noti19SkillInfoBody info = skillInfo(s, row);
        byte[] skills = info.toBytes();
        s.send(Noti19SkillInfoBody.PACKET_ID, skills);

        CombatStats stats = s.stats() != null ? s.stats() : combatStats(s, row);
        Noti2PartyEntry record = detailRecord(s, row, uniqueId, stats);
        byte[] detail = Noti2Body.partyRoster(List.of(record)).toBytes();
        s.send(Noti2Body.PACKET_ID, detail);

        s.stamina().sendOnEnterWorld("进世界");

        QuestService.sendAcceptableQuests(s, "进世界");
        s.dungeonClear().sendFullList("进世界");
        s.mail().sendList(com.dylan.protocol.body.Noti97MailBoxListBody.MODE_REPLACE, "进世界");
        s.expertJob().sendInfo("进世界");
        A4WorldEntry.afterEnterWorld(s);
        FriendService.onEnterWorld(s);
        PvpLobby.onEnterWorld(s);
        s.flush();
    }

    /** 同一个角色又进了世界：先让旧连接按离线从它所在的频道消失，再放新连接号。 */
    private static void retireSameCharacter(GameSession current, int characNo) {
        if (characNo <= 0) {
            return;
        }
        for (GameSession other : new java.util.ArrayList<>(SessionRegistry.playing())) {
            if (other == current || other.characNo() != characNo || !other.isActive()) {
                continue;
            }
            NeighborService.announceDeparture(other, "同一角色换频道");
            other.disconnect("同一角色换频道");
        }
    }

    public static void sendEquipChanged(GameSession s, int slot, String why) {
        if (!ServerConfig.EQUIP_REFRESH) {
            return;
        }
        CharacterRow row = s.character();
        if (row == null) {
            return;
        }

        refreshSnapshots(s, row, why);

        if (!EquipmentSlot.refreshesOwnInfo(slot)) {
            return;
        }
        if (s.inDungeon() && EquipmentSlot.broadcastSuppressedInDungeon(slot)) {
            return;
        }
        sendOwnCharacterInfo(s, row, s.uniqueId());
    }

    public static void refreshSnapshots(GameSession s, String why) {
        CharacterRow row = s.character();
        if (row == null || !s.hasCharacter() || SessionRegistry.presenceOf(s) == null) {
            return;
        }
        refreshSnapshots(s, row, why);
    }

    private static void refreshSnapshots(GameSession s, CharacterRow row, String why) {
        if (s.uniqueId() < 0) {
            return;
        }
        if (ServerConfig.OWN_INFO) {
            SessionRegistry.updateIdentity(s, ownCharacterEntry(s, row, s.uniqueId(),
                    s.inventory().equipmentList()));
        }
        refreshDetail(s, why);
    }

    public static void resendOwnCharacterInfo(GameSession s, String why) {
        CharacterRow row = s.character();
        if (row == null || !s.hasCharacter()) {
            return;
        }
        sendOwnCharacterInfo(s, row, s.uniqueId());
        s.itemLock().resendIfAnyLocked(why);
    }

    public static void resendOwnCharacterInfoToSelf(GameSession s, String why) {
        CharacterRow row = s.character();
        if (row == null || !s.hasCharacter() || !ServerConfig.OWN_INFO) {
            return;
        }
        var worn = s.inventory().equipmentList();
        Noti2OwnCharacterEntry entry = ownCharacterEntry(s, row, s.uniqueId(), worn);
        byte[] body = Noti2Body.ownCharacter(entry).toBytes();
        s.sendNow(Noti2Body.PACKET_ID, body);
        s.itemLock().resendIfAnyLocked(why);
    }

    private static void sendOwnCharacterInfo(GameSession s, CharacterRow row, int uniqueId) {
        sendOwnCharacterInfo(s, row, uniqueId, false, "换装 / 重发");
    }

    private static void sendOwnCharacterInfo(GameSession s, CharacterRow row, int uniqueId,
                                             boolean exchange, String why) {
        if (!ServerConfig.OWN_INFO) {
            return;
        }
        var worn = s.inventory().equipmentList();
        Noti2OwnCharacterEntry entry = ownCharacterEntry(s, row, uniqueId, worn);

        int pad = Math.max(0, ServerConfig.OWN_INFO_PAD);
        if (pad > 0) {
            byte[] body = Noti2Body.ownCharacter(entry).toBytes();
            byte[] padded = new byte[body.length + pad];
            System.arraycopy(body, 0, padded, 0, body.length);
            s.sendNow(Noti2Body.PACKET_ID, padded);
            return;
        }

        SessionRegistry.updateIdentity(s, entry);
        if (exchange) {
            NeighborService.exchangeIdentities(s, entry, why);
        } else {
            NeighborService.announceIdentity(s, entry, why);
        }
    }

    private static Noti2OwnCharacterEntry ownCharacterEntry(GameSession s, CharacterRow row,
            int uniqueId, java.util.List<Noti2CharacterEntry.Equipment> worn) {
        return Noti2OwnCharacterEntry.forCharacter(
                uniqueId, row.characName(), row.job(), row.growType(), row.lev(),
                worn, 0, 0, s.inventory().equippedCreatureItemId(),
                s.inventory().equippedCreatureName(), s.inventory().equippedCreatureAlive(),
                ServerConfig.PRIVATE_STORE_ALL || s.premium().canOpenPrivateStore(),
                ServerConfig.PREMIUM_FLAG >= 0
                        ? ServerConfig.PREMIUM_FLAG != 0
                        : s.premium().isAffected(PremiumService.PREMIUM_PC_ROOM_BONUS),
                // A4 黑钻标记在 pcRoomNo（u32）= 1，nameBadge 恒 0。依据：A4 官方实抓满级假人，
                // CMD 68 黑钻开通前登录 NOTI 2/0 偏移 176 = 0、开通后 = 1，偏移 180（nameBadge）始终 0。
                ServerConfig.A4 && ServerConfig.PC_ROOM_NO < 0
                        ? (s.premium().hasBlackDiamond() ? 1 : 0)
                        : PremiumService.pcRoomNoFor(ServerConfig.PC_ROOM_NO),
                PremiumService.nameBadgeFor(!ServerConfig.A4 && s.premium().hasBlackDiamond(),
                        ServerConfig.NAME_BADGE_18),
                0, "")
                .withEmotion(s.emotion())
                .withExpertJob(s.expertJob().type(), s.expertJob().exp())
                .withStamina(s.stamina().current())
                .withAdminIcon(GmManager.isGm(s.account() == null ? 0L : s.account().mId()))
                // 官方有没有公会，公会名前那个 u8 都是 00（probe_guild.py S1 / S2 的 NOTI 2/0）
                .withGuild(GuildService.idOf(s), 0, GuildService.nameOf(s));
    }

    public static void resendItemListsForGmMode(GameSession s, String why) {
        if (!s.hasCharacter() || s.store() == null || s.inventory().inventory() == null) {
            return;
        }
        java.util.List<Noti13ItemListBody> all = s.inventory().allLists();
        int[] official = {
                com.dylan.protocol.body.ItemListType.LIST_AVATAR,
                com.dylan.protocol.body.ItemListType.LIST_MAIN,
                com.dylan.protocol.body.ItemListType.LIST_CARGO,
        };
        for (int listType : official) {
            all.stream()
                    .filter(list -> list.listType() == listType)
                    .findFirst()
                    .ifPresent(list -> {
                        byte[] body = list.toBytes();
                        s.send(Noti13ItemListBody.PACKET_ID, body);
                    });
        }
        s.flush();
    }

    public static void resendItemLists(GameSession s, String why) {
        if (!s.hasCharacter() || s.store() == null) {
            return;
        }
        if (s.inventory().inventory() == null) {
            return;
        }
        for (Noti13ItemListBody list : s.inventory().allLists()) {
            sendCreatureItemListBefore(s, list.listType(), why);
            byte[] body = list.toBytes();
            s.send(Noti13ItemListBody.PACKET_ID, body);
        }
        // A4：官方每次整栏重发 NOTI 13 后都跟一条 NOTI 192 锁列表（scen_off 9 次 192 / 13 次 13），
        // 客户端新建的物品对象锁状态是 0，不补就会把已锁物品的锁图标冲掉。
        s.itemLock().sendLockList(why);
        s.flush();
    }

    private static void sendCreatureItemListBefore(GameSession s, int listType, String why) {
        if (listType != com.dylan.protocol.body.ItemListType.LIST_CREATURE) {
            return;
        }
        var list = s.inventory().creatureItemList();
        byte[] body = list.toBytes();
        s.send(com.dylan.protocol.body.Noti105CreatureItemListBody.PACKET_ID, body);
    }

    private static List<Noti13ItemListBody> itemLists(GameSession s, CharacterRow row) {
        if (!ServerConfig.ITEM_LIST) {
            return InventoryService.itemLists(null, 0, 0, s.scripts());
        }
        return s.inventory().allLists();
    }

    private static Noti19SkillInfoBody skillInfo(GameSession s, CharacterRow row) {
        if (!ServerConfig.INITIAL_SKILLS) {
            return Noti19SkillInfoBody.empty(0);
        }
        var slots = s.skills().slots();
        if (slots == null) {
            return SkillService.toSkillInfo(SkillService.initialSlots(s.scripts(), row.job()), 0);
        }
        return s.skills().skillInfo();
    }

    static Noti2PartyEntry detailRecord(GameSession s, CharacterRow row, int uniqueId,
                                        CombatStats stats) {
        var worn = s.inventory().detailedEquipmentList();
        Noti2PartyEntry record = Noti2PartyEntry.withStats(uniqueId, row.characName(),
                (int) Math.min(Integer.MAX_VALUE, Math.max(0, s.exp())),
                stats, worn,
                SkillService.learnedSkills(s.skills().slots()),
                partyTail());
        SessionRegistry.updateDetail(s, record);
        return record;
    }

    public static void refreshDetail(GameSession s, String why) {
        CharacterRow row = s.character();
        if (row == null || s.uniqueId() < 0) {
            return;
        }
        CombatStats stats = s.stats() != null ? s.stats() : combatStats(s, row);
        detailRecord(s, row, s.uniqueId(), stats);
    }

    public static void sendDetailToParty(GameSession s, String why) {
        CharacterRow row = s.character();
        if (row == null || !s.hasCharacter() || s.uniqueId() < 0) {
            return;
        }
        CombatStats stats = s.stats() != null ? s.stats() : combatStats(s, row);
        Noti2PartyEntry record = detailRecord(s, row, s.uniqueId(), stats);
        byte[] body = Noti2Body.partyRoster(List.of(record)).toBytes();
        List<GameSession> members = CombatService.partyMembers(s);
        for (GameSession member : members) {
            member.sendNow(Noti2Body.PACKET_ID, body);
        }
    }

    public static void sendDetailToSelf(GameSession s, String why) {
        CharacterRow row = s.character();
        if (row == null || !s.hasCharacter() || s.uniqueId() < 0) {
            return;
        }
        CombatStats stats = s.stats() != null ? s.stats() : combatStats(s, row);
        Noti2PartyEntry record = detailRecord(s, row, s.uniqueId(), stats);
        byte[] body = Noti2Body.partyRoster(List.of(record)).toBytes();
        s.sendNow(Noti2Body.PACKET_ID, body);
    }

    public static CombatStats recalcCombatStats(GameSession s, String why) {
        CharacterRow row = s.character();
        if (row == null || !s.hasCharacter()) {
            return s.stats();
        }
        CombatStats stats = combatStats(s, row);
        s.stats(stats);
        return stats;
    }

    static CombatStats combatStats(GameSession s, CharacterRow row) {
        if (ServerConfig.STAT_PROBE) {
            return CombatStats.probe();
        }
        if (!ServerConfig.STAT_FROM_SCRIPT) {
            CombatStats stats = CombatStats.defaults();
            return stats;
        }
        CombatStats stats = StatService.forCharacter(s.scripts(), row.job(), row.growType(),
                row.lev());
        return withElixirs(s, row.characNo(), stats);
    }

    static CombatStats withElixirs(GameSession s, int characNo, CombatStats base) {
        if (s.store() == null || characNo <= 0) {
            return base;
        }
        var used = s.store().elixir().loadElixirs(characNo);
        if (used.isEmpty()) {
            return base;
        }
        CombatStats bonus = ElixirStats.total(used);
        return base.plus(bonus);
    }

    static Noti2PartyEntry.Tail partyTail() {
        if (ServerConfig.PARTY_TAIL_PROBE) {
            return Noti2PartyEntry.Tail.probe();
        }
        return Noti2PartyEntry.Tail.defaults();
    }

    public static void createCharacter(GameSession s, byte[] plain) {
        if (plain == null) {
            s.fail("CMD " + Cmd5CreateCharacterRequest.PACKET_ID + " CREATE_CHARACTER 的包体解不开");
            return;
        }
        Cmd5CreateCharacterRequest req = Cmd5CreateCharacterRequest.parse(plain);

        int characterCount = characterCount(s);
        int slotLimit = characterSlotLimit(s);
        if (characterCount >= slotLimit || characterCount >= MAX_CHARAC_SLOT) {
            replyCreateFailure(s, CREATE_SLOTS_FULL,
                    "角色栏已满（" + characterCount + " / " + slotLimit + "）");
            return;
        }

        String rejected = NameFilter.rejectReason(req.name());
        if (rejected != null) {
            replyCreateFailure(s, rejected);
            return;
        }

        if (!ServerConfig.ECHO_CREATED_CHARACTER) {
            replyCreateSuccess(s);
            return;
        }
        if (s.store() == null || s.account() == null) {
            s.memoryCharacters().add(new Noti2CharacterEntry(
                    s.memoryCharacters().size(), req.name(), 0, req.job(),
                    NEW_CHARACTER_GROW_TYPE, NEW_CHARACTER_LEVEL, 0,
                    Noti2CharacterEntry.STATUS_NORMAL, List.of(), 0, 0, 0, 0, 0, 0, 0, 0));
            replyCreateSuccess(s);
            return;
        }

        try {
            var initialSkills = SkillService.initialSlots(s.scripts(), req.job());
            int coin = coinOnCreateCharac();
            s.store().character().createCharacter(s.account().mId(), req.name(), req.job(),
                    NEW_CHARACTER_SEX, initialSkills.encode(), coin, starterEquipslot(s, req.job()));
            replyCreateSuccess(s);
        } catch (DuplicateNameException e) {
            if (s.store().character().nameHeldByDeletedCharacter(req.name())) {
                replyCreateFailure(s, CREATE_NAME_RESERVED,
                        "「" + req.name() + "」被一个已删除的角色占着");
                return;
            }
            replyCreateFailure(s, e.getMessage());
        }
    }

    /**
     * A4 新角色武器槽带一把初始武器（{@link ServerConfig#A4_STARTER_WEAPONS}），官方成品服建角即有。
     * 物品照 A5 自己的 {@link InventoryService#newItem} 生成（耐久等取 pvf）。不是 A4 或表里没有就返回 null（装备栏留空）。
     */
    private static byte[] starterEquipslot(GameSession s, int job) {
        if (!ServerConfig.A4 || s.scripts() == null) {
            return null;
        }
        Integer itemId = ServerConfig.A4_STARTER_WEAPONS.get(job);
        if (itemId == null) {
            return null;
        }
        var script = s.scripts().item(itemId).orElse(null);
        if (script == null) {
            return null;
        }
        var equipslot = com.dylan.store.blob.ItemArray.emptyEquipslot();
        equipslot.set(EquipmentSlot.WEAPON, InventoryService.newItem(script, 1));
        return equipslot.encode();
    }

    private static final int CREATE_NAME_RESERVED = 24;

    private static final int CREATE_SLOTS_FULL = 4;

    private static final int MAX_CHARAC_SLOT = 36;

    private static int characterCount(GameSession s) {
        if (s.store() == null || s.account() == null) {
            return s.memoryCharacters().size();
        }
        return s.store().character().countCharacters(s.account().mId());
    }

    private static int characterSlotLimit(GameSession s) {
        if (s.account() == null) {
            return AccountStore.DEFAULT_CHARACTER_SLOTS;
        }
        return s.account().slot();
    }

    private static final int NEW_CHARACTER_GROW_TYPE = 0;
    private static final int NEW_CHARACTER_LEVEL = 1;

    private static void replyCreateSuccess(GameSession s) {
        s.replyNow(Cmd5CreateCharacterRequest.PACKET_ID, new byte[]{1});
        sendCharacterList(s, "建角色之后的全量重发");
    }

    private static void replyCreateFailure(GameSession s, String why) {
        replyCreateFailure(s, CREATE_FAILED_CODE, why);
    }

    private static void replyCreateFailure(GameSession s, int code, String why) {
        s.replyNow(Cmd5CreateCharacterRequest.PACKET_ID, new byte[]{0, (byte) code});
    }

    static final int DELETE_DB_ERROR = 2;

    static final int DELETE_IN_USE = 19;

    static final int DELETE_NO_SUCH_CHARACTER = 21;

    static final int DELETE_CREATED_TODAY = 22;

    public static void deleteCharacter(GameSession s, byte[] plain) {
        if (plain == null) {
            s.fail("CMD " + Cmd6DeleteCharacterRequest.PACKET_ID + " DELETE_CHARACTER 的包体解不开");
            return;
        }
        Cmd6DeleteCharacterRequest req = Cmd6DeleteCharacterRequest.parse(plain);

        if (s.characNo() > 0) {
            replyDeleteFailure(s, DELETE_IN_USE,
                    "这条连接已经选中角色 " + s.characNo() + " 了，不在选角界面");
            return;
        }

        List<CharacterRow> rows = currentCharacterRows(s);
        if (rows == null) {
            deleteFromMemory(s, req);
            return;
        }

        if (req.listIndex() < 0 || req.listIndex() >= rows.size()) {
            replyDeleteFailure(s, DELETE_NO_SUCH_CHARACTER,
                    "序号 " + req.listIndex() + " 越界，列表里只有 " + rows.size() + " 个角色");
            return;
        }
        CharacterRow target = rows.get(req.listIndex());

        if (req.name().isEmpty() || !target.characName().equals(req.name())) {
            replyDeleteFailure(s, DELETE_NO_SUCH_CHARACTER,
                    "确认框里打的是「" + req.name() + "」，序号 " + req.listIndex()
                            + " 上的角色叫「" + target.characName() + "」");
            return;
        }

        if (createdToday(s, target.characNo())) {
            replyDeleteFailure(s, DELETE_CREATED_TODAY,
                    "角色 " + target.characName() + " 是今天建的");
            return;
        }

        if (!s.store().character().deleteCharacter(s.account().mId(), target.characNo())) {
            replyDeleteFailure(s, DELETE_DB_ERROR,
                    "charac_no=" + target.characNo() + " 没删掉（不属于这个账号，或已经删过了）");
            return;
        }
        replyDeleteSuccess(s);
    }

    private static boolean createdToday(GameSession s, int characNo) {
        Optional<Long> createTime = s.store().character().characterCreateTime(characNo);
        if (createTime.isEmpty() || createTime.get() <= 0) {
            return false;
        }
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.LocalDate created = java.time.Instant.ofEpochSecond(createTime.get())
                .atZone(zone).toLocalDate();
        return created.equals(java.time.LocalDate.now(zone));
    }

    private static void deleteFromMemory(GameSession s, Cmd6DeleteCharacterRequest req) {
        List<Noti2CharacterEntry> list = s.memoryCharacters();
        if (req.listIndex() < 0 || req.listIndex() >= list.size()) {
            replyDeleteFailure(s, DELETE_NO_SUCH_CHARACTER,
                    "序号 " + req.listIndex() + " 越界，内存列表里只有 " + list.size() + " 个角色");
            return;
        }
        Noti2CharacterEntry target = list.get(req.listIndex());
        if (req.name().isEmpty() || !target.name().equals(req.name())) {
            replyDeleteFailure(s, DELETE_NO_SUCH_CHARACTER,
                    "确认框里打的是「" + req.name() + "」，序号 " + req.listIndex()
                            + " 上的角色叫「" + target.name() + "」");
            return;
        }
        list.remove(req.listIndex());
        for (int i = req.listIndex(); i < list.size(); i++) {
            Noti2CharacterEntry e = list.get(i);
            list.set(i, new Noti2CharacterEntry(i, e.name(), e.nameFlagged(), e.job(),
                    e.growType(), e.level(), e.reserved7(), e.statusFlag(), e.equipment(),
                    e.reserved15(), e.guildId(), e.privateStore(), e.premiumFlag(),
                    e.pcRoomNo(), e.unknown20(), e.unknown21(), e.banRemainDays()));
        }
        replyDeleteSuccess(s);
    }

    private static void replyDeleteSuccess(GameSession s) {
        s.replyNow(Cmd6DeleteCharacterRequest.PACKET_ID, new byte[]{1});
        sendCharacterList(s, "删角色之后的全量重发");
    }

    private static void replyDeleteFailure(GameSession s, int code, String why) {
        s.replyNow(Cmd6DeleteCharacterRequest.PACKET_ID, new byte[]{0, (byte) code});
    }

    public static void returnToSelect(GameSession s) {
        // 先记下要注销的角色号 —— 下面 s.characNo(-1) 之后就拿不到了。
        int leavingCharacNo = s.characNo();
        s.party().onLeaveWorld("回选角界面");
        NeighborService.announceDeparture(s, "回选角界面");
        s.enchant().onLeaveUser();
        s.fatigue().save();
        s.fatigue().clear();
        s.stamina().clear();
        s.inventory().save();
        s.inventory().clear();
        s.itemLock().save();
        s.itemLock().clear();
        s.skills().save();
        s.skills().clear();
        s.expertJob().save();
        s.expertJob().clear();
        s.mail().clear();
        s.dungeonClear().clear();
        s.premium().clear();
        s.characNo(-1);
        s.characName(null);
        s.stats(null);
        // 小退完成：上面那些整块数据都已 save+clear，服务端不再持有这个角色了 ——
        // 向外部工具注销（网页 GM 据此放行直写，见 PlayerPresence）。
        PlayerPresence.leave(s.store(), leavingCharacNo);
        s.landAt(ServerConfig.LANDING_TOWN_ID, ServerConfig.LANDING_AREA_ID,
                ServerConfig.TOWN_SPAWN_X, ServerConfig.TOWN_SPAWN_Y);
        s.position(-1, -1, com.dylan.protocol.body.Noti24Body.DIRECTION_RIGHT);
        s.dungeonSelectOpened(false);
        s.tutorials().reset();
        s.emotion(EmotionService.NONE);

        byte[] body = Cmd7ReplyBody.success();
        s.reply(Cmd7ReplyBody.PACKET_ID, body);

        sendCharacterList(s, "应 CMD 7 —— 选角界面刚把自己的列表清空了");
    }

    public static void queryCharacInfo(GameSession s, byte[] plain) {
        if (!s.hasCharacter()) {
            s.fail("CMD " + Cmd146QueryCharacInfoRequest.PACKET_ID
                    + " QUERY_CHARAC_INFO 来得比选角还早（官方 get_state <= 2 那道闸）");
            return;
        }
        Cmd146QueryCharacInfoRequest request = Cmd146QueryCharacInfoRequest.parse(plain);

        if (request.name().equals(s.characName())) {
            replyQueryFailure(s, Cmd146ReplyBody.ERROR_SELF, "查的是自己");
            return;
        }
        var found = s.store().character().queryCharacterByName(request.name());
        if (found.isEmpty()) {
            replyQueryFailure(s, Cmd146ReplyBody.ERROR_NO_SUCH_CHARACTER,
                    "没有「" + request.name() + "」这个角色，或者它已经被删了");
            return;
        }
        var row = found.get();
        int gifts = s.store().account().giftRecvCount(row.mId());
        byte[] body = new Cmd146ReplyBody(request.name(), row.level(), row.job(),
                row.growType(), row.sex(), gifts).toBytes();
        s.reply(Cmd146ReplyBody.PACKET_ID, body);
    }

    private static void replyQueryFailure(GameSession s, int errorCode, String why) {
        byte[] body = Cmd146ReplyBody.failure(errorCode);
        s.reply(Cmd146ReplyBody.PACKET_ID, body);
    }
}
