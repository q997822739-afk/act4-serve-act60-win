package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class CreatureScriptTable {

    public static final String EXP_TABLE_PATH = "creature/exptable.tbl";

    private final Map<Integer, CreatureScript> scripts;
    private final Map<Integer, Integer> itemIdByCreatureId;
    private final ExpTable expTable;

    private CreatureScriptTable(Map<Integer, CreatureScript> scripts,
                                Map<Integer, Integer> itemIdByCreatureId, ExpTable expTable) {
        this.scripts = Map.copyOf(scripts);
        this.itemIdByCreatureId = Map.copyOf(itemIdByCreatureId);
        this.expTable = expTable;
    }

    public static CreatureScriptTable empty() {
        return new CreatureScriptTable(Map.of(), Map.of(), null);
    }

    public static CreatureScriptTable load(PvfArchive archive,
                                           Map<Integer, Integer> itemIdByCreatureId) {
        ScriptList list = ScriptList.load(archive, CreatureScript.LIST_PATH);
        Map<Integer, CreatureScript> scripts = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : list.entries().entrySet()) {
            if (!archive.contains(entry.getValue())) {
                continue;
            }
            try {
                scripts.put(entry.getKey(),
                        CreatureScript.load(archive, entry.getKey(), entry.getValue()));
            } catch (RuntimeException ex) {
            }
        }
        ExpTable expTable = null;
        try {
            expTable = ExpTable.parse(archive.readText(EXP_TABLE_PATH));
        } catch (RuntimeException ex) {
        }
        return new CreatureScriptTable(scripts, itemIdByCreatureId, expTable);
    }

    public int size() {
        return scripts.size();
    }

    public Optional<CreatureScript> byCreatureId(int creatureId) {
        return Optional.ofNullable(scripts.get(creatureId));
    }

    public int itemIdOf(int creatureId) {
        return itemIdByCreatureId.getOrDefault(creatureId, 0);
    }

    public ExpTable expTable() {
        return expTable;
    }

    @Override
    public String toString() {
        return scripts.size() + " 份宠物脚本、" + itemIdByCreatureId.size() + " 条 creature→item、"
                + (expTable == null ? "没有经验表" : "经验表 " + (expTable.maxLevel() - 1) + " 档");
    }
}
