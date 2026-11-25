package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HellPartyTable {
    public static final String PATH = "etc/hellparty.etc";

    public static final int RATE_DENOMINATOR = 10000;

    public static final int LEVEL_DIFF_RATE_SLOTS = 4;

    public static final int DIFFICULTY_RATE_SLOTS = 5;

    public static final int PARTY_RATE_SLOTS = 4;

    public static final int MAX_DIFFICULTY_GRADES = 5;

    public static final int MAX_GROUP_MONSTERS = 16;

    public static final int NO_GUIDE_LINE = 0;

    private static final String GRADE_LETTERS = "ABCDE";

    public record Difficulty(int grade, String name, int dropRoutineCount, int expRate,
                             int randomProb, int sealStoneProb) {
    }

    public record Monster(int index, int kind) {
    }

    public record MonsterGroup(int index, int grade, List<Monster> monsters) {
        public MonsterGroup {
            monsters = List.copyOf(monsters);
        }
    }

    private final int maxBaseValue;
    private final int minBaseValue;
    private final int baseValue;
    private final int baseRisingValue;
    private final double subBaseRate;
    private final double passSubBaseRate;
    private final double[] levelDifferenceRates;
    private final double[] difficultyRates;
    private final double[] partyRates;
    private final double ancientDungeonAddRate;
    private final int guideLineValue;
    private final List<Difficulty> difficulties;
    private final Map<Integer, MonsterGroup> monsterGroups;

    private HellPartyTable(int maxBaseValue, int minBaseValue, int baseValue,
                           int baseRisingValue, double subBaseRate, double passSubBaseRate,
                           double[] levelDifferenceRates, double[] difficultyRates,
                           double[] partyRates, double ancientDungeonAddRate,
                           int guideLineValue, List<Difficulty> difficulties,
                           Map<Integer, MonsterGroup> monsterGroups) {
        this.maxBaseValue = maxBaseValue;
        this.minBaseValue = minBaseValue;
        this.baseValue = baseValue;
        this.baseRisingValue = baseRisingValue;
        this.subBaseRate = subBaseRate;
        this.passSubBaseRate = passSubBaseRate;
        this.levelDifferenceRates = levelDifferenceRates;
        this.difficultyRates = difficultyRates;
        this.partyRates = partyRates;
        this.ancientDungeonAddRate = ancientDungeonAddRate;
        this.guideLineValue = guideLineValue;
        this.difficulties = List.copyOf(difficulties);
        this.monsterGroups = Map.copyOf(monsterGroups);
    }

    public static HellPartyTable empty() {
        return new HellPartyTable(1, 0, 0, 0, 0.0, 0.0,
                new double[LEVEL_DIFF_RATE_SLOTS], new double[DIFFICULTY_RATE_SLOTS],
                new double[PARTY_RATE_SLOTS], 0.0, NO_GUIDE_LINE, List.of(), Map.of());
    }

    public static HellPartyTable load(PvfArchive archive) {
        return parse(archive.readText(PATH));
    }

    public static HellPartyTable parse(String text) {
        var tokens = ScriptReader.tokenize(text);

        int maxBaseValue = 1;
        int minBaseValue = 0;
        int baseValue = 0;
        int baseRisingValue = 0;
        double subBaseRate = 0.0;
        double passSubBaseRate = 0.0;
        int subBaseRateSeen = 0;
        double[] levelDifferenceRates = new double[LEVEL_DIFF_RATE_SLOTS];
        double[] difficultyRates = new double[DIFFICULTY_RATE_SLOTS];
        double[] partyRates = new double[PARTY_RATE_SLOTS];
        double ancientDungeonAddRate = 0.0;
        int guideLineValue = NO_GUIDE_LINE;
        List<Difficulty> difficulties = new ArrayList<>();
        Map<Integer, MonsterGroup> groups = new LinkedHashMap<>();

        int pendingGroupIndex = -1;
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            switch (token.text().toLowerCase()) {
                case "max base value" -> maxBaseValue = Math.max(1, nextInt(tokens, i, 1));
                case "min base value" -> minBaseValue = nextInt(tokens, i, 0);
                case "base value" -> baseValue = nextInt(tokens, i, 0);
                case "base rising value" -> baseRisingValue = nextInt(tokens, i, 0);
                case "sub base rates" -> {
                    double rate = nextRate(tokens, i);
                    if (subBaseRateSeen++ == 0) {
                        subBaseRate = rate;
                    } else {
                        passSubBaseRate = rate;
                    }
                }
                case "pass sub base rates" -> passSubBaseRate = nextRate(tokens, i);
                case "level difference rates" -> readRates(tokens, i, levelDifferenceRates, 1);
                case "difficulty rates" -> readRates(tokens, i, difficultyRates, 0);
                case "party rates" -> readRates(tokens, i, partyRates, 0);
                case "ancient dungeon add rate" -> ancientDungeonAddRate = nextRate(tokens, i);
                case "guide line value" -> guideLineValue = nextInt(tokens, i, NO_GUIDE_LINE);
                case "difficulty" -> readDifficulties(tokens, i, difficulties);
                case "group index" -> pendingGroupIndex = nextInt(tokens, i, -1);
                case "group" -> {
                    MonsterGroup group = readGroup(tokens, i, pendingGroupIndex);
                    if (group != null) {
                        groups.putIfAbsent(group.index(), group);
                    }
                    pendingGroupIndex = -1;
                }
                default -> {
                }
            }
        }
        return new HellPartyTable(maxBaseValue, minBaseValue, baseValue, baseRisingValue,
                subBaseRate, passSubBaseRate, levelDifferenceRates, difficultyRates,
                partyRates, ancientDungeonAddRate, guideLineValue, difficulties, groups);
    }

    public int maxBaseValue() {
        return maxBaseValue;
    }

    public int minBaseValue() {
        return minBaseValue;
    }

    public int baseValue() {
        return baseValue;
    }

    public int baseRisingValue() {
        return baseRisingValue;
    }

    public double subBaseRate() {
        return subBaseRate;
    }

    public double levelDifferenceRate(int index) {
        return at(levelDifferenceRates, index);
    }

    public double difficultyRate(int index) {
        return at(difficultyRates, index);
    }

    public double partyRate(int index) {
        return at(partyRates, index);
    }

    public double ancientDungeonAddRate() {
        return ancientDungeonAddRate;
    }

    public int guideLineValue() {
        return guideLineValue;
    }

    public Difficulty difficulty(int grade) {
        for (Difficulty d : difficulties) {
            if (d.grade() == grade) {
                return d;
            }
        }
        return null;
    }

    public Map<Integer, MonsterGroup> monsterGroups() {
        return monsterGroups;
    }

    public boolean isEmpty() {
        return difficulties.isEmpty() && monsterGroups.isEmpty();
    }

    @Override
    public String toString() {
        return "深渊派对表[上限 " + maxBaseValue + "，初值 " + baseValue + "，每关 +"
                + baseRisingValue + "，扣减率 " + subBaseRate + "，" + difficulties.size()
                + " 个难度档，" + monsterGroups.size() + " 个怪物组]";
    }

    private static double at(double[] array, int index) {
        return index < 0 || index >= array.length ? 0.0 : array[index];
    }

    private static void readDifficulties(List<ScriptReader.Token> tokens, int at,
                                         List<Difficulty> out) {
        int count = nextInt(tokens, at, 0);
        int i = at + 2;
        for (int grade = 1; grade <= count; grade++) {
            if (i >= tokens.size() || tokens.get(i).kind() != ScriptReader.Kind.STRING) {
                return;
            }
            String name = tokens.get(i).text();
            i++;
            int[] cols = new int[4];
            for (int c = 0; c < cols.length; c++) {
                if (i >= tokens.size() || tokens.get(i).kind() != ScriptReader.Kind.WORD) {
                    return;
                }
                cols[c] = tokens.get(i).asInt();
                i++;
            }
            out.add(new Difficulty(grade, name, cols[0], cols[1], cols[2], cols[3]));
        }
    }

    private static MonsterGroup readGroup(List<ScriptReader.Token> tokens, int at, int index) {
        int i = at + 1;
        int grade = 0;
        if (i < tokens.size() && tokens.get(i).kind() == ScriptReader.Kind.STRING) {
            grade = GRADE_LETTERS.indexOf(tokens.get(i).text().trim().toUpperCase()) + 1;
            i++;
        }
        if (index < 0 || grade <= 0) {
            return null;
        }
        List<Monster> monsters = new ArrayList<>(MAX_GROUP_MONSTERS);
        while (i + 1 < tokens.size()
                && tokens.get(i).kind() == ScriptReader.Kind.WORD
                && tokens.get(i + 1).kind() == ScriptReader.Kind.WORD) {
            if (monsters.size() >= MAX_GROUP_MONSTERS) {
                break;
            }
            monsters.add(new Monster(tokens.get(i).asInt(), tokens.get(i + 1).asInt()));
            i += 2;
        }
        return new MonsterGroup(index, grade, monsters);
    }

    private static void readRates(List<ScriptReader.Token> tokens, int at,
                                  double[] out, int first) {
        int slot = first;
        for (int i = at + 1; i < tokens.size() && slot < out.length; i++, slot++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.WORD) {
                return;
            }
            out[slot] = token.asDouble() / RATE_DENOMINATOR;
        }
    }

    private static double nextRate(List<ScriptReader.Token> tokens, int at) {
        int i = at + 1;
        if (i >= tokens.size() || tokens.get(i).kind() != ScriptReader.Kind.WORD) {
            return 0.0;
        }
        return tokens.get(i).asDouble() / RATE_DENOMINATOR;
    }

    private static int nextInt(List<ScriptReader.Token> tokens, int at, int fallback) {
        int i = at + 1;
        if (i >= tokens.size() || tokens.get(i).kind() != ScriptReader.Kind.WORD) {
            return fallback;
        }
        return tokens.get(i).asInt();
    }
}
