package com.dylan.pvf.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

public final class MagicEnchantScript {
    public static final String PATH = "character/expertjob.etc";

    public static final String SECTION = "magic enchant";

    public static final int RARITIES = 5;

    public record LevelDecrease(int minLevel, int maxLevel, int decrease) {
    }

    public record BaseRate(int exp, int rate) {
    }

    public record ExpIncrease(int minRate, int maxRate, int onTry, int onSuccess) {
    }

    private final List<LevelDecrease> decreases;
    private final List<BaseRate> baseRates;
    private final List<ExpIncrease> increases;
    private final Map<String, Integer> specialityClass;
    private final int specialityRateBonus;
    private final int specialityExpBonus;
    private final List<int[]> skills;
    private final double[] equipmentRarity;
    private final double[] monsterCardRarity;

    private MagicEnchantScript(List<LevelDecrease> decreases, List<BaseRate> baseRates,
                               List<ExpIncrease> increases, Map<String, Integer> specialityClass,
                               int specialityRateBonus, int specialityExpBonus,
                               List<int[]> skills, double[] equipmentRarity,
                               double[] monsterCardRarity) {
        this.decreases = List.copyOf(decreases);
        this.baseRates = List.copyOf(baseRates);
        this.increases = List.copyOf(increases);
        this.specialityClass = Map.copyOf(specialityClass);
        this.specialityRateBonus = specialityRateBonus;
        this.specialityExpBonus = specialityExpBonus;
        this.skills = List.copyOf(skills);
        this.equipmentRarity = equipmentRarity.clone();
        this.monsterCardRarity = monsterCardRarity.clone();
    }

    public static MagicEnchantScript load(PvfArchive archive) {
        return parse(archive.readText(PATH));
    }

    public static MagicEnchantScript parse(String text) {
        var tokens = ScriptReader.tokenize(text);

        List<LevelDecrease> decreases = new ArrayList<>();
        List<BaseRate> baseRates = new ArrayList<>();
        List<ExpIncrease> increases = new ArrayList<>();
        Map<String, Integer> speciality = new LinkedHashMap<>();
        int rateBonus = 0;
        int expBonus = 0;
        List<int[]> skills = new ArrayList<>();
        double[] equipmentRarity = null;
        double[] cardRarity = null;

        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            switch (token.text().toLowerCase(Locale.ROOT)) {
                case "rate of success decrease" -> {
                    for (var r : rows(tokens, i, 3, "[rate of success decrease]")) {
                        decreases.add(new LevelDecrease(r.get(0).asInt(), r.get(1).asInt(),
                                r.get(2).asInt()));
                    }
                }
                case "expertness exp" -> {
                    var rows = rows(tokens, i, 2, "[expertness exp]");
                    if (!rows.isEmpty()) {
                        baseRates.clear();
                        for (var r : rows) {
                            baseRates.add(new BaseRate(r.get(0).asInt(), r.get(1).asInt()));
                        }
                    }
                }
                case "expertness increase" -> {
                    for (var r : rows(tokens, i, 4, "[expertness increase]")) {
                        increases.add(new ExpIncrease(r.get(0).asInt(), r.get(1).asInt(),
                                r.get(2).asInt(), r.get(3).asInt()));
                    }
                }
                case "speciality class" -> {
                    for (var r : rows(tokens, i, 2, "[speciality class]")) {
                        speciality.put(r.get(0).text().trim().toLowerCase(Locale.ROOT),
                                r.get(1).asInt());
                    }
                }
                case "speciality bonus" -> {
                    var rows = rows(tokens, i, 2, "[speciality bonus]");
                    if (rows.size() != 1) {
                        throw new PvfFormatException(PATH
                                + " 的 [speciality bonus] 应该只有一行两个数，读到 "
                                + rows.size() + " 行");
                    }
                    rateBonus = rows.get(0).get(0).asInt();
                    expBonus = rows.get(0).get(1).asInt();
                }
                case "skill" -> {
                    for (var r : rows(tokens, i, 2, "[skill]")) {
                        skills.add(new int[]{r.get(0).asInt(), r.get(1).asInt()});
                    }
                }
                case "equipment rarity" -> equipmentRarity = doubles(tokens, i, "[equipment rarity]");
                case "monster card rarity" ->
                        cardRarity = doubles(tokens, i, "[monster card rarity]");
                default -> {
                }
            }
        }

        if (baseRates.isEmpty()) {
            throw new PvfFormatException(PATH + " 的 [magic enchant] 里没有 [expertness exp]");
        }
        if (equipmentRarity == null || equipmentRarity.length != RARITIES) {
            throw new PvfFormatException(PATH + " 的 [equipment rarity] 不是 " + RARITIES + " 档");
        }
        if (cardRarity == null || cardRarity.length != RARITIES) {
            throw new PvfFormatException(PATH + " 的 [monster card rarity] 不是 "
                    + RARITIES + " 档");
        }
        return new MagicEnchantScript(decreases, baseRates, increases, speciality,
                rateBonus, expBonus, skills, equipmentRarity, cardRarity);
    }

    public List<int[]> skills() {
        return skills;
    }

    public List<BaseRate> baseRates() {
        return baseRates;
    }

    private static double pick(double[] table, int index) {
        if (index < 0) {
            return table[0];
        }
        return index >= table.length ? table[table.length - 1] : table[index];
    }

    private static double[] doubles(List<ScriptReader.Token> tokens, int at, String section) {
        var out = new ArrayList<Double>();
        for (int i = at + 1; i < tokens.size(); i++) {
            var t = tokens.get(i);
            if (t.kind() == ScriptReader.Kind.SECTION
                    || t.kind() == ScriptReader.Kind.SECTION_END) {
                break;
            }
            out.add(t.asDouble());
        }
        if (out.isEmpty()) {
            throw new PvfFormatException(PATH + " 的 " + section + " 里没有数据");
        }
        double[] arr = new double[out.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = out.get(i);
        }
        return arr;
    }

    private static List<List<ScriptReader.Token>> rows(List<ScriptReader.Token> tokens,
                                                       int sectionAt, int width, String section) {
        List<List<ScriptReader.Token>> out = new ArrayList<>();
        List<ScriptReader.Token> row = new ArrayList<>(width);
        for (int i = sectionAt + 1; i < tokens.size(); i++) {
            var t = tokens.get(i);
            if (t.kind() == ScriptReader.Kind.SECTION
                    || t.kind() == ScriptReader.Kind.SECTION_END) {
                break;
            }
            row.add(t);
            if (row.size() == width) {
                out.add(List.copyOf(row));
                row.clear();
            }
        }
        if (!row.isEmpty()) {
            throw new PvfFormatException(PATH + " 的 " + section + " 末尾多出 " + row.size()
                    + " 个词，凑不满 " + width + " 列一行");
        }
        return out;
    }

    @Override
    public String toString() {
        return "MagicEnchantScript[成功率 " + baseRates.size() + " 档、等级减值 "
                + decreases.size() + " 档、熟练度增量 " + increases.size() + " 档、特长 "
                + specialityClass.size() + " 职业]";
    }
}
