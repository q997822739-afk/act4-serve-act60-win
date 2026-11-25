package com.dylan.game;

import com.dylan.protocol.body.Cmd65DebugCommandRequest;
import com.dylan.protocol.body.GmSubCommand;

import java.util.ArrayList;
import java.util.List;

public final class GmCommandParser {
    private GmCommandParser() {
    }

    private static final int MAX_NAME_TOKENS = 3;

    public static boolean hasCommandPrefix(String text) {
        return GmSubCommand.hasPrefix(text);
    }

    private static List<String> split(String text) {
        List<String> tokens = new ArrayList<>();
        for (String piece : text.trim().split("\\s+")) {
            if (!piece.isEmpty()) {
                tokens.add(piece);
            }
        }
        return tokens;
    }

    public record Parsed(Cmd65DebugCommandRequest request, String name, String problem) {
        public boolean ok() {
            return request != null;
        }

        static Parsed fail(String name, String problem) {
            return new Parsed(null, name, problem);
        }
    }

    public static Parsed parse(String text) {
        List<String> tokens = split(text);
        if (tokens.isEmpty()) {
            return Parsed.fail(text, "空命令");
        }

        GmSubCommand sub = null;
        int used = 0;
        for (int n = Math.min(MAX_NAME_TOKENS, tokens.size()); n >= 1; n--) {
            String candidate = String.join(" ", tokens.subList(0, n));
            GmSubCommand hit = GmSubCommand.byName(candidate);
            if (hit != null) {
                sub = hit;
                used = n;
                break;
            }
        }
        if (sub == null) {
            return Parsed.fail(tokens.get(0), "没有这条命令");
        }
        List<String> args = tokens.subList(used, tokens.size());

        if (sub.args().isText()) {
            String joined = String.join(" ", args);
            if (joined.isEmpty() && !sub.args().isOptionalText()) {
                return Parsed.fail(sub.display(), "这条命令要一个名字：" + sub.display() + " <角色名>");
            }
            return new Parsed(new Cmd65DebugCommandRequest(sub, sub.id(), 0, 0, 0, 0, joined),
                    sub.display(), null);
        }

        int want = sub.args().count();
        if (args.size() < want) {
            return Parsed.fail(sub.display(),
                    "这条命令要 " + want + " 个参数，只给了 " + args.size());
        }
        int[] values = new int[4];
        for (int i = 0; i < want; i++) {
            Integer parsed = parseNumber(args.get(i));
            if (parsed == null) {
                return Parsed.fail(sub.display(), "第 " + (i + 1) + " 个参数「"
                        + args.get(i) + "」不是数字");
            }
            values[i] = parsed;
        }
        for (int i = want; i < args.size() && i < values.length; i++) {
            Integer parsed = parseNumber(args.get(i));
            if (parsed == null) {
                break;
            }
            values[i] = parsed;
        }
        Cmd65DebugCommandRequest request = new Cmd65DebugCommandRequest(
                sub, sub.id(), values[0], values[1], values[2], values[3], "");
        return new Parsed(request, sub.display(), null);
    }

    private static Integer parseNumber(String token) {
        try {
            long value = token.startsWith("0x") || token.startsWith("0X")
                    ? Long.parseLong(token.substring(2), 16)
                    : Long.parseLong(token);
            if (value < Integer.MIN_VALUE || value > 0xFFFF_FFFFL) {
                return null;
            }
            return (int) value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
