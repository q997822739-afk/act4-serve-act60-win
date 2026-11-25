package com.dylan.game.event;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.dylan.net.ServerConfig;
import com.dylan.store.EventLogRow;
import com.dylan.store.GameStore;

public final class EventScheduler {

    private EventScheduler() {
    }

    public static final int RESCAN_SECONDS = 60;

    public static int serverId() {
        return ServerConfig.MY_CHANNEL;
    }

    private static ScheduledExecutorService scheduler;
    private static GameStore store;

    public static synchronized void start(GameStore gameStore) {
        store = gameStore;
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "event-scheduler");
                t.setDaemon(true);
                return t;
            });
        }
        if (store != null) {
            Map<Integer, String> texts = new HashMap<>();
            for (EventNo.ClientText t : EventNo.clientTexts()) {
                texts.put(t.no(), t.text());
            }
            store.event().updateEventInfoClientTexts(texts);
        }
        loadFromDb(true);
        applyWeekendBonus();
        scheduler.scheduleWithFixedDelay(EventScheduler::tick,
                RESCAN_SECONDS, RESCAN_SECONDS, TimeUnit.SECONDS);
    }

    public static synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        store = null;
    }

    private static void tick() {
        try {
            loadFromDb(false);
            applyWeekendBonus();
        } catch (RuntimeException e) {
        }
    }

    static synchronized void loadFromDb(boolean first) {
        if (store == null) {
            return;
        }
        List<EventLogRow> rows = store.event().loadActiveEvents(serverId());

        Map<Integer, EventLogRow> wanted = new HashMap<>();
        for (EventLogRow row : rows) {
            wanted.put(row.eventType(), row);
        }

        wanted.remove(EventNo.WEEKEND_BONUS);

        List<String> changes = new ArrayList<>();
        for (Map.Entry<Integer, EventLogRow> e : wanted.entrySet()) {
            int no = e.getKey();
            EventLogRow row = e.getValue();
            EventState now = EventService.get(no);
            if (now == null) {
                continue;
            }
            if (now.running() && now.param1() == row.parameter1()
                    && now.param2() == row.parameter2()) {
                continue;
            }
            if (EventService.start(no, row.parameter1(), row.parameter2())) {
                changes.add("开 " + no);
            }
        }
        for (int no : EventService.runningNumbers()) {
            if (no == EventNo.WEEKEND_BONUS || wanted.containsKey(no)) {
                continue;
            }
            if (EventService.end(no)) {
                changes.add("关 " + no);
            }
        }

    }

    static void applyWeekendBonus() {
        boolean should = weekendBonusShouldRun(ZonedDateTime.now(ZoneId.systemDefault()));
        boolean now = EventService.isRunning(EventNo.WEEKEND_BONUS);
        if (should && !now) {
            EventService.start(EventNo.WEEKEND_BONUS, 0, 0);
        } else if (!should && now) {
            EventService.end(EventNo.WEEKEND_BONUS);
        }
    }

    static boolean weekendBonusShouldRun(ZonedDateTime at) {
        int wday = at.getDayOfWeek().getValue() % 7;
        int hour = at.getHour();
        return (wday == 6 && hour > 5) || wday == 0 || (wday == 1 && hour <= 5);
    }
}
