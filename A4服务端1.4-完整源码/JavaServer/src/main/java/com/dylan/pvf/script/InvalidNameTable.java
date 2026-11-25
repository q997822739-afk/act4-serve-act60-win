package com.dylan.pvf.script;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

public final class InvalidNameTable {

    public static final String PATH = "etc/invalidname.etc";

    private static final Charset CHARSET = Charset.forName("GBK");

    private static final int GBK_LEAD_MIN = 0x81;

    private static final int GBK_LEAD_MAX = 0xFE;

    public record Range(int lo, int hi) {

        public boolean containsClosed(int v) {
            return lo <= v && hi >= v;
        }

        public boolean containsHalfOpen(int v) {
            return lo <= v && hi > v;
        }
    }

    private final List<Range> oneByteRanges;
    private final Set<Integer> oneByteChars;
    private final List<Range> twoByteRanges;
    private final Set<Integer> twoByteChars;

    private InvalidNameTable(List<Range> oneByteRanges, Set<Integer> oneByteChars,
                             List<Range> twoByteRanges, Set<Integer> twoByteChars) {
        this.oneByteRanges = List.copyOf(oneByteRanges);
        this.oneByteChars = Set.copyOf(oneByteChars);
        this.twoByteRanges = List.copyOf(twoByteRanges);
        this.twoByteChars = Set.copyOf(twoByteChars);
    }

    public static InvalidNameTable load(PvfArchive archive) {
        return parse(new String(archive.read(PATH), CHARSET));
    }

    public static InvalidNameTable parse(String text) {
        List<Range> oneByteRanges = new ArrayList<>();
        Set<Integer> oneByteChars = new LinkedHashSet<>();
        List<Range> twoByteRanges = new ArrayList<>();
        Set<Integer> twoByteChars = new LinkedHashSet<>();

        List<ScriptReader.Token> tokens = ScriptReader.tokenize(text);
        int i = 0;
        while (i < tokens.size()) {
            ScriptReader.Token token = tokens.get(i++);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            List<Integer> values = readInts(tokens, i);
            i += values.size();
            switch (token.text().toLowerCase()) {
                case "one byte range" -> pairUp(values, oneByteRanges, "one byte range");
                case "one byte" -> oneByteChars.addAll(values);
                case "two byte range" -> pairUp(values, twoByteRanges, "two byte range");
                case "two byte" -> twoByteChars.addAll(values);
                default -> {
                }
            }
        }
        return new InvalidNameTable(oneByteRanges, oneByteChars, twoByteRanges, twoByteChars);
    }

    private static List<Integer> readInts(List<ScriptReader.Token> tokens, int at) {
        List<Integer> out = new ArrayList<>();
        for (int i = at; i < tokens.size(); i++) {
            ScriptReader.Token token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.WORD) {
                break;
            }
            out.add(parseChar(token.text()));
        }
        return out;
    }

    private static int parseChar(String word) {
        try {
            return Integer.decode(word.trim());
        } catch (NumberFormatException e) {
            throw new PvfFormatException(PATH + " 里读到一个不是数的字符编号：\"" + word + "\"");
        }
    }

    private static void pairUp(List<Integer> values, List<Range> out, String section) {
        if (values.size() % 2 != 0) {
            throw new PvfFormatException(PATH + " 的 [" + section + "] 有 " + values.size()
                    + " 个数，区间要成对出现");
        }
        for (int i = 0; i + 1 < values.size(); i += 2) {
            out.add(new Range(values.get(i), values.get(i + 1)));
        }
    }

    public boolean hasSpecialCharacter(byte[] name) {
        return scan(name, true);
    }

    public boolean hasSpecialCharacterExceptSpace(byte[] text) {
        return scan(text, false);
    }

    private boolean scan(byte[] bytes, boolean closed) {
        if (bytes == null) {
            return false;
        }
        int i = 0;
        while (i < bytes.length) {
            int b0 = bytes[i] & 0xFF;
            if (isLeadByte(b0) && i + 1 < bytes.length) {
                int wide = (b0 << 8) | (bytes[i + 1] & 0xFF);
                for (Range range : twoByteRanges) {
                    if (range.containsClosed(wide)) {
                        return true;
                    }
                }
                if (twoByteChars.contains(wide)) {
                    return true;
                }
                i += 2;
                continue;
            }
            for (Range range : oneByteRanges) {
                if (closed ? range.containsClosed(b0) : range.containsHalfOpen(b0)) {
                    return true;
                }
            }
            if (oneByteChars.contains(b0)) {
                return true;
            }
            i++;
        }
        return false;
    }

    private static boolean isLeadByte(int b) {
        return b >= GBK_LEAD_MIN && b <= GBK_LEAD_MAX;
    }

    public int oneByteRangeCount() {
        return oneByteRanges.size();
    }

    public int oneByteCharCount() {
        return oneByteChars.size();
    }

    public int twoByteRangeCount() {
        return twoByteRanges.size();
    }

    public int twoByteCharCount() {
        return twoByteChars.size();
    }

    public boolean isEmpty() {
        return oneByteRanges.isEmpty() && oneByteChars.isEmpty()
                && twoByteRanges.isEmpty() && twoByteChars.isEmpty();
    }
}
