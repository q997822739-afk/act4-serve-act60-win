package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

import java.util.HashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

public final class GoldDropTable {
    public static final String PATH = "etc/itemdropinfo_common.etc";

    public record GoldGen(int amount, int variancePercent) {
        public static final GoldGen NONE = new GoldGen(0, 0);
    }

    private final Map<Integer, GoldGen> byLevel;

    private GoldDropTable(Map<Integer, GoldGen> byLevel) {
        this.byLevel = Map.copyOf(byLevel);
    }

    public static GoldDropTable load(PvfArchive archive) {
        return parse(archive.readText(PATH));
    }

    public static GoldDropTable parse(String text) {
        Map<Integer, GoldGen> byLevel = new HashMap<>();
        for (String line : EtcSections.split(text)
                .getOrDefault("gold drop ref table", java.util.List.of())) {
            String[] tokens = EtcSections.tokens(line);
            if (tokens.length < 3) {
                throw new PvfFormatException(PATH + " 的 [gold drop ref table] 里 \"" + line
                        + "\" 只有 " + tokens.length + " 列，要 3 列");
            }
            try {
                byLevel.put(Integer.parseInt(tokens[0]),
                        new GoldGen(Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2])));
            } catch (NumberFormatException e) {
                throw new PvfFormatException(PATH + " 的 [gold drop ref table] 里读到非整数："
                        + line);
            }
        }
        if (byLevel.isEmpty()) {
            throw new PvfFormatException(PATH + " 里没有 [gold drop ref table] 段");
        }
        return new GoldDropTable(byLevel);
    }

    public GoldGen forLevel(int level) {
        return byLevel.getOrDefault(level, GoldGen.NONE);
    }

    public static long roll(GoldGen gen, RandomGenerator random) {
        if (gen.amount() <= 0) {
            return 0;
        }
        int variance = Math.max(0, gen.variancePercent());
        int offset = variance == 0 ? 0 : random.nextInt(2 * variance + 1) - variance;
        return Math.max(1, gen.amount() + (long) offset * gen.amount() / 100);
    }
}
