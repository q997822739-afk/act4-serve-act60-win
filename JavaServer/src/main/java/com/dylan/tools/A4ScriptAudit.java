package com.dylan.tools;

import com.dylan.net.ServerConfig;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.script.*;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * 把 A5 的每个脚本加载器对一份 Script.pvf 各跑一遍，不在第一个错误处停下。
 * GameScripts.load 会把大部分表的失败吞掉（留 null/空表），服务端起得来不代表脚本读对了，所以这里逐个直接调。
 *
 *   java -Dstdout.encoding=UTF-8 -Ds1a5.clientDir=... -cp target\s1a5-0.1.0-SNAPSHOT-all.jar com.dylan.tools.A4ScriptAudit
 */
public final class A4ScriptAudit {

    /** 同类错误只按"去掉数字和路径后的消息"归一组，每组留几个例子。 */
    private static final class Failures {
        final String what;
        int ok;
        int total;
        final Map<String, List<String>> byShape = new TreeMap<>();

        Failures(String what) {
            this.what = what;
        }

        void run(String id, Runnable r) {
            total++;
            try {
                r.run();
                ok++;
            } catch (RuntimeException e) {
                String msg = rootMessage(e);
                String shape = msg.replaceAll("`[^`]*`", "`…`")
                        .replaceAll("[A-Za-z0-9_\\-./]+\\.(?:equ|stk|mob|map|dgn|qst|skl|aic|twn|lst|etc|tbl|str|cre|wmp)", "<路径>")
                        .replaceAll("-?\\d+(?:\\.\\d+)?", "N");
                List<String> examples = byShape.computeIfAbsent(shape, k -> new java.util.ArrayList<>());
                if (examples.size() < 3) {
                    examples.add(id + " → " + msg);
                } else {
                    examples.add(null);
                }
            }
        }

        void print() {
            String status = byShape.isEmpty() ? "OK" : "失败 " + (total - ok);
            System.out.printf("%-34s %6d/%-6d %s%n", what, ok, total, status);
            for (var e : byShape.entrySet()) {
                long count = e.getValue().size();
                System.out.println("    [" + count + "] " + e.getKey());
                e.getValue().stream().filter(java.util.Objects::nonNull)
                        .forEach(x -> System.out.println("        例 " + x));
            }
        }
    }

