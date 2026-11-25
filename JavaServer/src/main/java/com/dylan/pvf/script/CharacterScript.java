package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CharacterScript {

    public record InitialSkill(int skillId, int level) {}

    public record StatBlock(Map<String, Double> values) {

        public StatBlock {
            values = Map.copyOf(values);
        }

        public static final StatBlock EMPTY = new StatBlock(Map.of());

        public double get(String key) {
            return values.getOrDefault(key.toLowerCase(), 0.0);
        }

        public boolean has(String key) {
            return values.containsKey(key.toLowerCase());
        }

        public int size() {
            return values.size();
        }
    }

    public static final List<String> STAT_KEYS = List.of(
            "hp max", "mp max",
            "physical attack", "physical defense",
            "magical attack", "magical defense",
            "fire resistance", "water resistance",
            "dark resistance", "light resistance",
            "inventory limit",
            "hp regen speed", "mp regen speed",
            "move speed", "attack speed", "cast speed",
            "hit recovery", "jump power", "jump speed", "weight");

    public static final int MIN_GROWTYPE = 1;
    public static final int MAX_GROWTYPE = 5;

    public static final int MIN_AWAKENING = 1;
    public static final int MAX_AWAKENING = 2;

    public record AwakeningKey(int growtypeIndex, int index) {}

    private final int job;
    private final String path;
    private final List<InitialSkill> initialSkills;
    private final StatBlock initialValue;
    private final Map<Integer, StatBlock> growth;
    private final Map<AwakeningKey, StatBlock> awakening;
    private final Map<Integer, List<InitialSkill>> growtypeSkills;
    private final Map<AwakeningKey, List<InitialSkill>> awakeningSkills;

    private CharacterScript(int job, String path, List<InitialSkill> initialSkills,
                            StatBlock initialValue, Map<Integer, StatBlock> growth,
                            Map<AwakeningKey, StatBlock> awakening,
                            Map<Integer, List<InitialSkill>> growtypeSkills,
                            Map<AwakeningKey, List<InitialSkill>> awakeningSkills) {
        this.job = job;
        this.path = path;
        this.initialSkills = List.copyOf(initialSkills);
        this.initialValue = initialValue;
        this.growth = Map.copyOf(growth);
        this.awakening = Map.copyOf(awakening);
        this.growtypeSkills = Map.copyOf(growtypeSkills);
        this.awakeningSkills = Map.copyOf(awakeningSkills);
    }

    public static CharacterScript load(PvfArchive archive, int job, String path) {
        return parse(job, path, archive.readText(path));
    }

    public static CharacterScript parse(int job, String path, String text) {
        var tokens = ScriptReader.tokenize(text);

        int start = -1;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).isSection("initial value")) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return new CharacterScript(job, path, List.of(), StatBlock.EMPTY,
                    Map.of(), Map.of(), Map.of(), Map.of());
        }

        var stats = parseStats(tokens, start);
        var skills = parseSkillSections(tokens, start);
        return new CharacterScript(job, path,
                parseInitialSkills(tokens, start),
                stats.initial(), stats.growth(), stats.awakening(),
                skills.growtype(), skills.awakening());
    }

    private record SectionBreak(int growtype, int awakening) {

        static final SectionBreak NONE = new SectionBreak(-1, -1);

        boolean isBreak() {
            return growtype >= 0 || awakening >= 0;
        }
    }

    private static SectionBreak isSectionBreak(ScriptReader.Token token) {
        if (token.kind() != ScriptReader.Kind.SECTION) {
            return SectionBreak.NONE;
        }
        String name = token.text().toLowerCase();
        int growtype = numbered(name, "growtype ", MIN_GROWTYPE, MAX_GROWTYPE);
        if (growtype >= 0) {
            return new SectionBreak(growtype, -1);
        }
        int awakening = numbered(name, "awakening ", MIN_AWAKENING, MAX_AWAKENING);
        return awakening >= 0 ? new SectionBreak(-1, awakening) : SectionBreak.NONE;
    }

    private static int numbered(String name, String prefix, int min, int max) {
        if (!name.startsWith(prefix)) {
            return -1;
        }
        try {
            int n = Integer.parseInt(name.substring(prefix.length()).trim());
            return n >= min && n <= max ? n : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private record ParsedStats(StatBlock initial, Map<Integer, StatBlock> growth,
                               Map<AwakeningKey, StatBlock> awakening) {}

    private static ParsedStats parseStats(List<ScriptReader.Token> tokens, int start) {
        Set<String> keys = Set.copyOf(STAT_KEYS);
        var initial = new LinkedHashMap<String, Double>();
        var growth = new LinkedHashMap<Integer, LinkedHashMap<String, Double>>();
        var awakening = new LinkedHashMap<AwakeningKey, LinkedHashMap<String, Double>>();

        Map<String, Double> current = initial;
        int currentGrowtype = -1;
        for (int i = start + 1; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            var brk = isSectionBreak(token);
            if (brk.isBreak()) {
                if (brk.growtype() >= 0) {
                    currentGrowtype = brk.growtype();
                    current = growth.computeIfAbsent(currentGrowtype,
                            k -> new LinkedHashMap<>());
                } else {
                    current = awakening.computeIfAbsent(
                            new AwakeningKey(currentGrowtype, brk.awakening()),
                            k -> new LinkedHashMap<>());
                }
                continue;
            }
            String name = token.text().toLowerCase();
            if (!keys.contains(name) || i + 1 >= tokens.size()) {
                continue;
            }
            var value = tokens.get(i + 1);
            if (value.kind() != ScriptReader.Kind.WORD) {
                continue;
            }
            try {
                current.put(name, Double.parseDouble(value.text().trim()));
            } catch (NumberFormatException e) {
            }
        }

        var growthBlocks = new LinkedHashMap<Integer, StatBlock>();
        growth.forEach((k, v) -> growthBlocks.put(k, new StatBlock(v)));
        var awakeningBlocks = new LinkedHashMap<AwakeningKey, StatBlock>();
        awakening.forEach((k, v) -> awakeningBlocks.put(k, new StatBlock(v)));
        return new ParsedStats(new StatBlock(initial), growthBlocks, awakeningBlocks);
    }

    private static List<InitialSkill> parseInitialSkills(List<ScriptReader.Token> tokens,
                                                        int start) {
        for (int i = start + 1; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            if (isSectionBreak(token).isBreak()) {
                break;
            }
            if (token.isSection("skill")) {
                return readSkillPairs(tokens, i);
            }
        }
        return List.of();
    }

    private record ParsedSkills(Map<Integer, List<InitialSkill>> growtype,
                                Map<AwakeningKey, List<InitialSkill>> awakening) {}

    private static ParsedSkills parseSkillSections(List<ScriptReader.Token> tokens, int start) {
        var growtype = new LinkedHashMap<Integer, List<InitialSkill>>();
        var awakening = new LinkedHashMap<AwakeningKey, List<InitialSkill>>();
        int currentGrowtype = -1;
        int currentAwakening = -1;

        for (int i = start + 1; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            var brk = isSectionBreak(token);
            if (brk.isBreak()) {
                if (brk.growtype() >= 0) {
                    currentGrowtype = brk.growtype();
                    currentAwakening = -1;
                } else {
                    currentAwakening = brk.awakening();
                }
                continue;
            }
            if (currentGrowtype < 0) {
                continue;
            }
            if (token.isSection("skill") && currentAwakening < 0) {
                growtype.computeIfAbsent(currentGrowtype, k -> new ArrayList<>())
                        .addAll(readSkillPairs(tokens, i));
            } else if (token.isSection("awakening skill") && currentAwakening >= 0) {
                awakening.computeIfAbsent(
                                new AwakeningKey(currentGrowtype, currentAwakening),
                                k -> new ArrayList<>())
                        .addAll(readSkillPairs(tokens, i));
            }
        }

        var growtypeCopy = new LinkedHashMap<Integer, List<InitialSkill>>();
        growtype.forEach((k, v) -> growtypeCopy.put(k, List.copyOf(v)));
        var awakeningCopy = new LinkedHashMap<AwakeningKey, List<InitialSkill>>();
        awakening.forEach((k, v) -> awakeningCopy.put(k, List.copyOf(v)));
        return new ParsedSkills(growtypeCopy, awakeningCopy);
    }

    private static List<InitialSkill> readSkillPairs(List<ScriptReader.Token> tokens, int at) {
        List<InitialSkill> out = new ArrayList<>();
        for (int j = at + 1; j + 1 < tokens.size(); j += 2) {
            var idToken = tokens.get(j);
            var levelToken = tokens.get(j + 1);
            if (idToken.kind() != ScriptReader.Kind.WORD
                    || levelToken.kind() != ScriptReader.Kind.WORD) {
                break;
            }
            out.add(new InitialSkill(idToken.asInt(), levelToken.asInt()));
        }
        return out;
    }

    public int job() {
        return job;
    }

    public String path() {
        return path;
    }

    public List<InitialSkill> initialSkills() {
        return initialSkills;
    }

    public StatBlock initialValue() {
        return initialValue;
    }

    public StatBlock growth(int growtypeIndex) {
        return growth.getOrDefault(growtypeIndex, StatBlock.EMPTY);
    }

    public StatBlock growthForGrowType(int growType) {
        return growth(growType + 1);
    }

    public int growtypeCount() {
        return growth.size();
    }

    public StatBlock awakening(int growtypeIndex, int index) {
        return awakening.getOrDefault(new AwakeningKey(growtypeIndex, index), StatBlock.EMPTY);
    }

    public StatBlock awakeningForGrowType(int growType, int index) {
        return awakening(growType + 1, index);
    }

    public int awakeningCount() {
        return awakening.size();
    }

    public List<InitialSkill> growTypeSkills(int growType) {
        return growtypeSkills.getOrDefault(growType + 1, List.of());
    }

    public List<InitialSkill> awakeningSkills(int growtypeIndex, int index) {
        return awakeningSkills.getOrDefault(new AwakeningKey(growtypeIndex, index), List.of());
    }

    public List<InitialSkill> awakeningSkillsForGrowType(int growType, int index) {
        return awakeningSkills(growType + 1, index);
    }

    @Override
    public String toString() {
        return "职业 " + job + "（" + path + "，初始 " + initialValue.size() + " 项属性 / "
                + initialSkills.size() + " 个技能，" + growtypeCount() + " 条成长分支，"
                + awakeningCount() + " 个觉醒段）";
    }
}
