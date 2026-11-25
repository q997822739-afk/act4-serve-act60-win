package com.dylan.game;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Noti23Body;
import com.dylan.protocol.body.Noti24Body;
import com.dylan.protocol.body.Noti2OwnCharacterEntry;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class SessionRegistry {
    private static final Set<GameSession> SESSIONS = ConcurrentHashMap.newKeySet();

    private static final UniqueIdAllocator UNIQUE_IDS = new UniqueIdAllocator();

    private static final Map<GameSession, Presence> PRESENCES = new ConcurrentHashMap<>();

    private static final Map<Integer, Set<GameSession>> BY_AREA = new ConcurrentHashMap<>();

    private SessionRegistry() {
    }

    private static int areaKey(int town, int area) {
        return (town << 8) | (area & 0xFF);
    }

    public record Presence(int uniqueId, int characNo, String name,
                           Noti2OwnCharacterEntry identity,
                           int town, int area, int x, int y, int direction,
                           boolean inTown, boolean privateArea,
                           int accId, com.dylan.protocol.body.Cmd2SetUdpIpPortRequest peer,
                           int sex,
                           com.dylan.protocol.body.Noti2PartyEntry detail) {
        public Presence(int uniqueId, int characNo, String name,
                        Noti2OwnCharacterEntry identity,
                        int town, int area, int x, int y, int direction,
                        boolean inTown, boolean privateArea) {
            this(uniqueId, characNo, name, identity, town, area, x, y, direction,
                    inTown, privateArea, 0, null);
        }

        public Presence(int uniqueId, int characNo, String name,
                        Noti2OwnCharacterEntry identity,
                        int town, int area, int x, int y, int direction,
                        boolean inTown, boolean privateArea,
                        int accId, com.dylan.protocol.body.Cmd2SetUdpIpPortRequest peer) {
            this(uniqueId, characNo, name, identity, town, area, x, y, direction,
                    inTown, privateArea, accId, peer,
                    com.dylan.protocol.body.Noti9PartyInfoBody.SEX_FALLBACK, null);
        }

        public Presence(int uniqueId, int characNo, String name,
                        Noti2OwnCharacterEntry identity,
                        int town, int area, int x, int y, int direction,
                        boolean inTown, boolean privateArea,
                        int accId, com.dylan.protocol.body.Cmd2SetUdpIpPortRequest peer,
                        int sex) {
            this(uniqueId, characNo, name, identity, town, area, x, y, direction,
                    inTown, privateArea, accId, peer, sex, null);
        }

        public Noti24Body.TownUser asTownUser() {
            return new Noti24Body.TownUser(uniqueId, x, y, direction,
                    ServerConfig.VISIBLE_VALUES);
        }

        public Noti23Body asUserArea() {
            return new Noti23Body(uniqueId, town, area, x, y, direction,
                    visibleValuesOf(uniqueId));
        }

        private static int visibleValuesOf(int uniqueId) {
            GameSession session = findInWorld(uniqueId);
            return session != null && session.invisible()
                    ? ServerConfig.VISIBLE_VALUES & ~1
                    : ServerConfig.VISIBLE_VALUES;
        }

        Presence movedTo(int newTown, int newArea, int newX, int newY, int newDirection,
                         boolean nowPrivateArea) {
            return new Presence(uniqueId, characNo, name, identity, newTown, newArea,
                    newX, newY, newDirection, inTown, nowPrivateArea, accId, peer, sex, detail);
        }

        Presence positionedAt(int newX, int newY, int newDirection) {
            return new Presence(uniqueId, characNo, name, identity, town, area,
                    newX, newY, newDirection, inTown, privateArea, accId, peer, sex, detail);
        }

        Presence inTown(boolean nowInTown) {
            return new Presence(uniqueId, characNo, name, identity, town, area, x, y,
                    direction, nowInTown, privateArea, accId, peer, sex, detail);
        }

        Presence withIdentity(Noti2OwnCharacterEntry updated) {
            return new Presence(uniqueId, characNo, name, updated, town, area, x, y,
                    direction, inTown, privateArea, accId, peer, sex, detail);
        }

        Presence withDetail(com.dylan.protocol.body.Noti2PartyEntry updated) {
            return new Presence(uniqueId, characNo, name, identity, town, area, x, y,
                    direction, inTown, privateArea, accId, peer, sex, updated);
        }
    }

    public static void enter(GameSession session) {
        SESSIONS.add(session);
        session.uniqueId(UNIQUE_IDS.allocate());
    }

    public static void leave(GameSession session) {
        if (!SESSIONS.remove(session)) {
            return;
        }
        leaveWorld(session);
        UNIQUE_IDS.release(session.uniqueId());
        session.uniqueId(UniqueIdAllocator.UNASSIGNED);
    }

    public static GameSession findByUniqueId(int uniqueId) {
        if (uniqueId < UniqueIdAllocator.FIRST || uniqueId > UniqueIdAllocator.LAST) {
            return null;
        }
        for (GameSession s : SESSIONS) {
            if (s.uniqueId() == uniqueId) {
                return s;
            }
        }
        return null;
    }

    public static int size() {
        return SESSIONS.size();
    }

    public static Collection<GameSession> all() {
        return List.copyOf(SESSIONS);
    }

    public static List<GameSession> playing() {
        return SESSIONS.stream().filter(GameSession::hasCharacter).toList();
    }

    public static GameSession findByAccountId(long mId, GameSession except) {
        for (GameSession s : SESSIONS) {
            if (s == except || !s.isActive() || s.account() == null) {
                continue;
            }
            if (s.account().mId() == mId) {
                return s;
            }
        }
        return null;
    }

    public static GameSession findInWorld(int uniqueId) {
        if (uniqueId < UniqueIdAllocator.FIRST || uniqueId > UniqueIdAllocator.LAST) {
            return null;
        }
        for (Map.Entry<GameSession, Presence> e : PRESENCES.entrySet()) {
            if (e.getValue().uniqueId() == uniqueId && e.getKey().isActive()) {
                return e.getKey();
            }
        }
        return null;
    }

    public static GameSession findByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (Map.Entry<GameSession, Presence> e : PRESENCES.entrySet()) {
            if (name.equals(e.getValue().name()) && e.getKey().isActive()) {
                return e.getKey();
            }
        }
        return null;
    }

    public static Presence reachWorld(GameSession session, Presence presence) {
        Presence previous = PRESENCES.put(session, presence);
        if (previous != null) {
            unindex(session, previous);
        }
        index(session, presence);
        return previous;
    }

    public static Presence leaveWorld(GameSession session) {
        Presence gone = PRESENCES.remove(session);
        if (gone == null) {
            return null;
        }
        unindex(session, gone);
        return gone;
    }

    public static Presence presenceOf(GameSession session) {
        return PRESENCES.get(session);
    }

    public static void moveTo(GameSession session, int town, int area, int x, int y,
                              int direction, boolean privateArea) {
        PRESENCES.computeIfPresent(session, (s, p) -> {
            Presence moved = p.movedTo(town, area, x, y, direction, privateArea).inTown(true);
            index(s, moved);
            if (areaKey(p.town(), p.area()) != areaKey(town, area)) {
                unindex(s, p);
            }
            return moved;
        });
    }

    public static boolean positionAt(GameSession session, int x, int y, int direction) {
        Presence before = PRESENCES.get(session);
        if (before == null) {
            return false;
        }
        PRESENCES.computeIfPresent(session, (s, p) -> p.positionedAt(x, y, direction));
        return before.x() != x || before.y() != y || before.direction() != direction;
    }

    public static void enterDungeon(GameSession session) {
        PRESENCES.computeIfPresent(session, (s, p) -> {
            unindex(s, p);
            return p.inTown(false);
        });
    }

    public static void updateIdentity(GameSession session, Noti2OwnCharacterEntry identity) {
        PRESENCES.computeIfPresent(session, (s, p) -> p.withIdentity(identity));
    }

    public static void updateStamina(GameSession session, int stamina) {
        PRESENCES.computeIfPresent(session,
                (s, p) -> p.withIdentity(p.identity().withStamina(stamina)));
    }

    public static void updateDetail(GameSession session,
                                    com.dylan.protocol.body.Noti2PartyEntry detail) {
        PRESENCES.computeIfPresent(session, (s, p) -> p.withDetail(detail));
    }

    public static void updateExp(GameSession session, long exp) {
        int clamped = (int) Math.min(Integer.MAX_VALUE, Math.max(0, exp));
        PRESENCES.computeIfPresent(session, (s, p) -> p.detail() == null
                ? p
                : p.withDetail(p.detail().withTotalExp(clamped)));
    }

    /**
     * 城镇互见：town/area 里能互相看见的其他在线角色（在城里、不在私人区域、已经有外观）。
     * 流程照 A21 服务端 TownHandler.BroadcastAreaRosterAsync / GetSessionsInArea。
     */
    public static List<GameSession> inArea(int town, int area, GameSession except) {
        Set<GameSession> bucket = BY_AREA.get(areaKey(town, area));
        if (bucket == null) {
            return List.of();
        }
        List<GameSession> out = new java.util.ArrayList<>(bucket.size());
        for (GameSession s : bucket) {
            Presence p = PRESENCES.get(s);
            // 出生点 1/1 是 Gate = 赛丽亚房间（A4 town/elvengard.twn area 1 `Elvengard/Gate.map` `[gate]`），每人一间，互相看不见。
            // 2026-10-28 用户实测「赛丽亚房间能看到其他玩家」，之前对 A4 放开是错的。
            // 一个端口一个频道。城镇 7002 和角斗场 7004 的区域号会撞上，不能互相看见。
            if (s == except || p == null || !s.isActive() || s.localPort() != except.localPort()
                    || !p.inTown()
                    || p.privateArea()
                    || p.identity() == null || p.town() != town || p.area() != area) {
                continue;
            }
            out.add(s);
        }
        return out;
    }

    public static void forEachInWorld(BiConsumer<GameSession, Presence> action) {
        PRESENCES.forEach((session, presence) -> {
            if (session.isActive()) {
                action.accept(session, presence);
            }
        });
    }

    /** 同一个监听端口上的在线角色。官方一个频道一份 GameWorld，名单不跨频道。 */
    public static void forEachOnPort(int port, BiConsumer<GameSession, Presence> action) {
        PRESENCES.forEach((session, presence) -> {
            if (session.isActive() && session.localPort() == port) {
                action.accept(session, presence);
            }
        });
    }

    private static void index(GameSession session, Presence presence) {
        BY_AREA.computeIfAbsent(areaKey(presence.town(), presence.area()),
                k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    private static void unindex(GameSession session, Presence presence) {
        int key = areaKey(presence.town(), presence.area());
        Set<GameSession> bucket = BY_AREA.get(key);
        if (bucket == null) {
            return;
        }
        bucket.remove(session);
        BY_AREA.computeIfPresent(key, (k, v) -> v.isEmpty() ? null : v);
    }

    static void clear() {
        SESSIONS.clear();
        PRESENCES.clear();
        BY_AREA.clear();
        UNIQUE_IDS.reset();
    }
}
