package com.dylan.pvf.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

public final class UpgradeTable {
    public static final String PATH = "etc/upgrade.etc";

    public static final int FAIL_PROBABILITY_SCALE = 100_000;

    public static final int COST_TABLE_SIZE = 100;

    public static final int RARITIES = 6;

    public static final int PENALTY_NONE = 1;

    public static final int PENALTY_DEGRADE = 2;

    public static final int PENALTY_DESTROY = 3;

    public static final int NOT_CONFIGURED = -1;

    private static final List<String> RARITY_NAMES = List.of(
            "common", "uncommon", "rare", "unique", "epic", "chronicle");

    public record Step(double weaponBonus, double armorBonus, double armorPercent,
                       double accessoryBonus, double accessoryPercent,
                       int failProbability, int penaltyType, int degradeTo,
                       int materialItemId, int materialCount) {
        public int degradedLevel(int currentUpgrade) {
            if (degradeTo >= currentUpgrade) {
                return 0;
            }
            return currentUpgrade - degradeTo;
        }
    }

    public record BonusItem(int itemId, int count) {}

    private final List<Step> steps;
    private final int noticeLevel;
    private final double[] levelWeights;
    private final double[] costWeightByRarity;
    private final double[] statWeightByRarity;
    private final double[] typeWeightBySlot;
    private final int[] costByGrade;
    private final double[] repairCostRateByUpgrade;
    private final int[] destroyLevelByRarity;
    private final int[] maxUpgradeLevelByRarity;
    private final int disjointBonusItemId;
    private final int equipLevelConst;
    private final double[] failedBonusWeightByRarity;
    private final int upgradeConstForBonusItemCount;
    private final int[] correctionGradeByRarity;

    private UpgradeTable(Builder b) {
        this.steps = List.copyOf(b.steps);
        this.noticeLevel = b.noticeLevel;
        this.levelWeights = b.levelWeights;
        this.costWeightByRarity = b.costWeights;
        this.statWeightByRarity = b.statWeights;
        this.typeWeightBySlot = b.typeBySlot;
        this.costByGrade = b.costByGrade;
        this.repairCostRateByUpgrade = b.repairCostRate;
        this.destroyLevelByRarity = b.destroyLevel;
        this.maxUpgradeLevelByRarity = b.maxUpgradeLevel;
        this.disjointBonusItemId = b.disjointBonusItemId;
        this.equipLevelConst = b.equipLevelConst;
        this.failedBonusWeightByRarity = b.failedBonusWeight;
        this.upgradeConstForBonusItemCount = b.upgradeConstForBonusItemCount;
        this.correctionGradeByRarity = b.correctionGrade;
    }

    public int maxUpgrade() {
        return steps.size();
    }

    public Step stepFrom(int currentUpgrade) {
        if (currentUpgrade < 0 || currentUpgrade >= steps.size()) {
            return null;
        }
        return steps.get(currentUpgrade);
    }

    public int noticeLevel() {
        return noticeLevel;
    }

    public int penaltyTypeOf(int currentUpgrade, int rarity, Step step) {
        int destroyLevel = destroyLevel(rarity);
        if (destroyLevel == NOT_CONFIGURED || currentUpgrade < destroyLevel) {
            return step.penaltyType();
        }
        return PENALTY_DESTROY;
    }

    public int destroyLevel(int rarity) {
        return at(destroyLevelByRarity, rarity, NOT_CONFIGURED);
    }

    public int maxUpgradeLevel(int rarity) {
        return at(maxUpgradeLevelByRarity, rarity, NOT_CONFIGURED);
    }

    public double repairCostRate(int upgrade) {
        if (upgrade < 0 || upgrade >= repairCostRateByUpgrade.length) {
            return 1.0;
        }
        return repairCostRateByUpgrade[upgrade];
    }

    public BonusItem additionalDisjointBonus(int grade, int rarity, int upgradeBefore) {
        if (disjointBonusItemId <= 0) {
            return null;
        }
        int corrected = Math.max(0, grade - at(correctionGradeByRarity, rarity, 0));
        if (corrected < equipLevelConst) {
            return null;
        }
        if (upgradeBefore <= upgradeConstForBonusItemCount) {
            return null;
        }
        double weight = at(failedBonusWeightByRarity, rarity, 0.0);
        int diff = upgradeBefore - upgradeConstForBonusItemCount;
        int count = (int) (Math.pow(diff, 2) * weight);
        if (count <= 0) {
            return null;
        }
        return new BonusItem(disjointBonusItemId, count);
    }

