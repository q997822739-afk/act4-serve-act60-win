package com.dylan.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class LogArchive {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private LogArchive() {
    }

    public static void rotate() {
        String dir = System.getProperty("s1a5.logDir");
        rotate(Path.of(dir == null || dir.isBlank() ? "logs" : dir.trim())
                .resolve(logFileName()));
    }

    public static String logFileName() {
        return "s1a5.log";
    }

    public static void rotate(Path current) {
        try {
            if (!Files.isRegularFile(current) || Files.size(current) == 0) {
                return;
            }
            FileTime when = Files.getLastModifiedTime(current);
            Path archive = current.getParent() == null
                    ? Path.of("archive") : current.getParent().resolve("archive");
            Files.createDirectories(archive);

            String name = current.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String base = dot < 0 ? name : name.substring(0, dot);
            String ext = dot < 0 ? "" : name.substring(dot);

            Path target = archive.resolve(base + "-" + STAMP.format(when.toInstant()) + ext);
            for (int i = 2; Files.exists(target); i++) {
                target = archive.resolve(base + "-" + STAMP.format(when.toInstant())
                        + "-" + i + ext);
            }
            Files.move(current, target, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("[log] 上一次的日志已归档：" + target);
        } catch (IOException | RuntimeException e) {
            System.err.println("[log] 归档上一份日志失败（不影响启动）：" + e);
        }
    }
}
