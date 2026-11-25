package com.dylan.pvf.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public record LotteryInfo(int defaultItemId, int defaultCount, List<Entry> entries) {

    public record Entry(int itemId, int weight, int count) {}

    public static final LotteryInfo EMPTY = new LotteryInfo(0, 0, List.of());

    public static final int RANDOM_RANGE = 100_000;

    public LotteryInfo {
        entries = List.copyOf(entries);
    }

    public static LotteryInfo plain(List<Integer> intData) {
        if (intData == null || intData.isEmpty()) {
            return EMPTY;
        }
        var entries = new ArrayList<Entry>();
        for (int i = 1; i + 1 < intData.size(); i += 2) {
            entries.add(new Entry(intData.get(i), intData.get(i + 1), 1));
        }
        return new LotteryInfo(intData.get(0), 1, entries);
    }

    public static LotteryInfo stackable(List<Integer> intData) {
        if (intData == null || intData.size() < 2) {
            return EMPTY;
        }
        var entries = new ArrayList<Entry>();
        for (int i = 2; i + 2 < intData.size(); i += 3) {
            entries.add(new Entry(intData.get(i), intData.get(i + 1), intData.get(i + 2)));
        }
        return new LotteryInfo(intData.get(0), intData.get(1), entries);
    }

    public record Roll(int itemId, int count) {

        public boolean isGold() {
            return itemId == 0 && count > 0;
        }
    }

    public Roll roll(Random random) {
        int rand = random.nextInt(RANDOM_RANGE + 1);
        int acc = 0;
        int chosen = -1;
        int count = 1;
        for (Entry e : entries) {
            acc += e.weight();
            if (acc > rand) {
                chosen = e.itemId();
                count = e.count();
                break;
            }
        }
        if (chosen != 0 || count <= 0) {
            if (chosen == -1) {
                chosen = defaultItemId;
                count = defaultCount;
            }
            if (chosen != 0) {
                return new Roll(chosen, count);
            }
        }
        return new Roll(0, count);
    }

    public int totalWeight() {
        int sum = 0;
        for (Entry e : entries) {
            sum += e.weight();
        }
        return sum;
    }

    @Override
    public String toString() {
        return "抽奖池[" + entries.size() + " 条，权重合计 " + totalWeight()
                + "，默认 " + defaultItemId + " × " + defaultCount + "]";
    }
}
