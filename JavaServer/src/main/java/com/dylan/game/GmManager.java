package com.dylan.game;

import com.dylan.store.GameStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GmManager {
    private GmManager() {
    }

    private static final class Record {
        private volatile boolean gmMode;

        private volatile int level;

        Record(int level) {
            this.level = level;
        }
    }

    private static final Map<Long, Record> RECORDS = new ConcurrentHashMap<>();

    private static volatile boolean loaded;

    public static void init(GameStore store) {
        if (store == null) {
            loaded = true;
            return;
        }
        reload(store);
    }

    public static void reload(GameStore store) {
        Map<Long, Integer> fromDb = store.gm().loadGmManifest();
        RECORDS.keySet().removeIf(mId -> !fromDb.containsKey(mId));
        fromDb.forEach((mId, level) -> RECORDS.compute(mId, (k, old) -> {
            if (old == null) {
                return new Record(level);
            }
            old.level = level;
            return old;
        }));
        loaded = true;
    }

    public static void refresh(GameStore store, long accountId) {
        if (store == null) {
            return;
        }
        int level = store.gm().gmLevel(accountId);
        if (level > 0) {
            setGmLevel(accountId, level);
        } else {
            removeGm(accountId);
        }
    }

    public static boolean isGm(long accountId) {
        return RECORDS.containsKey(accountId);
    }

    public static int gmLevel(long accountId) {
        Record record = RECORDS.get(accountId);
        return record == null ? 0 : record.level;
    }

    public static boolean inGmMode(long accountId) {
        Record record = RECORDS.get(accountId);
        return record != null && record.gmMode;
    }

    public static boolean turnGmMode(long accountId) {
        Record record = RECORDS.get(accountId);
        if (record == null) {
            return false;
        }
        boolean now = !record.gmMode;
        record.gmMode = now;
        return now;
    }

    public static void leaveGmMode(long accountId) {
        Record record = RECORDS.get(accountId);
        if (record != null) {
            record.gmMode = false;
        }
    }

    public static void setGmLevel(long accountId, int level) {
        RECORDS.compute(accountId, (k, old) -> {
            if (old == null) {
                return new Record(level);
            }
            old.level = level;
            return old;
        });
    }

    public static boolean removeGm(long accountId) {
        return RECORDS.remove(accountId) != null;
    }

    public static int size() {
        return RECORDS.size();
    }
}
