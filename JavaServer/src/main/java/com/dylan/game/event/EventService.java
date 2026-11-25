package com.dylan.game.event;

import java.util.ArrayList;
import java.util.List;

import com.dylan.game.GameSession;
import com.dylan.game.SessionRegistry;
import com.dylan.protocol.body.Noti120EventInfoBody;

public final class EventService {
    private EventService() {
    }

    private static final EventState[] SLOTS = new EventState[EventNo.SLOT_COUNT];

    static {
        reset();
    }

    public static synchronized void reset() {
        java.util.Arrays.fill(SLOTS, null);
        for (EventDefinition def : EventDefinition.all()) {
            SLOTS[def.no()] = EventState.idle(def);
        }
    }

    public static EventState get(int no) {
        if (no < 0 || no > EventNo.MAX_EVENT_NO) {
            return null;
        }
        synchronized (EventService.class) {
            return SLOTS[no];
        }
    }

    public static boolean isRunning(int no, GameSession session) {
        EventState state = get(no);
        if (state == null || !state.running()) {
            return false;
        }
        if (no == EventNo.PC_ROOM_BURNING) {
            return session != null && session.premium() != null
                    && session.premium().isAffected(
                            com.dylan.game.PremiumService.PREMIUM_PC_ROOM_BONUS);
        }
        return true;
    }

    public static boolean isRunning(int no) {
        return isRunning(no, null);
    }

    public static int param1(int no) {
        EventState state = get(no);
        if (state == null) {
            EventDefinition def = EventDefinition.of(no);
            return def == null ? 0 : def.defaultParam1();
        }
        return state.param1();
    }

    public static int param2(int no) {
        EventState state = get(no);
        if (state == null) {
            EventDefinition def = EventDefinition.of(no);
            return def == null ? 0 : def.defaultParam2();
        }
        return state.param2();
    }

    public static double bonusFactor(int no, GameSession session) {
        return isRunning(no, session) ? param1(no) / 100.0 : 0.0;
    }

    public static List<Integer> runningNumbers() {
        List<Integer> out = new ArrayList<>();
        synchronized (EventService.class) {
            for (int no = 0; no <= EventNo.MAX_EVENT_NO; no++) {
                EventState state = SLOTS[no];
                if (state != null && state.running()) {
                    out.add(no);
                }
            }
        }
        return out;
    }

    static boolean filterEvent(int no) {
        return false;
    }

    public static boolean start(int no, int param1, int param2) {
        if (filterEvent(no)) {
            return true;
        }
        EventDefinition.of(no);
        EventState before;
        synchronized (EventService.class) {
            before = no < 0 || no > EventNo.MAX_EVENT_NO ? null : SLOTS[no];
            if (before == null) {
                return false;
            }
            SLOTS[no] = before.started(param1, param2);
        }
        if (before.running() && before.param1() == param1 && before.param2() == param2) {
            return true;
        }
        applySideEffects(no);
        broadcast();
        return true;
    }

    public static boolean end(int no) {
        if (filterEvent(no)) {
            return true;
        }
        EventDefinition def = EventDefinition.of(no);
        boolean wasRunning;
        synchronized (EventService.class) {
            EventState before = no < 0 || no > EventNo.MAX_EVENT_NO ? null : SLOTS[no];
            if (before == null) {
                return false;
            }
            wasRunning = before.running();
            SLOTS[no] = before.ended(def);
        }
        if (!wasRunning) {
            return true;
        }
        broadcast();
        applySideEffects(no);
        return true;
    }

    private static void applySideEffects(int no) {
        if (no == EventNo.MAX_FATIGUE_FACTOR) {
            com.dylan.game.FatigueService.sendFatigueEventToWorld();
        }
    }

    public static void broadcast() {
        List<GameSession> targets = SessionRegistry.playing();
        int sent = 0;
        for (GameSession s : targets) {
            if (!s.isActive()) {
                continue;
            }
            s.run(() -> {
                if (s.isActive() && s.hasCharacter()) {
                    sendTo(s);
                }
            });
            sent++;
        }
    }

    public static Noti120EventInfoBody makeInfo(GameSession session) {
        List<Integer> running = new ArrayList<>();
        synchronized (EventService.class) {
            for (int no = 0; no <= EventNo.MAX_EVENT_NO; no++) {
                EventState state = SLOTS[no];
                if (state != null && state.running()) {
                    running.add(no);
                }
            }
        }
        running.removeIf(no -> !isRunning(no, session));
        return new Noti120EventInfoBody(running, List.of());
    }

    public static void sendTo(GameSession s) {
        // A4 的 NOTI 120 是 4 字节官方恒值 00 80 00 20（含义未知，依据见 Noti120EventInfoBody.A4_BYTES），
        // 与 A5 的「u16 数 + 活动号 + u8 附加数」不是一个格式。
        byte[] body = makeInfo(s).toBytes();
        s.sendNow(Noti120EventInfoBody.PACKET_ID, body);
    }

}