    public long cost(int grade, int equipmentSlot, int rarity) {
        if (grade < 0 || grade >= costByGrade.length) {
            return 0;
        }
        long cost = costByGrade[grade];
        cost = (long) (cost * typeWeight(equipmentSlot) + 0.5);
        cost = (long) (cost * at(costWeightByRarity, rarity, 0.0) + 0.5);
        return cost;
    }

    public double typeWeight(int equipmentSlot) {
        return at(typeWeightBySlot, equipmentSlot, 0.0);
    }

    public boolean upgradable(int equipmentSlot) {
        return typeWeight(equipmentSlot) > 0;
    }

    private static double at(double[] table, int index, double fallback) {
        if (index < 0 || index >= table.length) {
            return fallback;
        }
        return table[index];
    }

    private static int at(int[] table, int index, int fallback) {
        if (index < 0 || index >= table.length) {
            return fallback;
        }
        return table[index];
    }

    public static UpgradeTable load(PvfArchive archive) {
        return parse(archive.readText(PATH));
    }

    public static UpgradeTable parse(String text) {
        var tokens = ScriptReader.tokenize(text);
        Builder b = new Builder();

        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            switch (token.text().toLowerCase(Locale.ROOT)) {
                case "table" -> readTable(tokens, i, b);
                case "notice" -> b.noticeLevel = readInts(tokens, i, 1).get(0);
                case "level" -> b.levelWeights = toArray(readDoubles(tokens, i));
                case "cost weights by rarity" -> b.costWeights = toArray(readDoubles(tokens, i));
                case "stat weights by rarity" -> b.statWeights = toArray(readDoubles(tokens, i));
                case "type" -> b.typeBySlot = typeBySlot(readDoubles(tokens, i));
                case "cost" -> b.costByGrade = toIntArray(readInts(tokens, i, COST_TABLE_SIZE));
                case "repair cost rate by upgrade level" ->
                        b.repairCostRate = toArray(readDoubles(tokens, i));
                case "destroy level by rarity" ->
                        b.destroyLevel = readByRarity(tokens, i, NOT_CONFIGURED);
                case "max upgrade level by rarity" ->
                        b.maxUpgradeLevel = readByRarity(tokens, i, NOT_CONFIGURED);
                case "disjoint bonus item" -> b.disjointBonusItemId = readInts(tokens, i, 1).get(0);
                case "equip level const" -> b.equipLevelConst = readInts(tokens, i, 1).get(0);
                case "upgrade failed bonus weight by rarity" ->
                        b.failedBonusWeight = readByRarityDouble(tokens, i);
                case "upgrade const for bonus item count" ->
                        b.upgradeConstForBonusItemCount = readInts(tokens, i, 1).get(0);
                case "correction grade by rarity" -> b.correctionGrade = readByRarity(tokens, i, 0);
                default -> {
                }
            }
        }

