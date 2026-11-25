package com.dylan.game;

import com.dylan.protocol.body.Cmd210SaveGameOptionRequest;
import com.dylan.protocol.body.Cmd211SaveHotkeyOptionRequest;
import com.dylan.protocol.body.Noti187GameOptionBody;
import com.dylan.store.GameStore;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public final class GameOptionService {

    public enum SaveResult {
        WRITTEN,
        UNCHANGED,
        ECHO,
    }

    private static final long ECHO_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(5);

    private final GameSession session;

    private byte[] option;

    private byte[] hotkeys;

    private long loadedFor = -1;

    private long echoWindowUntil;

    public GameOptionService(GameSession session) {
        this.session = session;
    }

    public void load() {
        long mId = accountId();
        if (mId <= 0) {
            return;
        }
        GameStore store = session.store();
        if (store == null) {
            option = blankOption();
            hotkeys = blankHotkeys();
            loadedFor = mId;
            return;
        }
        var row = store.gameOption().loadGameOption(mId);
        if (row.isEmpty()) {
            option = blankOption();
            hotkeys = blankHotkeys();
        } else {
            option = fit(row.get().option(), Cmd210SaveGameOptionRequest.OPTION_LENGTH);
            hotkeys = fit(row.get().hotkeys(), Cmd211SaveHotkeyOptionRequest.HOTKEY_LENGTH);
        }
        loadedFor = mId;
    }

    public void sendToClient() {
        if (option == null || hotkeys == null || loadedFor != accountId()) {
            load();
        }
        if (option == null || hotkeys == null) {
            return;
        }
        byte[] body = Noti187GameOptionBody.of(option, hotkeys).toBytes();
        session.send(Noti187GameOptionBody.PACKET_ID, body);
        echoWindowUntil = System.nanoTime() + ECHO_WINDOW_NANOS;
    }

    public SaveResult saveOption(byte[] incoming) {
        byte[] fitted = fit(incoming, Cmd210SaveGameOptionRequest.OPTION_LENGTH);
        if (consumeEchoWindow()) {
            logEchoDiff(fitted);
            return SaveResult.ECHO;
        }
        if (option != null && Arrays.equals(option, fitted)) {
            return SaveResult.UNCHANGED;
        }
        option = fitted;
        long mId = accountId();
        if (mId <= 0 || session.store() == null) {
            return SaveResult.UNCHANGED;
        }
        session.store().gameOption().saveGameOption(mId, fitted);
        return SaveResult.WRITTEN;
    }

    public SaveResult saveHotkeys(byte[] incoming) {
        byte[] fitted = fit(incoming, Cmd211SaveHotkeyOptionRequest.HOTKEY_LENGTH);
        if (hotkeys != null && Arrays.equals(hotkeys, fitted)) {
            return SaveResult.UNCHANGED;
        }
        hotkeys = fitted;
        long mId = accountId();
        if (mId <= 0 || session.store() == null) {
            return SaveResult.UNCHANGED;
        }
        session.store().gameOption().saveHotkeyOption(mId, fitted);
        return SaveResult.WRITTEN;
    }

    private boolean consumeEchoWindow() {
        long until = echoWindowUntil;
        if (until == 0L) {
            return false;
        }
        echoWindowUntil = 0L;
        if (System.nanoTime() - until < 0) {
            return true;
        }
        return false;
    }

    private void logEchoDiff(byte[] echo) {
        if (option == null) {
            return;
        }
        var sent = new Cmd210SaveGameOptionRequest(option);
        var got = new Cmd210SaveGameOptionRequest(echo);
        StringBuilder diff = new StringBuilder();
        int changed = 0;
        for (int i = 0; i < Cmd210SaveGameOptionRequest.OPTION_COUNT; i++) {
            if (sent.value(i) != got.value(i)) {
                diff.append(changed++ == 0 ? "" : "，")
                        .append('#').append(i).append(' ')
                        .append(sent.value(i)).append('→').append(got.value(i));
            }
        }
    }

    public byte[] option() {
        return option;
    }

    public byte[] hotkeys() {
        return hotkeys;
    }

    private long accountId() {
        return session.account() == null ? 0L : session.account().mId();
    }

    private static byte[] blankOption() {
        return new byte[Cmd210SaveGameOptionRequest.OPTION_LENGTH];
    }

    private static byte[] blankHotkeys() {
        return new byte[Cmd211SaveHotkeyOptionRequest.HOTKEY_LENGTH];
    }

    private static byte[] fit(byte[] raw, int length) {
        if (raw != null && raw.length == length) {
            return raw;
        }
        byte[] out = new byte[length];
        if (raw != null) {
            System.arraycopy(raw, 0, out, 0, Math.min(raw.length, length));
        }
        return out;
    }
}
