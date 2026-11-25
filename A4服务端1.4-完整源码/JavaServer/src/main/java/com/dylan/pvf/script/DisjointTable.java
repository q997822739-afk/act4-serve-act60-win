package com.dylan.pvf.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

public final class DisjointTable {
    public static final String PATH = "etc/disjoint.etc";

    public static final int RARITIES = 6;

    public static final int ELEMENTS = 5;

    public static final int ELEMENT_NONE = 4;

    static int elementOf(String name) {
        return switch (name.trim()) {
            case "[fire element]" -> 0;
            case "[water element]" -> 1;
            case "[dark element]" -> 2;
            case "[light element]" -> 3;
            default -> ELEMENT_NONE;
        };
    }

    public record AdditionalConst(double divisor, double bigWinPercent, int unusedThird) {
        static final AdditionalConst NONE = new AdditionalConst(0, 0, 0);

        boolean enabled() {
            return divisor != 0;
        }
    }

    public record Product(int itemId, int count) {}

    private final int mainCubeId;
    private final int cubeDivisor;
    private final double[] cubeRarityFactor;
    private final List<List<Integer>> additionalIds;
    private final List<AdditionalConst> additionalConsts;

    private DisjointTable(int mainCubeId, int cubeDivisor, double[] cubeRarityFactor,
                          List<List<Integer>> additionalIds,
                          List<AdditionalConst> additionalConsts) {
        this.mainCubeId = mainCubeId;
        this.cubeDivisor = cubeDivisor;
        this.cubeRarityFactor = cubeRarityFactor;
        this.additionalIds = additionalIds;
        this.additionalConsts = additionalConsts;
    }

    public List<Product> resultOf(long sellPrice, int grade, int rarity, Random random) {
        List<Product> out = new ArrayList<>(2);

        int count = (int) ((1000L * sellPrice / cubeDivisor) * rarityFactor(rarity) / 1000.0);
        if (count <= 0) {
            count = 1;
        }
        out.add(new Product(mainCubeId, count));

        int extraId = pickAdditionalId(rarity, random);
        if (extraId != 0) {
            out.add(new Product(extraId, additionalCount(grade, rarity)));
        }
        return out;
    }

    public int predictResultItemTypeCount(int rarity) {
        return additionalCandidates(rarity).isEmpty() ? 1 : 2;
    }

    public List<Integer> predictResult(int rarity) {
        List<Integer> candidates = additionalCandidates(rarity);
        List<Integer> out = new ArrayList<>(1 + candidates.size());
        out.add(mainCubeId);
        out.addAll(candidates);
        return List.copyOf(out);
    }

    public List<Integer> additionalCandidates(int rarity) {
        if (rarity < 0 || rarity >= additionalIds.size()) {
            return List.of();
        }
        return additionalIds.get(rarity);
    }

    int pickAdditionalId(int rarity, Random random) {
        List<Integer> ids = additionalCandidates(rarity);
        if (ids.isEmpty()) {
            return 0;
        }
        return ids.get(random.nextInt(ids.size()));
    }

    int additionalCount(int grade, int rarity) {
        AdditionalConst c = rarity >= 0 && rarity < additionalConsts.size()
                ? additionalConsts.get(rarity) : AdditionalConst.NONE;
        int count = c.enabled() ? (int) (grade / c.divisor()) : 0;
        return count <= 0 ? 1 : count;
    }

    private double rarityFactor(int rarity) {
        if (rarity < 0 || rarity >= cubeRarityFactor.length) {
            return 1.0;
        }
        return cubeRarityFactor[rarity];
    }

    public static DisjointTable load(PvfArchive archive) {
        return parse(archive.readText(PATH));
    }

