package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DropInfoTable {
    public static final String PATH_MONSTER = "etc/itemdropinfo_monseter.etc";

    public static final String PATH_OBJECT = "etc/itemdropinfo_object.etc";

    public static final int DENOMINATOR = 10000;

    public static final int RARITY_COUNT = 6;

    /** A4 脚本只有 5 档稀有度（普通/高级/稀有/神器/史诗），比 ACT5 少一档。 */
    public static final int A4_RARITY_COUNT = 5;

    public static final int BONUS_COLUMNS = 4;

    public record Band(int minLevel, int maxLevel, int[] byType) {
        public int of(ItemDropType type) {
            return byType[type.ordinal()];
        }
    }

    public record LevelWindow(int minus, int plus) {
        public static final LevelWindow EXACT = new LevelWindow(0, 1);
    }

    private final List<Band> bands;
    private final Map<ItemDropType, int[]> rarityThresholds;
    private final double[][] partyBonus;
    private final double[][] difficultyBonus;
    private final double[][] monsterTypeBonus;
    private final Map<Integer, LevelWindow> levelWindows;

    private DropInfoTable(List<Band> bands, Map<ItemDropType, int[]> rarityThresholds,
                          double[][] partyBonus, double[][] difficultyBonus,
                          double[][] monsterTypeBonus, Map<Integer, LevelWindow> levelWindows) {
        this.bands = List.copyOf(bands);
        this.rarityThresholds = Map.copyOf(rarityThresholds);
        this.partyBonus = partyBonus;
        this.difficultyBonus = difficultyBonus;
        this.monsterTypeBonus = monsterTypeBonus;
        this.levelWindows = Map.copyOf(levelWindows);
    }

    public static DropInfoTable load(PvfArchive archive, String path) {
        return parse(archive.readText(path), path);
    }

    public static DropInfoTable parse(String text, String path) {
        Map<String, List<String>> sections = EtcSections.split(text);

        List<Band> bands = new ArrayList<>();
        for (String line : sections.getOrDefault("drop prob", List.of())) {
            int[] v = ints(path, line, 2 + ItemDropType.COUNT, "drop prob");
            int[] byType = new int[ItemDropType.COUNT];
            System.arraycopy(v, 2, byType, 0, ItemDropType.COUNT);
            bands.add(new Band(v[0], v[1], byType));
        }
        if (bands.isEmpty()) {
            throw new PvfFormatException(path + " 里没有 [drop prob] 段");
        }

        Map<ItemDropType, int[]> rarity = new EnumMap<>(ItemDropType.class);
        List<String> rarityLines = sections.getOrDefault("basis of rarity dicision", List.of());
        for (int i = 0; i < rarityLines.size() && i < ItemDropType.ITEMS.length; i++) {
            rarity.put(ItemDropType.ITEMS[i],
                    rarityRow(path, rarityLines.get(i), "basis of rarity dicision"));
        }
        if (rarity.size() != ItemDropType.ITEMS.length) {
            throw new PvfFormatException(path + " 的 [basis of rarity dicision] 只有 "
                    + rarity.size() + " 行，四类物品各要一行");
        }

        Map<Integer, LevelWindow> windows = new HashMap<>();
        for (String line : sections.getOrDefault("item drop ref table", List.of())) {
            int[] v = ints(path, line, 3, "item drop ref table");
            windows.put(v[0], new LevelWindow(v[1], v[2]));
        }
        if (windows.isEmpty()) {
            throw new PvfFormatException(path + " 里没有 [item drop ref table] 段，"
                    + "随机掉落抽不出任何物品");
        }

        return new DropInfoTable(bands, rarity,
                bonusTable(path, sections, "party member drop bonusrate"),
                bonusTable(path, sections, "dungeon difficulty drop bonusrate"),
                bonusTable(path, sections, "monster type drop bonusrate"),
                windows);
    }

    /** 稀有度阈值行：ACT5 6 列、A4 5 列，按实际列数取。掷骰按数组长度走，少一档就不会掷出第 6 档。 */
    static int[] rarityRow(String path, String line, String section) {
        int columns = EtcSections.tokens(line).length >= RARITY_COUNT ? RARITY_COUNT : A4_RARITY_COUNT;
        return ints(path, line, columns, section);
    }

    static int[] ints(String path, String line, int expected, String section) {
        String[] tokens = EtcSections.tokens(line);
        if (tokens.length < expected) {
            throw new PvfFormatException(path + " 的 [" + section + "] 里 \"" + line
                    + "\" 只有 " + tokens.length + " 列，要 " + expected + " 列");
        }
        int[] out = new int[expected];
        for (int i = 0; i < expected; i++) {
            try {
                out[i] = Integer.parseInt(tokens[i]);
            } catch (NumberFormatException e) {
                throw new PvfFormatException(path + " 的 [" + section + "] 里读到非整数 \""
                        + tokens[i] + "\"");
            }
        }
        return out;
    }

    private static double[][] bonusTable(String path, Map<String, List<String>> sections,
                                         String name) {
        double[][] out = new double[ItemDropType.COUNT][BONUS_COLUMNS];
        for (double[] row : out) {
            Arrays.fill(row, 1.0);
        }
        List<String> lines = sections.getOrDefault(name, List.of());
        for (int r = 0; r < lines.size() && r < out.length; r++) {
            String[] tokens = EtcSections.tokens(lines.get(r));
            for (int c = 0; c < BONUS_COLUMNS && c < tokens.length; c++) {
                try {
                    out[r][c] = Double.parseDouble(tokens[c]);
                } catch (NumberFormatException e) {
                    throw new PvfFormatException(path + " 的 [" + name + "] 里读到非小数 \""
                            + tokens[c] + "\"");
                }
            }
        }
        return out;
    }

    public Band bandFor(int dungeonLevel) {
        for (Band band : bands) {
            if (dungeonLevel >= band.minLevel() && dungeonLevel <= band.maxLevel()) {
                return band;
            }
        }
        return null;
    }

    public LevelWindow levelWindow(int level) {
        return levelWindows.getOrDefault(level, LevelWindow.EXACT);
    }

    public double partyBonus(ItemDropType type, int members) {
        return bonus(partyBonus, type, members - 1);
    }

    public double difficultyBonus(ItemDropType type, int difficulty) {
        return bonus(difficultyBonus, type, difficulty);
    }

    public double monsterTypeBonus(ItemDropType type, int roleType) {
        return bonus(monsterTypeBonus, type, roleType);
    }

    private static double bonus(double[][] table, ItemDropType type, int column) {
        if (column < 0 || column >= BONUS_COLUMNS) {
            return 1.0;
        }
        return table[type.ordinal()][column];
    }

    public int[] rarityThresholds(ItemDropType type) {
        int[] known = rarityThresholds.get(type);
        if (known == null) {
            throw new IllegalArgumentException(type + " 没有稀有度阈值行（只有四类物品有）");
        }
        return known;
    }
}
