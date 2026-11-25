package com.dylan.pvf.script;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

public final class SlangNameTable {

    public static final String PATH = "etc/slangname.etc";

    private static final Charset CHARSET = Charset.forName("GBK");

    private final List<String> slangs;
    private final Set<Character> skipChars;
    private final int oneByteSkipCount;
    private final int twoByteSkipCount;

    private SlangNameTable(List<String> slangs, Set<Character> skipChars,
                           int oneByteSkipCount, int twoByteSkipCount) {
        this.slangs = List.copyOf(slangs);
        this.skipChars = Set.copyOf(skipChars);
        this.oneByteSkipCount = oneByteSkipCount;
        this.twoByteSkipCount = twoByteSkipCount;
    }

    public static SlangNameTable load(PvfArchive archive) {
        return parse(new String(archive.read(PATH), CHARSET));
    }

    public static SlangNameTable parse(String text) {
        List<String> slangs = new ArrayList<>();
        Set<Character> skipChars = new LinkedHashSet<>();
        int oneByte = 0;
        int twoByte = 0;

        List<ScriptReader.Token> tokens = ScriptReader.tokenize(text);
        int i = 0;
        while (i < tokens.size()) {
            ScriptReader.Token token = tokens.get(i++);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            switch (token.text().toLowerCase()) {
                case "slang" -> {
                    while (i < tokens.size()
                            && tokens.get(i).kind() == ScriptReader.Kind.STRING) {
                        slangs.add(tokens.get(i++).text());
                    }
                }
                case "one byte skip char" -> {
                    List<Integer> values = readInts(tokens, i);
                    i += values.size();
                    oneByte = values.size();
                    for (int value : values) {
                        skipChars.add(oneByteToChar(value));
                    }
                }
                case "two byte skip char" -> {
                    List<Integer> values = readInts(tokens, i);
                    i += values.size();
                    twoByte = values.size();
                    for (int value : values) {
                        twoByteToChar(value).ifPresent(skipChars::add);
                    }
                }
                default -> {
                }
            }
        }
        return new SlangNameTable(slangs, skipChars, oneByte, twoByte);
    }

    private static List<Integer> readInts(List<ScriptReader.Token> tokens, int at) {
        List<Integer> out = new ArrayList<>();
        for (int i = at; i < tokens.size(); i++) {
            ScriptReader.Token token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.WORD) {
                break;
            }
            try {
                out.add(Integer.decode(token.text().trim()));
            } catch (NumberFormatException e) {
                throw new PvfFormatException(PATH + " 里读到一个不是数的字符编号：\""
                        + token.text() + "\"");
            }
        }
        return out;
    }

    private static char oneByteToChar(int value) {
        return (char) (value & 0xFF);
    }

    private static java.util.Optional<Character> twoByteToChar(int value) {
        byte[] pair = {(byte) ((value >> 8) & 0xFF), (byte) (value & 0xFF)};
        String decoded = new String(pair, CHARSET);
        if (decoded.length() != 1) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(decoded.charAt(0));
    }

    public List<String> slangs() {
        return slangs;
    }

    public Set<Character> skipChars() {
        return skipChars;
    }

    public String stripSkipChars(String text) {
        if (text == null || text.isEmpty() || skipChars.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!skipChars.contains(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    public int oneByteSkipCount() {
        return oneByteSkipCount;
    }

    public int twoByteSkipCount() {
        return twoByteSkipCount;
    }
}
