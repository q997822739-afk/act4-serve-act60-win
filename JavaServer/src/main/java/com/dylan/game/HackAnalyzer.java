package com.dylan.game;

import com.dylan.net.ServerConfig;
import com.dylan.store.GameStore;

import java.util.HashMap;
import java.util.Map;

public final class HackAnalyzer {
    private static final int LOG_EVERY_FIRST = 10;

    private static final int LOG_SAMPLE_EVERY = 100;

    public static final int POINT_MAX = 10_000;

    public static final int DROP_RATE_POINT = 1_000;

    private static final int PUNISH_DELAY_MIN_SEC = 10;

    private static final int PUNISH_DELAY_SPREAD_SEC = 290;

    private final GameSession session;

    private final Map<Integer, Counter> counters = new HashMap<>();

    private int points;

    private boolean punishScheduled;

    HackAnalyzer(GameSession session) {
        this.session = session;
    }

    private static final class Counter {
        private int total;

        private int loggedAt;
    }

    public void count(int code, int param1, int param2, String detail) {
        Counter counter = counters.computeIfAbsent(code, k -> new Counter());
        counter.total++;

        int gained = HackType.points(code);
        if (gained > 0) {
            points = Math.clamp((long) points + gained, 0, POINT_MAX);
        }

        if (shouldPersist(counter)) {
            counter.loggedAt = counter.total;
            persist(code, param1, param2, counter.total, detail);
        }
        punish(code);
    }

    public int dropRatePercent() {
        if (points <= DROP_RATE_POINT) {
            return 100;
        }
        return Math.clamp(100 - points / 100, 0, 100);
    }

    public int points() {
        return points;
    }

    private static boolean shouldPersist(Counter counter) {
        return counter.total <= LOG_EVERY_FIRST
                || counter.total - counter.loggedAt >= LOG_SAMPLE_EVERY;
    }

    private void persist(int code, int param1, int param2, int total, String detail) {
        GameStore store = session.store();
        if (store == null) {
            return;
        }
        long mId = session.account() == null ? 0L : session.account().mId();
        store.security().logHack(mId, session.characNo(), code, param1, param2, total,
                HackType.describe(code) + "：" + detail, session.clientIp());
    }

    private void punish(int code) {
        int threshold = ServerConfig.HACK_PUNISH_POINT;
        if (threshold <= 0 || punishScheduled || points < threshold) {
            return;
        }
        punishScheduled = true;
        int delaySec = PUNISH_DELAY_MIN_SEC
                + java.util.concurrent.ThreadLocalRandom.current()
                        .nextInt(PUNISH_DELAY_SPREAD_SEC);
        String why = "作弊积分到了 " + points + "（阈值 " + threshold + "），"
                + "最后一笔是 " + code + "（" + HackType.describe(code) + "）";
        session.schedule(() -> session.fail(why), delaySec * 1000L);
    }

    public int countOf(int code) {
        Counter counter = counters.get(code);
        return counter == null ? 0 : counter.total;
    }

    public int total() {
        return counters.values().stream().mapToInt(c -> c.total).sum();
    }

    public static final class Site {
        private Site() {
        }

        public static final int DELETE_ITEM_MISMATCH = 1;

        public static final int MOVE_ITEM_MISMATCH = 2;

        public static final int SELL_COUNT_MISMATCH = 3;

        public static final int MAIL_ITEM_MISMATCH = 4;
    }
}
