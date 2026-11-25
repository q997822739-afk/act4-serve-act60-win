package com.dylan.game;

import com.dylan.game.event.CharacterDayEvent;
import com.dylan.game.event.EventNo;
import com.dylan.game.event.EventService;
import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Noti36FatigueBody;
import com.dylan.store.FatigueRow;
import com.dylan.store.GameStore;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class FatigueService {

    public static final int FATIGUE_UNLIMITED_MARK = 0xFFFF;

    private final GameSession session;

    private final ZoneId zone = ZoneId.systemDefault();

    private int characNo;

    private int fatigue;

    private int maxFatigue = ServerConfig.FATIGUE_MAX;

    private int premiumFatigue;

    private int maxPremiumFatigue;

    private int usedFatigue;

    private int grownUpBuff;

    private int lastSpentFatigue;

    private Instant periodStart = Instant.EPOCH;

    public FatigueService(GameSession session) {
        this.session = session;
    }

    public int totalFatigue() {
        if (!hasPremiumAdvantage()) {
            return fatigue;
        }
        return Math.min(fatigue + Math.min(premiumFatigue, maxPremiumFatigue), totalMaxFatigue());
    }

    public int totalMaxFatigue() {
        return hasPremiumAdvantage() ? maxFatigue + maxPremiumFatigue : maxFatigue;
    }

    public int currentFatigue() {
        return fatigue;
    }

    public int usedFatigue() {
        return usedFatigue;
    }

    public int takeLastSpentFatigue() {
        int spent = lastSpentFatigue;
        lastSpentFatigue = 0;
        return spent;
    }

    public int grownUpBuff() {
        return grownUpBuff;
    }

    public int remaining() {
        return Math.max(0, totalMaxFatigue() - totalFatigue());
    }

    public boolean checkFatigue() {
        if (!ServerConfig.FATIGUE_ENFORCE) {
            return true;
        }
        rolloverIfNeeded();
        return totalFatigue() < totalMaxFatigue();
    }

    private boolean hasPremiumAdvantage() {
        return premiumFatigueRate() > 0;
    }

    private int premiumFatigueRate() {
        return session.premium().advantageFatigueRate();
    }

    public void refreshPremiumCap() {
        int before = maxPremiumFatigue;
        recalcCaps();
        if (before == maxPremiumFatigue) {
            return;
        }
        premiumFatigue = Math.clamp(premiumFatigue, 0, maxPremiumFatigue);
        sendFatigue("会员变动");
    }

    public void fatigueUp(int amount, String why) {
        if (characNo == 0) {
            return;
        }
        rolloverIfNeeded();
        int spend = amount <= 0 ? 1 : amount;

        int usedBefore = usedFatigue;
        usedFatigue = Math.min(usedFatigue + spend, 0xFFFF);
        grownUpBuff = Math.max(0, grownUpBuff - spend);
        lastSpentFatigue = spend;

        CharacterDayEvent.onFatigueUp(session, usedBefore, spend);

        if (EventService.isRunning(EventNo.UNLIMIT_FATIGUE, session)) {
            persist();
            return;
        }

        if (fatigue == FATIGUE_UNLIMITED_MARK) {
            persist();
            return;
        }

        totalFatigue();
        int burningRefill = burningFatigueRefill(spend);
        int rest = spend;
        if (hasPremiumAdvantage()) {
            if (premiumFatigue + rest <= maxPremiumFatigue) {
                premiumFatigue += rest;
                rest = 0;
            } else if (premiumFatigue < maxPremiumFatigue) {
                rest -= maxPremiumFatigue - premiumFatigue;
                premiumFatigue = maxPremiumFatigue;
            }
        }
        if (rest > 0) {
            fatigue = Math.clamp((long) fatigue + rest, 0, maxFatigue);
        }

        totalFatigue();
        persist();
        if (burningRefill > 0) {
            gainFatigue(burningRefill, "燃烧疲劳活动跨档回补");
        }
    }

    private int burningFatigueRefill(int spend) {
        if (!EventService.isRunning(EventNo.BURNING_FATIGUE, session)) {
            return 0;
        }
        var params = session.scripts() == null ? null : session.scripts().serverParameter();
        return params == null ? 0 : params.burningFatigueRefill(usedFatigue, spend);
    }

    public void gainFatigue(int amount, String why) {
        if (characNo == 0 || amount <= 0) {
            return;
        }
        rolloverIfNeeded();
        int cap = hasPremiumAdvantage() ? maxPremiumFatigue : 0;
        int cur = Math.min(premiumFatigue, cap);
        int rest = cur - amount;
        if (rest >= 0) {
            premiumFatigue = rest;
        } else {
            if (cur > 0) {
                premiumFatigue = 0;
            }
            fatigue = Math.max(0, fatigue + rest);
        }
        persist();
        sendFatigue(why);
    }

    public void recoverFatigueFromItem(int amount, String why) {
        if (characNo == 0 || amount <= 0) {
            return;
        }
        rolloverIfNeeded();
        fatigue = Math.max(0, fatigue - amount);
        persist();
        sendFatigue(why);
    }

    public void recoverFatigue(int value) {
        premiumFatigue = value;
        fatigue = value;
        recalcCaps();
        usedFatigue = 0;
    }

    private void recalcCaps() {
        int base = ServerConfig.FATIGUE_MAX;
        maxFatigue = EventService.isRunning(EventNo.MAX_FATIGUE_FACTOR, session)
                ? base * EventService.param1(EventNo.MAX_FATIGUE_FACTOR) / 100
                : base;
        int rate = premiumFatigueRate();
        if (EventService.isRunning(EventNo.WEEKEND_BONUS, session)) {
            maxFatigue += weekendBonusFatigue();
            maxPremiumFatigue = rate > 0 ? base * rate / 100 : 0;
        } else {
            maxPremiumFatigue = rate > 0 ? rate * maxFatigue / 100 : 0;
        }
    }

    private int weekendBonusFatigue() {
        var params = session.scripts() == null ? null : session.scripts().serverParameter();
        return params == null ? 0 : params.weekendBonusFatigue();
    }

    public static void sendFatigueEventToWorld() {
        java.util.List<GameSession> playing = SessionRegistry.playing();
        for (GameSession s : playing) {
            if (!s.isActive()) {
                continue;
            }
            s.run(() -> {
                if (!s.isActive() || !s.hasCharacter()) {
                    return;
                }
                FatigueService f = s.fatigue();
                int before = f.totalMaxFatigue();
                f.recalcCaps();
                f.fatigue = Math.clamp((long) f.fatigue, 0, f.maxFatigue);
                f.premiumFatigue = Math.clamp((long) f.premiumFatigue, 0, f.maxPremiumFatigue);
                f.sendFatigue("最大疲劳活动 " + before + " → " + f.totalMaxFatigue());
                s.flush();
            });
        }
    }

    public void sendFatigue(String why) {
        Noti36FatigueBody body = snapshot();
        session.send(Noti36FatigueBody.PACKET_ID, body.toBytes());
    }

    public Noti36FatigueBody snapshot() {
        rolloverIfNeeded();
        return new Noti36FatigueBody(totalFatigue(), totalMaxFatigue(), usedFatigue, grownUpBuff);
    }

    public void load(int characNo) {
        this.characNo = characNo;
        this.periodStart = currentPeriodStart();

        recoverFatigue(0);

        GameStore store = session.store();
        if (store == null) {
            return;
        }

        FatigueRow row = store.character().loadFatigue(characNo).orElseGet(() -> {
            return FatigueRow.fresh();
        });
        if (crossedResetPoint(row.lastPlayTime())) {
            QuestService.resetDailyQuest(session);
        } else {
            // 原版 A5 读了存档却从不赋值，紧接着 persist() 把 0 写回去 → 重新登录疲劳全满（2026-10-28 用户实测、
            // 测试服 charac_stat.fatigue 全是 0）。没跨恢复点才沿用存档，跨了就是上面 recoverFatigue(0) 的新一天。
            fatigue = row.fatigue() == FATIGUE_UNLIMITED_MARK ? FATIGUE_UNLIMITED_MARK
                    : Math.clamp((long) row.fatigue(), 0, maxFatigue);
            premiumFatigue = Math.clamp((long) row.premiumFatigue(), 0, maxPremiumFatigue);
            usedFatigue = Math.clamp((long) row.usedFatigue(), 0, 0xFFFF);
            grownUpBuff = Math.max(0, row.grownUpBuff());
        }
        persist();
    }

    public void clear() {
        characNo = 0;
        fatigue = 0;
        premiumFatigue = 0;
        usedFatigue = 0;
        grownUpBuff = 0;
        maxFatigue = ServerConfig.FATIGUE_MAX;
        maxPremiumFatigue = 0;
        periodStart = Instant.EPOCH;
    }

    public void save() {
        persist();
    }

    private void rolloverIfNeeded() {
        Instant now = currentPeriodStart();
        if (!now.isAfter(periodStart)) {
            return;
        }
        totalFatigue();
        periodStart = now;
        recoverFatigue(0);
        if (characNo != 0) {
            persist();
            QuestService.resetDailyQuest(session);
            sendFatigue("跨过疲劳恢复点");
        }
    }

    private void persist() {
        if (characNo == 0) {
            return;
        }
        GameStore store = session.store();
        if (store == null) {
            return;
        }
        try {
            store.character().saveFatigue(characNo, new FatigueRow(fatigue, usedFatigue, premiumFatigue,
                    grownUpBuff, Instant.now()));
        } catch (RuntimeException e) {
        }
    }

    private Instant currentPeriodStart() {
        return periodStartFor(Instant.now(), zone, ServerConfig.FATIGUE_RESET_HOUR);
    }

    private boolean crossedResetPoint(Instant lastPlayTime) {
        return periodStartFor(lastPlayTime, zone, ServerConfig.FATIGUE_RESET_HOUR)
                .isBefore(periodStart);
    }

    static Instant periodStartFor(Instant at, ZoneId zone, int resetHour) {
        ZonedDateTime now = at.atZone(zone);
        ZonedDateTime reset = now.toLocalDate().atTime(resetHour, 0).atZone(zone);
        if (now.isBefore(reset)) {
            reset = reset.minusDays(1);
        }
        return reset.toInstant();
    }
}
