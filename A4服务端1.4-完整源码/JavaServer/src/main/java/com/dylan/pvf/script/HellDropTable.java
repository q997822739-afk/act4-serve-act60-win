package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HellDropTable {
    public static final String PATH = "etc/itemdropinfo_monster_hell.etc";

    public static final int PROB_DENOMINATOR = 1000;

    public static final int DIFFICULTY_SLOTS = 4;

    public record Band(int minLevel, int maxLevel, int[] byDifficulty) {
        public int of(int dungeonDiff) {
            return dungeonDiff < 0 || dungeonDiff >= byDifficulty.length
                    ? 0 : byDifficulty[dungeonDiff];
        }
    }

    private final List<Band> bands;
    private final int[][] rarityThresholds;
    private final Map<Integer, DropInfoTable.LevelWindow> levelWindows;

    private HellDropTable(List<Band> bands, int[][] rarityThresholds,
                          Map<Integer, DropInfoTable.LevelWindow> levelWindows) {
        this.bands = List.copyOf(bands);
        this.rarityThresholds = rarityThresholds;
        this.levelWindows = Map.copyOf(levelWindows);
    }

    public static HellDropTable load(PvfArchive archive) {
        return parse(archive.readText(PATH), PATH);
    }

    public static HellDropTable parse(String text, String path) {
        Map<String, List<String>> sections = EtcSections.split(text);

        List<Band> bands = new ArrayList<>();
        for (String line : sections.getOrDefault("dungeon difficulty drop prob", List.of())) {
            int[] v = DropInfoTable.ints(path, line, 2 + DropInfoTable.BONUS_COLUMNS,
                    "dungeon difficulty drop prob");
            int[] byDifficulty = new int[DropInfoTable.BONUS_COLUMNS];
            System.arraycopy(v, 2, byDifficulty, 0, DropInfoTable.BONUS_COLUMNS);
            bands.add(new Band(v[0], v[1], byDifficulty));
        }
        if (bands.isEmpty()) {
            throw new PvfFormatException(path + " 里没有 [dungeon difficulty drop prob] 段，"
                    + "深渊怪一件都掉不出来");
        }

        int[][] rarity = new int[DIFFICULTY_SLOTS][];
        List<String> rarityLines = new ArrayList<>(
                sections.getOrDefault("basis of rarity dicision", List.of()));
        if (!rarityLines.isEmpty()
                && EtcSections.tokens(rarityLines.get(0)).length == 1) {
            rarityLines.remove(0);
        }
        for (int i = 0; i < rarityLines.size() && i + 1 < DIFFICULTY_SLOTS; i++) {
            rarity[i + 1] = DropInfoTable.rarityRow(path, rarityLines.get(i),
                    "basis of rarity dicision");
        }
        // A4 只有一行，不分 A/B/C 三档难度：三档共用这一行。
        if (rarityLines.size() == 1) {
            for (int d = 2; d < DIFFICULTY_SLOTS; d++) {
                rarity[d] = rarity[1];
            }
        }
        for (int d = 1; d < DIFFICULTY_SLOTS; d++) {
            if (rarity[d] == null) {
                throw new PvfFormatException(path + " 的 [basis of rarity dicision] 缺难度 "
                        + d + " 那一行，三档 A/B/C 各要一行");
            }
        }

        Map<Integer, DropInfoTable.LevelWindow> windows = new HashMap<>();
        for (String line : sections.getOrDefault("item drop ref table", List.of())) {
            int[] v = DropInfoTable.ints(path, line, 3, "item drop ref table");
            windows.put(v[0], new DropInfoTable.LevelWindow(v[1], v[2]));
        }
        if (windows.isEmpty()) {
            throw new PvfFormatException(path + " 里没有 [item drop ref table] 段，"
                    + "深渊掉落池建不起来");
        }

        return new HellDropTable(bands, rarity, windows);
    }

    public Band bandFor(int dungeonLevel) {
        for (Band band : bands) {
            if (dungeonLevel >= band.minLevel() && dungeonLevel <= band.maxLevel()) {
                return band;
            }
        }
        return null;
    }

    public int[] rarityThresholds(int hellDifficulty) {
        if (hellDifficulty < 1 || hellDifficulty >= DIFFICULTY_SLOTS) {
            return null;
        }
        return rarityThresholds[hellDifficulty];
    }

    public DropInfoTable.LevelWindow levelWindow(int level) {
        return levelWindows.getOrDefault(level, DropInfoTable.LevelWindow.EXACT);
    }
}
