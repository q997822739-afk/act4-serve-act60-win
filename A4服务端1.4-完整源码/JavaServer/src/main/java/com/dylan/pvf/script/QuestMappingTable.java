package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;

public final class QuestMappingTable {
    public static final String PATH = "quest/questmappingtable.tbl";

    public static final int[] CATEGORY_STARTS =
            {0, 512, 1024, 1536, 2048, 2560, 3072, 20480, 3584, 4096};

    public static final int BITS_PER_CATEGORY = 512;

    public static final int BLOB_LENGTH = BITS_PER_CATEGORY / 8;

    private final Map<Integer, Integer> indexByQuest;

    private QuestMappingTable(Map<Integer, Integer> indexByQuest) {
        this.indexByQuest = Map.copyOf(indexByQuest);
    }

    public static QuestMappingTable empty() {
        return new QuestMappingTable(Map.of());
    }

    public static QuestMappingTable load(PvfArchive archive) {
        return parse(archive.readText(PATH));
    }

    public static QuestMappingTable parse(String text) {
        var tokens = ScriptReader.tokenize(text);
        Map<Integer, Integer> indexByQuest = new LinkedHashMap<>();

        boolean inTable = false;
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() == ScriptReader.Kind.SECTION) {
                inTable = token.text().equalsIgnoreCase("quest mapping table");
                continue;
            }
            if (token.kind() == ScriptReader.Kind.SECTION_END) {
                inTable = false;
                continue;
            }
            if (!inTable || token.kind() != ScriptReader.Kind.WORD) {
                continue;
            }
            int questId = token.asInt();
            int at = nextWord(tokens, i + 1);
            if (at < 0) {
                throw new PvfFormatException(PATH + " 里任务 " + questId + " 后面没有跟一个下标");
            }
            int index = tokens.get(at).asInt();
            i = at;

            if (index < 0) {
                continue;
            }
            if (categoryOf(index) < 0) {
                continue;
            }
            indexByQuest.put(questId, index);
        }

        if (indexByQuest.isEmpty()) {
            throw new PvfFormatException(PATH + " 里没有 [quest mapping table] 段");
        }
        return new QuestMappingTable(indexByQuest);
    }

    private static int nextWord(java.util.List<ScriptReader.Token> tokens, int from) {
        for (int i = from; i < tokens.size(); i++) {
            var kind = tokens.get(i).kind();
            if (kind == ScriptReader.Kind.WORD) {
                return i;
            }
            if (kind == ScriptReader.Kind.SECTION || kind == ScriptReader.Kind.SECTION_END) {
                return -1;
            }
        }
        return -1;
    }

    public int size() {
        return indexByQuest.size();
    }

    public OptionalInt indexOf(int questId) {
        Integer index = indexByQuest.get(questId);
        return index == null ? OptionalInt.empty() : OptionalInt.of(index);
    }

    public static int categoryOf(int index) {
        for (int c = 0; c < CATEGORY_STARTS.length; c++) {
            int start = CATEGORY_STARTS[c];
            if (index >= start && index < start + BITS_PER_CATEGORY) {
                return c;
            }
        }
        return -1;
    }

    public Map<Integer, byte[]> completionSets(Collection<Integer> finished) {
        Map<Integer, byte[]> sets = new TreeMap<>();
        for (int questId : finished) {
            Integer index = indexByQuest.get(questId);
            if (index == null) {
                continue;
            }
            int category = categoryOf(index);
            if (category < 0) {
                continue;
            }
            int bit = index - CATEGORY_STARTS[category];
            byte[] bitmap = sets.computeIfAbsent(category, c -> new byte[BLOB_LENGTH]);
            bitmap[bit / 8] |= (byte) (1 << (bit % 8));
        }
        return sets;
    }
}
