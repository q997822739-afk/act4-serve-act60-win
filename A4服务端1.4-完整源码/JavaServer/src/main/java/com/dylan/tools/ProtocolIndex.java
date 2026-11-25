package com.dylan.tools;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameHandlers;
import com.dylan.protocol.CmdName;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 给 05_抓包与诊断脚本/protocol_index.py 出原始数据（TSV，打到 stdout）：
 * <pre>
 *   handler  &lt;cmd&gt;  &lt;Handler 类&gt;          A4 模式下 GameHandlers 实际注册的命令
 *   body     &lt;id&gt;   &lt;protocol.body 类&gt;    类里 public static final int PACKET_ID 的值
 *   cmdname  &lt;id&gt;   &lt;A5 命令名&gt;
 * </pre>
 * 用运行时反射而不是 grep，因为不少 handler 用别的类的常量或本地常量登记命令号。
 */
public final class ProtocolIndex {

    private ProtocolIndex() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        for (CommandHandler h : GameHandlers.all()) {
            for (int id : h.handles()) {
                out.println("handler\t" + id + "\t" + h.getClass().getSimpleName());
            }
        }
        for (String cls : bodyClasses()) {
            try {
                Class<?> c = Class.forName(cls, false, ProtocolIndex.class.getClassLoader());
                Field f = c.getDeclaredField("PACKET_ID");
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) && f.getType() == int.class) {
                    f.setAccessible(true);
                    out.println("body\t" + f.getInt(null) + "\t" + c.getSimpleName());
                }
            } catch (NoSuchFieldException | ClassNotFoundException | IllegalAccessException
                     | NoClassDefFoundError e) {
                // 没有 PACKET_ID 的辅助类（Entry、CombatStats 等）
            }
        }
        for (int id = 0; id <= CmdName.MAX_ID; id++) {
            String name = CmdName.of(id);
            if (name != null && !name.isEmpty()) {
                out.println("cmdname\t" + id + "\t" + name);
            }
        }
    }

    private static List<String> bodyClasses() throws IOException, URISyntaxException {
        List<String> out = new ArrayList<>();
        Path here = Path.of(ProtocolIndex.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String prefix = "com/dylan/protocol/body/";
        if (Files.isDirectory(here)) {
            Path dir = here.resolve(prefix);
            try (var s = Files.list(dir)) {
                s.map(p -> p.getFileName().toString())
                        .filter(n -> n.endsWith(".class") && !n.contains("$"))
                        .forEach(n -> out.add("com.dylan.protocol.body." + n.substring(0, n.length() - 6)));
            }
            return out;
        }
        try (JarFile jar = new JarFile(here.toFile())) {
            Enumeration<JarEntry> e = jar.entries();
            while (e.hasMoreElements()) {
                String n = e.nextElement().getName();
                if (n.startsWith(prefix) && n.endsWith(".class") && !n.contains("$")
                        && n.indexOf('/', prefix.length()) < 0) {
                    out.add(n.substring(0, n.length() - 6).replace('/', '.'));
                }
            }
        }
        return out;
    }
}
