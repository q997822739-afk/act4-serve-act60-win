package com.dylan.game;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

import com.dylan.pvf.script.ItemScript;

public final class ConsumableService {

    static final int MAX_ENTRIES = 0x32;

    private static final long MAX_ITEM_ID = 0x7FFFFFFEL;

    private static final long MAX_EXPIRE_SEC = 0x77359400L;

    private final GameSession session;

    private final LongSupplier clock;

    private final Map<Integer, Map<Integer, Long>> coolTimes = new HashMap<>();

    private final Map<Integer, Map<Integer, Long>> effects = new HashMap<>();

    private final Map<Integer, Double> expAffectRates = new HashMap<>();

    public ConsumableService(GameSession session) {
        this(session, () -> System.currentTimeMillis() / 1000L);
    }

    ConsumableService(GameSession session, LongSupplier clock) {
        this.session = session;
        this.clock = clock;
    }

    public void load(int characNo) {
        if (session == null || session.store() == null || characNo <= 0) {
            return;
        }
        var loaded = session.store().continuousItem().loadContinuousItems(characNo);
        long now = clock.getAsLong();

        Map<Integer, Long> cool = tableOf(coolTimes, characNo);
        cool.clear();
        for (var e : loaded.coolTimes().entrySet()) {
            if (acceptable(e.getKey(), e.getValue(), now)) {
                cool.put(e.getKey(), e.getValue());
            }
        }
        clearUseless(cool, now);

        Map<Integer, Long> effect = tableOf(effects, characNo);
        effect.clear();
        expAffectRates.remove(characNo);
        for (var e : loaded.effects().entrySet()) {
            if (acceptable(e.getKey(), e.getValue(), now)) {
                processSpecialContinuousTimeItem(characNo, e.getKey());
                effect.put(e.getKey(), e.getValue());
            }
        }
        clearUseless(effect, now);

    }

    private static boolean acceptable(int itemId, long expireSec, long now) {
        return expireSec > now && itemId <= MAX_ITEM_ID && expireSec <= MAX_EXPIRE_SEC;
    }

    public void processContinuousItem(int itemId, ItemScript script) {
        long now = clock.getAsLong();
        if (script != null) {
            var opt = script.continuous();
            if (opt.coolTimeProcess()) {
                processCoolTimeItem(itemId, script.coolTimeMs());
            }
            if (opt.effectProcess()) {
                processEffectItem(itemId, opt, now);
            }
        }
        processSpecialContinuousTimeItem(characNo(), itemId);
    }

    void processCoolTimeItem(int itemId, int coolTimeMs) {
        long now = clock.getAsLong();
        long until = now + coolTimeMs / 1000L;
        tableOf(coolTimes, characNo()).merge(itemId, until, Math::max);
        updateCoolTimeItemInfo(now);
    }

    private void processEffectItem(int itemId, ItemScript.ContinuousOption opt, long now) {
        if (!opt.statChangeDuration()) {
            return;
        }
        long durationSec = opt.statChangeDurationMs() / 1000L;
        long until = now + durationSec;
        if (opt.effectTimer()) {
            insertFinishTimer(itemId, durationSec);
        }
        tableOf(effects, characNo()).merge(itemId, until, Math::max);
        updateEffectItemInfo(now);
    }

    private void processSpecialContinuousTimeItem(int characNo, int itemId) {
        var scripts = session == null ? null : session.scripts();
        if (scripts == null) {
            return;
        }
        ItemScript item = scripts.item(itemId).orElse(null);
        if (item == null || !item.isStackableItem() || !item.continuous().expAffect()) {
            return;
        }
        double ratio = item.continuous().expAffectRatio();
        if (ratio > 0.0) {
            setExpAffectItemEffect(characNo, ratio);
        }
    }

    private void insertFinishTimer(int itemId, long durationSec) {
        if (session == null || session.uniqueId() == UniqueIdAllocator.UNASSIGNED) {
            return;
        }
        TimerQueue.insertTimer(TimerQueue.OBJ_USER, session.uniqueId(),
                TimerMessage.FINISH_ITEM_EFFECT, durationSec, itemId, characNo());
    }

