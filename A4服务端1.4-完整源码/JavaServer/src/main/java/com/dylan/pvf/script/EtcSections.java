package com.dylan.pvf.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EtcSections {

    private EtcSections() {
    }

    static Map<String, List<String>> split(String text) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        List<String> current = null;
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.split("//", 2)[0].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[")) {
                int close = line.indexOf(']');
                if (close < 0) {
                    continue;
                }
                String name = line.substring(1, close).trim();
                if (name.startsWith("/")) {
                    current = null;
                    continue;
                }
                current = out.computeIfAbsent(name.toLowerCase(), k -> new ArrayList<>());
                String inline = line.substring(close + 1).trim();
                if (!inline.isEmpty()) {
                    current.add(inline);
                }
                continue;
            }
            if (current != null) {
                current.add(line);
            }
        }
        return out;
    }

    static String[] tokens(String line) {
        return line.trim().split("\\s+");
    }

    static int[] ints(String line, int limit) {
        int[] out = new int[limit];
        String[] parts = tokens(line);
        for (int i = 0; i < limit && i < parts.length; i++) {
            out[i] = parseInt(parts[i], 0);
        }
        return out;
    }

    static double[] doubles(String line, int limit, double fallback) {
        double[] out = new double[limit];
        java.util.Arrays.fill(out, fallback);
        String[] parts = tokens(line);
        for (int i = 0; i < limit && i < parts.length; i++) {
            out[i] = parseDouble(parts[i], fallback);
        }
        return out;
    }

    static int parseInt(String token, int fallback) {
        try {
            return Integer.parseInt(token.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static double parseDouble(String token, double fallback) {
        try {
            return Double.parseDouble(token.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static String groupLabel(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("`")) {
            return null;
        }
        int end = trimmed.indexOf('`', 1);
        return end < 0 ? null : trimmed.substring(1, end).trim();
    }
}
