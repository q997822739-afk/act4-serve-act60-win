package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SkillTable {
    public static final String SKILL_LIST = "skill/skilllist.lst";

    private final PvfArchive archive;

    private final Map<Integer, ScriptList> byJob;

    private final Map<Integer, SkillScript> cache = new ConcurrentHashMap<>();

    private SkillTable(PvfArchive archive, Map<Integer, ScriptList> byJob) {
        this.archive = archive;
        this.byJob = Map.copyOf(byJob);
    }

    public static SkillTable load(PvfArchive archive) {
        ScriptList jobLists = ScriptList.load(archive, SKILL_LIST);
        Map<Integer, ScriptList> byJob = new LinkedHashMap<>();
        for (var e : jobLists.entries().entrySet()) {
            try {
                byJob.put(e.getKey(), ScriptList.load(archive, e.getValue()));
            } catch (RuntimeException ex) {
                throw new com.dylan.pvf.PvfFormatException(
                        "职业 " + e.getKey() + " 的技能清单 " + e.getValue() + " 读不出来", ex);
            }
        }
        return new SkillTable(archive, byJob);
    }

    public Set<Integer> jobs() {
        return byJob.keySet();
    }

    public int size(int job) {
        ScriptList list = byJob.get(job);
        return list == null ? 0 : list.size();
    }

    public Optional<SkillScript> skill(int job, int id) {
        ScriptList list = byJob.get(job);
        if (list == null) {
            return Optional.empty();
        }
        var path = list.path(id);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(cache.computeIfAbsent(job * 1000 + id,
                key -> SkillScript.load(archive, job, id, path.get())));
    }

    public Map<Integer, String> entries(int job) {
        ScriptList list = byJob.get(job);
        return list == null ? Map.of() : list.entries();
    }
}
