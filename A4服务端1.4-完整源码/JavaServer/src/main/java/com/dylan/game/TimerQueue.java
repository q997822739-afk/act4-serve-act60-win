package com.dylan.game;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class TimerQueue {
    public static final int OBJ_USER = 0;

    public static final int OBJ_PARTY = 1;

    public static final int OBJ_DEATH_TOWER = 6;

    @FunctionalInterface
    public interface Handler {
        void fire(int objIndex, int key, int param);
    }

    private static final Handler[] HANDLERS = new Handler[TimerMessage.COUNT];

    private static final ScheduledExecutorService CLOCK =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "timer-queue");
                t.setDaemon(true);
                return t;
            });

    private TimerQueue() {
    }

    public static void register(int message, Handler handler) {
        if (message < 0 || message >= HANDLERS.length) {
            throw new IllegalArgumentException("TIMER_MESSAGE " + message + " 超出 0.."
                    + (HANDLERS.length - 1) + "，要用新编号先去 TimerMessage 里补一条");
        }
        if (HANDLERS[message] != null) {
            throw new IllegalStateException(
                    TimerMessage.name(message) + " 已经注册过处理器了，不能注册两次");
        }
        HANDLERS[message] = handler;
    }

    public static void registerUser(int message, UserHandler handler) {
        register(message, (objIndex, key, param) -> {
            GameSession session = SessionRegistry.findByUniqueId(objIndex);
            if (session == null) {
                return;
            }
            session.run(() -> handler.fire(session, key, param));
        });
    }

    @FunctionalInterface
    public interface UserHandler {
        void fire(GameSession session, int key, int param);
    }

    public static void registerParty(int message, PartyHandler handler) {
        register(message, (objIndex, key, param) -> {
            Party party = PartyRegistry.byIndex(objIndex);
            if (party == null) {
                return;
            }
            handler.fire(party, key, param);
        });
    }

    @FunctionalInterface
    public interface PartyHandler {
        void fire(Party party, int key, int param);
    }
    public static void registerDeathTower(int message, DeathTowerHandler handler) {
        register(message, (objIndex, key, param) -> {
            DeathTower tower = DeathTowerRegistry.byIndex(objIndex);
            if (tower == null) {
                return;
            }
            handler.fire(tower, key, param);
        });
    }

    @FunctionalInterface
    public interface DeathTowerHandler {
        void fire(DeathTower tower, int key, int param);
    }

    public static void insertTimer(int objType, int objIndex, int message, long seconds,
                                   int key, int param) {
        insertTimerInMillis(objType, objIndex, message, seconds * 1000L, key, param);
    }

    public static void insertTimerInMillis(int objType, int objIndex, int message,
                                           long delayMillis, int key, int param) {
        if (objIndex < 0) {
            throw new IllegalArgumentException(
                    TimerMessage.name(message) + " 的 objIndex 不能是 " + objIndex);
        }
        CLOCK.schedule(() -> fire(objType, objIndex, message, key, param),
                delayMillis, TimeUnit.MILLISECONDS);
    }

    public static void insertPartyTimer(Party party, int message, long seconds, int param) {
        int key = party.genTimerKey(message);
        insertTimer(OBJ_PARTY, party.index(), message, seconds, key, param);
    }

    public static void insertDeathTowerTimer(DeathTower tower, int message, long seconds,
                                             int param) {
        Party party = tower.party();
        if (party == null) {
            throw new IllegalStateException(
                    "塔 " + tower.index() + " 还没 attachParty 就要挂定时器 " + message);
        }
        int key = party.genTimerKey(message);
        insertTimer(OBJ_DEATH_TOWER, tower.index(), message, seconds, key, param);
    }

    private static void fire(int objType, int objIndex, int message, int key, int param) {
        try {
            Handler handler = HANDLERS[message];
            if (handler == null) {
                return;
            }
            handler.fire(objIndex, key, param);
        } catch (RuntimeException e) {
        }
    }
}
