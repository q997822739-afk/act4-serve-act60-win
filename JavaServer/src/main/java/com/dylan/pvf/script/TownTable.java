package com.dylan.pvf.script;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.dylan.pvf.PvfArchive;

public final class TownTable {

    public static final String LIST_PATH = "town/town.lst";

    public record Spawn(int town, int area, int x, int y) {}

    private final Map<Integer, TownScript> towns;

    private TownTable(Map<Integer, TownScript> towns) {
        this.towns = towns;
    }

    public static TownTable empty() {
        return new TownTable(Map.of());
    }

    public static TownTable of(TownScript... scripts) {
        Map<Integer, TownScript> parsed = new TreeMap<>();
        for (TownScript script : scripts) {
            parsed.put(script.townId(), script);
        }
        return new TownTable(Collections.unmodifiableMap(parsed));
    }

    public static TownTable load(PvfArchive archive) {
        ScriptList list = ScriptList.load(archive, LIST_PATH);
        Map<Integer, TownScript> parsed = new TreeMap<>();
        for (var entry : list.entries().entrySet()) {
            try {
                parsed.put(entry.getKey(),
                        TownScript.load(archive, entry.getKey(), entry.getValue()));
            } catch (RuntimeException e) {
            }
        }
        return new TownTable(Collections.unmodifiableMap(parsed));
    }

    public int size() {
        return towns.size();
    }

    public Map<Integer, TownScript> entries() {
        return towns;
    }

    public boolean contains(int townId) {
        return towns.containsKey(townId);
    }

    public Optional<TownScript> town(int townId) {
        return Optional.ofNullable(towns.get(townId));
    }

    public int villageCount() {
        return towns.size();
    }

    public Optional<TownScript.Area> area(int townId, int areaId) {
        return town(townId).flatMap(t -> t.area(areaId));
    }

    public boolean isPrivateArea(int townId, int areaId) {
        return area(townId, areaId).map(TownScript.Area::isGate).orElse(false);
    }

    public boolean checkValidArea(int townId, int areaId) {
        if (townId <= 0 || townId > villageCount()) {
            return false;
        }
        TownScript town = towns.get(townId);
        int areaCount = town == null ? 0 : town.areas().size();
        if (areaCount == 0) {
            return false;
        }
        if (areaId < 0 || areaId >= areaCount) {
            return false;
        }
        return true;
    }

    public Optional<Spawn> spawn(int townId) {
        return town(townId)
                .flatMap(TownScript::gate)
                .map(gate -> new Spawn(townId, gate.id(),
                        gate.param(0, 0), gate.param(1, 0)));
    }
}
