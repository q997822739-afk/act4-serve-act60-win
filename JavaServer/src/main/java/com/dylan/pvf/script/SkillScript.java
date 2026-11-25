package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

import java.util.ArrayList;
import java.util.List;

public final class SkillScript {
    public static final int LIFE_SKILL_CLASS = 4;

    public static final int MAX_SKILL_CLASS = LIFE_SKILL_CLASS;

    public static final int NO_GROUP = -1;

    private final int job;
    private final int id;
    private final String path;
    private final boolean active;
    private final int skillClass;
    private final List<Integer> fitnessGrowTypes;
    private final int maximumLevel;
    private final int requiredLevel;
    private final int requiredLevelRange;
    private final List<Integer> growTypeMaximumLevel;
    private final List<Integer> secondGrowTypeMaximumLevel;
    private final List<Integer> purchaseCost;
    private final List<Integer> purchaseGsp;
    private final List<Integer> purchaseGold;
    private final List<Integer> levelInfo;
    private final List<PreRequisite> preRequiredSkills;

    private SkillScript(int job, int id, String path, boolean active, int skillClass,
                        List<Integer> fitnessGrowTypes, int maximumLevel,
                        int requiredLevel, int requiredLevelRange,
                        List<Integer> growTypeMaximumLevel,
                        List<Integer> secondGrowTypeMaximumLevel, List<Integer> purchaseCost,
                        List<Integer> purchaseGsp, List<Integer> purchaseGold,
                        List<Integer> levelInfo,
                        List<PreRequisite> preRequiredSkills) {
        this.job = job;
        this.id = id;
        this.path = path;
        this.active = active;
        this.skillClass = skillClass;
        this.fitnessGrowTypes = List.copyOf(fitnessGrowTypes);
        this.maximumLevel = maximumLevel;
        this.requiredLevel = requiredLevel;
        this.requiredLevelRange = requiredLevelRange;
        this.growTypeMaximumLevel = List.copyOf(growTypeMaximumLevel);
        this.secondGrowTypeMaximumLevel = List.copyOf(secondGrowTypeMaximumLevel);
        this.purchaseCost = List.copyOf(purchaseCost);
        this.purchaseGsp = List.copyOf(purchaseGsp);
        this.purchaseGold = List.copyOf(purchaseGold);
        this.levelInfo = List.copyOf(levelInfo);
        this.preRequiredSkills = List.copyOf(preRequiredSkills);
    }

    public record PreRequisite(int skillId, int level) {
    }

    public static SkillScript load(PvfArchive archive, int job, int id, String path) {
        return parse(job, id, path, archive.readText(path));
    }

    public static SkillScript parse(int job, int id, String path, String text) {
        var tokens = ScriptReader.tokenize(text);

        boolean active = false;
        int skillClass = NO_GROUP;
        List<Integer> fitnessGrowTypes = new ArrayList<>();
        int maximumLevel = 1;
        int requiredLevel = 1;
        int requiredLevelRange = 0;
        List<Integer> growTypeMaximumLevel = new ArrayList<>();
        List<Integer> secondGrowTypeMaximumLevel = new ArrayList<>();
        List<Integer> purchaseCost = new ArrayList<>();
        List<Integer> purchaseGsp = new ArrayList<>();
        List<Integer> purchaseGold = new ArrayList<>();
        List<Integer> levelInfo = new ArrayList<>();
        List<PreRequisite> preRequiredSkills = new ArrayList<>();

        boolean guildSkill = hasSection(tokens, "purchase gsp");

        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            if (token.isSection("type")) {
                var value = valueAfter(tokens, i);
                active = value != null && value.text().toLowerCase().contains("active");
            } else if (token.isSection("skill class")) {
                skillClass = intAfter(tokens, i, skillClass);
            } else if (token.isSection("skill fitness growtype")) {
                fitnessGrowTypes.addAll(wordsAfter(tokens, i));
            } else if (token.isSection("maximum level")) {
                maximumLevel = intAfter(tokens, i, maximumLevel);
            } else if (token.isSection("required level")) {
                requiredLevel = intAfter(tokens, i, requiredLevel);
            } else if (token.isSection("required level range")) {
                requiredLevelRange = intAfter(tokens, i, requiredLevelRange);
            } else if (token.isSection("growtype maximum level")) {
                growTypeMaximumLevel.addAll(wordsAfter(tokens, i));
            } else if (token.isSection("second growtype maximum level")) {
                secondGrowTypeMaximumLevel.addAll(wordsAfter(tokens, i));
            } else if (token.isSection("purchase cost")) {
                purchaseCost.addAll(wordsAfter(tokens, i));
            } else if (token.isSection("purchase gsp")) {
                purchaseGsp.addAll(wordsAfter(tokens, i));
            } else if (token.isSection("purchase gold")) {
                purchaseGold.addAll(wordsAfter(tokens, i));
            } else if (guildSkill && token.isSection("level info")) {
                levelInfo.addAll(wordsAfter(tokens, i));
            } else if (token.isSection("pre required skill")) {
                var flat = wordsAfter(tokens, i);
                for (int j = 0; j + 1 < flat.size(); j += 2) {
                    preRequiredSkills.add(new PreRequisite(flat.get(j), flat.get(j + 1)));
                }
            }
        }

