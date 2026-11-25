package com.dylan.pvf.script;

import com.dylan.pvf.PvfFormatException;

import java.util.ArrayList;
import java.util.List;

public final class ScriptReader {

    public record Token(Kind kind, String text) {

        public boolean isSection(String name) {
            return kind == Kind.SECTION && text.equalsIgnoreCase(name);
        }

        public int asInt() {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException e) {
                throw new PvfFormatException("期望一个整数，读到 \"" + text + "\"");
            }
        }

        public double asDouble() {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException e) {
                throw new PvfFormatException("期望一个数，读到 \"" + text + "\"");
            }
        }
    }

    public enum Kind {
        SECTION,
        SECTION_END,
        STRING,
        WORD
    }

    private ScriptReader() {
    }

    public static List<Token> tokenize(String text) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);

            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                i++;
                continue;
            }

            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                while (i < n && text.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }

            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                continue;
            }

            if (c == '[') {
                int end = text.indexOf(']', i + 1);
                if (end < 0) {
                    throw new PvfFormatException("第 " + i + " 个字符处的小节标记没有闭合的 ']'");
                }
                String name = text.substring(i + 1, end).trim();
                i = end + 1;
                if (name.startsWith("/")) {
                    out.add(new Token(Kind.SECTION_END, name.substring(1).trim()));
                } else {
                    out.add(new Token(Kind.SECTION, name));
                }
                continue;
            }

            if (c == '`') {
                int end = text.indexOf('`', i + 1);
                if (end < 0) {
                    throw new PvfFormatException("第 " + i + " 个字符处的反引号字符串没有闭合");
                }
                out.add(new Token(Kind.STRING, text.substring(i + 1, end)));
                i = end + 1;
                continue;
            }

            int start = i;
            while (i < n) {
                char d = text.charAt(i);
                if (d == ' ' || d == '\t' || d == '\r' || d == '\n' || d == '[' || d == '`') {
                    break;
                }
                if (d == '/' && i + 1 < n
                        && (text.charAt(i + 1) == '/' || text.charAt(i + 1) == '*')) {
                    break;
                }
                i++;
            }
            out.add(new Token(Kind.WORD, text.substring(start, i)));
        }
        return out;
    }
}
