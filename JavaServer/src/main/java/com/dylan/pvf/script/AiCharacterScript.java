package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record AiCharacterScript(int id, String path, String name, int level,
                                int appearancePoint, boolean hellMonster,
                                List<MonsterScript.IndependentDrop> independentDrops,
                                List<MonsterScript.DropEntry> deathTowerItems,
                                int pvpWinPointMin, int pvpWinPointMax) {
    public static final String LIST_PATH = "aicharacter/aicharacter.lst";

    public AiCharacterScript {
        independentDrops = List.copyOf(independentDrops);
        deathTowerItems = List.copyOf(deathTowerItems);
    }

    public AiCharacterScript(int id, String path, String name, int level,
                             int appearancePoint, boolean hellMonster,
                             List<MonsterScript.IndependentDrop> independentDrops,
                             List<MonsterScript.DropEntry> deathTowerItems) {
        this(id, path, name, level, appearancePoint, hellMonster,
                independentDrops, deathTowerItems, 0, 0);
    }

    public List<MonsterScript.DropEntry> deathTowerItems() {
        return deathTowerItems;
    }

    public boolean canAppearRandomly() {
        return appearancePoint > 0;
    }

    public static AiCharacterScript load(PvfArchive archive, int id, String path) {
        return parse(id, path, archive.readText(path));
    }

    public static AiCharacterScript parse(int id, String path, String text) {
        return new AiCharacterScript(id, path,
                aiCharacterName(text),
                level(text),
                appearancePoint(text),
                MonsterScript.hellMonster(text),
                MonsterScript.independentDrops(
                        MonsterScript.section(text, "independent drop")),
                MonsterScript.pairs(MonsterScript.section(text, "death tower item")),
                pvpWinPointAt(text, 0),
                pvpWinPointAt(text, 1));
    }

    private static int pvpWinPointAt(String text, int index) {
        Matcher m = PVP_WIN_POINT.matcher(text);
        if (!m.find()) {
            return 0;
        }
        String[] tokens = m.group(1).trim().split("\\s+");
        if (index >= tokens.length) {
            return 0;
        }
        try {
            return Integer.parseInt(tokens[index]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static final Pattern PVP_WIN_POINT =
            Pattern.compile("\\[pvp win point\\]([^\\[\\r\\n]*)", Pattern.CASE_INSENSITIVE);

    public int rollPvpWinPoint(java.util.random.RandomGenerator random) {
        if (pvpWinPointMin == pvpWinPointMax) {
            return Math.max(0, pvpWinPointMin);
        }
        int min = Math.min(pvpWinPointMin, pvpWinPointMax);
        int max = Math.max(pvpWinPointMin, pvpWinPointMax);
        return Math.max(0, min + random.nextInt(max - min + 1));
    }

    private static final Pattern NAME =
            Pattern.compile("\\[minimum info\\]\\s*(\\S+)", Pattern.CASE_INSENSITIVE);

    private static String aiCharacterName(String text) {
        Matcher m = NAME.matcher(text);
        return m.find() ? m.group(1) : "";
    }

    private static int level(String text) {
        List<Integer> numbers = minimumInfoNumbers(text);
        return numbers.size() >= LEVEL_AT ? numbers.get(LEVEL_AT - 1) : 0;
    }

    private static final int LEVEL_AT = 5;

    private static List<Integer> minimumInfoNumbers(String text) {
        Matcher m = MINIMUM_INFO_BODY.matcher(text);
        if (!m.find()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        for (String line : m.group(1).split("\\R")) {
            int comment = line.indexOf("//");
            String body = comment >= 0 ? line.substring(0, comment) : line;
            for (String word : body.trim().split("\\s+")) {
                try {
                    out.add(Integer.valueOf(word.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    private static final Pattern MINIMUM_INFO_BODY =
            Pattern.compile("\\[minimum info\\]([^\\[]*)", Pattern.CASE_INSENSITIVE);

    private static int appearancePoint(String text) {
        Matcher m = APPEARANCE_POINT.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static final Pattern APPEARANCE_POINT =
            Pattern.compile("\\[appearance point\\]\\s*(-?\\d+)", Pattern.CASE_INSENSITIVE);
}
