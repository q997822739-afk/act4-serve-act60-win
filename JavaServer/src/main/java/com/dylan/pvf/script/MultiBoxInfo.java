package com.dylan.pvf.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public record MultiBoxInfo(List<Pool> pools, List<KeyItem> keys) {

    public static final int RANDOM_RANGE = 1_000_000;

    public static final int EVENT_COIN_ITEM_ID = 1;

    public static final int EVENT_COIN_MAX = 999;

    public record Entry(int itemId, int weight, int count, boolean broadcast) {}

    public record KeyItem(int itemId, int count) {}

    public record Roll(int itemId, int count, boolean broadcast) {

        public boolean isEventCoin() {
            return itemId == EVENT_COIN_ITEM_ID;
        }
    }

    public record Pool(int defaultItemId, int defaultCount, boolean defaultBroadcast,
                       List<Entry> entries) {

        public Pool {
            entries = List.copyOf(entries);
        }

        public Roll roll(Random random) {
            int rand = random.nextInt(RANDOM_RANGE + 1);
            int acc = 0;
            for (Entry e : entries) {
                acc += e.weight();
                if (acc > rand) {
                    if (e.itemId() != 0) {
                        return new Roll(e.itemId(), e.count(), e.broadcast());
                    }
                    break;
                }
            }
            return new Roll(defaultItemId, defaultCount, defaultBroadcast);
        }

        public int totalWeight() {
            int sum = 0;
            for (Entry e : entries) {
                sum += e.weight();
            }
            return sum;
        }
    }

    public static final MultiBoxInfo EMPTY = new MultiBoxInfo(List.of(), List.of());

    public MultiBoxInfo {
        pools = List.copyOf(pools);
        keys = List.copyOf(keys);
    }

    public static MultiBoxInfo of(List<List<Integer>> intDataPools, List<Integer> sealing) {
        var pools = new ArrayList<Pool>();
        for (List<Integer> raw : intDataPools) {
            if (raw == null || raw.size() < 3) {
                continue;
            }
            var entries = new ArrayList<Entry>();
            for (int i = 3; i + 3 < raw.size(); i += 4) {
                entries.add(new Entry(raw.get(i), raw.get(i + 1), raw.get(i + 2),
                        raw.get(i + 3) != 0));
            }
            pools.add(new Pool(raw.get(0), raw.get(1), raw.get(2) != 0, entries));
        }
        return new MultiBoxInfo(pools, parseKeys(sealing));
    }

    private static List<KeyItem> parseKeys(List<Integer> sealing) {
        if (sealing == null || sealing.size() < 3) {
            return List.of();
        }
        var out = new ArrayList<KeyItem>();
        for (int i = 1; i + 1 < sealing.size(); i += 2) {
            out.add(new KeyItem(sealing.get(i), sealing.get(i + 1)));
        }
        return out;
    }

    public boolean isEmpty() {
        return pools.isEmpty();
    }

    public KeyItem key() {
        return keys.isEmpty() ? null : keys.get(0);
    }

    public int boxCount() {
        return keys.size() < 2 ? 1 : Math.max(1, keys.get(1).count());
    }

    @Override
    public String toString() {
        if (pools.isEmpty()) {
            return "无池";
        }
        var sb = new StringBuilder(pools.size() + " 个池");
        for (int i = 0; i < pools.size(); i++) {
            Pool p = pools.get(i);
            sb.append(i == 0 ? "（" : "，")
              .append(p.entries().size()).append(" 条/权重 ").append(p.totalWeight());
        }
        sb.append('）');
        KeyItem k = key();
        if (k != null) {
            sb.append(" 钥匙 ").append(k.itemId()).append('×').append(k.count());
        }
        return sb.toString();
    }
}
