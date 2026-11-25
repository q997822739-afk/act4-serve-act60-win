package com.dylan.pvf.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

public final class ResealTable {
    public static final String PATH = "etc/reseal.etc";

    public static final int RARITIES = 6;

    /** A4 的 reseal.etc 稀有度只有 5 档（ACT5 6 档），同 DropInfoTable。 */
    public static final int A4_RARITIES = DropInfoTable.A4_RARITY_COUNT;

    public static final int MAX_RESEAL_COUNT = 6;

    public static final int ONESHOT_COST = 1;

    public static final int ERROR_NO_GRADE = -1;

    public static final int ERROR_BAD_RARITY = -2;

    public static final int ERROR_BAD_GRADE = -3;

    public static final int ERROR_BAD_RESEAL_COUNT = -4;

    private final int[] gradeCost;
    private final int[] rarityWeight;
    private final int[] extendedRarityWeight;

    private ResealTable(int[] gradeCost, int[] rarityWeight, int[] extendedRarityWeight) {
        this.gradeCost = gradeCost;
        this.rarityWeight = rarityWeight;
        this.extendedRarityWeight = extendedRarityWeight;
    }

    public static ResealTable load(PvfArchive archive) {
        return parse(archive.readText(PATH));
    }

    public static ResealTable parse(String text) {
        var tokens = ScriptReader.tokenize(text);
        List<Integer> grade = null;
        int[] rarity = null;
        int[] extended = null;

        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            switch (token.text().toLowerCase(Locale.ROOT)) {
                case "grade" -> grade = readInts(tokens, i, Integer.MAX_VALUE);
                case "rarity weight" -> rarity = toArray(readInts(tokens, i, RARITIES));
                case "extended rarity weight" ->
                        extended = toArray(readInts(tokens, i, RARITIES));
                default -> {
                }
            }
        }

        if (grade == null || grade.isEmpty()) {
            throw new PvfFormatException(PATH + " 里没有 [grade] 数据");
        }
        // ACT5 6 档；A4 5 档（没有第 6 档稀有度）。两段档数必须一致。
        if (rarity == null || (rarity.length != RARITIES && rarity.length != A4_RARITIES)) {
            throw new PvfFormatException(PATH + " 的 [rarity weight] 不是 " + RARITIES
                    + " 或 " + A4_RARITIES + " 档");
        }
        if (extended == null || extended.length != rarity.length) {
            throw new PvfFormatException(PATH + " 的 [extended rarity weight] 不是 "
                    + rarity.length + " 档");
        }
        return new ResealTable(toArray(grade), rarity, extended);
    }

    public int resealCost(int rarity, int grade, int resealCount) {
        if (grade == 0) {
            return ERROR_NO_GRADE;
        }
        if (rarity < 0 || rarity > rarityWeight.length - 1) {
            return ERROR_BAD_RARITY;
        }
        if (grade < 0 || grade > 99) {
            return ERROR_BAD_GRADE;
        }
        if (resealCount < 0 || resealCount > MAX_RESEAL_COUNT) {
            return ERROR_BAD_RESEAL_COUNT;
        }
        int index = (grade - 1) / 10;
        if (index >= gradeCost.length) {
            index = gradeCost.length - 1;
        }
        return rarityWeight[rarity] * gradeCost[index] + extendedRarityWeight[rarity] * resealCount;
    }

    public int oneshotResealCost(int rarity, int grade) {
        if (grade == 0) {
            return ERROR_NO_GRADE;
        }
        if (rarity < 0 || rarity > rarityWeight.length - 1) {
            return ERROR_BAD_RARITY;
        }
        if (grade < 0 || grade > 99) {
            return ERROR_BAD_GRADE;
        }
        return ONESHOT_COST;
    }

    private static List<Integer> readInts(List<ScriptReader.Token> tokens, int sectionAt,
                                          int limit) {
        List<Integer> out = new ArrayList<>();
        for (int k = sectionAt + 1; k < tokens.size() && out.size() < limit
                && tokens.get(k).kind() == ScriptReader.Kind.WORD; k++) {
            out.add(tokens.get(k).asInt());
        }
        if (out.isEmpty()) {
            throw new PvfFormatException(PATH + " 的小节 [" + tokens.get(sectionAt).text()
                    + "] 里没有数据");
        }
        return out;
    }

    private static int[] toArray(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }

    @Override
    public String toString() {
        return "ResealTable[等级段 " + gradeCost.length + " 档、稀有度 " + rarityWeight.length + " 档]";
    }
}
