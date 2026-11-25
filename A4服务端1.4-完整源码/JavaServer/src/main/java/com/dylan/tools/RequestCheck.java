package com.dylan.tools;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;

/**
 * 把 A4 客户端真实发过的上行包（官方日志里的 CMD body，去掉 u16 序号）喂给 A5 每个 Cmd{n}*Request.parse，
 * 报解析异常、以及 valid()/validType() 返回 false 的（A5 很多 handler 校验失败就静默丢弃，CMD 36 就是这样）。
 *
 *   java -cp a5.jar com.dylan.tools.RequestCheck 输入.tsv（每行 cmd \t hex）
 */
public final class RequestCheck {

    private RequestCheck() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        Map<Integer, List<Method>> parsers = parsers();
        Map<Integer, int[]> stats = new TreeMap<>();          // cmd -> ok, bad, noParser
        Map<String, String> examples = new LinkedHashMap<>();
        for (String ln : Files.readAllLines(Path.of(args[0]))) {
            String[] f = ln.split("\t");
            if (f.length < 2) {
                continue;
            }
            int cmd = Integer.parseInt(f[0]);
            byte[] body = HexFormat.of().parseHex(f[1]);
            int[] s = stats.computeIfAbsent(cmd, k -> new int[3]);
            List<Method> ms = parsers.get(cmd);
            if (ms == null) {
                s[2]++;
                continue;
            }
            for (Method m : ms) {
                String verdict;
                try {
                    Object req = m.invoke(null, (Object) body);
                    verdict = validity(req);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    verdict = "exception: " + e.getCause();
                }
                if (verdict == null) {
                    s[0]++;
                } else {
                    s[1]++;
                    examples.putIfAbsent(cmd + " " + m.getDeclaringClass().getSimpleName() + " " + verdict,
                            f[1].length() > 96 ? f[1].substring(0, 96) + "…" : f[1]);
                }
            }
        }
        out.println("cmd   ok  bad  (no A5 parser class)");
        stats.forEach((cmd, s) -> out.printf("%4d %4d %4d %s%n", cmd, s[0], s[1], s[2] > 0 ? "no parser x" + s[2] : ""));
        out.println();
        examples.forEach((k, v) -> out.println("## " + k + "\n   " + v));
    }

    /** null = fine; otherwise why the handler would drop it. */
    static String validity(Object req) {
        for (String name : new String[]{"valid", "validType", "isValid"}) {
            try {
                Method v = req.getClass().getMethod(name);
                if (v.getReturnType() == boolean.class && !(boolean) v.invoke(req)) {
                    return name + "() == false";
                }
            } catch (ReflectiveOperationException e) {
                // no such check
            }
        }
        return null;
    }

    static Map<Integer, List<Method>> parsers() throws Exception {
        String jar = RequestCheck.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        jar = java.net.URLDecoder.decode(jar, StandardCharsets.UTF_8);
        if (jar.matches("^/[A-Za-z]:.*")) {
            jar = jar.substring(1);
        }
        Map<Integer, List<Method>> out = new TreeMap<>();
        try (JarFile jf = new JarFile(jar)) {
            for (var e : jf.stream().toList()) {
                String n = e.getName();
                if (!n.startsWith("com/dylan/protocol/body/Cmd") || !n.endsWith("Request.class") || n.contains("$")) {
                    continue;
                }
                Class<?> c = Class.forName(n.replace('/', '.').replace(".class", ""));
                Integer id = WireSamples.packetId(c);
                if (id == null) {
                    continue;
                }
                try {
                    Method p = c.getMethod("parse", byte[].class);
                    out.computeIfAbsent(id, k -> new java.util.ArrayList<>()).add(p);
                } catch (NoSuchMethodException ignored) {
                    // not a byte[] parser
                }
            }
        }
        return out;
    }
}
