package com.dylan.net;

import com.dylan.game.GameSession;
import com.dylan.game.SessionRegistry;

import io.netty.channel.Channel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GracefulShutdown {
    private static final long POLL_MS = 50;

    private static volatile Channel listen;

    private static final AtomicBoolean started = new AtomicBoolean();

    /**
     * 需要在停机时一并收掉的旁路服务（目前是频道网关 entrance）。
     * 它们不在 Netty 的会话表里，所以 {@code disconnectEveryone} 管不到。
     */
    private static final List<Runnable> onStop = new CopyOnWriteArrayList<>();

    private GracefulShutdown() {
    }

    public static void arm(Channel serverChannel) {
        listen = serverChannel;
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> begin("进程收到停止信号"), "graceful-shutdown"));
    }

    /** 注册一个停机回调。幂等性由回调自己保证。 */
    public static void onStop(Runnable action) {
        if (action != null) {
            onStop.add(action);
        }
    }

    public static boolean begin(String reason) {
        if (!started.compareAndSet(false, true)) {
            return false;
        }
        SessionRegistry.size();

        stopAccepting();
        disconnectEveryone(reason);
        awaitDrain();

        stopSideServices();

        closeListener();
        return true;
    }

    /**
     * 收掉注册进来的旁路服务。放在 {@code awaitDrain} 之后：
     * 先让游戏会话自然结束，再关掉网关监听。
     */
    private static void stopSideServices() {
        for (Runnable action : onStop) {
            try {
                action.run();
            } catch (RuntimeException e) {
                System.out.println("[shutdown] 旁路服务停止失败：" + e);
            }
        }
        onStop.clear();
    }

    private static void stopAccepting() {
        Channel ch = listen;
        if (ch == null) {
            return;
        }
        ch.config().setAutoRead(false);
    }

    private static void disconnectEveryone(String reason) {
        int closed = 0;
        for (GameSession session : SessionRegistry.all()) {
            if (session.isActive()) {
                session.disconnect(reason);
                closed++;
            }
        }
    }

    private static boolean awaitDrain() {
        long deadline = System.nanoTime() + ServerConfig.SHUTDOWN_WAIT_MS * 1_000_000L;
        long lastLogged = 0;
        while (SessionRegistry.size() > 0) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            long left = SessionRegistry.size();
            if (left != lastLogged) {
                lastLogged = left;
            }
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    private static void closeListener() {
        Channel ch = listen;
        if (ch != null) {
            ch.close();
        }
    }
}
