package com.dylan.game;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Noti4StaminaBody;
import com.dylan.protocol.body.Noti33StaminaPenaltyBody;
import com.dylan.store.GameStore;
import com.dylan.store.StaminaStore;
import java.time.Duration;
import java.time.Instant;

public final class StaminaService {

    public static final int FULL = StaminaStore.FULL;

    public static final int PENALTY_VALUE = 10;

    public static final int PENALTY_MIN_LEVEL = 6;

    public static final java.util.List<Integer> PENALTY_EXEMPT_PREMIUMS = java.util.List.of(
            PremiumService.PREMIUM_PC_ROOM_BONUS,
            PremiumService.PREMIUM_BLACK_DIAMOND);

    public static final int RECOVER_PER_TICK = 9;

    public static final long RECOVER_PERIOD_MS = 60_000L;

    private static final long OFFLINE_WINDOW_SECONDS = 600L;

    private final GameSession session;

    private int stamina = FULL;

    private int generation;

    public StaminaService(GameSession session) {
        this.session = session;
    }

    public int current() {
        return stamina;
    }

    public boolean weakened() {
        return stamina < FULL;
    }

    public void load(int characNo) {
        this.generation++;
        GameStore store = session.store();
        if (store == null) {
            stamina = FULL;
            scheduleRecover();
            return;
        }

        stamina = Math.clamp(store.stamina().loadStamina(characNo), 0, FULL);
        if (stamina < FULL) {
            int before = stamina;
            stamina = afterOffline(stamina, lastPlayTime(store, characNo));
            if (stamina != before) {
                persist();
            }
        }
        scheduleRecover();
    }

    static int afterOffline(int stored, Instant lastPlayTime) {
        if (stored >= FULL) {
            return stored;
        }
        long elapsed = Math.clamp(
                Duration.between(lastPlayTime, Instant.now()).getSeconds(),
                0L, OFFLINE_WINDOW_SECONDS);
        return (int) Math.min(FULL, stored + FULL * elapsed / OFFLINE_WINDOW_SECONDS);
    }

    private Instant lastPlayTime(GameStore store, int characNo) {
        return store.character().loadFatigue(characNo)
                .map(row -> row.lastPlayTime())
                .orElse(Instant.EPOCH);
    }

    public void clear() {
        generation++;
        stamina = FULL;
    }

    public boolean penalty(String why) {
        String skip = penaltySkipReason();
        if (skip != null) {
            return false;
        }
        stamina = PENALTY_VALUE;
        persist();
        byte[] body = Noti33StaminaPenaltyBody.of(stamina);
        session.send(Noti33StaminaPenaltyBody.PACKET_ID, body);
        return true;
    }

    private String penaltySkipReason() {
        if (!ServerConfig.STAMINA_PENALTY) {
            return "-Ds1a5.staminaPenalty=0";
        }
        if (session.level() < PENALTY_MIN_LEVEL) {
            return "等级 " + session.level() + " < Lv" + PENALTY_MIN_LEVEL;
        }
        for (int premiumType : PENALTY_EXEMPT_PREMIUMS) {
            if (session.premium().isAffected(premiumType)) {
                return "有免罚会员 premium " + premiumType;
            }
        }
        return null;
    }

    private void scheduleRecover() {
        int mine = generation;
        session.schedule(() -> {
            if (mine != generation) {
                return;
            }
            recoverTick();
            scheduleRecover();
        }, RECOVER_PERIOD_MS);
    }

    void recoverTick() {
        if (session.characNo() == 0) {
            return;
        }
        if (session.inDungeon()) {
            return;
        }
        if (stamina > FULL - 1) {
            return;
        }
        stamina = Math.min(FULL, stamina + RECOVER_PER_TICK);
        persist();
        send();
    }

    public boolean setFull(String why) {
        return setTo(FULL, why);
    }

    public boolean setTo(int value, String why) {
        int next = Math.clamp(value, 0, FULL);
        if (next == stamina) {
            return false;
        }
        stamina = next;
        persist();
        send();
        return true;
    }

    public void sendOnEnterWorld(String why) {
        if (stamina >= FULL) {
            return;
        }
        byte[] body = Noti4StaminaBody.of(stamina);
        session.send(Noti4StaminaBody.PACKET_ID, body);
    }

    public void send() {
        byte[] body = Noti4StaminaBody.of(stamina);
        session.send(Noti4StaminaBody.PACKET_ID, body);
        session.flush();
    }

    private void persist() {
        SessionRegistry.updateStamina(session, stamina);
        GameStore store = session.store();
        if (store == null || session.characNo() == 0) {
            return;
        }
        store.stamina().saveStamina(session.characNo(), stamina);
    }
}
