package com.dylan.tools;

import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

/**
 * 给 protocol/body 下每个下行包编码器造样本，交给 05_抓包与诊断脚本/client_check.py 用 A4 客户端的读包代码校验。
 * 每个编码器两份：变体 0 = 数字全 0、列表空；变体 1 = 数字 1、每个列表 1 条（量出循环里每条的长度）。
 * 记录类调构造 + toBytes()；工具类调每个返回 byte[] 的 public static 方法。参数不合法就换另一套值再试。
 *
 *   java -cp target\s1a5-0.1.0-SNAPSHOT-all.jar com.dylan.tools.WireSamples 输出.tsv
 * 每行：NOTI|REPLY  包号  类.方法  变体  hex
 */
public final class WireSamples {

    private WireSamples() {
    }

    public static void main(String[] args) throws Exception {
        String jar = WireSamples.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        jar = java.net.URLDecoder.decode(jar, StandardCharsets.UTF_8);
        if (jar.matches("^/[A-Za-z]:.*")) {
            jar = jar.substring(1);
        }
        PrintStream out = args.length > 0
                ? new PrintStream(args[0], StandardCharsets.UTF_8) : new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int classes = 0;
        int samples = 0;
        List<String> failed = new ArrayList<>();
        try (JarFile jf = new JarFile(jar)) {
            var names = jf.stream().map(e -> e.getName())
                    .filter(n -> n.startsWith("com/dylan/protocol/body/") && n.endsWith(".class") && !n.contains("$"))
                    .sorted().toList();
            for (String n : names) {
                Class<?> c = Class.forName(n.replace('/', '.').replace(".class", ""));
                String kind = kindOf(c);
                Integer id = packetId(c);
                if (kind == null || id == null) {
                    continue;
                }
                classes++;
                int before = samples;
                if (c.isRecord()) {
                    for (int v = 0; v < 2; v++) {
                        byte[] b = fromRecord(c, v);
                        if (b != null) {
                            out.println(kind + "\t" + id + "\t" + c.getSimpleName() + ".toBytes\t" + v + "\t"
                                    + HexFormat.of().formatHex(b));
                            samples++;
                        }
                    }
                }
                for (Method m : c.getDeclaredMethods()) {
                    // byte[] 工厂（Cmd49ReplyBody.success 这类），或返回本类的工厂（Noti35ClearRewardBody.empty）再 toBytes
                    boolean bytes = m.getReturnType() == byte[].class;
                    boolean self = c.isRecord() && m.getReturnType() == c;
                    if (!Modifier.isStatic(m.getModifiers()) || !Modifier.isPublic(m.getModifiers())
                            || !(bytes || self)) {
                        continue;
                    }
                    for (int v = 0; v < 2; v++) {
                        byte[] b = bytes ? call(m, v) : callThenEncode(c, m, v);
                        if (b != null) {
                            out.println(kind + "\t" + id + "\t" + c.getSimpleName() + "." + m.getName()
                                    + "\t" + v + "\t" + HexFormat.of().formatHex(b));
                            samples++;
                        }
                    }
                }
                if (samples == before) {
                    failed.add(c.getSimpleName());
                }
            }
        }
        out.flush();
        System.err.println(classes + " encoders, " + samples + " samples; no sample for " + failed.size()
                + ": " + failed);
    }

    /** Noti*Body → NOTI；Cmd*Reply* / *ReplyBody → REPLY；请求解析类跳过。 */
    static String kindOf(Class<?> c) {
        String s = c.getSimpleName();
        if (s.startsWith("Noti")) {
            return "NOTI";
        }
        if (s.startsWith("Cmd") && s.contains("Reply")) {
            return "REPLY";
        }
        if (s.endsWith("ReplyBody")) {
            return "REPLY";
        }
        return null;
    }

