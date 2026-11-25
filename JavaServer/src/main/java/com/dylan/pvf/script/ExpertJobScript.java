package com.dylan.pvf.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

public final class ExpertJobScript {
    public static final String LIST_PATH = "character/expertjob.lst";

    public static final int TYPE_NONE = 0;

    public static final int TYPE_ENCHANTER = 1;

    public static final int TYPE_DISJOINTER = 3;

    public static final int MAX_RECIPE_INDEX = 239;

    public static final int MAX_GIVEUP_COST_STEPS = 10;

    public static final int NO_RECIPE = -1;

    public record ExpertnessExp(int exp, int rate, String title) {
    }

    public record CompoundRate(int levelDiff, int rate, int expMin, int expMax) {
    }

    public record RecipeEntry(int index, int recipeItemId, int resultItemId, int level) {
    }

    private final int type;
    private final String path;
    private final List<ExpertnessExp> expertnessExp;
    private final List<int[]> skills;
    private final List<Integer> clearQuests;
    private final List<Integer> giveupCosts;
    private final List<CompoundRate> compoundRates;
    private final Map<Integer, RecipeEntry> byIndex;
    private final Map<Integer, RecipeEntry> byRecipeItemId;

    private ExpertJobScript(int type, String path, List<ExpertnessExp> expertnessExp,
                            List<int[]> skills, List<Integer> clearQuests,
                            List<Integer> giveupCosts, List<CompoundRate> compoundRates,
                            Map<Integer, RecipeEntry> byIndex,
                            Map<Integer, RecipeEntry> byRecipeItemId) {
        this.type = type;
        this.path = path;
        this.expertnessExp = List.copyOf(expertnessExp);
        this.skills = List.copyOf(skills);
        this.clearQuests = List.copyOf(clearQuests);
        this.giveupCosts = List.copyOf(giveupCosts);
        this.compoundRates = List.copyOf(compoundRates);
        this.byIndex = Map.copyOf(byIndex);
        this.byRecipeItemId = Map.copyOf(byRecipeItemId);
    }

    public static ExpertJobScript load(PvfArchive archive, int type, String path) {
        String normalized = com.dylan.pvf.PvfPath.normalize(path);
        return parse(type, normalized, archive.readText(normalized));
    }

    public static ExpertJobScript parse(int type, String path, String text) {
        var tokens = ScriptReader.tokenize(text);

        List<ExpertnessExp> expertness = new ArrayList<>();
        List<int[]> skills = new ArrayList<>();
        List<Integer> clearQuests = new ArrayList<>();
        List<Integer> giveupCosts = new ArrayList<>();
        List<CompoundRate> compoundRates = new ArrayList<>();
        Map<Integer, RecipeEntry> byIndex = new LinkedHashMap<>();
        Map<Integer, RecipeEntry> byRecipeItemId = new LinkedHashMap<>();

        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            switch (token.text().toLowerCase(Locale.ROOT)) {
                case "expertness exp" -> {
                    var rows = rows(tokens, i, 3, path, "[expertness exp]");
                    for (var r : rows) {
                        expertness.add(new ExpertnessExp(asInt(r.get(0)), asInt(r.get(1)),
                                r.get(2).text().trim()));
                    }
                }
                case "skill" -> {
                    for (var r : rows(tokens, i, 2, path, "[skill]")) {
                        skills.add(new int[]{asInt(r.get(0)), asInt(r.get(1))});
                    }
                }
                case "clear quests" -> {
                    for (var r : rows(tokens, i, 1, path, "[clear quests]")) {
                        clearQuests.add(asInt(r.get(0)));
                    }
                }
                case "giveup cost" -> {
                    for (var r : rows(tokens, i, 1, path, "[giveup cost]")) {
                        giveupCosts.add(asInt(r.get(0)));
                    }
                }
                case "compound rate" -> {
                    for (var r : rows(tokens, i, 4, path, "[compound rate]")) {
                        compoundRates.add(new CompoundRate(asInt(r.get(0)), asInt(r.get(1)),
                                asInt(r.get(2)), asInt(r.get(3))));
                    }
                }
                case "items" -> {
                    for (var r : rows(tokens, i, 4, path, "[items]")) {
                        int index = asInt(r.get(0));
                        if (index < 0 || index > MAX_RECIPE_INDEX) {
                            throw new PvfFormatException(path + " 的 [items] 里序号 " + index
                                    + " 超出 0.." + MAX_RECIPE_INDEX);
                        }
                        var entry = new RecipeEntry(index, asInt(r.get(1)), asInt(r.get(2)),
                                asInt(r.get(3)));
                        var dupIndex = byIndex.put(index, entry);
                        if (dupIndex != null) {
                            throw new PvfFormatException(path + " 的 [items] 里序号 " + index
                                    + " 出现了两次");
                        }
                        byRecipeItemId.putIfAbsent(entry.recipeItemId(), entry);
                    }
                }
                default -> {
                }
            }
        }