        return new SkillScript(job, id, path, active, skillClass, fitnessGrowTypes,
                maximumLevel, requiredLevel, requiredLevelRange, growTypeMaximumLevel,
                secondGrowTypeMaximumLevel, purchaseCost, purchaseGsp, purchaseGold,
                levelInfo, preRequiredSkills);
    }

    private static boolean hasSection(List<ScriptReader.Token> tokens, String name) {
        for (var token : tokens) {
            if (token.kind() == ScriptReader.Kind.SECTION && token.isSection(name)) {
                return true;
            }
        }
        return false;
    }

    private static List<Integer> wordsAfter(List<ScriptReader.Token> tokens, int i) {
        List<Integer> out = new ArrayList<>();
        for (int j = i + 1; j < tokens.size(); j++) {
            var word = tokens.get(j);
            if (word.kind() != ScriptReader.Kind.WORD) {
                break;
            }
            out.add(word.asInt());
        }
        return out;
    }

    private static ScriptReader.Token valueAfter(List<ScriptReader.Token> tokens, int i) {
        if (i + 1 >= tokens.size()) {
            return null;
        }
        var next = tokens.get(i + 1);
        return next.kind() == ScriptReader.Kind.WORD || next.kind() == ScriptReader.Kind.STRING
                ? next : null;
    }

    private static int intAfter(List<ScriptReader.Token> tokens, int i, int fallback) {
        var value = valueAfter(tokens, i);
        if (value == null || value.kind() != ScriptReader.Kind.WORD) {
            return fallback;
        }
        try {
            return value.asInt();
        } catch (PvfFormatException e) {
            return fallback;
        }
    }

    public int job() {
        return job;
    }

    public int id() {
        return id;
    }

    public String path() {
        return path;
    }

    public boolean isActive() {
        return active;
    }

    public List<Integer> fitnessGrowTypes() {
        return fitnessGrowTypes;
    }

    public int slotGroup() {
        if (skillClass < 0 || skillClass > MAX_SKILL_CLASS) {
            return NO_GROUP;
        }
        return skillClass;
    }

    public int maximumLevel() {
        return maximumLevel;
    }

    public List<PreRequisite> preRequiredSkills() {
        return preRequiredSkills;
    }

    public int requiredLevelFor(int level) {
        return requiredLevel + Math.max(level - 1, 0) * requiredLevelRange;
    }

    public int typeMaxLevel(int growType) {
        if (growType < 0 || growType >= growTypeMaximumLevel.size()) {
            return 0;
        }
        return growTypeMaximumLevel.get(growType);
    }

    public int secondTypeMaxLevel(int firstGrow, int secondGrow) {
        if (firstGrow < 0 || secondGrow <= 0 || secondGrow > 2) {
            return 0;
        }
        int index = 2 * firstGrow + (secondGrow - 1);
        if (index >= secondGrowTypeMaximumLevel.size()) {
            return 0;
        }
        return secondGrowTypeMaximumLevel.get(index);
    }

    public boolean canLearn(int characLevel, int growType, int targetLevel) {
        return canLearn(characLevel, growType, 0, targetLevel);
    }

    public boolean canLearn(int characLevel, int firstGrow, int secondGrow, int targetLevel) {
        int maxLevel = maxLevelFor(firstGrow, secondGrow);
        if (targetLevel <= 0 || maxLevel < targetLevel) {
            return false;
        }
        int reachable = Math.min(maxLevel, characLevel - requiredLevel + 1);
        return reachable >= targetLevel && requiredLevelFor(targetLevel) <= characLevel;
    }

    public int maxLevelFor(int firstGrow, int secondGrow) {
        if (secondGrow > 0) {
            int max = secondTypeMaxLevel(firstGrow, secondGrow);
            if (max != 0) {
                return max;
            }
        }
        return typeMaxLevel(firstGrow);
    }

    public int needSpForLevel(int fromLevel, int toLevel) {
        if (purchaseCost.isEmpty() || fromLevel < 0 || toLevel <= fromLevel) {
            return -1;
        }
        int sum = 0;
        for (int level = fromLevel + 1; level <= toLevel; level++) {
            sum += costAtLevel(level);
        }
        return sum;
    }

    private int costAtLevel(int level) {
        return purchaseCost.get(Math.min(level, purchaseCost.size()) - 1);
    }

    @Override
    public String toString() {
        return "技能 " + id + "（职业 " + job + "，" + (active ? "主动" : "被动")
                + "，最高 " + maximumLevel + " 级，" + path + "）";
    }
}