    static Integer packetId(Class<?> c) {
        try {
            var f = c.getField("PACKET_ID");
            return f.getInt(null);
        } catch (ReflectiveOperationException e) {
            var m = java.util.regex.Pattern.compile("^(?:Noti|Cmd)(\\d+)").matcher(c.getSimpleName());
            return m.find() ? Integer.parseInt(m.group(1)) : null;
        }
    }

    static byte[] fromRecord(Class<?> c, int variant) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Object o = build(c, attempt == 0 ? variant : 0, 0);
                if (o == null) {
                    return null;
                }
                Method tb = c.getMethod("toBytes");
                return (byte[]) tb.invoke(o);
            } catch (ReflectiveOperationException | RuntimeException e) {
                // try the plain-zero values next
            }
        }
        return null;
    }

    static byte[] call(Method m, int variant) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Type[] ps = m.getGenericParameterTypes();
                Object[] args = new Object[ps.length];
                for (int i = 0; i < ps.length; i++) {
                    args[i] = value(ps[i], attempt == 0 ? variant : 0, 0);
                }
                return (byte[]) m.invoke(null, args);
            } catch (ReflectiveOperationException | RuntimeException e) {
                // next attempt
            }
        }
        return null;
    }

    static byte[] callThenEncode(Class<?> c, Method m, int variant) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Type[] ps = m.getGenericParameterTypes();
                Object[] args = new Object[ps.length];
                for (int i = 0; i < ps.length; i++) {
                    args[i] = value(ps[i], attempt == 0 ? variant : 0, 0);
                }
                Object o = m.invoke(null, args);
                return o == null ? null : (byte[]) c.getMethod("toBytes").invoke(o);
            } catch (ReflectiveOperationException | RuntimeException e) {
                // next attempt
            }
        }
        return null;
    }

    static Object build(Class<?> c, int variant, int depth) throws ReflectiveOperationException {
        if (depth > 4) {
            return null;
        }
        RecordComponent[] rc = c.getRecordComponents();
        Class<?>[] types = new Class<?>[rc.length];
        Object[] args = new Object[rc.length];
        for (int i = 0; i < rc.length; i++) {
            types[i] = rc[i].getType();
            args[i] = value(rc[i].getGenericType(), variant, depth + 1);
        }
        Constructor<?> k = c.getDeclaredConstructor(types);
        k.setAccessible(true);
        return k.newInstance(args);
    }

    static Object value(Type t, int variant, int depth) throws ReflectiveOperationException {
        Class<?> raw = t instanceof ParameterizedType p ? (Class<?>) p.getRawType() : (Class<?>) t;
        int n = variant;
        if (raw == int.class || raw == Integer.class) return n;
        if (raw == long.class || raw == Long.class) return (long) n;
        if (raw == short.class || raw == Short.class) return (short) n;
        if (raw == byte.class || raw == Byte.class) return (byte) n;
        if (raw == boolean.class || raw == Boolean.class) return variant == 1;
        if (raw == double.class || raw == Double.class) return (double) n;
        if (raw == float.class || raw == Float.class) return (float) n;
        if (raw == String.class) return variant == 1 ? "a" : "";
        if (raw == byte[].class) return variant == 1 ? new byte[]{1} : new byte[0];
        if (raw == int[].class) return variant == 1 ? new int[]{1} : new int[0];
        if (raw == long[].class) return variant == 1 ? new long[]{1} : new long[0];
        if (raw.isEnum()) return raw.getEnumConstants()[0];
        if (raw == java.util.Optional.class) return java.util.Optional.empty();
        if (List.class.isAssignableFrom(raw)) {
            if (variant == 0 || !(t instanceof ParameterizedType p)) {
                return List.of();
            }
            Object one = value(p.getActualTypeArguments()[0], variant, depth + 1);
            return one == null ? List.of() : List.of(one);
        }
        if (Map.class.isAssignableFrom(raw)) return Map.of();
        if (raw.isRecord()) return build(raw, variant, depth);
        return null;
    }
}