        if (giveupCosts.size() > MAX_GIVEUP_COST_STEPS) {
            throw new PvfFormatException(path + " 的 [giveup cost] 有 " + giveupCosts.size()
                    + " 档，官方上限是 " + MAX_GIVEUP_COST_STEPS);
        }
        return new ExpertJobScript(type, path, expertness, skills, clearQuests, giveupCosts,
                compoundRates, byIndex, byRecipeItemId);
    }

    /**
     * A4 没有 {@code character/expertjob.lst} 和每个副职业的 .exj，唯一的副职业「附魔师」写在
     * {@code character/expertjob.etc} 的 {@code [magic enchant]} 里（{@code [skill] 191 1}、
     * {@code [expertness exp]} 7 档「熟练度 成功率」）。任务 2702 {@code [reward type] `[expert job]` 1}。
     * 官方 A4 实抓交 2702：NOTI 19 技能表多出 191 Lv1（{@code _tmp} expert_off 探针，2026-10-28）。
     * 没有配方、放弃费用和完成任务清单。
     */
    public static ExpertJobScript fromMagicEnchant(MagicEnchantScript enchant) {
        List<ExpertnessExp> expertness = new ArrayList<>();
        for (var r : enchant.baseRates()) {
            expertness.add(new ExpertnessExp(r.exp(), r.rate(), ""));
        }
        return new ExpertJobScript(TYPE_ENCHANTER, MagicEnchantScript.PATH, expertness,
                enchant.skills(), List.of(), List.of(), List.of(), Map.of(), Map.of());
    }

    public int type() {
        return type;
    }

    public String path() {
        return path;
    }

    public int level(int exp) {
        for (int i = 0; i < expertnessExp.size(); i++) {
            if (expertnessExp.get(i).exp() > exp) {
                return i + 1;
            }
        }
        return expertnessExp.size();
    }

    public int maxExp() {
        return expertnessExp.isEmpty() ? 0 : expertnessExp.get(expertnessExp.size() - 1).exp();
    }

    public boolean isBoundaryExp(int exp) {
        for (var e : expertnessExp) {
            if (e.exp() == exp) {
                return true;
            }
        }
        return false;
    }

    public int expertnessExpAt(int index) {
        if (index < 0 || index >= expertnessExp.size()) {
            return -1;
        }
        return expertnessExp.get(index).exp();
    }

    public List<int[]> skills() {
        return skills;
    }

    public List<Integer> clearQuests() {
        return clearQuests;
    }

    public int giveupCost(int giveupCount) {
        if (giveupCount < 0 || giveupCount >= giveupCosts.size()) {
            return NO_GIVEUP_COST;
        }
        return giveupCosts.get(giveupCount);
    }

    public static final int NO_GIVEUP_COST = -1;

    public int maxGiveupCount() {
        return Math.max(0, giveupCosts.size() - 1);
    }

    public CompoundRate compoundRate(int levelDiff) {
        if (compoundRates.isEmpty()) {
            return null;
        }
        for (var e : compoundRates) {
            if (e.levelDiff() >= levelDiff) {
                return e;
            }
        }
        return compoundRates.get(compoundRates.size() - 1);
    }

    public int recipeIndex(int recipeItemId) {
        var e = byRecipeItemId.get(recipeItemId);
        return e == null ? NO_RECIPE : e.index();
    }

    public RecipeEntry recipe(int index) {
        return byIndex.get(index);
    }

    public RecipeEntry recipeByItemId(int recipeItemId) {
        return byRecipeItemId.get(recipeItemId);
    }

    public java.util.Collection<RecipeEntry> recipes() {
        return byIndex.values();
    }

    public boolean canLearn(RecipeEntry entry, int expertJobLevel) {
        return entry != null && entry.level() <= expertJobLevel + 2;
    }

    private static int asInt(ScriptReader.Token t) {
        return t.asInt();
    }

    private static List<List<ScriptReader.Token>> rows(List<ScriptReader.Token> tokens,
                                                       int sectionAt, int width,
                                                       String path, String section) {
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
            throw new PvfFormatException(path + " 的 " + section + " 末尾多出 " + row.size()
                    + " 个词，凑不满 " + width + " 列一行");
        }
        return out;
    }

    @Override
    public String toString() {
        return "ExpertJobScript[" + path + " type=" + type
                + " 熟练度 " + expertnessExp.size() + " 档"
                + "、成功率 " + compoundRates.size() + " 档"
                + "、配方 " + byIndex.size() + " 条]";
    }
}
