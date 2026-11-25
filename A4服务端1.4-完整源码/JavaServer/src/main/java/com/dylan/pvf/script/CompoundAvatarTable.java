package com.dylan.pvf.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;
import com.dylan.protocol.body.EquipmentSlot;

public final class CompoundAvatarTable {
    public static final String LIST_PATH = "etc/compoundavatar.etc";

    public static final int PARTS = EquipmentSlot.SKIN_AVATAR + 1;

    public static final int JOBS = 6;

    public static final int JOB_NONE = JOBS;

    private static final Map<String, Integer> PART_NAMES = Map.of(
            "hat", 0,
            "hair", 1,
            "face", 2,
            "coat", 3,
            "pants", 4,
            "shoes", 5,
            "neck", 6,
            "belt", 7,
            "skin", 8);

    public record Entry(int itemId, int cumulative) {}

    public record Need(int itemId, int count) {}

    public static final class Grade {
        private final int grade;
        private int upperGrade;
        private final int[] rareRate = new int[PARTS];
        private final int[] upperRareRate = new int[PARTS];
        private final List<List<Entry>> rarePool = newPools();
        private final List<List<Entry>> normalPool = newPools();
        private final int[] rareTotal = new int[PARTS];
        private final int[] normalTotal = new int[PARTS];
        private final List<Need> material = new ArrayList<>();

        private Grade(int grade) {
            this.grade = grade;
        }

        private static List<List<Entry>> newPools() {
            List<List<Entry>> out = new ArrayList<>(PARTS);
            for (int i = 0; i < PARTS; i++) {
                out.add(List.of());
            }
            return out;
        }

        public int grade() {
            return grade;
        }

        public int upperGrade() {
            return upperGrade;
        }

        public int rareRate(int part) {
            return rareRate[part];
        }

        public int upperRareRate(int part) {
            return upperRareRate[part];
        }

        public List<Entry> rarePool(int part) {
            return rarePool.get(part);
        }

        public List<Entry> normalPool(int part) {
            return normalPool.get(part);
        }

        public int rareTotal(int part) {
            return rareTotal[part];
        }

        public int normalTotal(int part) {
            return normalTotal[part];
        }

        public List<Need> material() {
            return material;
        }
    }

    private final List<List<Grade>> byJob;

    private CompoundAvatarTable(List<List<Grade>> byJob) {
        this.byJob = byJob;
    }

    public Grade at(int job, int grade) {
        if (job < 0 || job >= byJob.size() || grade <= 0) {
            return null;
        }
        List<Grade> grades = byJob.get(job);
        if (grade >= grades.size()) {
            return null;
        }
        Grade row = grades.get(grade);
        return row != null && row.grade == grade ? row : null;
    }

    public int gradeUsing(int job, int materialItemId) {
        if (job < 0 || job >= byJob.size()) {
            return 0;
        }
        List<Grade> grades = byJob.get(job);
        for (int grade = 1; grade < grades.size(); grade++) {
            Grade row = at(job, grade);
            if (row == null) {
                continue;
            }
            for (Need need : row.material) {
                if (need.itemId() == materialItemId) {
                    return grade;
                }
            }
        }
        return 0;
    }

    public static CompoundAvatarTable load(PvfArchive archive) {
        ScriptList list = ScriptList.load(archive, LIST_PATH);
        List<List<Grade>> byJob = emptyJobs();
        for (Map.Entry<Integer, String> e : list.entries().entrySet()) {
            parseInto(byJob, e.getValue(), archive.readText(e.getValue()));
        }
        return new CompoundAvatarTable(byJob);
    }

    public static CompoundAvatarTable parse(Map<String, String> files) {
        List<List<Grade>> byJob = emptyJobs();
        files.forEach((path, text) -> parseInto(byJob, path, text));
        return new CompoundAvatarTable(byJob);
    }

    private static List<List<Grade>> emptyJobs() {
        List<List<Grade>> out = new ArrayList<>(JOBS);
        for (int i = 0; i < JOBS; i++) {
            out.add(new ArrayList<>());
        }
        return out;
    }

    private static void parseInto(List<List<Grade>> byJob, String path, String text) {
        List<ScriptReader.Token> tokens = ScriptReader.tokenize(text);
        List<Grade> grades = null;
        Grade row = null;

        for (int i = 0; i < tokens.size(); i++) {
            ScriptReader.Token token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            String name = token.text().toLowerCase(Locale.ROOT);
            switch (name) {
                case "character type" -> {
                    int job = jobOf(path, tokens, i);
                    grades = byJob.get(job);
                }
                case "grade" -> {
                    if (grades == null) {
                        throw new PvfFormatException(path + " 的 [grade] 出现在 [character type] 之前");
                    }
                    int grade = word(path, tokens, i + 1).asInt();
                    if (grade <= 0) {
                        throw new PvfFormatException(path + " 的 [grade] 是 " + grade + "，必须是正数");
                    }
                    while (grades.size() <= grade) {
                        grades.add(null);
                    }
                    row = new Grade(grade);
                    grades.set(grade, row);
                }
                case "rare rate" -> readRateByPart(path, tokens, i, need(path, row, name).rareRate);
                case "upper grade" ->
                        need(path, row, name).upperGrade = word(path, tokens, i + 1).asInt();
                case "upper rare rate" ->
                        readRateByPart(path, tokens, i, need(path, row, name).upperRareRate);
                case "material" -> readMaterial(path, tokens, i, need(path, row, name));
                default -> {
                    Integer part = partOfSection(name);
                    if (part != null) {
                        readPools(path, tokens, i, need(path, row, name), part);
                    }
                }
            }
        }
    }