        if (b.steps.isEmpty()) {
            throw new PvfFormatException(PATH + " 里没有 [table] 数据");
        }
        if (b.costByGrade.length == 0) {
            throw new PvfFormatException(PATH + " 里没有 [cost] 数据");
        }
        return new UpgradeTable(b);
    }

    private static void readTable(List<ScriptReader.Token> tokens, int sectionAt, Builder b) {
        var row = new double[10];
        int col = 0;
        for (int k = sectionAt + 1; k < tokens.size()
                && tokens.get(k).kind() == ScriptReader.Kind.WORD; k++) {
            row[col++] = asDouble(tokens.get(k));
            if (col == row.length) {
                b.steps.add(new Step(row[0], row[1], row[2], row[3], row[4],
                        (int) row[5], (int) row[6], (int) row[7],
                        (int) row[8], (int) row[9]));
                col = 0;
            }
        }
        if (col != 0) {
            throw new PvfFormatException(PATH + " 的 [table] 最后一行只有 " + col
                    + " 个数，应当是 10 个 —— 这一版的表是 60 版形态，别照 70 版的 17 列读");
        }
    }

    private static int[] readByRarity(List<ScriptReader.Token> tokens, int sectionAt,
                                      int fallback) {
        int[] out = new int[RARITIES];
        java.util.Arrays.fill(out, fallback);
        for (int k = sectionAt + 1; k + 1 < tokens.size(); k += 2) {
            if (tokens.get(k).kind() != ScriptReader.Kind.STRING
                    || tokens.get(k + 1).kind() != ScriptReader.Kind.WORD) {
                break;
            }
            out[rarityOf(tokens.get(k).text())] = (int) asDouble(tokens.get(k + 1));
        }
        return out;
    }

    private static double[] readByRarityDouble(List<ScriptReader.Token> tokens, int sectionAt) {
        double[] out = new double[RARITIES];
        for (int k = sectionAt + 1; k + 1 < tokens.size(); k += 2) {
            if (tokens.get(k).kind() != ScriptReader.Kind.STRING
                    || tokens.get(k + 1).kind() != ScriptReader.Kind.WORD) {
                break;
            }
            out[rarityOf(tokens.get(k).text())] = asDouble(tokens.get(k + 1));
        }
        return out;
    }

    private static int rarityOf(String name) {
        int index = RARITY_NAMES.indexOf(name.trim().toLowerCase(Locale.ROOT));
        if (index < 0) {
            throw new PvfFormatException(PATH + " 里出现了认不出的稀有度名 `" + name + "`");
        }
        return index;
    }

    private static double[] typeBySlot(List<Double> typeWeights) {
        int base = com.dylan.protocol.body.EquipmentSlot.WEAPON;
        double[] out = new double[base + typeWeights.size()];
        for (int k = 0; k < typeWeights.size(); k++) {
            out[base + k] = typeWeights.get(k);
        }
        return out;
    }

    private static List<Double> readDoubles(List<ScriptReader.Token> tokens, int sectionAt) {
        List<Double> out = new ArrayList<>();
        for (int k = sectionAt + 1; k < tokens.size()
                && tokens.get(k).kind() == ScriptReader.Kind.WORD; k++) {
            out.add(asDouble(tokens.get(k)));
        }
        return out;
    }

    private static List<Integer> readInts(List<ScriptReader.Token> tokens, int sectionAt,
                                          int limit) {
        List<Integer> out = new ArrayList<>();
        for (int k = sectionAt + 1; k < tokens.size() && out.size() < limit
                && tokens.get(k).kind() == ScriptReader.Kind.WORD; k++) {
            out.add((int) asDouble(tokens.get(k)));
        }
        if (out.isEmpty()) {
            throw new PvfFormatException(PATH + " 的小节 [" + tokens.get(sectionAt).text()
                    + "] 里没有数据");
        }
        return out;
    }

    private static double asDouble(ScriptReader.Token token) {
        try {
            return Double.parseDouble(token.text().trim());
        } catch (NumberFormatException e) {
            throw new PvfFormatException("期望一个数字，读到 \"" + token.text() + "\"");
        }
    }

    private static double[] toArray(List<Double> values) {
        double[] out = new double[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }

    private static int[] toIntArray(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    private static final class Builder {
        final List<Step> steps = new ArrayList<>();
        int noticeLevel = Integer.MAX_VALUE;
        double[] levelWeights = new double[0];
        double[] costWeights = new double[0];
        double[] statWeights = new double[0];
        double[] typeBySlot = new double[0];
        int[] costByGrade = new int[0];
        double[] repairCostRate = new double[0];
        int[] destroyLevel = filled(NOT_CONFIGURED);
        int[] maxUpgradeLevel = filled(NOT_CONFIGURED);
        int disjointBonusItemId = NOT_CONFIGURED;
        int equipLevelConst;
        double[] failedBonusWeight = new double[RARITIES];
        int upgradeConstForBonusItemCount;
        int[] correctionGrade = new int[RARITIES];

        private static int[] filled(int value) {
            int[] out = new int[RARITIES];
            java.util.Arrays.fill(out, value);
            return out;
        }
    }

    @Override
    public String toString() {
        return "UpgradeTable[最高 +" + maxUpgrade() + "，广播门槛 +" + noticeLevel
                + "，费用表 " + costByGrade.length + " 档，破碎起点 "
                + java.util.Arrays.toString(destroyLevelByRarity) + "，强化上限 "
                + java.util.Arrays.toString(maxUpgradeLevelByRarity) + "]";
    }
}
