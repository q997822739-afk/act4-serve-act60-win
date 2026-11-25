package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class ClearRewardTable {
    public static final String PATH = "etc/itemdropinfo_clearreward.etc";

    public static final int DENOMINATOR = 10000;

    public static final String PROFILE_DEFAULT = "default";

    public static final String PROFILE_GOLD_CARD = "event";

    public static final int DIFFICULTY_COUNT = 4;

    public static final int MAX_PARTY_MEMBERS = 4;

    public static final int RARITY_COUNT = DropInfoTable.RARITY_COUNT;

    public record Band(int minLevel, int maxLevel, int probability) {}

    private final Map<String, List<Band>> dropProb;
    private final int randomKindProbability;
    private final int[] itemTypeWeights;
    private final int[] difficultyItemBonus;
    private final int[] rarityThresholds;
    private final double[] partyGoldRate;
    private final double[] difficultyGoldRate;
    private final NavigableMap<Integer, Integer> goldCardCost;
    private final double goldCardCreateRate;
    private final Map<Integer, DropInfoTable.LevelWindow> levelWindows;

    private ClearRewardTable(Map<String, List<Band>> dropProb, int randomKindProbability,
                             int[] itemTypeWeights, int[] difficultyItemBonus,
                             int[] rarityThresholds, double[] partyGoldRate,
                             double[] difficultyGoldRate,
                             NavigableMap<Integer, Integer> goldCardCost,
                             double goldCardCreateRate,
                             Map<Integer, DropInfoTable.LevelWindow> levelWindows) {
        this.dropProb = Map.copyOf(dropProb);
        this.randomKindProbability = randomKindProbability;
        this.itemTypeWeights = itemTypeWeights;
        this.difficultyItemBonus = difficultyItemBonus;
        this.rarityThresholds = rarityThresholds;
        this.partyGoldRate = partyGoldRate;
        this.difficultyGoldRate = difficultyGoldRate;
        this.goldCardCost = new TreeMap<>(goldCardCost);
        this.goldCardCreateRate = goldCardCreateRate;
        this.levelWindows = Map.copyOf(levelWindows);
    }

    public static ClearRewardTable load(PvfArchive archive) {
        return parse(archive.readText(PATH));
    }

    public static ClearRewardTable parse(String text) {
        Map<String, List<String>> sections = EtcSections.split(text);

        Map<String, List<Band>> dropProb = parseDropProb(
                sections.getOrDefault("drop prob", List.of()));
        if (dropProb.isEmpty()) {
            throw new PvfFormatException(PATH + " 里没有可用的 [drop prob] 段");
        }

        int randomKind = DENOMINATOR;
        var kindLines = sections.getOrDefault("drop kind prob", List.of());
        if (!kindLines.isEmpty()) {
            randomKind = EtcSections.ints(kindLines.get(0), 2)[0];
        }

        int[] itemTypeWeights = firstRowInts(sections, "drop item type prob",
                ItemDropType.ITEMS.length, new int[]{0, DENOMINATOR, 0, 0});
        int[] difficultyBonus = firstRowInts(sections, "dungeon difficulty drop bonusrate",
                DIFFICULTY_COUNT, new int[DIFFICULTY_COUNT]);
        int[] rarityFallback = new int[RARITY_COUNT];
        java.util.Arrays.fill(rarityFallback, DENOMINATOR);
        rarityFallback[RARITY_COUNT - 1] = DENOMINATOR + 1;
        int[] rarity = firstRowInts(sections, "basis of rarity dicision", RARITY_COUNT,
                rarityFallback);
        double[] partyRate = firstRowDoubles(sections, "party member drop bonusrate",
                MAX_PARTY_MEMBERS);
        double[] difficultyGold = firstRowDoubles(sections,
                "dungeon difficulty gold drop bonusrate", DIFFICULTY_COUNT);

        NavigableMap<Integer, Integer> cost = new TreeMap<>();
        for (String line : sections.getOrDefault("gold card cost table", List.of())) {
            int[] v = EtcSections.ints(line, 2);
            cost.put(v[0], Math.max(0, v[1]));
        }

        double createRate = 1.0;
        var createLines = sections.getOrDefault("gold card create rate", List.of());
        if (!createLines.isEmpty()) {
            createRate = Math.max(0.0, EtcSections.doubles(createLines.get(0), 1, 1.0)[0]);
        }

        Map<Integer, DropInfoTable.LevelWindow> windows = new LinkedHashMap<>();
        for (String line : sections.getOrDefault("item drop ref table", List.of())) {
            int[] v = EtcSections.ints(line, 3);
            windows.put(v[0], new DropInfoTable.LevelWindow(v[1], v[2]));
        }

        return new ClearRewardTable(dropProb, randomKind, itemTypeWeights, difficultyBonus,
                rarity, partyRate, difficultyGold, cost, createRate, windows);
    }

    private static Map<String, List<Band>> parseDropProb(List<String> lines) {
        Map<String, List<Band>> out = new LinkedHashMap<>();
        List<Band> current = null;
        for (String line : lines) {
            String label = EtcSections.groupLabel(line);
            if (label != null) {
                current = out.computeIfAbsent(label, k -> new ArrayList<>());
                continue;
            }
            if (current == null) {
                continue;
            }
            int[] v = EtcSections.ints(line, 3);
            current.add(new Band(v[0], v[1], v[2]));
        }
        out.values().removeIf(List::isEmpty);
        return out;
    }

    private static int[] firstRowInts(Map<String, List<String>> sections, String name,
                                      int columns, int[] fallback) {
        var lines = sections.getOrDefault(name, List.of());
        return lines.isEmpty() ? fallback.clone() : EtcSections.ints(lines.get(0), columns);
    }

    private static double[] firstRowDoubles(Map<String, List<String>> sections, String name,
                                            int columns) {
        var lines = sections.getOrDefault(name, List.of());
        if (lines.isEmpty()) {
            double[] neutral = new double[columns];
            java.util.Arrays.fill(neutral, 1.0);
            return neutral;
        }
        return EtcSections.doubles(lines.get(0), columns, 1.0);
    }

    public int itemProbability(String profile, int dungeonLevel) {
        List<Band> bands = dropProb.get(profile);
        if (bands == null || bands.isEmpty()) {
            return 0;
        }
        for (Band band : bands) {
            if (dungeonLevel >= band.minLevel() && dungeonLevel <= band.maxLevel()) {
                return band.probability();
            }
        }
        return bands.get(bands.size() - 1).probability();
    }

    public boolean hasProfile(String profile) {
        return dropProb.containsKey(profile);
    }

    public int[] itemTypeWeights() {
        return itemTypeWeights.clone();
    }

    public int difficultyItemBonus(int difficulty) {
        return at(difficultyItemBonus, difficulty);
    }

    public int[] rarityThresholds() {
        return rarityThresholds.clone();
    }

    public double partyGoldRate(int members) {
        return at(partyGoldRate, members - 1);
    }

    public double difficultyGoldRate(int difficulty) {
        return at(difficultyGoldRate, difficulty);
    }

    public int goldCardCost(int dungeonLevel) {
        var entry = goldCardCost.floorEntry(dungeonLevel);
        if (entry == null) {
            entry = goldCardCost.firstEntry();
        }
        return entry == null ? 0 : entry.getValue();
    }

    public boolean hasGoldCardCost() {
        return !goldCardCost.isEmpty();
    }

    public double goldCardCreateRate() {
        return goldCardCreateRate;
    }

    public DropInfoTable.LevelWindow levelWindow(int dungeonLevel) {
        return levelWindows.getOrDefault(dungeonLevel, DropInfoTable.LevelWindow.EXACT);
    }

    public int profileCount() {
        return dropProb.size();
    }

    private static int at(int[] values, int index) {
        if (values.length == 0) {
            return 0;
        }
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }

    private static double at(double[] values, int index) {
        if (values.length == 0) {
            return 1.0;
        }
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }

    @Override
    public String toString() {
        return profileCount() + " 组概率、金卡手续费 " + goldCardCost.size() + " 级、"
                + "等级窗口 " + levelWindows.size() + " 级";
    }
}
