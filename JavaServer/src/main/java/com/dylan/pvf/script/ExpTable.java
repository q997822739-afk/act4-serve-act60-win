package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

import java.util.ArrayList;
import java.util.List;

public final class ExpTable {
    public static final String PATH = "character/exptable.tbl";

    public static final long MAX_EXP = 0xFFFFFFFFL;

    private final List<Long> thresholds;

    private ExpTable(List<Long> thresholds) {
        this.thresholds = List.copyOf(thresholds);
    }

    public static ExpTable load(PvfArchive archive) {
        return parse(archive.readText(PATH));
    }

    public static ExpTable parse(String text) {
        List<Long> out = new ArrayList<>();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.split("//", 2)[0].trim();
            if (line.isEmpty()) {
                continue;
            }
            for (String token : line.split("\\s+")) {
                try {
                    out.add(Long.parseLong(token));
                } catch (NumberFormatException e) {
                    throw new PvfFormatException(PATH + " 里读到非整数 \"" + token + "\"");
                }
            }
        }
        if (out.isEmpty()) {
            throw new PvfFormatException(PATH + " 是空的");
        }
        return new ExpTable(out);
    }

    public int maxLevel() {
        return thresholds.size() + 1;
    }

    public long thresholdFor(int level) {
        if (level < 1 || level > thresholds.size()) {
            return Long.MAX_VALUE;
        }
        return thresholds.get(level - 1);
    }

    public long levelBottom(int level) {
        if (level <= 1) {
            return 0;
        }
        if (level - 1 > thresholds.size()) {
            return Long.MAX_VALUE;
        }
        return thresholds.get(level - 2);
    }

    public int levelFor(int level, long totalExp) {
        int result = Math.max(1, level);
        while (result < maxLevel() && totalExp >= thresholdFor(result)) {
            result++;
        }
        return result;
    }
}
