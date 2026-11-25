package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MonsterScript(int id, String path, String name, int level, boolean hellMonster,
                            List<List<DropEntry>> dropPools,
                            List<IndependentDrop> independentDrops,
                            List<DropEntry> deathTowerItems) {
    public static final String LIST_PATH = "monster/monster.lst";

    public static final List<String> POOL_SECTIONS = List.of(
            "item",
            "common champion drop item",
            "super champion drop item",
            "boss drop item");

    public static final int POOL_COUNT = 4;

    public record DropEntry(int itemId, int weight) {}

    public static final int DIFFICULTY_COUNT = 4;

    public static final int MEMBER_COUNT_SLOTS = 5;

    public static final int INDEPENDENT_DENOMINATOR = 1_000_000;

    public record IndependentDrop(int itemId, List<Integer> probByDifficulty,
                                  List<Integer> countByMemberCount, List<DropEntry> subPool) {
        public IndependentDrop {
            probByDifficulty = List.copyOf(probByDifficulty);
            countByMemberCount = List.copyOf(countByMemberCount);
            subPool = List.copyOf(subPool);
        }

        public int probFor(int dungeonDiff) {
            return dungeonDiff < 0 || dungeonDiff >= probByDifficulty.size()
                    ? 0 : probByDifficulty.get(dungeonDiff);
        }

        public int countFor(int memberCount) {
            return memberCount < 0 || memberCount >= countByMemberCount.size()
                    ? 0 : countByMemberCount.get(memberCount);
        }
    }

    public MonsterScript {
        List<List<DropEntry>> copy = new ArrayList<>(POOL_COUNT);
        for (int i = 0; i < POOL_COUNT; i++) {
            copy.add(i < dropPools.size() ? List.copyOf(dropPools.get(i)) : List.of());
        }
        dropPools = List.copyOf(copy);
        independentDrops = List.copyOf(independentDrops);
        deathTowerItems = List.copyOf(deathTowerItems);
    }

    public List<DropEntry> deathTowerItems() {
        return deathTowerItems;
    }

    public static final int NO_DROP = -2;

    public static int dropItemIndex(int roll, List<DropEntry> pool) {
        int sum = 0;
        for (DropEntry entry : pool) {
            sum += entry.weight();
            if (roll < sum) {
                return entry.itemId();
            }
        }
        return NO_DROP;
    }

    public List<DropEntry> dropPool(int role) {
        return role < 0 || role >= POOL_COUNT ? List.of() : dropPools.get(role);
    }

    public List<DropEntry> dropPool() {
        return dropPools.get(0);
    }

    private static final Pattern SECTION = Pattern.compile(
            "\\[(?<tag>[^\\]/][^\\]]*)\\](?<body>[\\s\\S]*?)\\[/\\k<tag>\\]");

    public static MonsterScript load(PvfArchive archive, int id, String path) {
        return parse(id, path, archive.readText(path));
    }

    public static MonsterScript parse(int id, String path, String text) {
        List<List<DropEntry>> pools = new ArrayList<>(POOL_COUNT);
        for (String tag : POOL_SECTIONS) {
            pools.add(pairs(section(text, tag)));
        }
        return new MonsterScript(id, path,
                firstToken(section(text, "name")),
                intOr(section(text, "level"), 1),
                hellMonster(text),
                pools,
                independentDrops(section(text, "independent drop")),
                pairs(section(text, "death tower item")));
    }

    static boolean hellMonster(String text) {
        Matcher m = HELL_MONSTER.matcher(text);
        return m.find() && Integer.parseInt(m.group(1)) != 0;
    }

    private static final Pattern HELL_MONSTER =
            Pattern.compile("\\[hell monster\\][^\\S\\n]*(-?\\d+)", Pattern.CASE_INSENSITIVE);

    private static final int INDEPENDENT_HEAD_COLUMNS =
            1 + DIFFICULTY_COUNT + MEMBER_COUNT_SLOTS + 1;

    private static final int INDEPENDENT_SUB_POOL_FLAG = INDEPENDENT_HEAD_COLUMNS - 1;

    private static final int INDEPENDENT_SUB_COLUMNS = 2;

    static List<IndependentDrop> independentDrops(String body) {
        List<int[]> heads = new ArrayList<>();
        List<List<DropEntry>> subPools = new ArrayList<>();
        for (String rawLine : body.split("\\R")) {
            int[] row = intRow(rawLine);
            if (row == null) {
                continue;
            }
            if (row.length == INDEPENDENT_SUB_COLUMNS) {
                if (!subPools.isEmpty() && row[0] > 0 && row[1] > 0) {
                    subPools.get(subPools.size() - 1).add(new DropEntry(row[0], row[1]));
                }
                continue;
            }
            if (row.length != INDEPENDENT_HEAD_COLUMNS) {
                continue;
            }
            heads.add(row);
            subPools.add(row[INDEPENDENT_SUB_POOL_FLAG] == 0 ? null : new ArrayList<>());
        }

        List<IndependentDrop> out = new ArrayList<>(heads.size());
        for (int i = 0; i < heads.size(); i++) {
            int[] row = heads.get(i);
            List<Integer> prob = new ArrayList<>(DIFFICULTY_COUNT);
            for (int d = 0; d < DIFFICULTY_COUNT; d++) {
                prob.add(row[1 + d]);
            }
            List<Integer> count = new ArrayList<>(MEMBER_COUNT_SLOTS);
            for (int m = 0; m < MEMBER_COUNT_SLOTS; m++) {
                count.add(row[1 + DIFFICULTY_COUNT + m]);
            }
            List<DropEntry> pool = subPools.get(i);
            out.add(new IndependentDrop(row[0], prob, count,
                    pool == null ? List.of() : pool));
        }
        return List.copyOf(out);
    }

    private static int[] intRow(String rawLine) {
        String line = rawLine.split("//", 2)[0].trim();
        if (line.isEmpty()) {
            return null;
        }
        String[] tokens = line.split("\\s+");
        int[] row = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                row[i] = Integer.parseInt(tokens[i]);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return row;
    }

    static String section(String text, String tag) {
        Matcher m = SECTION.matcher(text);
        while (m.find()) {
            if (m.group("tag").trim().equalsIgnoreCase(tag)) {
                return m.group("body");
            }
        }
        return "";
    }

    private static List<String> tokens(String body) {
        List<String> out = new ArrayList<>();
        for (String rawLine : body.split("\\R")) {
            String line = rawLine.split("//", 2)[0].trim();
            if (line.isEmpty()) {
                continue;
            }
            for (String token : line.split("\\s+")) {
                if (!token.isEmpty()) {
                    out.add(token);
                }
            }
        }
        return out;
    }

    static List<DropEntry> pairs(String body) {
        List<Integer> numbers = new ArrayList<>();
        for (String token : tokens(body)) {
            try {
                numbers.add(Integer.parseInt(token));
            } catch (NumberFormatException ignored) {
            }
        }
        List<DropEntry> out = new ArrayList<>();
        for (int i = 0; i + 1 < numbers.size(); i += 2) {
            int itemId = numbers.get(i);
            int weight = numbers.get(i + 1);
            if (itemId > 0 && weight > 0) {
                out.add(new DropEntry(itemId, weight));
            }
        }
        return out;
    }

    private static String firstToken(String body) {
        List<String> tokens = tokens(body);
        return tokens.isEmpty() ? "" : tokens.get(0).replace("`", "");
    }

    private static int intOr(String body, int fallback) {
        for (String token : tokens(body)) {
            try {
                return Integer.parseInt(token);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }
}
