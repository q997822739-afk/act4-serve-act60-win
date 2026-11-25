package com.dylan.pvf.script;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.random.RandomGenerator;

public final class AiCharacterList {

    private final NavigableMap<Integer, List<AiCharacterScript>> byAppearancePoint;

    private final NavigableMap<Integer, Integer> countByAppearancePoint;

    private final int minAppearancePoint;

    private AiCharacterList(NavigableMap<Integer, List<AiCharacterScript>> byAppearancePoint,
                            NavigableMap<Integer, Integer> countByAppearancePoint,
                            int minAppearancePoint) {
        this.byAppearancePoint = byAppearancePoint;
        this.countByAppearancePoint = countByAppearancePoint;
        this.minAppearancePoint = minAppearancePoint;
    }

    public static AiCharacterList empty() {
        return new AiCharacterList(new TreeMap<>(), new TreeMap<>(), Integer.MAX_VALUE);
    }

    public static AiCharacterList of(Collection<AiCharacterScript> all) {
        NavigableMap<Integer, List<AiCharacterScript>> buckets = new TreeMap<>();
        NavigableMap<Integer, Integer> counts = new TreeMap<>();
        int min = Integer.MAX_VALUE;
        for (AiCharacterScript apc : all) {
            if (!apc.canAppearRandomly()) {
                continue;
            }
            int point = apc.appearancePoint();
            buckets.computeIfAbsent(point, k -> new ArrayList<>()).add(apc);
            counts.merge(point, 1, Integer::sum);
            min = Math.min(min, point);
        }
        return new AiCharacterList(buckets, counts, min);
    }

    public boolean isEmpty() {
        return byAppearancePoint.isEmpty();
    }

    public int minAppearancePoint() {
        return minAppearancePoint;
    }

    public NavigableMap<Integer, Integer> countByAppearancePoint() {
        return new TreeMap<>(countByAppearancePoint);
    }

    public AiCharacterScript getByAP(int point, RandomGenerator random) {
        if (byAppearancePoint.isEmpty()) {
            return null;
        }
        Map.Entry<Integer, List<AiCharacterScript>> entry = byAppearancePoint.ceilingEntry(point);
        if (entry == null) {
            entry = byAppearancePoint.lastEntry();
        }
        if (entry.getKey() > point) {
            var cheaper = byAppearancePoint.lowerEntry(entry.getKey());
            if (cheaper != null) {
                entry = cheaper;
            }
        }
        List<AiCharacterScript> bucket = entry.getValue();
        return bucket.get(random.nextInt(bucket.size()));
    }

    public int size() {
        int total = 0;
        for (Integer count : countByAppearancePoint.values()) {
            total += count;
        }
        return total;
    }

    @Override
    public String toString() {
        Map<Integer, Integer> preview = new LinkedHashMap<>(countByAppearancePoint);
        return "AiCharacterList[" + size() + " 个能随机出场的 APC，最低出场点数 "
                + (isEmpty() ? "-" : minAppearancePoint) + "，各价位 " + preview + "]";
    }
}
