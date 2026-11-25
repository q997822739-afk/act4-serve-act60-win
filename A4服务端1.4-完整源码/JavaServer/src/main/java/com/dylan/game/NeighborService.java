package com.dylan.game;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Noti22UserPositionBody;
import com.dylan.protocol.body.Noti23Body;
import com.dylan.protocol.body.Noti24Body;
import com.dylan.protocol.body.Noti2Body;
import com.dylan.protocol.body.Noti2Entry;
import com.dylan.protocol.body.Noti2OwnCharacterEntry;
import com.dylan.protocol.body.Noti3UserStateBody;
import com.dylan.protocol.body.Noti6UserLeaveBody;

import java.util.ArrayList;
import java.util.List;

public final class NeighborService {

    /** A4 官方 NOTI 23/24 末字节（可见值），A5 默认 01。 */
    static final int A4_VISIBLE = 3;

    private NeighborService() {
    }

    public static int exchangeIdentities(GameSession s, Noti2OwnCharacterEntry self, String why) {
        sendWorldIdentities(s, why);
        return announceIdentity(s, self, why);
    }

    public static int sendWorldIdentities(GameSession s, String why) {
        List<Noti2Entry> others = new ArrayList<>();
        int port = s.localPort();
        SessionRegistry.forEachOnPort(port, (other, presence) -> {
            if (other != s && presence.identity() != null) {
                others.add(presence.identity());
            }
        });
        if (others.isEmpty()) {
            return 0;
        }
        byte[] body = new Noti2Body(Noti2Body.SUBTYPE_OWN_CHARACTER, others).toBytes();
        s.sendNow(Noti2Body.PACKET_ID, body);
        return others.size();
    }

    public static int announceIdentity(GameSession s, Noti2OwnCharacterEntry entry, String why) {
        byte[] body = Noti2Body.ownCharacter(entry).toBytes();
        int[] sent = {0};
        boolean[] gotItHimself = {false};
        int port = s.localPort();
        SessionRegistry.forEachOnPort(port, (other, presence) -> {
            other.sendNow(Noti2Body.PACKET_ID, body);
            sent[0]++;
            if (other == s) {
                gotItHimself[0] = true;
            }
        });
        if (!gotItHimself[0]) {
            s.sendNow(Noti2Body.PACKET_ID, body);
            sent[0]++;
        }
        return sent[0];
    }

    public static int announceUserState(Noti3UserStateBody body, String why) {
        if (body.users().isEmpty()) {
            return 0;
        }
        byte[] bytes = body.toBytes();
        java.util.Set<Integer> ports = new java.util.HashSet<>();
        for (Noti3UserStateBody.Entry entry : body.users()) {
            GameSession who = SessionRegistry.findInWorld(entry.userId());
            if (who != null) {
                ports.add(who.localPort());
            }
        }
        if (ports.isEmpty()) {
            return 0;
        }
        int[] sent = {0};
        SessionRegistry.forEachInWorld((other, presence) -> {
            if (ports.contains(other.localPort())) {
                other.sendNow(Noti3UserStateBody.PACKET_ID, bytes);
                sent[0]++;
            }
        });
        return sent[0];
    }

    public static int enterArea(GameSession s, String why) {
        SessionRegistry.Presence self = SessionRegistry.presenceOf(s);
        if (self == null) {
            sendSoloRoster(s, why);
            return 0;
        }
        if (ServerConfig.A4) {
            // A4 官方在 Gate（A5 当私人区域）也发 NOTI 3 → 23 → 24，可见值 03（enter_off.txt 逐字节）。
            byte[] state = Noti3UserStateBody.single(s.uniqueId(), Noti3UserStateBody.STATE_IN_TOWN).toBytes();
            s.sendNow(Noti3UserStateBody.PACKET_ID, state);
            s.sendNow(Noti23Body.PACKET_ID, a4Area(self).toBytes());
            List<GameSession> others = SessionRegistry.inArea(self.town(), self.area(), s);
            List<SessionRegistry.Presence> neighbours = new ArrayList<>(others.size());
            for (GameSession o : others) {
                SessionRegistry.Presence p = SessionRegistry.presenceOf(o);
                if (p != null) {
                    neighbours.add(p);
                }
            }
            sendRoster(s, self, neighbours, why);
            projectEachOther(s, self, neighbours);
            // 摆摊：NOTI 84 要求摊主的角色 / 头像已在场景里（0042CA9E 不新建），所以放在外观之后
            StallService.sendAreaStalls(s, self.town(), self.area());
            // 决斗频道：客户端是「进区域」之后才建决斗场大厅的，这里补一次空房间表（有房间则不补，
            // 因为 NOTI 56 是追加语义）。见 PvpLobby.sendEmptyLobbyIfEmpty。
            PvpLobby.sendEmptyLobbyIfEmpty(s);
            return neighbours.size();
        }
        if (self.privateArea()) {
            sendRoster(s, self, List.of(), why + " —— 赛利亚房间");
            return 0;
        }

        s.sendNow(Noti23Body.PACKET_ID, self.asUserArea().toBytes());
        sendRoster(s, self, List.of(), why);
        return 0;
    }

