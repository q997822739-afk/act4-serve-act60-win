package com.dylan.game;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Cmd0CheckConnectionRequest;

public final class ConnectionCheckService {
    public static final int NOTI_ID = Cmd0CheckConnectionRequest.PACKET_ID;

    public static final int MISSED_LIMIT = 3;

    private static final byte[] EMPTY_BODY = new byte[0];

    private final GameSession session;

    private int missed;

    private int generation;

    private boolean running;

    public ConnectionCheckService(GameSession session) {
        this.session = session;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        scheduleNext();
    }

    public void stop() {
        running = false;
        generation++;
    }

    public void onReply() {
        missed = 0;
    }

    private void scheduleNext() {
        int mine = generation;
        session.schedule(() -> {
            if (mine != generation || !running) {
                return;
            }
            if (tick()) {
                scheduleNext();
            }
        }, ServerConfig.CHECK_CONN_INTERVAL_MS);
    }

    private boolean tick() {
        if (!session.isActive()) {
            return false;
        }
        if (missed > MISSED_LIMIT - 1) {
            session.fail("心跳连续 " + MISSED_LIMIT + " 条没有应答（官方 no response during 3 times ping）"
                    + "—— 客户端已经不在了，但 TCP 没断");
            return false;
        }
        missed++;
        session.sendNow(NOTI_ID, EMPTY_BODY);
        return true;
    }
}
