package com.dylan.tools;

import com.dylan.net.ServerConfig;
import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfEntry;
import com.dylan.pvf.script.ScriptList;
import com.dylan.pvf.script.ScriptReader;
import com.dylan.pvf.script.StringTable;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class PvfDump {

    private static final String USAGE = """
            命令行翻 Script.pvf。

              java -cp target\\s1a5-0.1.0-SNAPSHOT-all.jar com.dylan.tools.PvfDump <子命令> [参数]

              info                       文件头、文件数、目录分布
              ls [前缀] [上限]            按前缀列文件（默认上限 100，给 0 表示不限）
              cat <路径> [关键字]         解出脚本原文；给了关键字就只打含它的行（带行号）
              grep <前缀> <关键字> [上限]  把前缀底下每个文件都解出来找关键字，打「文件:行号 原文」
              tokens <路径> [上限]        打分词结果，用来核对 ScriptReader 的理解
              lst <清单路径> [上限]       解析一份 .lst，打编号 -> 路径
              str <文案表路径> [键]       解析一份 .str；给了键就只打那一条
              extract <路径> <落盘位置>   把解出来的字节原样写到磁盘
              verify [上限]              逐个解密校验 CRC，报失败的（上限默认 2000，0 表示全量）
              check                      把包里全部 .map / .dgn / .twn 过一遍解析器，报解不动的
              items [落盘位置]            导出「编号 中文名」的物品总表（默认 item.md），发 GM 物品时查号用

            包的位置取 -Ds1a5.pvf，默认是 -Ds1a5.clientDir 底下的 Script.pvf。
            控制台输出建议加 -Dstdout.encoding=UTF-8，否则 Windows 终端会把韩文打成乱码。
            """;

    private PvfDump() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        if (args.length == 0) {
            out.print(USAGE);
            return;
        }
        Path path = Path.of(System.getProperty("s1a5.pvf",
                ServerConfig.PVF_PATH.toString()));
        try (PvfArchive archive = PvfArchive.open(path)) {
            switch (args[0]) {
                case "info" -> info(out, archive);
                case "ls" -> ls(out, archive, arg(args, 1, ""), num(args, 2, 100));
                case "cat" -> cat(out, archive, require(args, 1), arg(args, 2, null));
                case "grep" -> grep(out, archive, require(args, 1), require(args, 2),
                        num(args, 3, 200));
                case "tokens" -> tokens(out, archive, require(args, 1), num(args, 2, 200));
                case "lst" -> lst(out, archive, require(args, 1), num(args, 2, 100));
                case "str" -> str(out, archive, require(args, 1), arg(args, 2, null));
                case "extract" -> extract(out, archive, require(args, 1), require(args, 2));
                case "verify" -> verify(out, archive, num(args, 1, 2000));
                case "check" -> check(out, archive);
                case "items" -> items(out, archive, arg(args, 1, "item.md"));
                default -> out.println("不认识的子命令：" + args[0]);
            }
        }
    }

    private static void info(PrintStream out, PvfArchive a) {
        out.println("包        " + a.path());
        out.println("guid      " + a.guid());
        out.println("version   " + a.fileVersion());
        out.println("文件数    " + a.size());
        Map<String, Integer> dirs = new TreeMap<>();
        for (PvfEntry e : a.entries()) {
            int slash = e.name().indexOf('/');
            dirs.merge(slash < 0 ? "(根目录)" : e.name().substring(0, slash), 1, Integer::sum);
        }
        out.println("顶层目录：");
        dirs.forEach((k, v) -> out.printf("  %-24s %6d%n", k, v));
    }

    private static void ls(PrintStream out, PvfArchive a, String prefix, int limit) {
        String p = com.dylan.pvf.PvfPath.normalize(prefix);
        List<PvfEntry> hits = a.entries().stream()
                .filter(e -> e.name().startsWith(p))
                .sorted(Comparator.comparing(PvfEntry::name))
                .toList();
        int shown = limit <= 0 ? hits.size() : Math.min(limit, hits.size());
        for (int i = 0; i < shown; i++) {
            out.printf("%-70s %8d%n", hits.get(i).name(), hits.get(i).length());
        }
        out.println("—— 命中 " + hits.size() + " 条，显示 " + shown + " 条");
    }

    private static void cat(PrintStream out, PvfArchive a, String path, String keyword) {
        String text = a.readText(path);
        if (keyword == null) {
            out.println(text);
            return;
        }
        String[] lines = text.split("\r?\n");
        int hits = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(keyword)) {
                out.printf("%6d| %s%n", i + 1, lines[i]);
                hits++;
            }
        }
        out.println("—— " + hits + " 行含 \"" + keyword + "\"");
    }

    private static void grep(PrintStream out, PvfArchive a, String prefix, String keyword,
                             int limit) {
        String p = com.dylan.pvf.PvfPath.normalize(prefix);
        List<PvfEntry> hits = a.entries().stream()
                .filter(e -> e.name().startsWith(p))
                .sorted(Comparator.comparing(PvfEntry::name))
                .toList();
        int shown = 0;
        int files = 0;
        int scanned = 0;
        for (PvfEntry entry : hits) {
            String text;
            try {
                text = a.readText(entry.name());
            } catch (RuntimeException e) {
                continue;
            }
            scanned++;
            boolean first = true;
            String[] lines = text.split("\r?\n");
            for (int i = 0; i < lines.length; i++) {
                if (!lines[i].contains(keyword)) {
                    continue;
                }
                if (limit > 0 && shown >= limit) {
                    out.println("—— 到上限 " + limit + " 行，后面的没打");
                    out.println("—— " + files + " 个文件命中（扫过 " + scanned + " 个）");
                    return;
                }
                if (first) {
                    files++;
                    first = false;
                }
                out.printf("%s:%d| %s%n", entry.name(), i + 1, lines[i].trim());
                shown++;
            }
        }
        out.println("—— " + files + " 个文件命中、共 " + shown + " 行（扫过 " + scanned + " 个文件）");
    }

    private static void tokens(PrintStream out, PvfArchive a, String path, int limit) {
        List<ScriptReader.Token> tokens = ScriptReader.tokenize(a.readText(path));
        int shown = limit <= 0 ? tokens.size() : Math.min(limit, tokens.size());
        for (int i = 0; i < shown; i++) {
            out.printf("%5d  %-12s %s%n", i, tokens.get(i).kind(), tokens.get(i).text());
        }
        out.println("—— 共 " + tokens.size() + " 个词法单元，显示 " + shown + " 个");
    }

    private static void lst(PrintStream out, PvfArchive a, String path, int limit) {
        ScriptList list = ScriptList.load(a, path);
        int n = 0;
        for (Map.Entry<Integer, String> e : list.entries().entrySet()) {
            if (limit > 0 && n++ >= limit) {
                break;
            }
            out.printf("%8d  %-60s %s%n", e.getKey(), e.getValue(),
                    a.contains(e.getValue()) ? "" : "  ← 包里没有这个文件");
        }
        out.println("—— 共 " + list.size() + " 条，目录 " + list.directory());
    }

    private static void str(PrintStream out, PvfArchive a, String path, String key) {
        StringTable table = StringTable.load(a, path);
        out.println("表 " + path + "（" + StringTable.charsetFor(path) + "）共 "
                + table.size() + " 条");
        if (key != null) {
            out.println(key + " > " + table.get(key).orElse("(查不到)"));
        }
    }

    private static final java.util.regex.Pattern STRING_REF =
            java.util.regex.Pattern.compile("<\\s*(\\d+)\\s*::\\s*([^>]+?)\\s*>");

    private static final String STRING_LIST = "string.lst";

    private static final java.nio.charset.Charset ITEM_TEXT_CHARSET =
            java.nio.charset.Charset.forName("GBK");

    private static void items(PrintStream out, PvfArchive a, String target) throws Exception {
        Map<Integer, StringTable> tables = new java.util.HashMap<>();
        Map<Integer, StringTable> korean = new java.util.HashMap<>();
        for (Map.Entry<Integer, String> e : ScriptList.load(a, STRING_LIST).entries().entrySet()) {
            if (!a.contains(e.getValue())) {
                out.println("string.lst 里第 " + e.getKey() + " 张表 " + e.getValue()
                        + " 包里没有，引用它的名字会查不到");
                continue;
            }
            tables.put(e.getKey(), StringTable.load(a, e.getValue()));
            String kor = e.getValue().replace(".chn.", ".kor.");
            if (!kor.equals(e.getValue()) && a.contains(kor)) {
                korean.put(e.getKey(), StringTable.load(a, kor));
            }
        }
        out.println("文案表就绪：中文 " + tables.size() + " 张，韩文 " + korean.size() + " 张");

        StringBuilder md = new StringBuilder();
        md.append("# ACT5 物品编号总表\n\n");
        md.append("> 从 `").append(a.path()).append("` 导出（`PvfDump items`）。\n");
        md.append("> 每行是「编号 中文名」，编号就是 GM 命令和协议里用的那个物品编号；\n");
        md.append("> 装备和可堆叠物品共用一个不重叠的编号空间。\n>\n");
        md.append("> 带 `").append(UNLOCALIZED)
                .append("` 的是中文文案表里没译的条目（值就是键名本身，中文客户端里").append("\n");
        md.append("> 显示的也是 `name_123` 这种），名字取自同一张表的韩文版，只为认得出是什么东西。\n\n");

        int equipment = section(md, out, a, tables, korean, "装备（`equipment/equipment.lst`）",
                "equipment/equipment.lst");
        int stackable = section(md, out, a, tables, korean, "可堆叠（`stackable/stackable.lst`）",
                "stackable/stackable.lst");

        Path file = Path.of(target);
        Files.writeString(file, md, StandardCharsets.UTF_8);
        out.println("写出 " + (equipment + stackable) + " 件物品到 " + file.toAbsolutePath());
    }

    private static final String UNLOCALIZED = "（未汉化）";

    private static int section(StringBuilder md, PrintStream out, PvfArchive a,
                               Map<Integer, StringTable> tables, Map<Integer, StringTable> korean,
                               String title, String listPath) {
        ScriptList list = ScriptList.load(a, listPath);
        int missing = 0;
        int untranslated = 0;
        StringBuilder body = new StringBuilder();
        for (Map.Entry<Integer, String> e : list.entries().entrySet()) {
            String name;
            try {
                name = itemName(a, e.getValue(), tables, korean);
            } catch (RuntimeException ex) {
                name = "";
                out.println("物品 " + e.getKey() + " (" + e.getValue() + ") 读不出名字：" + ex);
            }
            if (name.endsWith(UNLOCALIZED)) {
                untranslated++;
            }
            if (name.isEmpty()) {
                missing++;
                name = "(无名) " + e.getValue().substring(e.getValue().lastIndexOf('/') + 1);
            }
            body.append(e.getKey()).append(' ').append(name).append('\n');
        }
        md.append("## ").append(title).append("，共 ").append(list.size()).append(" 件");
        if (untranslated > 0 || missing > 0) {
            md.append("（其中 ").append(untranslated).append(" 件未汉化");
            if (missing > 0) {
                md.append("、").append(missing).append(" 件连韩文名都查不到");
            }
            md.append("）");
        }
        md.append("\n\n```\n").append(body).append("```\n\n");
        out.println(title + "：" + list.size() + " 件，未汉化 " + untranslated
                + " 件，一个名字都查不到的 " + missing + " 件");
        return list.size();
    }

    private static String itemName(PvfArchive a, String path, Map<Integer, StringTable> tables,
                                   Map<Integer, StringTable> korean) {
        List<ScriptReader.Token> tokens =
                ScriptReader.tokenize(new String(a.read(path), ITEM_TEXT_CHARSET));
        String fallback = "";
        for (int i = 0; i + 1 < tokens.size(); i++) {
            boolean primary = tokens.get(i).isSection("name");
            if (!primary && !tokens.get(i).isSection("name2")) {
                continue;
            }
            String value = resolveText(tokens.get(i + 1).text().trim(), tables, korean);
            if (primary && !value.isEmpty()) {
                return value;
            }
            if (fallback.isEmpty()) {
                fallback = value;
            }
        }
        return fallback;
    }

    private static String resolveText(String raw, Map<Integer, StringTable> tables,
                                      Map<Integer, StringTable> korean) {
        java.util.regex.Matcher m = STRING_REF.matcher(raw);
        if (!m.matches()) {
            return raw;
        }
        int tableId = Integer.parseInt(m.group(1));
        String key = m.group(2);
        StringTable table = tables.get(tableId);
        String value = table == null ? "" : table.get(key).orElse("").trim();
        if (!value.isEmpty() && !value.equals(key)) {
            return value;
        }
        StringTable kor = korean.get(tableId);
        String korValue = kor == null ? "" : kor.get(key).orElse("").trim();
        if (korValue.isEmpty() || korValue.equals(key)) {
            return "";
        }
        return korValue + UNLOCALIZED;
    }

    private static void extract(PrintStream out, PvfArchive a, String path, String target)
            throws Exception {
        byte[] data = a.read(path);
        Files.write(Path.of(target), data);
        out.println("写出 " + data.length + " 字节到 " + target);
    }

    private static void verify(PrintStream out, PvfArchive a, int limit) {
        int checked = 0;
        int failed = 0;
        for (PvfEntry e : a.entries()) {
            if (limit > 0 && checked >= limit) {
                break;
            }
            checked++;
            try {
                a.read(e.name());
            } catch (RuntimeException ex) {
                failed++;
                if (failed <= 20) {
                    out.println("失败 " + e.name() + " —— " + ex.getMessage());
                }
            }
        }
        out.println("—— 校验 " + checked + " 个文件，失败 " + failed + " 个");
    }

    private static void check(PrintStream out, PvfArchive a) {
        ScriptList dungeons = ScriptList.load(a, "dungeon/dungeon.lst");
        int okDungeons = 0;
        int towers = 0;
        int failed = 0;
        for (Map.Entry<Integer, String> e : dungeons.entries().entrySet()) {
            try {
                var script = com.dylan.pvf.script.DungeonScript.load(a, e.getKey(),
                        e.getValue());
                okDungeons++;
                if (script.isTower()) {
                    towers++;
                }
            } catch (RuntimeException ex) {
                failed++;
                out.println("副本 " + e.getKey() + " 解析失败 " + e.getValue() + " —— " + ex);
            }
        }
        out.println("清单里 " + dungeons.size() + " 份副本：解析通过 " + okDungeons
                + "（其中塔类 " + towers + "），失败 " + failed);

        int maps = 0;
        int mapFailed = 0;
        for (PvfEntry e : a.entries()) {
            if (!e.name().endsWith(".map") || e.name().startsWith("sample/")) {
                continue;
            }
            try {
                com.dylan.pvf.script.MapScript.load(a, e.name());
                maps++;
            } catch (RuntimeException ex) {
                mapFailed++;
                if (mapFailed <= 30) {
                    out.println("地图解析失败 " + e.name() + " —— " + ex);
                }
            }
        }
        out.println("地图 " + maps + " 张解析通过，失败 " + mapFailed + " 个");

        try {
            var towns = com.dylan.pvf.script.TownTable.load(a);
            out.println("城镇 " + towns.size() + " 个；各自的区域数：");
            towns.entries().forEach((id, t) -> out.printf("  %2d  %-28s 区域 %2d  gate=%s%n",
                    id, t.path(), t.areas().size(),
                    t.gate().map(g -> "区域 " + g.id() + " (" + g.param(0, -1) + ","
                            + g.param(1, -1) + ")").orElse("无")));
        } catch (RuntimeException ex) {
            out.println("城镇表加载失败 —— " + ex);
        }

        checkSkills(out, a);
        checkItems(out, a);
        checkMultiBox(out, a);
        checkQuests(out, a);
        checkMonsters(out, a);
        checkCashShop(out, a);
        checkItemShops(out, a);
        checkExpertJob(out, a);
        checkBossRooms(out, a);
        checkConsistMap(out, a);
        checkNameFilter(out, a);
    }

    private static void checkNameFilter(PrintStream out, PvfArchive a) {
        try {
            var table = com.dylan.pvf.script.InvalidNameTable.load(a);
            out.println("名字非法字符表：单字节 " + table.oneByteRangeCount() + " 段区间 + "
                    + table.oneByteCharCount() + " 个字符，双字节 "
                    + table.twoByteRangeCount() + " 段区间 + "
                    + table.twoByteCharCount() + " 个字符");
        } catch (RuntimeException ex) {
            out.println("invalidname.etc 解析失败 —— " + ex);
        }
        try {
            var table = com.dylan.pvf.script.SlangNameTable.load(a);
            out.println("敏感词 etc：[SLANG] " + table.slangs().size()
                    + " 个词（ACT5 本来就是 0，词表在库里），跳过字符 单字节 "
                    + table.oneByteSkipCount() + " + 双字节 " + table.twoByteSkipCount()
                    + " → 去重后 " + table.skipChars().size() + " 个");
        } catch (RuntimeException ex) {
            out.println("slangname.etc 解析失败 —— " + ex);
        }
    }

    private static void checkConsistMap(PrintStream out, PvfArchive a) {
        java.util.SortedMap<Integer, String> dungeons;
        java.util.SortedMap<Integer, String> mapList;
        try {
            dungeons = ScriptList.load(a, "dungeon/dungeon.lst").entries();
            mapList = ScriptList.load(a, "map/map.lst").entries();
        } catch (RuntimeException ex) {
            out.println("ConsistMap 自检跳过 —— " + ex);
            return;
        }

        java.util.Set<Integer> knownApc;
        try {
            knownApc = ScriptList.load(a,
                    com.dylan.pvf.script.AiCharacterScript.LIST_PATH).entries().keySet();
        } catch (RuntimeException ex) {
            out.println("  ★ 读不到 aicharacter.lst，APC 编号无法校验 —— " + ex);
            knownApc = java.util.Set.of();
        }

        int aiMaps = 0;
        int aiRows = 0;
        int aiBossRows = 0;
        int aiMissing = 0;
        int aiFriendlyRows = 0;
        int aiNeutralRows = 0;
        int eventPosMaps = 0;
        for (var e : mapList.entrySet()) {
            com.dylan.pvf.script.MapScript m;
            try {
                m = com.dylan.pvf.script.MapScript.load(a, e.getValue());
            } catch (RuntimeException ex) {
                continue;
            }
            if (e.getValue().contains("deadtower")) {
                continue;
            }
            if (m.eventMonsterPositionCount() > 0) {
                eventPosMaps++;
            }
            if (!m.hasAiCharacters()) {
                continue;
            }
            aiMaps++;
            for (var row : m.aiCharacters()) {
                aiRows++;
                boolean known = knownApc.isEmpty() || knownApc.contains(row.apcIndex());
                if (!known) {
                    aiMissing++;
                    out.printf("  ★ %-44s [ai character] 引用的 APC %d 在 %s 里查不到"
                                    + " —— 官方 continue 跳过，这一只<b>不会出现</b>%n",
                            e.getValue(), row.apcIndex(),
                            com.dylan.pvf.script.AiCharacterScript.LIST_PATH);
                }
                if (row.roleTypeValue() == com.dylan.pvf.script.MapScript.ROLE_BOSS) {
                    aiBossRows++;
                    out.printf("  ! %-44s [ai character] 里有 `[boss]` 档 APC %d%s"
                                    + " —— 不读这一段这张图就是「BOSS 不出现」%n",
                            e.getValue(), row.apcIndex(), known ? "" : "（★ 查不到！）");
                }
                if (row.campValue() != com.dylan.pvf.script.MapScript.CAMP_MONSTER) {
                    if (row.campValue() == com.dylan.pvf.script.MapScript.CAMP_NEUTRAL) {
                        aiNeutralRows++;
                    } else {
                        aiFriendlyRows++;
                    }
                    out.printf("  ★ %-44s [ai character] APC %d 阵营 %s（%d）"
                                    + " —— 不计入清场%n",
                            e.getValue(), row.apcIndex(), row.camp(), row.campValue());
                }
            }
        }
        out.printf("[ai character]（非塔）：%d 张图 %d 行，其中 `[boss]` 档 %d 个、"
                        + "APC 编号查不到的 %d 个、`[character]` 阵营 %d 个、"
                        + "`[neutral]` 阵营 %d 个；[event monster position]：%d 张图%n",
                aiMaps, aiRows, aiBossRows, aiMissing, aiFriendlyRows, aiNeutralRows,
                eventPosMaps);

        int withClearCondition = 0;
        int withEventMonster = 0;
        int withChampion = 0;
        for (var e : dungeons.entrySet()) {
            com.dylan.pvf.script.DungeonScript d;
            try {
                d = com.dylan.pvf.script.DungeonScript.load(a, e.getKey(), e.getValue());
            } catch (RuntimeException ex) {
                continue;
            }
            if (d.championCountFor(0) > 0) {
                withChampion++;
            }
            if (d.tryGenEventMonsterCount() > 0 && !d.eventMonsters().isEmpty()) {
                withEventMonster++;
            }
            for (var maze : d.mazes()) {
                if (maze.clearConditions().isEmpty()) {
                    continue;
                }
                withClearCondition++;
                out.printf("  ★ 副本 %-5d %-44s 第 %d 套迷宫 [clear condition]=%s"
                                + " —— 这一局的通关判据不看 BOSS 房%n",
                        e.getKey(), e.getValue(), maze.index(),
                        maze.clearConditions().stream()
                                .map(c -> com.dylan.game.ClearConditions.typeName(c.type())
                                        + " " + c.id() + " x" + c.count())
                                .toList());
            }
        }
        out.printf("[clear condition]：%d 套迷宫；[event monster]：%d 个副本；"
                        + "[champion] 非 0：%d 个副本%n",
                withClearCondition, withEventMonster, withChampion);

        checkQuestMazes(out, dungeons, a);
    }

    private static void checkQuestMazes(PrintStream out,
                                        java.util.SortedMap<Integer, String> dungeons,
                                        PvfArchive a) {
        int questMazes = 0;
        int questDungeons = 0;
        int emptyNormalPool = 0;
        for (var e : dungeons.entrySet()) {
            com.dylan.pvf.script.DungeonScript d;
            try {
                d = com.dylan.pvf.script.DungeonScript.load(a, e.getKey(), e.getValue());
            } catch (RuntimeException ex) {
                continue;
            }
            if (d.mazes().isEmpty()) {
                continue;
            }
            long quest = d.mazes().stream()
                    .filter(com.dylan.pvf.script.DungeonScript.Maze::isQuestMaze).count();
            if (quest == 0) {
                continue;
            }
            questDungeons++;
            questMazes += (int) quest;
            if (d.normalMazes().isEmpty()) {
                emptyNormalPool++;
                out.printf("  ★ 副本 %-5d %-44s %d 套迷宫<b>全带</b> [quest connection]"
                                + " —— 普通池是空的，一个任务都没接就进不去%n",
                        e.getKey(), e.getValue(), quest);
            }
        }
        out.printf("[quest connection]：%d 个副本 %d 套任务迷宫，其中普通池为空的 %d 个%n",
                questDungeons, questMazes, emptyNormalPool);
    }

    private static void checkBossRooms(PrintStream out, PvfArchive a) {
        java.util.SortedMap<Integer, String> dungeons;
        java.util.SortedMap<Integer, String> mapList;
        try {
            dungeons = ScriptList.load(a, "dungeon/dungeon.lst").entries();
            mapList = ScriptList.load(a, "map/map.lst").entries();
        } catch (RuntimeException ex) {
            out.println("BOSS 房自检跳过 —— " + ex);
            return;
        }

        Map<Integer, com.dylan.pvf.script.DungeonMaps> pools = new java.util.HashMap<>();
        Map<Integer, com.dylan.pvf.script.MapScript> all = new java.util.HashMap<>();
        for (var e : mapList.entrySet()) {
            try {
                var script = com.dylan.pvf.script.MapScript.load(a, e.getValue());
                all.put(e.getKey(), script);
                for (int dungeonId : script.dungeonIds()) {
                    pools.computeIfAbsent(dungeonId,
                                    k -> com.dylan.pvf.script.DungeonMaps.empty())
                            .insert(e.getKey(), script);
                }
            } catch (RuntimeException ignored) {
            }
        }

        int cells = 0;
        int viaSpec = 0;
        int viaPool = 0;
        int dead = 0;
        int shadowed = 0;
        for (var e : dungeons.entrySet()) {
            com.dylan.pvf.script.DungeonScript script;
            try {
                script = com.dylan.pvf.script.DungeonScript.load(a, e.getKey(), e.getValue());
            } catch (RuntimeException ex) {
                continue;
            }
            var pool = pools.getOrDefault(e.getKey(),
                    com.dylan.pvf.script.DungeonMaps.empty());
            for (var maze : script.mazes()) {
                for (var room : maze.bossRooms()) {
                    if (room.isNone()) {
                        continue;
                    }
                    cells++;
                    int gridCode = maze.gridCode(room.x(), room.y());
                    boolean spec = !maze.bossSpecifiedMaps(room.x(), room.y()).isEmpty();
                    boolean pooled = false;
                    for (int dir : new int[]{
                            com.dylan.pvf.script.GridCode.DIR_EAST,
                            com.dylan.pvf.script.GridCode.DIR_NORTH,
                            com.dylan.pvf.script.GridCode.DIR_WEST,
                            com.dylan.pvf.script.GridCode.DIR_SOUTH}) {
                        if (!pool.candidates(com.dylan.pvf.script.DungeonMaps.TYPE_BOSS,
                                gridCode, dir).isEmpty()) {
                            pooled = true;
                            break;
                        }
                    }
                    if (spec) {
                        viaSpec++;
                    } else if (pooled) {
                        viaPool++;
                    } else {
                        dead++;
                        out.printf("  ★ 副本 %d %s 第 %d 套迷宫 (%d,%d) 形态 %s："
                                        + "既没有 [boss map specification]、BOSS 图池也一张都拿不出来"
                                        + " —— 当上 BOSS 房就是错误码 21%n",
                                e.getKey(), e.getValue(), maze.index(), room.x(), room.y(),
                                com.dylan.pvf.script.GridCode.format(gridCode));
                    }
                    var shadow = maze.specifiedMaps(room.x(), room.y());
                    if (!shadow.isEmpty() && shadow.stream()
                            .noneMatch(id -> all.containsKey(id) && all.get(id).isBoss())) {
                        shadowed++;
                        out.printf("  ! 副本 %-5d %-44s (%d,%d) [map specification]=%s 全是非 BOSS 图"
                                        + " —— 先看那张表就会「BOSS 凭空消失」%n",
                                e.getKey(), e.getValue(), room.x(), room.y(), shadow);
                    }
                }
            }
        }
        out.printf("BOSS 房候选格 %d 个：走 [boss map specification] %d、走 BOSS 图池 %d、"
                        + "两条都空 %d；其中 %d 个被 [map specification] 的非 BOSS 图挡着"
                        + "（★ 官方 BOSS 支不看那张表，看了就是「BOSS 凭空消失」）%n",
                cells, viaSpec, viaPool, dead, shadowed);
    }

    private static void checkExpertJob(PrintStream out, PvfArchive a) {
        var known = new java.util.HashSet<Integer>();
        java.util.SortedMap<Integer, String> stackables;
        try {
            stackables = ScriptList.load(a, "stackable/stackable.lst").entries();
            known.addAll(stackables.keySet());
            known.addAll(ScriptList.load(a, "equipment/equipment.lst").entries().keySet());
        } catch (RuntimeException ex) {
            out.println("副职业统计跳过 —— " + ex);
            return;
        }

        try {
            var list = ScriptList.load(a,
                    com.dylan.pvf.script.ExpertJobScript.LIST_PATH);
            for (var e : list.entries().entrySet()) {
                com.dylan.pvf.script.ExpertJobScript script;
                try {
                    script = com.dylan.pvf.script.ExpertJobScript.load(
                            a, e.getKey(), e.getValue());
                } catch (RuntimeException ex) {
                    out.println("  副职业 " + e.getKey() + " 解析失败 " + e.getValue()
                            + " —— " + ex);
                    continue;
                }
                int deadRecipeItem = 0;
                int deadResultItem = 0;
                for (var r : script.recipes()) {
                    if (!known.contains(r.recipeItemId())) {
                        deadRecipeItem++;
                    }
                    if (!known.contains(r.resultItemId())) {
                        deadResultItem++;
                    }
                }
                out.println("  副职业 " + e.getKey() + " " + script
                        + "：配方物品查不到 " + deadRecipeItem
                        + " 条，产出查不到 " + deadResultItem + " 条");
            }
        } catch (RuntimeException ex) {
            out.println("副职业清单加载失败 —— " + ex);
        }
        try {
            out.println("  " + com.dylan.pvf.script.MagicEnchantScript.load(a));
        } catch (RuntimeException ex) {
            out.println("  [magic enchant] 解析失败 —— " + ex);
        }

        var byId = new java.util.HashMap<Integer, com.dylan.pvf.script.ItemScript>();
        for (var e : stackables.entrySet()) {
            try {
                byId.put(e.getKey(), com.dylan.pvf.script.ItemScript.load(
                        a, e.getKey(), e.getValue()));
            } catch (RuntimeException ex) {
            }
        }
        java.util.function.IntUnaryOperator subTypeOf = id -> {
            var s = byId.get(id);
            return s == null ? -1 : s.subType();
        };

        int recipeItems = 0;
        int expertJobRecipes = 0;
        int noMaterials = 0;
        int noCard = 0;
        for (var s : byId.values()) {
            if (!s.isRecipeItem()) {
                continue;
            }
            recipeItems++;
            if (s.isExpertJobRecipe()) {
                expertJobRecipes++;
            }
            if (s.recipeInfo().materials().isEmpty()) {
                noMaterials++;
                continue;
            }
            if (s.recipeInfo().monsterCardItemId(subTypeOf) == 0
                    && s.recipeInfo().outputs().isEmpty()) {
                noCard++;
                out.println("  ⚠ 配方 " + s.id() + "「" + s.name()
                        + "」既没有怪物卡也没有产出");
            }
        }
        out.println("配方卷轴 " + recipeItems + " 件（副职业配方 " + expertJobRecipes
                + " 件）：[int data] 切不出材料 " + noMaterials + " 件，"
                + "既无卡又无产出 " + noCard + " 件");

        int cards = 0;
        int emptyEnchant = 0;
        int unknownStat = 0;
        int unknownSlot = 0;
        int noSlot = 0;
        var unknownSlotTags = new java.util.TreeMap<String, Integer>();
        for (var s : byId.values()) {
            if (!s.isMonsterCard()) {
                continue;
            }
            cards++;
            var eff = s.enchant();
            if (eff.isEmpty()) {
                emptyEnchant++;
                out.println("  ⚠ 怪物卡 " + s.id() + "「" + s.name() + "」没有 [enchant] 效果");
            }
            for (String tag : eff.unknownStatTags()) {
                unknownStat++;
                out.println("  ⚠ 怪物卡 " + s.id() + " 的 [enchant] 里有认不出的标签 " + tag);
            }
            for (String tag : eff.unknownSlotTags()) {
                unknownSlot++;
                unknownSlotTags.merge(tag, 1, Integer::sum);
            }
            if (eff.slots().isEmpty()) {
                noSlot++;
            }
        }
        out.println("怪物卡 " + cards + " 张：[enchant] 空的 " + emptyEnchant
                + " 张，认不出的属性标签 " + unknownStat + " 个，"
                + "认不出的部位标签 " + unknownSlot + " 个 " + unknownSlotTags
                + "，一个可附魔部位都没有的 " + noSlot + " 张");
        if (!unknownSlotTags.isEmpty()) {
            out.println("  （上面这些标签不在 EquipmentSlot 表里，"
                    + "那几张卡这一版附不到任何装备上 —— 是数据本身的状态）");
        }
    }

    private static void checkMultiBox(PrintStream out, PvfArchive a) {
        ScriptList list;
        var known = new java.util.HashSet<Integer>();
        try {
            list = ScriptList.load(a, "stackable/stackable.lst");
            known.addAll(list.entries().keySet());
            known.addAll(ScriptList.load(a, "equipment/equipment.lst").entries().keySet());
        } catch (RuntimeException ex) {
            out.println("魔盒统计跳过 —— " + ex);
            return;
        }
        int boxes = 0;
        int deadEntries = 0;
        int keyMismatch = 0;
        for (Map.Entry<Integer, String> e : list.entries().entrySet()) {
            com.dylan.pvf.script.ItemScript box;
            try {
                box = com.dylan.pvf.script.ItemScript.load(a, e.getKey(), e.getValue());
            } catch (RuntimeException ex) {
                continue;
            }
            if (!box.isMultiBoxItem()) {
                continue;
            }
            boxes++;
            var info = box.multiBox();
            var sizes = new java.util.ArrayList<Integer>();
            int dead = 0;
            for (var pool : info.pools()) {
                sizes.add(pool.entries().size());
                for (var entry : pool.entries()) {
                    if (entry.itemId() > 1 && !known.contains(entry.itemId())) {
                        dead++;
                    }
                }
                if (pool.defaultItemId() > 1 && !known.contains(pool.defaultItemId())) {
                    dead++;
                }
            }
            deadEntries += dead;
            var key = info.key();
            boolean selfOk = info.keys().size() >= 2
                    && info.keys().get(1).itemId() == box.id();
            if (!selfOk) {
                keyMismatch++;
            }
            out.println("  魔盒 " + box.id() + "「" + box.name() + "」池 "
                    + info.pools().size() + " 个" + sizes + "，钥匙 "
                    + (key == null ? "无" : key.itemId() + "×" + key.count())
                    + "，盒子自身 ×" + info.boxCount()
                    + (selfOk ? "" : " ⚠ 第二条不是盒子自己")
                    + (dead == 0 ? "" : " ⚠ 查不到的产出 " + dead + " 条"));
        }
        out.println("魔盒：" + boxes + " 件，池子里查不到的产出 " + deadEntries
                + " 条，钥匙段自检不过 " + keyMismatch + " 件");
    }

    private static void checkItemShops(PrintStream out, PvfArchive a) {
        com.dylan.pvf.script.ItemShopTable shops;
        try {
            shops = com.dylan.pvf.script.ItemShopTable.load(a);
        } catch (RuntimeException ex) {
            out.println("NPC 商店货架加载失败 —— " + ex);
            return;
        }
        out.println("NPC 商店 " + shops);

        Map<Integer, String> itemPaths = new java.util.HashMap<>();
        for (String listPath : List.of("equipment/equipment.lst", "stackable/stackable.lst")) {
            itemPaths.putAll(ScriptList.load(a, listPath).entries());
        }

        int onShelf = 0;
        int missing = 0;
        int noPrice = 0;
        int byFinishPoint = 0;
        int emptyShops = 0;
        int needMaterial = 0;
        int materialNotStackable = 0;
        Map<Integer, Integer> missingByShop = new TreeMap<>();
        for (var shop : shops.shops().values()) {
            if (shop.itemCount() == 0) {
                emptyShops++;
            }
            for (int id : shop.sellItems()) {
                if (id < 0) {
                    continue;
                }
                onShelf++;
                String path = itemPaths.get(id);
                if (path == null) {
                    missing++;
                    missingByShop.merge(shop.shopNo(), 1, Integer::sum);
                    continue;
                }
                try {
                    var script = com.dylan.pvf.script.ItemScript.load(a, id, path);
                    if (script.priceCurrency()
                            != com.dylan.pvf.script.ItemScript.PRICE_IN_GOLD) {
                        byFinishPoint++;
                    } else if (script.shopPrice() <= 0) {
                        noPrice++;
                    }
                    var need = script.needMaterial();
                    if (need.required()) {
                        needMaterial++;
                        String materialPath = itemPaths.get(need.itemId());
                        if (materialPath == null || !com.dylan.pvf.script.ItemScript
                                .load(a, need.itemId(), materialPath).isStackableItem()) {
                            materialNotStackable++;
                        }
                    }
                } catch (RuntimeException ex) {
                    missing++;
                    missingByShop.merge(shop.shopNo(), 1, Integer::sum);
                }
            }
        }
        out.println("  货架上 " + onShelf + " 件商品：查不到脚本 " + missing
                + "，用通关点定价 " + byFinishPoint + "（这一版买不起），"
                + "两种价都没写 " + noPrice + "（客户端显示 0 并且照样能点购买）");
        out.println("  除了钱还要交材料（[need material]）的 " + needMaterial + " 件"
                + (materialNotStackable == 0 ? "，材料全都是可堆叠物品"
                        : "，其中 " + materialNotStackable
                                + " 件的材料查不到脚本或不可堆叠 —— 谁都买不了"));
        out.println("  空货架的店 " + emptyShops + " 家（NPC 立在那儿但什么都不卖，是合法数据）");
        if (!missingByShop.isEmpty()) {
            out.println("  查不到脚本的分布（店号 → 件数）：" + missingByShop);
        }
    }

    private static void checkCashShop(PrintStream out, PvfArchive a) {
        com.dylan.pvf.script.CashShopCatalog catalog;
        try {
            catalog = com.dylan.pvf.script.CashShopCatalog.load(a);
        } catch (RuntimeException ex) {
            out.println("商城目录加载失败 —— " + ex);
            return;
        }
        out.println("商城 " + catalog);

        Map<Integer, String> itemPaths = new java.util.HashMap<>();
        for (String listPath : List.of("equipment/equipment.lst", "stackable/stackable.lst")) {
            itemPaths.putAll(ScriptList.load(a, listPath).entries());
        }

        Map<Integer, Integer> periods = new TreeMap<>();
        Map<Integer, Integer> typeRows = new TreeMap<>();
        Map<Integer, Integer> missingItem = new TreeMap<>();
        int noPrice = 0;
        for (var e : catalog.entries()) {
            if (e.section() == com.dylan.pvf.script.CashShopCatalog.Section.AVATAR) {
                periods.merge(e.periodCode(), 1, Integer::sum);
            }
            if (e.itemId() <= 2) {
                continue;
            }
            String path = itemPaths.get(e.itemId());
            if (path == null) {
                missingItem.merge(e.itemId(), 1, Integer::sum);
                continue;
            }
            try {
                var item = com.dylan.pvf.script.ItemScript.load(a, e.itemId(), path);
                if (e.priceFromItemScript()) {
                    typeRows.merge(item.avatarTypeCount(), 1, Integer::sum);
                }
                if (com.dylan.game.CashShopPricing.ceraPrice(e, item, false) <= 0) {
                    noPrice++;
                    if (noPrice <= 5) {
                        out.println("  ★ 商品 " + e.commodityNo() + "（物品 " + e.itemId()
                                + " " + item.name() + "，期限码 " + e.periodCode()
                                + "）算不出点券价");
                    }
                }
            } catch (RuntimeException ex) {
                missingItem.merge(e.itemId(), 1, Integer::sum);
            }
        }
        out.println("  时装期限码分布 " + periods + "（应当 1/2/3 各占三分之一）");
        out.println("  时装的 [avatar type select] 档数分布 " + typeRows);
        out.println("  算不出点券价的商品 " + noPrice + " 件");
        if (missingItem.isEmpty()) {
            out.println("  全部商品的 itemId 都能在物品表里查到");
        } else {
            out.println("  ★ 查不到的 itemId " + missingItem.size() + " 个："
                    + missingItem.keySet().stream().limit(20).toList());
        }
    }

    private static void checkSkills(PrintStream out, PvfArchive a) {
        com.dylan.pvf.script.SkillTable table;
        try {
            table = com.dylan.pvf.script.SkillTable.load(a);
        } catch (RuntimeException ex) {
            out.println("技能清单加载失败 —— " + ex);
            return;
        }
        int ok = 0;
        int failed = 0;
        int active = 0;
        int maxId = 0;
        for (int job : table.jobs()) {
            for (Map.Entry<Integer, String> e : table.entries(job).entrySet()) {
                try {
                    var skill = table.skill(job, e.getKey()).orElseThrow();
                    ok++;
                    if (skill.isActive()) {
                        active++;
                    }
                    maxId = Math.max(maxId, e.getKey());
                } catch (RuntimeException ex) {
                    failed++;
                    if (failed <= 20) {
                        out.println("技能解析失败 职业 " + job + " 编号 " + e.getKey()
                                + " " + e.getValue() + " —— " + ex);
                    }
                }
            }
        }
        out.println("技能 " + table.jobs().size() + " 个职业共 " + ok + " 个解析通过（主动 "
                + active + "），失败 " + failed + "，最大编号 " + maxId);
    }

    private static void checkItems(PrintStream out, PvfArchive a) {
        for (String listPath : List.of("equipment/equipment.lst", "stackable/stackable.lst")) {
            ScriptList list;
            try {
                list = ScriptList.load(a, listPath);
            } catch (RuntimeException ex) {
                out.println(listPath + " 加载失败 —— " + ex);
                continue;
            }
            int ok = 0;
            int failed = 0;
            int avatars = 0;
            int unknownSlot = 0;
            Map<Integer, Integer> byContainer = new TreeMap<>();
            Map<String, Integer> byAttach = new TreeMap<>();
            for (Map.Entry<Integer, String> e : list.entries().entrySet()) {
                try {
                    var item = com.dylan.pvf.script.ItemScript.load(a, e.getKey(),
                            e.getValue());
                    ok++;
                    if (item.isAvatar()) {
                        avatars++;
                    }
                    byContainer.merge(item.clientContainer(), 1, Integer::sum);
                    byAttach.merge(item.attachTypeLabel(), 1, Integer::sum);
                    if (listPath.startsWith("equipment/")
                            && item.equipmentSlot()
                            == com.dylan.protocol.body.EquipmentSlot.NONE) {
                        unknownSlot++;
                        if (unknownSlot <= 10) {
                            out.println("  ⚠ 认不出装备部位：" + e.getKey() + " " + e.getValue());
                        }
                    }
                } catch (RuntimeException ex) {
                    failed++;
                    if (failed <= 20) {
                        out.println("物品解析失败 " + e.getKey() + " " + e.getValue() + " —— " + ex);
                    }
                }
            }
            out.println(listPath + "：" + ok + " 件解析通过（装扮 " + avatars + "），失败 "
                    + failed + "，认不出部位 " + unknownSlot + "，重复编号 "
                    + list.duplicateIds() + "，容器分布 " + byContainer
                    + "，归属分布 " + byAttach);
        }
    }

    private static void checkQuests(PrintStream out, PvfArchive a) {
        ScriptList list;
        try {
            list = ScriptList.load(a, "quest/quest.lst");
        } catch (RuntimeException ex) {
            out.println("任务清单加载失败 —— " + ex);
            return;
        }
        StringTable strings;
        try {
            strings = StringTable.load(a, "quest/quest.chn.str");
        } catch (RuntimeException ex) {
            out.println("任务文案表加载失败 —— " + ex);
            strings = StringTable.empty();
        }
        int ok = 0;
        int failed = 0;
        int missing = 0;
        for (Map.Entry<Integer, String> e : list.entries().entrySet()) {
            if (!a.contains(e.getValue())) {
                missing++;
                continue;
            }
            try {
                com.dylan.pvf.script.QuestScript.load(a, e.getKey(), e.getValue());
                ok++;
            } catch (RuntimeException ex) {
                failed++;
                if (failed <= 20) {
                    out.println("任务解析失败 " + e.getKey() + " " + e.getValue() + " —— " + ex);
                }
            }
        }
        out.println("任务 " + ok + " 个解析通过，失败 " + failed
                + "，清单里指向不存在文件的 " + missing + "，文案 " + strings.size() + " 条");
    }

    private static void checkMonsters(PrintStream out, PvfArchive a) {
        ScriptList list;
        try {
            list = ScriptList.load(a, com.dylan.pvf.script.MonsterScript.LIST_PATH);
        } catch (RuntimeException ex) {
            out.println("怪物清单加载失败 —— " + ex);
            return;
        }
        int ok = 0;
        int failed = 0;
        int missing = 0;
        for (Map.Entry<Integer, String> e : list.entries().entrySet()) {
            if (!a.contains(e.getValue())) {
                missing++;
                continue;
            }
            try {
                com.dylan.pvf.script.MonsterScript.load(a, e.getKey(), e.getValue());
                ok++;
            } catch (RuntimeException ex) {
                failed++;
                if (failed <= 20) {
                    out.println("怪物解析失败 " + e.getKey() + " " + e.getValue() + " —— " + ex);
                }
            }
        }
        out.println("怪物 " + ok + " 只解析通过，失败 " + failed
                + "，清单里指向不存在文件的 " + missing);
    }

    private static String arg(String[] args, int i, String fallback) {
        return i < args.length ? args[i] : fallback;
    }

    private static String require(String[] args, int i) {
        if (i >= args.length) {
            throw new IllegalArgumentException("这个子命令还需要第 " + (i + 1) + " 个参数");
        }
        return args[i];
    }

    private static int num(String[] args, int i, int fallback) {
        return i < args.length ? Integer.parseInt(args[i]) : fallback;
    }
}