    public static DisjointTable parse(String text) {
        var tokens = ScriptReader.tokenize(text);

        List<List<Integer>> cubeIndex = new ArrayList<>(ELEMENTS);
        for (int e = 0; e < ELEMENTS; e++) {
            cubeIndex.add(new ArrayList<>());
        }
        int cubeDivisor = 0;
        double[] cubeRarityFactor = new double[RARITIES];
        java.util.Arrays.fill(cubeRarityFactor, 1.0);
        List<List<Integer>> additionalIds = new ArrayList<>();
        List<AdditionalConst> additionalConsts = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            switch (token.text().toLowerCase(java.util.Locale.ROOT)) {
                case "cube index" -> {
                    int element = ELEMENT_NONE;
                    List<Integer> ids = new ArrayList<>();
                    for (int k = i + 1; k < tokens.size(); k++) {
                        var t = tokens.get(k);
                        if (t.kind() == ScriptReader.Kind.STRING) {
                            element = elementOf(t.text());
                        } else if (t.kind() == ScriptReader.Kind.WORD) {
                            ids.add(t.asInt());
                        } else {
                            break;
                        }
                    }
                    cubeIndex.get(element).clear();
                    cubeIndex.get(element).addAll(ids);
                }
                case "cube creation const" -> {
                    var values = words(tokens, i, 1 + RARITIES);
                    if (!values.isEmpty()) {
                        cubeDivisor = (int) (double) values.get(0);
                        for (int k = 1; k < values.size() && k <= RARITIES; k++) {
                            cubeRarityFactor[k - 1] = values.get(k);
                        }
                    }
                }
                case "additional result" -> {
                    var values = words(tokens, i, Integer.MAX_VALUE);
                    int at = 0;
                    while (at < values.size() && additionalIds.size() < RARITIES) {
                        int n = (int) (double) values.get(at++);
                        List<Integer> ids = new ArrayList<>(n);
                        for (int k = 0; k < n && at < values.size(); k++) {
                            ids.add((int) (double) values.get(at++));
                        }
                        additionalIds.add(List.copyOf(ids));
                    }
                }
                case "additional result const" -> {
                    var values = words(tokens, i, Integer.MAX_VALUE);
                    int at = 0;
                    while (at < values.size() && additionalConsts.size() < RARITIES) {
                        double first = values.get(at++);
                        if (first == 0) {
                            additionalConsts.add(AdditionalConst.NONE);
                            continue;
                        }
                        double second = at < values.size() ? values.get(at++) : 0;
                        int third = at < values.size() ? (int) (double) values.get(at++) : 0;
                        additionalConsts.add(new AdditionalConst(first, second, third));
                    }
                }
                default -> {
                }
            }
        }

        List<Integer> noElement = cubeIndex.get(ELEMENT_NONE);
        if (noElement.isEmpty()) {
            throw new PvfFormatException(PATH + " 里没有 `[no element]` 那一段 [cube index]，"
                    + "分解算不出主产物");
        }
        int mainCubeId = noElement.get(0);
        if (mainCubeId == 0) {
            throw new PvfFormatException(PATH + " 的 `[no element]` 段第一个编号是 0");
        }
        if (cubeDivisor <= 0) {
            throw new PvfFormatException(PATH + " 的 [cube creation const] 缺除数");
        }
        while (additionalIds.size() < RARITIES) {
            additionalIds.add(List.of());
        }
        while (additionalConsts.size() < RARITIES) {
            additionalConsts.add(AdditionalConst.NONE);
        }
        return new DisjointTable(mainCubeId, cubeDivisor, cubeRarityFactor,
                List.copyOf(additionalIds), List.copyOf(additionalConsts));
    }

    private static List<Double> words(List<ScriptReader.Token> tokens, int sectionAt, int limit) {
        List<Double> out = new ArrayList<>();
        for (int k = sectionAt + 1; k < tokens.size() && out.size() < limit
                && tokens.get(k).kind() == ScriptReader.Kind.WORD; k++) {
            try {
                out.add(Double.parseDouble(tokens.get(k).text().trim()));
            } catch (NumberFormatException e) {
                throw new PvfFormatException("期望一个数字，读到 \"" + tokens.get(k).text() + "\"");
            }
        }
        return out;
    }

    @Override
    public String toString() {
        return "DisjointTable[主产物 " + mainCubeId + "，除数 " + cubeDivisor + "]";
    }
}
