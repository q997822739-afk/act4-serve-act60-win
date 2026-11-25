package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;
import com.dylan.pvf.PvfPath;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

public final class ScriptList {

    private final String directory;
    private final SortedMap<Integer, String> byId;
    private final Map<String, Integer> byPath;
    private final int duplicateIds;

    private ScriptList(String directory, SortedMap<Integer, String> byId,
                       Map<String, Integer> byPath, int duplicateIds) {
        this.directory = directory;
        this.byId = byId;
        this.byPath = byPath;
        this.duplicateIds = duplicateIds;
    }

    public int duplicateIds() {
        return duplicateIds;
    }

    /**
     * A4 的 Script.pvf 里 .lst 有编号指向包里不存在的文件（例如 map.lst 有 200 多条）。
     * 客户端同样读不到它们，所以这里直接丢掉，等同于没有这个编号。
     */
    public static ScriptList load(PvfArchive archive, String listPath) {
        String normalized = PvfPath.normalize(listPath);
        ScriptList all = parse(normalized, archive.readText(normalized));
        SortedMap<Integer, String> byId = new TreeMap<>();
        Map<String, Integer> byPath = new LinkedHashMap<>();
        int dangling = 0;
        for (var e : all.byId.entrySet()) {
            if (!archive.contains(e.getValue())) {
                dangling++;
                continue;
            }
            byId.put(e.getKey(), e.getValue());
            byPath.putIfAbsent(e.getValue(), e.getKey());
        }
        if (dangling == 0) {
            return all;
        }
        System.out.println(normalized + "：" + dangling + " 个编号指向包里没有的文件，已忽略");
        return new ScriptList(all.directory,
                Collections.unmodifiableSortedMap(byId), Map.copyOf(byPath), all.duplicateIds);
    }

    public static ScriptList parse(String listPath, String text) {
        String normalized = PvfPath.normalize(listPath);
        int slash = normalized.lastIndexOf('/');
        String directory = slash < 0 ? "" : normalized.substring(0, slash + 1);

        var tokens = ScriptReader.tokenize(text);
        SortedMap<Integer, String> byId = new TreeMap<>();
        Map<String, Integer> byPath = new LinkedHashMap<>();
        int duplicateIds = 0;

        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.WORD) {
                continue;
            }
            if (i + 1 >= tokens.size()
                    || tokens.get(i + 1).kind() != ScriptReader.Kind.STRING) {
                throw new PvfFormatException(normalized + " 里编号 " + token.text()
                        + " 后面没有跟一个反引号路径");
            }
            int id = token.asInt();
            String relative = tokens.get(i + 1).text().trim();
            i++;

            String full = PvfPath.normalize(directory + relative);
            String previous = byId.put(id, full);
            if (previous != null && !previous.equals(full)) {
                duplicateIds++;
            }
            byPath.putIfAbsent(full, id);
        }

        return new ScriptList(directory,
                Collections.unmodifiableSortedMap(byId), Map.copyOf(byPath), duplicateIds);
    }

    public String directory() {
        return directory;
    }

    public int size() {
        return byId.size();
    }

    public Optional<String> path(int id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<Integer> id(String fullPath) {
        return Optional.ofNullable(byPath.get(PvfPath.normalize(fullPath)));
    }

    public SortedMap<Integer, String> entries() {
        return byId;
    }
}
