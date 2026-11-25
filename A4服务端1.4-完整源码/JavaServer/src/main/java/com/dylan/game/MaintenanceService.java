package com.dylan.game;

import com.dylan.protocol.body.Noti12MessageBody;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MaintenanceService {
    public static final int MAX_MINUTES = 120;

    public static final int PROHIBIT_ERROR_CODE = 4;

    static final long TICK_SECONDS = 60;

    private static final String COUNTDOWN_FORMAT = "%d 分钟后服务器将进行维护。%s";

    private static volatile boolean prohibitConnect;

    private static volatile String note = "";

    private static volatile int minutesLeft;

    private static ScheduledExecutorService scheduler;

    private MaintenanceService() {
    }

    public record Status(boolean running, int minutesLeft, String note) {
    }

    public static boolean prohibitingConnect() {
        return prohibitConnect;
    }

    public static Status status() {
        return new Status(prohibitConnect, minutesLeft, note);
    }

    public static synchronized boolean begin(int minutes, String text) {
        if (prohibitConnect) {
            return false;
        }
        int clamped = Math.max(1, Math.min(MAX_MINUTES, minutes));
        prohibitConnect = true;
        note = text == null ? "" : Noti12MessageBody.sanitize(text).trim();
        minutesLeft = clamped;
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "maintenance-countdown");
                t.setDaemon(true);
                return t;
            });
        }
        tick();
        return true;
    }

    public static synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private static synchronized void tick() {
        if (!prohibitConnect) {
            return;
        }
        if (minutesLeft <= 0) {
            String why = "维护停服" + (note.isEmpty() ? "" : "：" + note);
            com.dylan.net.GracefulShutdown.begin(why);
            return;
        }
        broadcast(countdownText(minutesLeft, note));
        minutesLeft--;
        scheduler.schedule(MaintenanceService::guardedTick, TICK_SECONDS, TimeUnit.SECONDS);
    }

    static String countdownText(int minutes, String text) {
        return String.format(COUNTDOWN_FORMAT, minutes, text == null ? "" : text).trim();
    }

    private static void guardedTick() {
        try {
            tick();
        } catch (RuntimeException e) {
            synchronized (MaintenanceService.class) {
                if (prohibitConnect && scheduler != null && !scheduler.isShutdown()) {
                    scheduler.schedule(MaintenanceService::guardedTick,
                            TICK_SECONDS, TimeUnit.SECONDS);
                }
            }
        }
    }

    static int broadcast(String text) {
        String fitted = Noti12MessageBody.fit(Noti12MessageBody.sanitize(text));
        if (fitted.isBlank()) {
            return 0;
        }
        byte[] body = Noti12MessageBody.system(fitted).toBytes();
        int sent = 0;
        for (GameSession session : SessionRegistry.playing()) {
            if (session.isActive()) {
                session.sendNow(Noti12MessageBody.PACKET_ID, body);
                sent++;
            }
        }
        return sent;
    }
}
