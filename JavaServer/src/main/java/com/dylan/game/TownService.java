package com.dylan.game;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Cmd37SetUserPositionRequest;
import com.dylan.protocol.body.Cmd38ReplyBody;
import com.dylan.protocol.body.Cmd38SetUserAreaRequest;
import com.dylan.game.event.EventService;
import com.dylan.protocol.body.Noti136Body;
import com.dylan.protocol.body.Noti3UserStateBody;
import com.dylan.pvf.script.TownTable;

import java.util.List;

public final class TownService {
    private TownService() {
    }

    public static TownTable.Spawn landingFor(GameSession s, int village) {
        TownTable towns = s.scripts() == null ? null : s.scripts().towns();
        return landingFor(towns, village);
    }

    /**
     * 决斗频道落地。{@code -Ds1a5.pvp.landing}：
     * <ul>
     *   <li>{@code pub}（默认）决斗场大厅 = 2 号城镇（赫顿玛尔）的 <b>pub 区</b>
     *       （{@code map/hendonmyre/hendon_pub.map}，PVF 里 {@code [pub] 330 220}）</li>
     *   <li>{@code gate} 2 号城镇的 gate 区（官方成品服频道 51 的坐标就是这里：474,250）</li>
     *   <li>{@code saved} 存档城镇（旧行为）</li>
     * </ul>
     * 2026-10-28：客户端在 kind 4 频道上加载不出大厅会一直黑屏；存档城镇在 1 号城时必黑。
     */
    public static String pvpLandingMode() {
        return System.getProperty("s1a5.pvp.landing", "pub");
    }

    static TownTable.Spawn pvpLanding(GameSession s, int savedVillage) {
        String mode = pvpLandingMode();
        if ("saved".equalsIgnoreCase(mode)) {
            return landingFor(s, savedVillage);
        }
        TownTable towns = s.scripts() == null ? null : s.scripts().towns();
        int village = GameChannel.PVP_VILLAGE;
        if (towns == null) {
            return landingFor(towns, village);
        }
        if ("gate".equalsIgnoreCase(mode)) {
            return landingFor(towns, village);
        }
        // pub：找这座城第一个 [pub] 区，坐标用区参数（[pub] 330 220）
        var town = towns.town(village).orElse(null);
        if (town != null) {
            for (var area : town.areas()) {
                if (area.type() != null && area.type().toLowerCase().contains("pub")) {
                    return new TownTable.Spawn(village, area.id(),
                            area.param(0, ServerConfig.TOWN_SPAWN_X), area.param(1, ServerConfig.TOWN_SPAWN_Y));
                }
            }
        }
        return landingFor(towns, village);
    }

    static TownTable.Spawn landingFor(TownTable towns, int village) {
        if (towns != null) {
            var spawn = towns.spawn(village);
            if (spawn.isPresent()) {
                return spawn.get();
            }
            if (village != ServerConfig.LANDING_TOWN_ID) {
                var fallback = towns.spawn(ServerConfig.LANDING_TOWN_ID);
                if (fallback.isPresent()) {
                    return fallback.get();
                }
            }
        }
        return new TownTable.Spawn(ServerConfig.LANDING_TOWN_ID, ServerConfig.LANDING_AREA_ID,
                ServerConfig.TOWN_SPAWN_X, ServerConfig.TOWN_SPAWN_Y);
    }

    public static void landAt(GameSession s, TownTable.Spawn landing, String why) {
        s.landAt(landing.town(), landing.area(), landing.x(), landing.y());
    }

    public static void reportPosition(GameSession s, byte[] plain) {
        Cmd37SetUserPositionRequest pos = Cmd37SetUserPositionRequest.parse(plain);
        s.position(pos.x(), pos.y(), pos.direction());

        if (!s.hasCharacter()) {
            return;
        }
        if (s.inDungeon()) {
            return;
        }

        SessionRegistry.positionAt(s, pos.x(), pos.y(), pos.direction());

        if (s.areaRosterSent()) {
            // 原地起步 / 停下只改移动状态，坐标不变也要转发
            NeighborService.relayPosition(s, pos.x(), pos.y(), pos.direction(), pos.movement());
            return;
        }
        s.areaRosterSent(true);
        NeighborService.enterArea(s, "客户端报的落点");
        sendEnterGameWorldComplete(s);
    }

    public static void changeArea(GameSession s, byte[] plain) {
        Cmd38SetUserAreaRequest req = Cmd38SetUserAreaRequest.parse(plain);

        if (!s.hasCharacter()) {
            return;
        }
        if (s.inDungeon()) {
            return;
        }
        if (req.townId() == s.townId() && req.areaId() == s.areaId()) {
            return;
        }
        if (!checkValidArea(s, req.townId(), req.areaId())) {
            return;
        }

        boolean crossTown = req.townId() != s.townId();
        int fromTown = s.townId();
        // 城镇互见：先让旧区域的人看到他去了新 town/area（客户端据此把他移出场景）
        NeighborService.leaveArea(s, s.townId(), s.areaId(), new com.dylan.protocol.body.Noti23Body(
                s.uniqueId(), req.townId(), req.areaId(), req.x(), req.y(), req.direction(),
                NeighborService.A4_VISIBLE));
        s.townId(req.townId());
        s.areaId(req.areaId());
        s.areaRosterSent(true);
        s.position(req.x(), req.y(), req.direction());

        SessionRegistry.moveTo(s, s.townId(), s.areaId(), req.x(), req.y(), req.direction(),
                isPrivateArea(s, s.townId(), s.areaId()));
        NeighborService.enterArea(s, crossTown ? "跨城镇的落点" : "换区域的落点");
        if (crossTown) {
            TownTable.Spawn gate = landingFor(s, req.townId());
            s.landingPoint(gate.area(), gate.x(), gate.y());
            persistVillage(s, fromTown);
        }
    }

