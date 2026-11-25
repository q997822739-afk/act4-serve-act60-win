package com.dylan.tools;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameHandlers;
import com.dylan.protocol.io.PacketReader;

/**
 * 对每个 A4 客户端会发的命令（client_sends.py 从 DNF.exe 静态扫出的写包宽度），查 A5 接不接得住：
 * <ul>
 *   <li>有没有 handler（没有 → 客户端等回包的功能会卡）；</li>
 *   <li>A5 的 Cmd{n}*Request.parse 读包的宽度序列（PacketReader.TRACE）和客户端写包的宽度序列逐个比。
 *       包体按线上的样子补 0 到 16 的倍数，填 0x00 和 0x01 各试一次（走到不同分支）。
 *       CMD 83 就是这类：客户端 2+4+2，A5 读 1+2+4+2 → 字段整体错 1 字节。</li>
 * </ul>
 * verdict：ok = 宽度完全一致；PAD = 前面一致，A5 多读了补位的 0（通常无害）；
 * SHORT = A5 少读了后面的字段；MISMATCH = 字段边界对不上（要修）；THROWS = 线上长度下解析抛异常。
 *
 *   java -cp a5.jar com.dylan.tools.RequestLengthCheck 输入.tsv（每行 cmd \t 字段宽度如 2+4+2 \t 总长或 ?）
 */
public final class RequestLengthCheck {

    private RequestLengthCheck() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        Map<Integer, List<Method>> parsers = RequestCheck.parsers();
        Map<Integer, String> owners = new TreeMap<>();
        for (CommandHandler h : GameHandlers.all()) {
            for (int id : h.handles()) {
                owners.merge(id, h.getClass().getSimpleName(), (a, b) -> a + "," + b);
            }
        }
        out.println("cmd\tclient\thandler\tparser\ta5 reads (fill 00 / 01)\tverdict");
        for (String ln : Files.readAllLines(Path.of(args[0]))) {
            String[] f = ln.split("\t");
            if (f.length < 3 || f[0].isBlank()) {
                continue;
            }
            int cmd = Integer.parseInt(f[0].trim());
            String layout = f[1];
            Integer total = f[2].equals("?") ? null : Integer.parseInt(f[2]);
            String owner = owners.getOrDefault(cmd, "-");
            List<Method> ms = parsers.get(cmd);
            if (ms == null) {
                out.printf("%d\t%s\t%s\t-\t-\t%s%n", cmd, layout, owner, owner.equals("-") ? "NO_HANDLER" : "inline");
                continue;
            }
            for (Method m : ms) {
                if (total == null) {
                    out.printf("%d\t%s\t%s\t%s\t-\tCHECK_VAR%n", cmd, layout, owner, m.getDeclaringClass().getSimpleName());
                    continue;
                }
                int wire = total == 0 ? 0 : (total + 15) / 16 * 16;
                String zeros = trace(m, wire, (byte) 0);
                String ones = trace(m, wire, (byte) 1);
                String v0 = verdict(fields(layout), total, zeros);
                String v1 = verdict(fields(layout), total, ones);
                String verdict = v0.equals(v1) ? v0 : v0 + "/" + v1;
                String reads = zeros.equals(ones) ? zeros : zeros + " / " + ones;
                out.printf("%d\t%s\t%s\t%s\t%s\t%s%n", cmd, layout, owner, m.getDeclaringClass().getSimpleName(),
                        reads, verdict);
            }
        }
    }

    /** "2+4+2(branch)" -> [2,4,2]; strings ('?') make the layout unusable -> null. */
    static List<Integer> fields(String layout) {
        String core = layout.replaceAll("\\(.*\\)$", "");
        if (core.equals("(空)") || core.isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        for (String p : core.split("\\+")) {
            if (p.equals("?")) {
                return null;
            }
            out.add(Integer.parseInt(p));
        }
        return out;
    }

    /** Reads of one parse call, "!" appended when it threw. */
    static String trace(Method m, int length, byte fill) {
        StringBuilder t = new StringBuilder();
        PacketReader.TRACE.set(t);
        try {
            byte[] body = new byte[length];
            Arrays.fill(body, fill);
            m.invoke(null, (Object) body);
            return t.isEmpty() ? "(none)" : t.toString();
        } catch (InvocationTargetException e) {
            return (t.isEmpty() ? "" : t + " ") + "!";
        } catch (ReflectiveOperationException e) {
            return "?";
        } finally {
            PacketReader.TRACE.remove();
        }
    }

    static String verdict(List<Integer> client, int total, String reads) {
        if (client == null) {
            return "CHECK_VAR";
        }
        if (reads.endsWith("!")) {
            return "THROWS";
        }
        if (reads.equals("(none)")) {
            return "NO_TRACE";
        }
        List<Integer> a5 = new ArrayList<>();
        for (String p : reads.split("\\+")) {
            a5.add(Integer.parseInt(p));
        }
        TreeSet<Integer> cb = boundaries(client);
        TreeSet<Integer> ab = boundaries(a5);
        int a5End = ab.isEmpty() ? 0 : ab.last();
        TreeSet<Integer> abInside = new TreeSet<>(ab.headSet(total, true));
        TreeSet<Integer> cbCovered = new TreeSet<>(cb.headSet(Math.min(total, a5End), true));
        if (!abInside.equals(cbCovered)) {
            return "MISMATCH";
        }
        if (a5End > total) {
            return "PAD";
        }
        return a5End < total ? "SHORT" : "ok";
    }

    private static TreeSet<Integer> boundaries(List<Integer> widths) {
        TreeSet<Integer> out = new TreeSet<>();
        int at = 0;
        for (int w : widths) {
            at += w;
            out.add(at);
        }
        return out;
    }
}