    public boolean checkCoolTimeItem(int itemId) {
        Long until = tableOf(coolTimes, characNo()).get(itemId);
        return until != null && clock.getAsLong() < until;
    }

    public boolean isExpItemAffected() {
        return expAffectRate(characNo()) > 0.0;
    }

    public double expAffectRate() {
        return expAffectRate(characNo());
    }

    public boolean enablePeriodExpItem() {
        int characNo = characNo();
        var scripts = session == null ? null : session.scripts();
        if (scripts == null) {
            return false;
        }
        long now = clock.getAsLong();
        Map<Integer, Long> table = tableOf(effects, characNo);
        for (var it = table.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ItemScript item = scripts.item(e.getKey()).orElse(null);
            if (item == null) {
                return false;
            }
            if (!item.isStackableItem() || !item.continuous().expAffect()) {
                continue;
            }
            if (now < e.getValue()) {
                return true;
            }
            it.remove();
            setExpAffectItemEffect(characNo, 0.0);
            return false;
        }
        return false;
    }

    private void updateCoolTimeItemInfo(long now) {
        Map<Integer, Long> mine = tableOf(coolTimes, characNo());
        if (!sweep(mine, now, "冷却")) {
            return;
        }
        if (session != null && session.store() != null && characNo() > 0) {
            session.store().continuousItem().saveCoolTimeItems(characNo(), mine);
        }
    }

    private void updateEffectItemInfo(long now) {
        Map<Integer, Long> mine = tableOf(effects, characNo());
        if (!sweep(mine, now, "效果")) {
            return;
        }
        if (session != null && session.store() != null && characNo() > 0) {
            session.store().continuousItem().saveEffectItems(characNo(), mine);
        }
    }

    private boolean sweep(Map<Integer, Long> table, long now, String what) {
        if (table.size() > MAX_ENTRIES) {
            return false;
        }
        clearUseless(table, now);
        return true;
    }

    private static void clearUseless(Map<Integer, Long> table, long now) {
        table.entrySet().removeIf(e ->
                e.getValue() < now || e.getKey() > MAX_ITEM_ID || e.getValue() > MAX_EXPIRE_SEC);
    }

    private void setExpAffectItemEffect(int characNo, double ratio) {
        if (ratio > 0.0) {
            expAffectRates.put(characNo, ratio);
        } else {
            expAffectRates.remove(characNo);
        }
    }

    static {
        TimerQueue.registerUser(TimerMessage.FINISH_ITEM_EFFECT,
                (session, itemId, characNo) ->
                        session.consumables().onFinishItemEffectTimer(itemId, characNo));
    }

    void onFinishItemEffectTimer(int itemId, int characNo) {
        if (session == null || !session.hasCharacter()) {
            return;
        }
        if (characNo != 0 && session.characNo() != characNo) {
            return;
        }
        Map<Integer, Long> table = tableOf(effects, session.characNo());
        Long until = table.get(itemId);
        if (until == null) {
            return;
        }
        long now = clock.getAsLong();
        if (until <= now) {
            finishItemEffect(session.characNo(), itemId);
            return;
        }
        insertFinishTimer(itemId, until - now);
    }

    void finishItemEffect(int characNo, int itemId) {
        if (itemId == EXP_ITEM_ANCIENT_ELIXIR) {
            setExpAffectItemEffect(characNo, 0.0);
        }
    }

    static final int EXP_ITEM_ANCIENT_ELIXIR = 7377;

    private static Map<Integer, Long> tableOf(Map<Integer, Map<Integer, Long>> tables,
                                              int characNo) {
        return tables.computeIfAbsent(characNo, k -> new LinkedHashMap<>());
    }

    private double expAffectRate(int characNo) {
        return expAffectRates.getOrDefault(characNo, 0.0);
    }

    private int characNo() {
        return session == null ? 0 : session.characNo();
    }
}
