package com.dylan.pvf.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

public final class ExpertJobEtcScript {
    public static final String PATH = MagicEnchantScript.PATH;

    public static final String SECTION_LEVEL_LIMIT = "expertjob level limit";

    public static final String SECTION_BIG_WIN_RATE = "disjointer big win rate";

    public static final int NO_LIMIT = -1;

    private final Map<Integer, Integer> levelLimit;
    private final Map<Integer, Integer> bigWinRate;

    private ExpertJobEtcScript(Map<Integer, Integer> levelLimit,
                               Map<Integer, Integer> bigWinRate) {
        this.levelLimit = Map.copyOf(levelLimit);
        this.bigWinRate = Map.copyOf(bigWinRate);
    }

    public static ExpertJobEtcScript load(PvfArchive archive) {
        return parse(archive.readText(PATH));
    }

    public static ExpertJobEtcScript parse(String text) {
        var tokens = ScriptReader.tokenize(text);
        Map<Integer, Integer> levelLimit = new LinkedHashMap<>();
        Map<Integer, Integer> bigWinRate = new LinkedHashMap<>();

        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            switch (token.text().toLowerCase(Locale.ROOT)) {
                case SECTION_LEVEL_LIMIT -> fill(levelLimit, tokens, i, SECTION_LEVEL_LIMIT);
                case SECTION_BIG_WIN_RATE -> fill(bigWinRate, tokens, i, SECTION_BIG_WIN_RATE);
                default -> {
                }
            }
        }
        return new ExpertJobEtcScript(levelLimit, bigWinRate);
    }

    private static void fill(Map<Integer, Integer> into, List<ScriptReader.Token> tokens,
                             int sectionAt, String section) {
        List<Integer> pair = new ArrayList<>(2);
        for (int i = sectionAt + 1; i < tokens.size(); i++) {
            var t = tokens.get(i);
            if (t.kind() == ScriptReader.Kind.SECTION
                    || t.kind() == ScriptReader.Kind.SECTION_END) {
                break;
            }
            pair.add(t.asInt());
            if (pair.size() == 2) {
                if (into.putIfAbsent(pair.get(0), pair.get(1)) != null) {
                    throw new PvfFormatException(PATH + " 的 [" + section + "] 里 "
                            + pair.get(0) + " 这一档出现了两次（官方 index duplicated）");
                }
                pair.clear();
            }
        }
        if (!pair.isEmpty()) {
            throw new PvfFormatException(PATH + " 的 [" + section + "] 末尾多出一个数，凑不成一对");
        }
    }

    public boolean isExpertJobLimitLevel(int characLevel) {
        return levelLimit.containsValue(characLevel);
    }

    public int requiredCharacLevel(int expertJobLevel) {
        return levelLimit.getOrDefault(expertJobLevel, NO_LIMIT);
    }

    public boolean isEmpty() {
        return levelLimit.isEmpty() && bigWinRate.isEmpty();
    }

    @Override
    public String toString() {
        return "ExpertJobEtcScript[等级门槛 " + levelLimit.size() + " 档、分解师大成功率 "
                + bigWinRate.size() + " 档"
                + (isEmpty() ? "（🔴 ACT5 这份文件顶层两段都没有，副职业升不了级）" : "") + "]";
    }
}
