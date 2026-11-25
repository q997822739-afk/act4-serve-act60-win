package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;

import java.util.ArrayList;
import java.util.List;

public final class DeathTowerTable {
    public static final String PATH = "etc/deathtower.etc";

    public static final int PROB_COUNT = 3;

    public static final int PROB_GOLD = 0;

    public static final int PROB_ITEM = 1;

    public static final int PROB_DENOMINATOR = 1000;

    private final double apcExpWeight;
    private final double monsterExpWeight;
    private final double rewardGoldWeight;
    private final int[] rewardItemProb;
    private final double[] rewardExpWeight;
    private final int[] rewardCardNum;
    private final double[] illusionExpWeight;
    private final int[] illusionCardNum;

    private DeathTowerTable(double apcExpWeight, double monsterExpWeight,
                            double rewardGoldWeight, int[] rewardItemProb,
                            double[] rewardExpWeight, int[] rewardCardNum,
                            double[] illusionExpWeight, int[] illusionCardNum) {
        this.apcExpWeight = apcExpWeight;
        this.monsterExpWeight = monsterExpWeight;
        this.rewardGoldWeight = rewardGoldWeight;
        this.rewardItemProb = rewardItemProb;
        this.rewardExpWeight = rewardExpWeight;
        this.rewardCardNum = rewardCardNum;
        this.illusionExpWeight = illusionExpWeight;
        this.illusionCardNum = illusionCardNum;
    }

    public static DeathTowerTable empty() {
        return new DeathTowerTable(0, 0, 0, new int[PROB_COUNT],
                new double[0], new int[0], new double[0], new int[0]);
    }

    public static DeathTowerTable load(PvfArchive archive) {
        return parse(archive.readText(PATH));
    }

    public static DeathTowerTable parse(String text) {
        var tokens = ScriptReader.tokenize(text);

        double apcExpWeight = 0;
        double monsterExpWeight = 0;
        double goldWeight = 0;
        int[] itemProb = new int[PROB_COUNT];
        List<Double> expWeight = new ArrayList<>();
        List<Integer> cardNum = new ArrayList<>();
        List<Double> illusionExp = new ArrayList<>();
        List<Integer> illusionCard = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            switch (token.text().toLowerCase()) {
                case "apc exp weight" -> apcExpWeight = nextDouble(tokens, i);
                case "monster exp weight" -> monsterExpWeight = nextDouble(tokens, i);
                case "reward gold weight" -> goldWeight = nextDouble(tokens, i);
                case "reward exp weight" -> readDoubles(tokens, i, expWeight);
                case "reward card num" -> readInts(tokens, i, cardNum);
                case "illusion reward exp weight" -> readDoubles(tokens, i, illusionExp);
                case "illusion reward card num" -> readInts(tokens, i, illusionCard);
                case "reward item prob" -> {
                    List<Integer> raw = new ArrayList<>(PROB_COUNT);
                    readInts(tokens, i, raw);
                    int sum = 0;
                    for (int k = 0; k < PROB_COUNT && k < raw.size(); k++) {
                        sum += raw.get(k);
                        itemProb[k] = sum;
                    }
                }
                default -> {
                }
            }
        }
        return new DeathTowerTable(apcExpWeight, monsterExpWeight, goldWeight, itemProb,
                toDoubles(expWeight), toInts(cardNum),
                toDoubles(illusionExp), toInts(illusionCard));
    }

    public double apcExpWeight() {
        return apcExpWeight;
    }

    public double monsterExpWeight() {
        return monsterExpWeight;
    }

    public double rewardGoldWeight() {
        return rewardGoldWeight;
    }

    public int rewardItemProb(int index) {
        return index >= 0 && index < rewardItemProb.length ? rewardItemProb[index] : 0;
    }

    public double expWeight(int floor, boolean illusion) {
        double[] table = illusion && illusionExpWeight.length > 0
                ? illusionExpWeight : rewardExpWeight;
        int at = floor - 1;
        return at >= 0 && at < table.length ? table[at] : 0;
    }

    public int cardNum(int floor, boolean illusion) {
        int[] table = illusion && illusionCardNum.length > 0
                ? illusionCardNum : rewardCardNum;
        int at = floor - 1;
        return at >= 0 && at < table.length ? table[at] : 0;
    }

    public boolean hasIllusionTables() {
        return illusionExpWeight.length > 0 && illusionCardNum.length > 0;
    }

    @Override
    public String toString() {
        return "DeathTowerTable[经验权重 " + rewardExpWeight.length + " 层，翻牌张数 "
                + rewardCardNum.length + " 层，累计概率 " + rewardItemProb[0] + "/"
                + rewardItemProb[Math.min(1, rewardItemProb.length - 1)]
                + "，金币权重 " + rewardGoldWeight + "，APC 经验 ×" + apcExpWeight
                + "，怪物经验 ×" + monsterExpWeight
                + (hasIllusionTables() ? "，迷妄塔另有一套表"
                        : "，没有 [illusion reward *]（迷妄之塔走同一套）") + "]";
    }

    private static void readInts(List<ScriptReader.Token> tokens, int at, List<Integer> out) {
        for (int i = at + 1; i < tokens.size(); i++) {
            if (tokens.get(i).kind() != ScriptReader.Kind.WORD) {
                return;
            }
            out.add(tokens.get(i).asInt());
        }
    }

    private static void readDoubles(List<ScriptReader.Token> tokens, int at, List<Double> out) {
        for (int i = at + 1; i < tokens.size(); i++) {
            if (tokens.get(i).kind() != ScriptReader.Kind.WORD) {
                return;
            }
            out.add(Double.parseDouble(tokens.get(i).text().trim()));
        }
    }

    private static double nextDouble(List<ScriptReader.Token> tokens, int at) {
        if (at + 1 >= tokens.size()
                || tokens.get(at + 1).kind() != ScriptReader.Kind.WORD) {
            return 0;
        }
        return Double.parseDouble(tokens.get(at + 1).text().trim());
    }

    private static double[] toDoubles(List<Double> list) {
        double[] out = new double[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private static int[] toInts(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }
}