    private static boolean checkValidArea(GameSession s, int destTown, int destArea) {
        var towns = s.scripts() == null ? null : s.scripts().towns();
        if (towns == null) {
            return true;
        }
        if (towns.checkValidArea(destTown, destArea)
                && towns.checkValidArea(s.townId(), s.areaId())) {
            return true;
        }
        byte[] reject = Cmd38ReplyBody.rejected(Cmd38ReplyBody.ERROR_21, 0);
        s.replyNow(Cmd38ReplyBody.PACKET_ID, reject);
        return false;
    }

    private static void persistVillage(GameSession s, int fromTown) {
        // 按连接判（决斗频道是临时城镇，别把它的 town 写回存档）。
        if (s.pvpChannel()) {
            return;
        }
        if (s.store() == null || s.characNo() <= 0) {
            return;
        }
        try {
            s.store().character().saveCharacterVillage(s.characNo(), s.townId());
        } catch (RuntimeException e) {
        }
    }

    public static void returnToTown(GameSession s, String why) {
        returnToTown(s, why, false);
    }

    public static void returnToTown(GameSession s, String why, boolean wholeParty) {
        if (!s.hasCharacter()) {
            s.fail("要回城了却没有 uniqueId —— 选角那一步没走完");
            return;
        }
        Party party = s.party().current();
        leaveDeathTower(s, party, wholeParty);
        if (party != null && wholeParty && !party.singlePlay()) {
            List<GameSession> members = party.view().members();
            party.endDungeon();
            returnPartyToTown(members, why);
            // 副本 Party 用完即散。城镇队伍栏还在 PartyInviteService 里，下次进本再并一次。
            // 不发 NOTI 9，避免把城镇队伍栏清掉。
            PartyRegistry.dissolve(party);
            return;
        }
        if (party != null) {
            if (party.singlePlay()) {
                party.endDungeon();
            }
            s.party().onLeaveDungeon(why);
        }
        moveOutOfDungeon(s, why);
    }

    private static void leaveDeathTower(GameSession s, Party party, boolean wholeParty) {
        DeathTower tower = s.deathTower();
        if (tower == null) {
            return;
        }
        if (!wholeParty) {
            DeathTowerService.onLeaveUser(s, tower);
            return;
        }
        for (GameSession member : party.view().members()) {
            member.run(() -> DeathTowerService.onLeaveUser(member, tower));
        }
    }

    private static void moveOutOfDungeon(GameSession s, String why) {
        Landing landing = landingOf(s);
        settleWorldTable(s, landing);
        CreatureService.turnStomach(s, false);
        announceArrival(s, landing, why);
    }

    private static void returnPartyToTown(List<GameSession> members, String why) {
        List<Landing> landings = new java.util.ArrayList<>(members.size());
        for (GameSession member : members) {
            Landing landing = landingOf(member);
            landings.add(landing);
            settleWorldTable(member, landing);
        }
        for (int i = 0; i < members.size(); i++) {
            GameSession member = members.get(i);
            Landing landing = landings.get(i);
            member.run(() -> {
                CreatureService.turnStomach(member, false);
                announceArrival(member, landing, why);
            });
        }
    }

    private record Landing(int town, int area, int x, int y, boolean restored) {
    }

    private static Landing landingOf(GameSession s) {
        boolean restored = s.hasTownReturnPosition();
        return new Landing(s.townId(),
                restored ? s.townReturnAreaId() : s.landingAreaId(),
                restored ? s.townReturnX() : s.landingX(),
                restored ? s.townReturnY() : s.landingY(),
                restored);
    }

    private static void settleWorldTable(GameSession s, Landing landing) {
        SessionRegistry.moveTo(s, landing.town(), landing.area(), landing.x(), landing.y(),
                s.lastDirection(), isPrivateArea(s, landing.town(), landing.area()));
    }

    private static void announceArrival(GameSession s, Landing landing, String why) {
        s.dungeonSelectOpened(false);
        s.runSummary().end();
        s.areaId(landing.area());
        s.areaRosterSent(true);
        s.position(landing.x(), landing.y(), s.lastDirection());

        NeighborService.enterArea(s, why);
        NeighborService.announceUserState(
                Noti3UserStateBody.single(s.uniqueId(), Noti3UserStateBody.STATE_IN_TOWN),
                "角色 " + s.characNo() + " 出副本回城（" + why + "）");
        s.fatigue().sendFatigue("出副本回城 —— " + why);
        sendEnterGameWorldComplete(s);
        s.flush();
    }

    static boolean isPrivateArea(GameSession s, int town, int area) {
        var towns = s.scripts() == null ? null : s.scripts().towns();
        return towns != null && towns.isPrivateArea(town, area);
    }

    public static void sendEnterGameWorldComplete(GameSession s) {
        Runnable send = () -> {
            if (!s.isActive()) {
                return;
            }
            if (ServerConfig.ENTER_COMPLETE) {
                s.sendNow(Noti136Body.PACKET_ID, Noti136Body.toBytes());
            }
            sendEventInfo(s);
        };
        int delay = ServerConfig.ENTER_COMPLETE_DELAY_MS;
        if (delay <= 0) {
            send.run();
        } else {
            s.schedule(send, delay);
        }
    }

    private static void sendEventInfo(GameSession s) {
        if (!ServerConfig.EVENT_INFO) {
            return;
        }
        EventService.sendTo(s);
    }
}