    private static String rootMessage(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (sb.length() > 0) {
                sb.append(" ← ");
            }
            sb.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
            if (t.getCause() == t) {
                break;
            }
        }
        String s = sb.toString();
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }

    private static final Map<String, Failures> RESULTS = new LinkedHashMap<>();

    private static void table(String what, Supplier<?> loader) {
        Failures f = new Failures(what);
        f.run(what, () -> loader.get());
        RESULTS.put(what, f);
    }

    private static void perFile(PvfArchive archive, String what, String listPath,
                                BiConsumer<Integer, String> loader) {
        Failures f = new Failures(what);
        ScriptList list;
        try {
            list = ScriptList.load(archive, listPath);
        } catch (RuntimeException e) {
            f.run(listPath, () -> {
                throw e;
            });
            RESULTS.put(what, f);
            return;
        }
        for (var e : list.entries().entrySet()) {
            f.run(e.getKey() + " " + e.getValue(), () -> loader.accept(e.getKey(), e.getValue()));
        }
        RESULTS.put(what, f);
    }

    public static void main(String[] args) throws Exception {
        Path pvf = args.length > 0 ? Path.of(args[0]) : ServerConfig.PVF_PATH;
        System.out.println("审计 " + pvf);
        try (PvfArchive archive = PvfArchive.open(pvf)) {
            System.out.println("GUID " + archive.guid() + "，文件 " + archive.size());
            System.out.println();
            System.out.println("== .lst（忽略的悬空编号见上面的输出）");

            perFile(archive, "character/*.chr", GameScripts.CHARACTER_LIST,
                    (id, p) -> CharacterScript.load(archive, id, p));
            perFile(archive, "quest/*.qst", GameScripts.QUEST_LIST,
                    (id, p) -> QuestScript.load(archive, id, p));
            perFile(archive, "map/*.map", GameScripts.MAP_LIST,
                    (id, p) -> MapScript.load(archive, p));
            perFile(archive, "dungeon/*.dgn", GameScripts.DUNGEON_LIST,
                    (id, p) -> DungeonScript.load(archive, id, p));
            perFile(archive, "equipment/*.equ", GameScripts.EQUIPMENT_LIST,
                    (id, p) -> ItemScript.load(archive, id, p));
            perFile(archive, "stackable/*.stk", GameScripts.STACKABLE_LIST,
                    (id, p) -> ItemScript.load(archive, id, p));
            perFile(archive, "monster/*.mob", MonsterScript.LIST_PATH,
                    (id, p) -> MonsterScript.load(archive, id, p));
            perFile(archive, "aicharacter/*.aic", AiCharacterScript.LIST_PATH,
                    (id, p) -> AiCharacterScript.load(archive, id, p));
            perFile(archive, "creature/*", CreatureScript.LIST_PATH,
                    (id, p) -> CreatureScript.load(archive, id, p));
            perFile(archive, "worldmap/*", WorldMapScript.LIST_PATH,
                    (id, p) -> WorldMapScript.load(archive, id, p));
            perFile(archive, "town/*.twn", TownTable.LIST_PATH,
                    (id, p) -> TownScript.load(archive, id, p));
            perFile(archive, "character/expertjob", ExpertJobScript.LIST_PATH,
                    (id, p) -> ExpertJobScript.load(archive, id, p));

            Failures skills = new Failures("skill/*.skl");
            try {
                SkillTable table = SkillTable.load(archive);
                for (int job : table.jobs()) {
                    ScriptList jobList = ScriptList.load(archive,
                            ScriptList.load(archive, SkillTable.SKILL_LIST).path(job).orElseThrow());
                    for (var e : jobList.entries().entrySet()) {
                        skills.run(job + "/" + e.getKey() + " " + e.getValue(),
                                () -> SkillScript.load(archive, job, e.getKey(), e.getValue()));
                    }
                }
            } catch (RuntimeException e) {
                skills.run(SkillTable.SKILL_LIST, () -> {
                    throw e;
                });
            }
            RESULTS.put(skills.what, skills);

            table("SkillTable", () -> SkillTable.load(archive));
            table("TownTable", () -> TownTable.load(archive));
            table("WorldMapTable", () -> WorldMapTable.load(archive));
            table("DropInfo monster", () -> DropInfoTable.load(archive, DropInfoTable.PATH_MONSTER));
            table("DropInfo object", () -> DropInfoTable.load(archive, DropInfoTable.PATH_OBJECT));
            table("GoldDropTable", () -> GoldDropTable.load(archive));
            table("HellDropTable", () -> HellDropTable.load(archive));
            table("WorldDropScript", () -> WorldDropScript.load(archive));
            table("ClearRewardTable", () -> ClearRewardTable.load(archive));
            table("ExpTable", () -> ExpTable.load(archive));
            table("ServerParameter", () -> ServerParameter.load(archive));
            table("QuestParameterScript", () -> QuestParameterScript.load(archive));
            table("QuestMappingTable", () -> QuestMappingTable.load(archive));
            table("StringTable quest", () -> StringTable.load(archive, GameScripts.QUEST_STRINGS));
            table("StringTable etc", () -> StringTable.load(archive, GameScripts.ETC_STRINGS));
            table("DeathTowerTable", () -> DeathTowerTable.load(archive));
            table("HellPartyTable", () -> HellPartyTable.load(archive));
            table("CashShopCatalog", () -> CashShopCatalog.load(archive));
            table("PremiumCatalog", () -> PremiumCatalog.load(archive));
            table("UpgradeTable", () -> UpgradeTable.load(archive));
            table("DisjointTable", () -> DisjointTable.load(archive));
            table("ResealTable", () -> ResealTable.load(archive));
            table("EmotionTable", () -> EmotionTable.load(archive));
            table("InvalidNameTable", () -> InvalidNameTable.load(archive));
            table("SlangNameTable", () -> SlangNameTable.load(archive));
            table("MagicEnchantScript", () -> MagicEnchantScript.load(archive));
            table("ExpertJobEtcScript", () -> ExpertJobEtcScript.load(archive));
            table("CompoundAvatarTable", () -> CompoundAvatarTable.load(archive));
            table("PriceTable", () -> PriceTable.load(archive));
            table("ItemShopTable", () -> ItemShopTable.load(archive));
            table("CreatureScriptTable", () -> CreatureScriptTable.load(archive, Map.of()));

            System.out.println();
            System.out.println("== 结果");
            RESULTS.values().forEach(Failures::print);

            System.out.println();
            System.out.println("== 整体 GameScripts.load");
            try (GameScripts all = GameScripts.load(pvf)) {
                System.out.println("OK，物品 " + all.itemCount());
            } catch (RuntimeException e) {
                System.out.println("失败：" + rootMessage(e));
            }
        }
    }
}