    private static int jobOf(String path, List<ScriptReader.Token> tokens, int sectionAt) {
        if (sectionAt + 1 >= tokens.size()
                || tokens.get(sectionAt + 1).kind() != ScriptReader.Kind.STRING) {
            throw new PvfFormatException(path + " 的 [character type] 后面不是职业名");
        }
        String label = tokens.get(sectionAt + 1).text().trim().toLowerCase(Locale.ROOT);
        Integer job = ItemScript.JOB_NAMES.get(label);
        if (job == null) {
            throw new PvfFormatException(path + " 的 [character type] 是认不出的职业 `" + label + "`");
        }
        return job;
    }

    private static void readRateByPart(String path, List<ScriptReader.Token> tokens, int sectionAt,
                                       int[] out) {
        for (int k = sectionAt + 1; k + 1 < tokens.size(); k += 2) {
            if (tokens.get(k).kind() != ScriptReader.Kind.STRING
                    || tokens.get(k + 1).kind() != ScriptReader.Kind.WORD) {
                return;
            }
            out[partOf(path, tokens.get(k).text())] = tokens.get(k + 1).asInt();
        }
    }

    private static void readPools(String path, List<ScriptReader.Token> tokens, int sectionAt,
                                  Grade row, int part) {
        List<Integer> numbers = new ArrayList<>();
        for (int k = sectionAt + 1; k < tokens.size()
                && tokens.get(k).kind() == ScriptReader.Kind.WORD; k++) {
            numbers.add(tokens.get(k).asInt());
        }
        if (numbers.isEmpty()) {
            throw new PvfFormatException(path + " 的 [" + tokens.get(sectionAt).text() + "] 是空段");
        }
        int rareCount = numbers.get(0);
        int need = 1 + 2 * rareCount;
        if (rareCount < 0 || numbers.size() < need) {
            throw new PvfFormatException(path + " 的 [" + tokens.get(sectionAt).text() + "] 说有 "
                    + rareCount + " 条稀有装扮，段里只有 " + (numbers.size() - 1) + " 个数");
        }
        if ((numbers.size() - need) % 2 != 0) {
            throw new PvfFormatException(path + " 的 [" + tokens.get(sectionAt).text()
                    + "] 稀有池之后剩下 " + (numbers.size() - need)
                    + " 个数，不成对 —— ACT5 的部位段只有「稀有池 + 普通池」两块，"
                    + "别照官方 70 版当三块读");
        }

        List<Entry> rare = new ArrayList<>(rareCount);
        int acc = 0;
        for (int k = 0; k < rareCount; k++) {
            acc += numbers.get(2 + 2 * k);
            rare.add(new Entry(numbers.get(1 + 2 * k), acc));
        }
        row.rarePool.set(part, List.copyOf(rare));
        row.rareTotal[part] = acc;

        List<Entry> normal = new ArrayList<>();
        acc = 0;
        for (int k = need; k + 1 < numbers.size(); k += 2) {
            acc += numbers.get(k + 1);
            normal.add(new Entry(numbers.get(k), acc));
        }
        row.normalPool.set(part, List.copyOf(normal));
        row.normalTotal[part] = acc;
    }

    private static void readMaterial(String path, List<ScriptReader.Token> tokens, int sectionAt,
                                     Grade row) {
        List<Integer> numbers = new ArrayList<>();
        for (int k = sectionAt + 1; k < tokens.size()
                && tokens.get(k).kind() == ScriptReader.Kind.WORD; k++) {
            numbers.add(tokens.get(k).asInt());
        }
        if (numbers.isEmpty()) {
            throw new PvfFormatException(path + " 的 [material] 是空段");
        }
        for (int k = 1; k + 1 < numbers.size(); k += 2) {
            int itemId = numbers.get(k);
            int count = numbers.get(k + 1);
            if (count != 1) {
                throw new PvfFormatException(path + " 的 [material] 里 " + itemId + " 要 " + count
                        + " 个，官方只收 1");
            }
            if (itemId == 0 || itemId == 2) {
                throw new PvfFormatException(path + " 的 [material] 用了官方的货币编码 " + itemId
                        + "（0 = 金币、2 = 决斗胜点），这一版没有实现");
            }
            row.material.add(new Need(itemId, count));
        }
        if (row.material.isEmpty()) {
            throw new PvfFormatException(path + " 的 [material] 里一条材料都没有");
        }
    }

    private static Integer partOfSection(String sectionName) {
        if (!sectionName.endsWith(" avatar")) {
            return null;
        }
        return PART_NAMES.get(sectionName.substring(0, sectionName.length() - " avatar".length()));
    }

    private static int partOf(String path, String name) {
        Integer part = PART_NAMES.get(name.trim().toLowerCase(Locale.ROOT));
        if (part == null) {
            throw new PvfFormatException(path + " 里出现了认不出的装扮部位 `" + name
                    + "` —— 官方那张表里的 aurora 在 ACT5 是 [weapon]，不是装扮部位");
        }
        return part;
    }

    private static Grade need(String path, Grade row, String sectionName) {
        if (row == null) {
            throw new PvfFormatException(path + " 的 [" + sectionName + "] 出现在 [grade] 之前");
        }
        return row;
    }

    private static ScriptReader.Token word(String path, List<ScriptReader.Token> tokens, int at) {
        if (at >= tokens.size() || tokens.get(at).kind() != ScriptReader.Kind.WORD) {
            throw new PvfFormatException(path + " 里第 " + at + " 个词法单元处期望一个数");
        }
        return tokens.get(at);
    }
}
