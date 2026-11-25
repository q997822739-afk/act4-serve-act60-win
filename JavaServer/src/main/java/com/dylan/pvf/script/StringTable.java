package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StringTable {

    private static final Pattern REFERENCE =
            Pattern.compile("<\\s*\\d+\\s*::\\s*([^>]+?)\\s*>");

    private final String path;
    private final Map<String, String> values;

    private StringTable(String path, Map<String, String> values) {
        this.path = path;
        this.values = Map.copyOf(values);
    }

    public static StringTable empty() {
        return new StringTable("", Map.of());
    }

    public static Charset charsetFor(String path) {
        String lower = path.toLowerCase();
        if (lower.contains(".kor.")) {
            return Charset.forName("EUC-KR");
        }
        if (lower.contains(".jpn.")) {
            return Charset.forName("Shift_JIS");
        }
        return Charset.forName("GBK");
    }

    public static StringTable load(PvfArchive archive, String path) {
        return parse(path, new String(archive.read(path), charsetFor(path)));
    }

    public static StringTable parse(String path, String text) {
        Map<String, String> values = new HashMap<>();
        for (String line : text.split("\r?\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                continue;
            }
            int at = trimmed.indexOf('>');
            if (at <= 0) {
                continue;
            }
            values.put(trimmed.substring(0, at).trim(), trimmed.substring(at + 1).trim());
        }
        return new StringTable(path, values);
    }

    public String path() {
        return path;
    }

    public int size() {
        return values.size();
    }

    public Optional<String> get(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(values.get(key));
    }

    public Optional<String> resolve(String raw) {
        return keyOf(raw).flatMap(this::get);
    }

    public static Optional<String> keyOf(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher m = REFERENCE.matcher(raw);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }
}
