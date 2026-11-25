package com.dylan.game;

import com.dylan.net.ServerConfig;
import com.dylan.store.GameStore;
import com.dylan.store.SecurityStore;
import com.dylan.store.blob.InvenItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class ItemHistoryRecorder {
    private static final int QUEUE_CAPACITY = 20_000;

    private static final int BATCH_MAX = 500;

    private static final long POLL_TIMEOUT_MS = 500;

    private static volatile ItemHistoryRecorder instance;

    private final GameStore store;
    private final BlockingQueue<SecurityStore.ItemHistoryRow> queue =
            new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong written = new AtomicLong();
    private volatile Thread worker;
    private volatile boolean running;

    private ItemHistoryRecorder(GameStore store) {
        this.store = store;
    }

    public static synchronized void start(GameStore store) {
        if (instance != null) {
            return;
        }
        if (store == null || !ServerConfig.ITEM_HISTORY) {
            return;
        }
        ItemHistoryRecorder recorder = new ItemHistoryRecorder(store);
        recorder.running = true;
        recorder.worker = Thread.ofVirtual().name("item-history").start(recorder::drainLoop);
        instance = recorder;
    }

    public static synchronized void stop() {
        ItemHistoryRecorder recorder = instance;
        if (recorder == null) {
            return;
        }
        recorder.running = false;
        Thread w = recorder.worker;
        if (w != null) {
            w.interrupt();
            try {
                w.join(TimeUnit.SECONDS.toMillis(10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        recorder.flushAll();
        instance = null;
    }

    public static void record(GameSession session, int invenType, int slot, InvenItem item,
                              long count) {
        ItemHistoryRecorder recorder = instance;
        if (recorder == null || session == null || item == null || count == 0) {
            return;
        }
        recorder.offer(new SecurityStore.ItemHistoryRow(
                session.characNo(),
                session.characName() == null ? "" : session.characName(),
                reasonOf(session),
                invenType,
                slot,
                item.itemId(),
                count,
                item.addInfo(),
                item.endurance(),
                item.upgrade(),
                item.enchant(),
                item.amplify() == null ? 0 : item.amplify().rawTypeByte() & 0xFF,
                item.amplify() == null ? 0 : item.amplify().abilityValue(),
                item.reSealCount(),
                randomOption(item),
                guid(item),
                0,
                0,
                moneyOf(session),
                0,
                0));
    }

    public static void recordMoney(GameSession session, long delta, long after) {
        ItemHistoryRecorder recorder = instance;
        if (recorder == null || session == null || delta == 0) {
            return;
        }
        recorder.offer(new SecurityStore.ItemHistoryRow(
                session.characNo(),
                session.characName() == null ? "" : session.characName(),
                reasonOf(session),
                com.dylan.protocol.body.ItemListType.LIST_MAIN,
                BagLayout.SLOT_GOLD,
                0,
                delta,
                0, 0, 0, 0, 0, 0, 0, "", "", 0, 0,
                after,
                0,
                0));
    }

    private static final int ITEM_ID_COIN = 1;

    public static final int COIN_COL_COIN = 2;

    public static final int COIN_COL_PAY = 3;

    public static final int COIN_COL_EVENT = 4;

    public static void recordCoin(GameSession session, int coinCol, long delta, long after) {
        ItemHistoryRecorder recorder = instance;
        if (recorder == null || session == null || delta == 0) {
            return;
        }
        recorder.offer(new SecurityStore.ItemHistoryRow(
                session.characNo(),
                session.characName() == null ? "" : session.characName(),
                reasonOf(session),
                com.dylan.protocol.body.ItemListType.LIST_MAIN,
                BagLayout.SLOT_COIN,
                ITEM_ID_COIN,
                delta,
                0, 0, 0, 0, 0, 0, 0, "", "", 0, 0,
                moneyOf(session),
                coinCol,
                after));
    }

    private static int reasonOf(GameSession session) {
        return Math.max(0, session.lastActionPacketId());
    }

    private static long moneyOf(GameSession session) {
        InventoryService inv = session.inventory();
        return inv == null || inv.inventory() == null ? 0 : inv.money();
    }

    private static String guid(InvenItem item) {
        var g = item.guid();
        if (g == null || (g.serverId() == 0 && g.datetime() == 0 && g.sequenceNo() == 0)) {
            return "";
        }
        return g.serverId() + "-" + g.datetime() + "-" + g.sequenceNo();
    }

    private static String randomOption(InvenItem item) {
        var c = item.chronicle();
        if (c == null || c.equals(com.dylan.store.blob.ChronicleRandomOption.EMPTY)) {
            return "";
        }
        return c.info() + "/" + c.random();
    }

    private void offer(SecurityStore.ItemHistoryRow row) {
        if (queue.offer(row)) {
            return;
        }
        dropped.incrementAndGet();
    }

    private void drainLoop() {
        List<SecurityStore.ItemHistoryRow> batch = new ArrayList<>(BATCH_MAX);
        while (running) {
            try {
                SecurityStore.ItemHistoryRow first =
                        queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, BATCH_MAX - 1);
                write(batch);
                batch.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                batch.clear();
            }
        }
    }

    private void flushAll() {
        List<SecurityStore.ItemHistoryRow> batch = new ArrayList<>(BATCH_MAX);
        while (!queue.isEmpty()) {
            queue.drainTo(batch, BATCH_MAX);
            if (batch.isEmpty()) {
                break;
            }
            write(batch);
            batch.clear();
        }
    }

    private void write(List<SecurityStore.ItemHistoryRow> batch) {
        written.addAndGet(store.security().logItemHistory(batch));
    }
}