    public static int announceDeparture(GameSession s, String why) {
        SessionRegistry.Presence self = SessionRegistry.presenceOf(s);
        if (self == null) {
            return 0;
        }
        if (ServerConfig.A4) {
            StallService.close(s, true);
            TradeService.cancel(s, why);
            PartyInviteService.onLeaveWorld(s, why);
            GuildService.onLeaveWorld(s);
            MentorService.onLeaveWorld(s);
            FriendService.onLeaveWorld(s);
            PvpLobby.onLeaveWorld(s);
        }
        if (ServerConfig.A4 && self.inTown()) {
            // 断线 / 回选角：同区域的人把这个分身收掉。用 NOTI 23 报到"副本区域 255"。
            leaveArea(s, self.town(), self.area(), departed(self));
        }
        if (ServerConfig.A4) {
            // NOTI 2/0 按端口发给了整个频道，光靠同区域的 NOTI 23 删不掉邀请名单。
            // A4 NOTI 6（004208A1）只读 u16 连接号，然后 00539C70 把人从 0xD7F274 拿掉。
            byte[] gone = new Noti6UserLeaveBody(self.uniqueId()).toBytes();
            int port = s.localPort();
            SessionRegistry.forEachOnPort(port, (other, presence) -> {
                if (other != s) {
                    other.sendNow(Noti6UserLeaveBody.PACKET_ID, gone);
                }
            });
        }
        SessionRegistry.leaveWorld(s);
        return 0;
    }

    /**
     * 官方 {@code GameWorld::out_from_pvp}：离开决斗房间后把人塞回当前区域，
     * 并给整个频道发 NOTI 3 状态 0（在城镇）。房间窗是另一条 NOTI 43 关的，这里只恢复角斗场里的人。
     * 不通知别人“又来了一个人”，避免同区域已经看得到的分身再叠一个。
     */
    public static void replayAreaToSelf(GameSession s) {
        if (!ServerConfig.A4 || s == null || !s.isActive()) {
            return;
        }
        SessionRegistry.Presence self = SessionRegistry.presenceOf(s);
        if (self == null || !self.inTown()) {
            return;
        }
        byte[] state = Noti3UserStateBody.single(s.uniqueId(), Noti3UserStateBody.STATE_IN_TOWN).toBytes();
        int port = s.localPort();
        SessionRegistry.forEachOnPort(port, (other, presence) ->
                other.sendNow(Noti3UserStateBody.PACKET_ID, state));
        s.sendNow(Noti23Body.PACKET_ID, a4Area(self).toBytes());
        List<GameSession> others = SessionRegistry.inArea(self.town(), self.area(), s);
        List<SessionRegistry.Presence> neighbours = new ArrayList<>(others.size());
        for (GameSession o : others) {
            SessionRegistry.Presence p = SessionRegistry.presenceOf(o);
            if (p != null) {
                neighbours.add(p);
            }
        }
        sendRoster(s, self, neighbours, "回角斗场");
        for (SessionRegistry.Presence other : neighbours) {
            if (other.identity() != null) {
                s.sendNow(Noti2Body.PACKET_ID, Noti2Body.ownCharacter(other.identity()).toBytes());
            }
            s.sendNow(Noti23Body.PACKET_ID, a4Area(other).toBytes());
        }
        if (self.identity() != null) {
            s.sendNow(Noti2Body.PACKET_ID, Noti2Body.ownCharacter(self.identity()).toBytes());
        }
    }

    /**
     * 城镇互见：s 离开 town/area（换区域 / 进副本），通知那里的人。
     * {@code where} 是 s 的新位置；客户端收到 town/area 和自己不同的 NOTI 23 就把这个人移出场景
     * （DNF.exe 00423D50 比较 town/area；A21 TownHandler.SetUserAreaCoreAsync 同法）。
     */
    public static int leaveArea(GameSession s, int town, int area, Noti23Body where) {
        if (!ServerConfig.A4) {
            return 0;
        }
        // 摆摊的人走开（换区域 / 进副本）= 收摊；交易中走开 = 取消（A21 CancelBeforeTransition）
        StallService.Stall closed = StallService.close(s, true);
        if (closed != null) {
            // 换区域 / 进副本收摊：同样要把货架上的东西放回背包窗口
            StallService.restoreBag(s, closed, "离开区域收摊");
        }
        TradeService.cancel(s, "离开区域");
        byte[] body = where.toBytes();
        List<GameSession> others = SessionRegistry.inArea(town, area, s);
        for (GameSession o : others) {
            o.sendNow(Noti23Body.PACKET_ID, body);
        }
        return others.size();
    }

