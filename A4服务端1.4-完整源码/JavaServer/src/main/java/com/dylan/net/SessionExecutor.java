package com.dylan.net;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class SessionExecutor implements AutoCloseable {

    private static final ThreadLocal<SessionExecutor> CURRENT = new ThreadLocal<>();

    public static final int QUEUE_LIMIT = 512;

    private final ThreadPoolExecutor pool;
    private final String name;

    private static final SessionExecutor CALLER_RUNS = new SessionExecutor(null, "caller-runs");

    private SessionExecutor(ThreadPoolExecutor pool, String name) {
        this.pool = pool;
        this.name = name;
    }

    public static SessionExecutor callerRuns() {
        return CALLER_RUNS;
    }

    public static SessionExecutor virtual(String name) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_LIMIT),
                Thread.ofVirtual().name("session-" + name).factory());
        pool.allowCoreThreadTimeOut(true);
        return new SessionExecutor(pool, name);
    }

    public boolean inSessionThread() {
        return CURRENT.get() == this;
    }

    public boolean submit(Runnable task) {
        if (pool == null) {
            task.run();
            return true;
        }
        try {
            pool.execute(() -> {
                CURRENT.set(this);
                try {
                    task.run();
                } catch (Throwable t) {
                } finally {
                    CURRENT.remove();
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            if (pool.isShutdown()) {
                return true;
            }
            return false;
        }
    }

    public int queueDepth() {
        return pool == null ? 0 : pool.getQueue().size();
    }

    @Override
    public void close() {
        if (pool != null) {
            pool.shutdown();
        }
    }
}
