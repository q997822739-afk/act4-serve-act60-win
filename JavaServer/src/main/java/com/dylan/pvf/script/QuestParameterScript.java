package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class QuestParameterScript {
    public static final String PATH = "quest/questparameter.etc";

    public static final int DEFAULT_WEIGHT = 100;

    public static final int GREEN_PENALTY_FROM = 7;

    public static final int GREEN_PENALTY_TO = 11;

    private final Map<Character, Integer> weights;
    private final int greenLevelPenalty;
    private final int greyLevelPenalty;
    private final int balKunPenalty;
    private final List<Integer> expRewardTable;
    private final List<Integer> goldRewardTable;

    private QuestParameterScript(Map<Character, Integer> weights, int greenLevelPenalty,
                                 int greyLevelPenalty, int balKunPenalty,
                                 List<Integer> expRewardTable, List<Integer> goldRewardTable) {
        this.weights = Map.copyOf(weights);
        this.greenLevelPenalty = greenLevelPenalty;
        this.greyLevelPenalty = greyLevelPenalty;
        this.balKunPenalty = balKunPenalty;
        this.expRewardTable = List.copyOf(expRewardTable);
        this.goldRewardTable = List.copyOf(goldRewardTable);
    }

    public static QuestParameterScript empty() {
        return new QuestParameterScript(Map.of(), 100, 100, 0, List.of(), List.of());
    }

    public static QuestParameterScript load(PvfArchive archive) {
        return parse(archive.readText(PATH));
    }

    public static QuestParameterScript parse(String text) {
        Map<String, List<String>> sections = EtcSections.split(text);
        Map<Character, Integer> weights = new HashMap<>();
        for (String line : sections.getOrDefault("difficulty", List.of())) {
            String label = EtcSections.groupLabel(line);
            if (label == null || label.isEmpty()) {
                continue;
            }
            String[] parts = EtcSections.tokens(line);
            int value = EtcSections.parseInt(parts[parts.length - 1], DEFAULT_WEIGHT);
            weights.put(normalize(label.charAt(0)), value);
        }
        return new QuestParameterScript(
                weights,
                firstInt(sections, "green level penalty", 100),
                firstInt(sections, "grey level penalty", 100),
                firstInt(sections, "bal kun penalty", 0),
                allInts(sections, "exp reward table"),
                allInts(sections, "gold reward table"));
    }

    public int rewardWeight(char difficulty) {
        Integer value = weights.get(normalize(difficulty));
        return value == null ? DEFAULT_WEIGHT : value;
    }

    public int levelPenalty(int levelDifference) {
        if (levelDifference >= GREEN_PENALTY_FROM && levelDifference <= GREEN_PENALTY_TO) {
            return greenLevelPenalty;
        }
        return levelDifference <= GREEN_PENALTY_TO ? 100 : greyLevelPenalty;
    }

    public int rewardExp(int level) {
        return at(expRewardTable, level);
    }

    public int rewardGold(int level) {
        return at(goldRewardTable, level);
    }

    private static int at(List<Integer> table, int index) {
        if (index < 0 || index >= table.size()) {
            return 0;
        }
        return table.get(index);
    }

    private static char normalize(char c) {
        return Character.toUpperCase(c);
    }

    private static int firstInt(Map<String, List<String>> sections, String name, int fallback) {
        List<String> lines = sections.getOrDefault(name, List.of());
        if (lines.isEmpty()) {
            return fallback;
        }
        return EtcSections.parseInt(EtcSections.tokens(lines.get(0))[0], fallback);
    }

    private static List<Integer> allInts(Map<String, List<String>> sections, String name) {
        List<Integer> out = new ArrayList<>();
        for (String line : sections.getOrDefault(name, List.of())) {
            for (String token : EtcSections.tokens(line)) {
                try {
                    out.add(Integer.parseInt(token));
                } catch (NumberFormatException e) {
                }
            }
        }
        return out;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "难度权重 %d 档、等级差惩罚 %d/%d、经验表 %d 项、金币表 %d 项",
                weights.size(), greenLevelPenalty, greyLevelPenalty,
                expRewardTable.size(), goldRewardTable.size());
    }
}