    /** 城镇互见：CMD 37 移动转发给同区域的人（NOTI 22；A21 SET_USER_POSITION 广播同法，不回给自己）。 */
    public static int relayPosition(GameSession s, int x, int y, int direction, int movement) {
        if (!ServerConfig.A4) {
            return 0;
        }
        SessionRegistry.Presence self = SessionRegistry.presenceOf(s);
        if (self == null || !self.inTown()) {
            return 0;
        }
        List<GameSession> others = SessionRegistry.inArea(self.town(), self.area(), s);
        if (others.isEmpty()) {
            return 0;
        }
        // NOTI 22 = u16 uid, u16 x, u16 y, u8 方向, u16 移动状态（DNF.exe 00423BB2 读 u16 u16 u16 u8 u16）
        byte[] body = new Noti22UserPositionBody(s.uniqueId(), x, y, direction, movement).toBytes();
        for (GameSession o : others) {
            o.sendNow(Noti22UserPositionBody.PACKET_ID, body);
        }
        return others.size();
    }

    /** 进区域那一刻的 NOTI 23：自己的 town/area/坐标，可见值 03。 */
    static Noti23Body a4Area(SessionRegistry.Presence p) {
        return new Noti23Body(p.uniqueId(), p.town(), p.area(), p.x(), p.y(), p.direction(), A4_VISIBLE);
    }

    /** 离开城镇（断线 / 回选角）时给别人的 NOTI 23：区域 255。 */
    static Noti23Body departed(SessionRegistry.Presence p) {
        return Noti23Body.enterDungeon(p.uniqueId(), p.town(), p.x(), p.y(), p.direction(), A4_VISIBLE);
    }

    /**
     * 城镇互见（A21 BroadcastAreaRosterAsync）：到达者先收名册（NOTI 24，含别人），
     * 再逐个收别人的外观 NOTI 2/0 + NOTI 23；已在场的人只收到达者的 NOTI 2/0 + NOTI 23，
     * 不发 NOTI 24（A21 实测发给已在场的人会切场景、关界面）。
     */
    private static void projectEachOther(GameSession s, SessionRegistry.Presence self,
                                         List<SessionRegistry.Presence> neighbours) {
        if (neighbours.isEmpty()) {
            return;
        }
        byte[] selfArea = a4Area(self).toBytes();
        byte[] selfLook = self.identity() == null ? null : Noti2Body.ownCharacter(self.identity()).toBytes();
        for (SessionRegistry.Presence other : neighbours) {
            if (other.identity() != null) {
                s.sendNow(Noti2Body.PACKET_ID, Noti2Body.ownCharacter(other.identity()).toBytes());
            }
            s.sendNow(Noti23Body.PACKET_ID, a4Area(other).toBytes());
            GameSession o = SessionRegistry.findInWorld(other.uniqueId());
            if (o == null) {
                continue;
            }
            if (selfLook != null) {
                o.sendNow(Noti2Body.PACKET_ID, selfLook);
            }
            o.sendNow(Noti23Body.PACKET_ID, selfArea);
        }
    }

    private static void sendRoster(GameSession s, SessionRegistry.Presence self,
                                   List<SessionRegistry.Presence> neighbours, String why) {
        List<Noti24Body.TownUser> users = new ArrayList<>(neighbours.size() + 1);
        users.add(new Noti24Body.TownUser(self.uniqueId(), self.x(), self.y(),
                self.direction(), ServerConfig.A4 ? A4_VISIBLE : ServerConfig.VISIBLE_VALUES));
        for (SessionRegistry.Presence other : neighbours) {
            users.add(ServerConfig.A4
                    ? new Noti24Body.TownUser(other.uniqueId(), other.x(), other.y(), other.direction(), A4_VISIBLE)
                    : other.asTownUser());
        }
        byte[] body = new Noti24Body(self.town(), self.area(), users).toBytes();
        s.sendNow(Noti24Body.PACKET_ID, body);
    }

    /** 只给自己重发一次本区域名单（NOTI 24），不动别人。A4 官方黑钻开通（CMD 68）后会补这一条。 */
    public static void resendRoster(GameSession s, String why) {
        SessionRegistry.Presence self = SessionRegistry.presenceOf(s);
        if (self == null) {
            sendSoloRoster(s, why);
            return;
        }
        List<SessionRegistry.Presence> neighbours = new ArrayList<>();
        if (!self.privateArea()) {
            for (GameSession o : SessionRegistry.inArea(self.town(), self.area(), s)) {
                SessionRegistry.Presence p = SessionRegistry.presenceOf(o);
                if (p != null) {
                    neighbours.add(p);
                }
            }
        }
        sendRoster(s, self, neighbours, why);
    }

    private static void sendSoloRoster(GameSession s, String why) {
        byte[] body = Noti24Body.solo(s.townId(), s.areaId(), s.uniqueId(),
                s.lastX() < 0 ? ServerConfig.TOWN_SPAWN_X : s.lastX(),
                s.lastY() < 0 ? ServerConfig.TOWN_SPAWN_Y : s.lastY()).toBytes();
        s.sendNow(Noti24Body.PACKET_ID, body);
    }
}
