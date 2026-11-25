package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfPath;

import java.util.ArrayList;
import java.util.List;

public record WorldMapScript(int index, String path, List<Dungeon> dungeons,
                             boolean hellDungeon, List<Integer> hellQuests,
                             List<ItemCondition> itemConditions,
                             List<FreepassItem> freepassItems) {
    public static final String LIST_PATH = "worldmap/worldmap.lst";

    public record Dungeon(int dungeonIndex, int questIndex, boolean inProgress) {
    }

    public record FreepassItem(int itemId, int count) {
    }

    public record ItemCondition(int itemId, int count) {
        @Override
        public String toString() {
            return itemId + "×" + count;
        }
    }

    public WorldMapScript {
        dungeons = List.copyOf(dungeons);
        hellQuests = List.copyOf(hellQuests);
        itemConditions = List.copyOf(itemConditions);
        freepassItems = List.copyOf(freepassItems);
    }

    public static WorldMapScript load(PvfArchive archive, int index, String path) {
        String normalized = PvfPath.normalize(path);
        return parse(index, normalized, archive.readText(normalized));
    }

    public static WorldMapScript parse(int index, String path, String text) {
        var tokens = ScriptReader.tokenize(text);

        List<Dungeon> dungeons = new ArrayList<>();
        boolean hellDungeon = false;
        List<Integer> hellQuests = new ArrayList<>();
        List<ItemCondition> itemConditions = new ArrayList<>();
        List<FreepassItem> freepass = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            switch (token.text().toLowerCase()) {
                case "dungeon" -> readDungeons(tokens, i, dungeons);
                case "hell dungeon" -> hellDungeon = nextInt(tokens, i, 0) != 0;
                case "hell quest" -> readInts(tokens, i, hellQuests);
                case "item condition" -> readItemConditions(tokens, i, itemConditions);
                case "hell freepass item" -> readFreepass(tokens, i, freepass);
                default -> {
                }
            }
        }
        return new WorldMapScript(index, path, dungeons, hellDungeon,
                hellQuests, itemConditions, freepass);
    }

    public boolean isInHellDungeon() {
        return hellDungeon;
    }

    private static void readDungeons(List<ScriptReader.Token> tokens, int at,
                                     List<Dungeon> out) {
        int i = at + 1;
        while (i < tokens.size()) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.WORD) {
                return;
            }
            int dungeonIndex = token.asInt();
            i++;
            boolean inProgress = false;
            if (i < tokens.size() && tokens.get(i).isSection("in progress")) {
                inProgress = true;
                i++;
            }
            if (i >= tokens.size() || tokens.get(i).kind() != ScriptReader.Kind.WORD) {
                return;
            }
            out.add(new Dungeon(dungeonIndex, tokens.get(i).asInt(), inProgress));
            i++;
        }
    }

    private static void readItemConditions(List<ScriptReader.Token> tokens, int at,
                                           List<ItemCondition> out) {
        int i = at + 1;
        while (i + 1 < tokens.size()
                && tokens.get(i).kind() == ScriptReader.Kind.WORD
                && tokens.get(i + 1).kind() == ScriptReader.Kind.WORD) {
            out.add(new ItemCondition(tokens.get(i).asInt(), tokens.get(i + 1).asInt()));
            i += 2;
        }
    }

    private static void readFreepass(List<ScriptReader.Token> tokens, int at,
                                     List<FreepassItem> out) {
        int i = at + 1;
        while (i + 1 < tokens.size()
                && tokens.get(i).kind() == ScriptReader.Kind.WORD
                && tokens.get(i + 1).kind() == ScriptReader.Kind.WORD) {
            out.add(new FreepassItem(tokens.get(i).asInt(), tokens.get(i + 1).asInt()));
            i += 2;
        }
    }

    private static void readInts(List<ScriptReader.Token> tokens, int at, List<Integer> out) {
        for (int i = at + 1; i < tokens.size()
                && tokens.get(i).kind() == ScriptReader.Kind.WORD; i++) {
            out.add(tokens.get(i).asInt());
        }
    }

    private static int nextInt(List<ScriptReader.Token> tokens, int at, int fallback) {
        int i = at + 1;
        return i < tokens.size() && tokens.get(i).kind() == ScriptReader.Kind.WORD
                ? tokens.get(i).asInt() : fallback;
    }
}
