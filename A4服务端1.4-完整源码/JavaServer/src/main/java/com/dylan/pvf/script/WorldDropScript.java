package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WorldDropScript {

    public static final String PATH = "etc/worlddrop.etc";

    public static final int DENOMINATOR = 100_000;

    private static final int END_FLAG = -1;

    public record Level(int totalWeight, int[] items, int[] weights) {

        public static final Level EMPTY = new Level(0, new int[0], new int[0]);

        public int pick(int roll) {
            int cumulative = 0;
            for (int i = 0; i < items.length; i++) {
                cumulative += weights[i];
                if (roll < cumulative) {
                    return items[i];
                }
            }
            return -1;
        }
    }

    private final Map<Integer, Level> byLevel;

    private WorldDropScript(Map<Integer, Level> byLevel) {
        this.byLevel = Map.copyOf(byLevel);
    }

    public static WorldDropScript load(PvfArchive archive) {
        return parse(archive.readText(PATH));
    }

    public static WorldDropScript parse(String text) {
        Map<Integer, Level> byLevel = new HashMap<>();
        for (String line : EtcSections.split(text).getOrDefault("world drop", List.of())) {
            String[] tokens = EtcSections.tokens(line);
            if (tokens.length < 2) {
                continue;
            }
            int level = EtcSections.parseInt(tokens[0], END_FLAG);
            if (level < 0) {
                continue;
            }
            List<Integer> items = new ArrayList<>();
            List<Integer> weights = new ArrayList<>();
            int total = 0;
            for (int i = 2; i + 1 < tokens.length; i += 2) {
                int itemId = EtcSections.parseInt(tokens[i], END_FLAG);
                if (itemId == END_FLAG) {
                    break;
                }
                int weight = EtcSections.parseInt(tokens[i + 1], 0);
                total += weight;
                items.add(itemId);
                weights.add(weight);
            }
            byLevel.put(level, new Level(total,
                    items.stream().mapToInt(Integer::intValue).toArray(),
                    weights.stream().mapToInt(Integer::intValue).toArray()));
        }
        return new WorldDropScript(byLevel);
    }

    public Level forLevel(int level) {
        return byLevel.getOrDefault(level, Level.EMPTY);
    }

    public int size() {
        return byLevel.size();
    }
}
