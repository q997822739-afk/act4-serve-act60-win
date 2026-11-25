package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class WorldMapTable {

    private final Map<Integer, WorldMapScript> maps;

    private WorldMapTable(Map<Integer, WorldMapScript> maps) {
        this.maps = maps;
    }

    public static WorldMapTable empty() {
        return new WorldMapTable(Map.of());
    }

    public static WorldMapTable of(WorldMapScript... scripts) {
        Map<Integer, WorldMapScript> parsed = new TreeMap<>();
        for (WorldMapScript script : scripts) {
            parsed.put(script.index(), script);
        }
        return new WorldMapTable(Collections.unmodifiableMap(parsed));
    }

    public static WorldMapTable load(PvfArchive archive) {
        ScriptList list = ScriptList.load(archive, WorldMapScript.LIST_PATH);
        Map<Integer, WorldMapScript> parsed = new TreeMap<>();
        for (var entry : list.entries().entrySet()) {
            try {
                parsed.put(entry.getKey(),
                        WorldMapScript.load(archive, entry.getKey(), entry.getValue()));
            } catch (RuntimeException e) {
            }
        }
        return new WorldMapTable(Collections.unmodifiableMap(parsed));
    }

    public Map<Integer, WorldMapScript> entries() {
        return maps;
    }

    public int size() {
        return maps.size();
    }

    public Optional<WorldMapScript> byIndex(int index) {
        return Optional.ofNullable(maps.get(index));
    }

}
