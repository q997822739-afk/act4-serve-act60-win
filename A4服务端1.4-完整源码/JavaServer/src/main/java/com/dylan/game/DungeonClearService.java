package com.dylan.game;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Noti5DungeonPermissionBody;
import com.dylan.store.GameStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class DungeonClearService {

    private final GameSession session;

    private final Map<Integer, Integer> cleared = new TreeMap<>();

    private long mId;

    public DungeonClearService(GameSession session) {
        this.session = session;
    }

    public void load(long accountId) {
        this.mId = accountId;
        cleared.clear();
        GameStore store = session.store();
        if (store == null) {
            return;
        }
        store.dungeonClear().loadDungeonClear(accountId).ifPresent(this::parse);
    }

    public void clear() {
        cleared.clear();
        mId = 0;
    }

    public int difficultyOf(int dungeonId) {
        return cleared.getOrDefault(dungeonId, 0);
    }

    public static final int MAX_CLEARED_VALUE = 3;

    public boolean recordClear(int dungeonId, int difficulty, ClearRank rank) {
        int value = difficulty + 1;
        if (value > MAX_CLEARED_VALUE) {
            return false;
        }
        ClearRank required = requiredRank(difficulty);
        if (required != null && !rank.atLeast(required)) {
            return false;
        }
        int known = difficultyOf(dungeonId);
        if (value <= known) {
            return false;
        }
        cleared.put(dungeonId, value);
        save();
        sendPermissions(Noti5DungeonPermissionBody.one(dungeonId, value),
                "通关副本 " + dungeonId);
        return true;
    }

    private static ClearRank requiredRank(int difficulty) {
        return switch (difficulty) {
            case 0 -> null;
            case 1 -> ServerConfig.UNLOCK_CHAMPION_RANK;
            case 2 -> ServerConfig.UNLOCK_KING_RANK;
            default -> ServerConfig.UNLOCK_KING_RANK;
        };
    }

    public void sendFullList(String why) {
        List<Noti5DungeonPermissionBody.Entry> entries = new ArrayList<>(cleared.size());
        for (var e : cleared.entrySet()) {
            entries.add(new Noti5DungeonPermissionBody.Entry(e.getKey(), e.getValue()));
        }
        sendPermissions(new Noti5DungeonPermissionBody(entries), why);
    }

    private void sendPermissions(Noti5DungeonPermissionBody body, String why) {
        byte[] bytes = body.toBytes();
        session.sendNow(Noti5DungeonPermissionBody.PACKET_ID, bytes);
    }

    private void save() {
        GameStore store = session.store();
        if (store == null || mId == 0) {
            return;
        }
        String serialized = serialize();
        store.dungeonClear().saveDungeonClear(mId, serialized);
    }

    String serialize() {
        StringBuilder sb = new StringBuilder();
        for (var e : cleared.entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(e.getKey()).append('|').append(e.getValue());
        }
        return sb.toString();
    }

    private void parse(String serialized) {
        for (String part : serialized.split(",")) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            int bar = item.indexOf('|');
            if (bar <= 0 || bar == item.length() - 1) {
                continue;
            }
            try {
                int dungeonId = Integer.parseInt(item.substring(0, bar).trim());
                int difficulty = Integer.parseInt(item.substring(bar + 1).trim());
                if (difficulty > MAX_CLEARED_VALUE) {
                    difficulty = MAX_CLEARED_VALUE;
                }
                cleared.put(dungeonId, difficulty);
            } catch (NumberFormatException e) {
            }
        }
    }

    private String describe() {
        return describe(null);
    }

    private String describe(Noti5DungeonPermissionBody body) {
        Map<Integer, Integer> shown = new LinkedHashMap<>();
        if (body == null) {
            shown.putAll(cleared);
        } else {
            for (var e : body.entries()) {
                shown.put(e.dungeonId(), e.difficulty());
            }
        }
        if (shown.isEmpty()) {
            return "（空）";
        }
        StringBuilder sb = new StringBuilder("[");
        for (var e : shown.entrySet()) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append('#').append(e.getKey()).append("→").append(e.getValue());
        }
        return sb.append(']').toString();
    }
}
