package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record TownScript(int townId, String path, List<Area> areas) {
    public record Area(int id, String mapPath, String type, List<Integer> params,
                       List<MapScript.TownPath> movableAreas, List<MapScript.NpcSpawn> npcs) {
        public static final String TYPE_GATE = "[gate]";

        public static final String TYPE_DUNGEON_GATE = "[dungeon gate]";

        public Area(int id, String mapPath, String type, List<Integer> params) {
            this(id, mapPath, type, params, List.of(), List.of());
        }

        public Area {
            params = List.copyOf(params);
            movableAreas = List.copyOf(movableAreas);
            npcs = List.copyOf(npcs);
        }

        public boolean isGate() {
            return TYPE_GATE.equalsIgnoreCase(type);
        }

        public boolean isDungeonGate() {
            return TYPE_DUNGEON_GATE.equalsIgnoreCase(type);
        }

        public int param(int index, int fallback) {
            return index >= 0 && index < params.size() ? params.get(index) : fallback;
        }

        public int worldMapIndex() {
            return isDungeonGate() ? param(0, NO_WORLD_MAP) : NO_WORLD_MAP;
        }

        public static final int NO_WORLD_MAP = -1;

        Area withMapData(List<MapScript.TownPath> paths, List<MapScript.NpcSpawn> spawns) {
            return new Area(id, mapPath, type, params, paths, spawns);
        }
    }

    public TownScript {
        areas = List.copyOf(areas);
    }

    public Optional<Area> gate() {
        return areas.stream().filter(Area::isGate).findFirst();
    }

    public Optional<Area> area(int areaId) {
        return areas.stream().filter(a -> a.id() == areaId).findFirst();
    }

    public static TownScript load(PvfArchive archive, int townId, String path) {
        String normalized = PvfPath.normalize(path);
        TownScript town = parse(townId, normalized, archive.readText(normalized));

        List<Area> withPaths = new ArrayList<>(town.areas().size());
        for (Area area : town.areas()) {
            MapScript map = loadMap(archive, townId, area);
            withPaths.add(map == null ? area
                    : area.withMapData(map.townPaths(), map.npcs()));
        }
        return new TownScript(townId, normalized, withPaths);
    }

    private static MapScript loadMap(PvfArchive archive, int townId, Area area) {
        if (area.mapPath() == null || area.mapPath().isBlank()) {
            return null;
        }
        String path = PvfPath.normalize(MAP_PREFIX + area.mapPath());
        try {
            return MapScript.load(archive, path);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final String MAP_PREFIX = "map/";

    public static TownScript parse(int townId, String normalized, String text) {
        var tokens = ScriptReader.tokenize(text);

        List<Area> areas = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).isSection("area")) {
                i = readArea(tokens, i + 1, areas);
            }
        }
        return new TownScript(townId, normalized, areas);
    }

    private static int readArea(List<ScriptReader.Token> tokens, int from, List<Area> out) {
        int id = -1;
        String mapPath = "";
        String type = "";
        List<Integer> params = new ArrayList<>();
        boolean inMinimap = false;

        int i = from;
        for (; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() == ScriptReader.Kind.SECTION
                    || token.kind() == ScriptReader.Kind.SECTION_END) {
                break;
            }
            if (token.kind() == ScriptReader.Kind.STRING) {
                String text = token.text().trim();
                if ("[minimap rect]".equalsIgnoreCase(text)) {
                    inMinimap = true;
                } else if ("[/minimap rect]".equalsIgnoreCase(text)) {
                    inMinimap = false;
                } else if (text.startsWith("[")) {
                    type = text;
                    params.clear();
                } else if (mapPath.isEmpty()) {
                    mapPath = text;
                }
                continue;
            }
            if (token.kind() != ScriptReader.Kind.WORD || inMinimap) {
                continue;
            }
            if (type.isEmpty() && id < 0) {
                id = token.asInt();
            } else if (!type.isEmpty()) {
                params.add(token.asInt());
            }
        }

        if (id >= 0) {
            out.add(new Area(id, mapPath, type, params));
        }
        return i;
    }
}
