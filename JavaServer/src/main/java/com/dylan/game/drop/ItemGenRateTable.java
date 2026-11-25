package com.dylan.game.drop;

import com.dylan.pvf.script.DropInfoTable;
import com.dylan.pvf.script.ItemDropType;
import com.dylan.pvf.script.ItemScript;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.IntFunction;
import java.util.random.RandomGenerator;

public final class ItemGenRateTable {
    public static final int NONE = -1;

    public static final int MAX_LEVEL = 99;

    private final NavigableMap<Integer, Integer>[][][] table;

    private final int entryCount;

    private ItemGenRateTable(NavigableMap<Integer, Integer>[][][] table, int entryCount) {
        this.table = table;
        this.entryCount = entryCount;
    }

    public static ItemGenRateTable generateTable(
            IntFunction<DropInfoTable.LevelWindow> windows,
            Iterable<ItemScript> items,
            String label) {
        List<List<ItemScript>> byGrade = new ArrayList<>(MAX_LEVEL + 1);
        for (int i = 0; i <= MAX_LEVEL; i++) {
            byGrade.add(new ArrayList<>());
        }
        int usable = 0;
        int zeroRate = 0;
        for (ItemScript item : items) {
            if (item == null) {
                continue;
            }
            if (item.creationRate() <= 0) {
                zeroRate++;
                continue;
            }
            int grade = item.grade();
            if (grade < 0 || grade > MAX_LEVEL) {
                continue;
            }
            byGrade.get(grade).add(item);
            usable++;
        }

        @SuppressWarnings("unchecked")
        NavigableMap<Integer, Integer>[][][] built =
                new NavigableMap[ItemDropType.COUNT][DropInfoTable.RARITY_COUNT][MAX_LEVEL + 1];
        int entries = 0;

        for (ItemDropType type : ItemDropType.ITEMS) {
            for (int rarity = 0; rarity < DropInfoTable.RARITY_COUNT; rarity++) {
                for (int level = 1; level <= MAX_LEVEL; level++) {
                    DropInfoTable.LevelWindow window = windows.apply(level);
                    NavigableMap<Integer, Integer> bucket = null;
                    int cumulative = 0;
                    for (int m = -window.minus(); m < window.plus() && level + m <= MAX_LEVEL; m++) {
                        int lv = level + m;
                        if (lv < 0) {
                            continue;
                        }
                        for (ItemScript item : byGrade.get(lv)) {
                            if (item.rarity() != rarity) {
                                continue;
                            }
                            if (!ItemDropType.accepts(type, item)) {
                                continue;
                            }
                            cumulative += item.creationRate();
                            if (bucket == null) {
                                bucket = new TreeMap<>();
                            }
                            bucket.put(cumulative, item.id());
                            entries++;
                        }
                    }
                    built[type.ordinal()][rarity][level] = bucket;
                }
            }
        }

        ItemGenRateTable result = new ItemGenRateTable(built, entries);
        return result;
    }

    public int chooseItem(ItemDropType type, int rarity, int level, RandomGenerator random) {
        if (type == ItemDropType.GOLD) {
            throw new IllegalArgumentException("金钱不走物品池，见 MonsterDropGenerator 的 j=0 那一支");
        }
        if (level < 1 || level > MAX_LEVEL || rarity < 0 || rarity >= DropInfoTable.RARITY_COUNT) {
            return NONE;
        }
        NavigableMap<Integer, Integer> bucket = table[type.ordinal()][rarity][level];
        if (bucket == null || bucket.isEmpty()) {
            return NONE;
        }
        int roll = random.nextInt(bucket.lastKey() + 1);
        var entry = bucket.ceilingEntry(roll);
        return entry == null ? bucket.lastEntry().getValue() : entry.getValue();
    }
}
