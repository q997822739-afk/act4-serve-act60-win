package com.dylan.tools;

import com.dylan.game.RoomMonster;
import com.dylan.game.SpawnService;
import com.dylan.net.ServerConfig;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.DungeonMaps;
import com.dylan.pvf.script.DungeonScript;
import com.dylan.pvf.script.GridCode;
import com.dylan.pvf.script.MapScript;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * 离线导出副本结构 + 每间房按 A5 服务逻辑刷出来的怪（伤害分析 / 速通值用，不改任何服务逻辑）。
 * <pre>
 *   java -Dstdout.encoding=UTF-8 -Ds1a5.clientDir=... -cp target\s1a5-0.1.0-SNAPSHOT-all.jar com.dylan.tools.DungeonDump &lt;副本号…|all&gt; [每张图抽样次数=20]
 * </pre>
 * 每个非任务迷宫：网格、起点、Boss 房、起点→Boss 最短路（门要两边都开），路上每格的候选地图
 * （起点 = startMap 池、Boss = [boss map specification] 或 Boss 图池、其它 = [map specification] 或普通图池，
 * 都按进门方向，和 BattleField.getCandidatorMap 一样），每张候选图跑 N 次
 * SpawnService.spawn + addPassiveMonsters + addAiCharacters（深渊 / 冠军 / 活动怪不加）。
 * 输出一行一个 JSON。
 */
public final class DungeonDump {
    private DungeonDump() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int samples = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        try (GameScripts scripts = GameScripts.load(ServerConfig.PVF_PATH)) {
            List<Integer> ids = new ArrayList<>();
            if (args.length == 0 || args[0].equals("all")) {
                for (int i = 1; i < 12000; i++) {
                    if (scripts.dungeon(i).isPresent()) {
                        ids.add(i);
                    }
                }
            } else {
                for (String s : args[0].split(",")) {
                    ids.add(Integer.parseInt(s.trim()));
                }
            }
            for (int id : ids) {
                DungeonScript d = scripts.dungeon(id).orElse(null);
                if (d == null) {
                    continue;
                }
                try {
                    dump(out, scripts, d, samples);
                } catch (RuntimeException e) {
                    out.println("{\"dungeon\":" + id + ",\"error\":" + q(String.valueOf(e)) + "}");
                }
            }
        }
    }

    private static void dump(PrintStream out, GameScripts scripts, DungeonScript d, int samples) {
        DungeonMaps maps = scripts.mapsFor(d.id());
        SpawnService spawner = new SpawnService(scripts);
        SplittableRandom random = new SplittableRandom(d.id());
        StringBuilder sb = new StringBuilder();
        sb.append("{\"dungeon\":").append(d.id()).append(",\"path\":").append(q(d.path()))
                .append(",\"min_level\":").append(d.minimumLevel())
                .append(",\"basis_level\":").append(d.basisLevel())
                .append(",\"champions\":").append(d.championCounts())
                .append(",\"mazes\":[");
        boolean firstMaze = true;
        for (DungeonScript.Maze m : d.mazes()) {
            if (m.questConnection() != null && m.isQuestMaze()) {
                continue;
            }
            if (!firstMaze) {
                sb.append(',');
            }
            firstMaze = false;
            sb.append("{\"index\":").append(m.index()).append(",\"w\":").append(m.width())
                    .append(",\"h\":").append(m.height()).append(",\"grid\":").append(m.gridCodes())
                    .append(",\"start\":").append(rooms(m.startRooms()))
                    .append(",\"boss\":").append(rooms(m.bossRooms()));
            List<int[]> path = shortest(m);
            sb.append(",\"route\":[");
            for (int i = 0; i < path.size(); i++) {
                int[] c = path.get(i);
                int dir = i == 0 ? 0 : GridCode.invadeDirection(path.get(i - 1)[0], path.get(i - 1)[1], c[0], c[1]);
                int code = m.gridCode(c[0], c[1]);
                List<Integer> cand;
                String kind;
                if (i == 0) {
                    kind = "start";
                    cand = maps.startCandidates(code);
                } else if (i == path.size() - 1) {
                    kind = "boss";
                    cand = m.bossSpecifiedMaps(c[0], c[1]);
                    if (cand.isEmpty()) {
                        cand = maps.candidates(DungeonMaps.TYPE_BOSS, code, dir);
                    }
                } else {
                    kind = "normal";
                    cand = m.specifiedMaps(c[0], c[1]);
                    if (cand.isEmpty()) {
                        cand = maps.candidates(DungeonMaps.TYPE_NORMAL, code, dir);
                    }
                }
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"x\":").append(c[0]).append(",\"y\":").append(c[1]).append(",\"kind\":").append(q(kind))
                        .append(",\"dir\":").append(dir).append(",\"maps\":[");
                boolean firstMap = true;
                for (int mapId : new java.util.LinkedHashSet<>(cand)) {
                    MapScript map = scripts.map(mapId).orElse(null);
                    if (map == null) {
                        continue;
                    }
                    if (!firstMap) {
                        sb.append(',');
                    }
                    firstMap = false;
                    sb.append("{\"id\":").append(mapId).append(",\"path\":").append(q(map.path())).append(",\"samples\":[");
                    for (int s = 0; s < samples; s++) {
                        List<RoomMonster> mons = spawner.spawn(map, d.basisLevel(), random, 0);
                        mons = SpawnService.addPassiveMonsters(map, mons, random);
                        mons = spawner.addAiCharacters(map, mons);
                        if (s > 0) {
                            sb.append(',');
                        }
                        sb.append('[');
                        for (int k = 0; k < mons.size(); k++) {
                            RoomMonster r = mons.get(k);
                            if (k > 0) {
                                sb.append(',');
                            }
                            sb.append('[').append(r.monsterId()).append(',').append(r.level()).append(',')
                                    .append(r.type()).append(',').append(r.named()).append(',')
                                    .append(r.passiveObject()).append(',').append(r.x()).append(',').append(r.y()).append(']');
                        }
                        sb.append(']');
                    }
                    sb.append("]}");
                }
                sb.append("]}");
            }
            sb.append("]}");
        }
        sb.append("]}");
        out.println(sb);
    }

    private static String rooms(List<DungeonScript.Room> rs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rs.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('[').append(rs.get(i).x()).append(',').append(rs.get(i).y()).append(']');
        }
        return sb.append(']').toString();
    }

    /** 所有起点到所有 Boss 房里最短的一条（BFS；相邻两格都朝对方开门才算通）。 */
    private static List<int[]> shortest(DungeonScript.Maze m) {
        List<int[]> best = List.of();
        for (DungeonScript.Room st : m.startRooms()) {
            if (st.isNone()) {
                continue;
            }
            Map<Long, long[]> prev = new HashMap<>();
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            queue.add(new int[]{st.x(), st.y()});
            prev.put(key(st.x(), st.y()), null);
            while (!queue.isEmpty()) {
                int[] c = queue.poll();
                int code = m.gridCode(c[0], c[1]);
                int[][] steps = {{1, 0, GridCode.DIR_EAST, GridCode.DIR_WEST}, {0, -1, GridCode.DIR_NORTH, GridCode.DIR_SOUTH},
                        {-1, 0, GridCode.DIR_WEST, GridCode.DIR_EAST}, {0, 1, GridCode.DIR_SOUTH, GridCode.DIR_NORTH}};
                for (int[] st2 : steps) {
                    int nx = c[0] + st2[0];
                    int ny = c[1] + st2[1];
                    if ((code & st2[2]) == 0 || (m.gridCode(nx, ny) & st2[3]) == 0 || prev.containsKey(key(nx, ny))) {
                        continue;
                    }
                    prev.put(key(nx, ny), new long[]{c[0], c[1]});
                    queue.add(new int[]{nx, ny});
                }
            }
            for (DungeonScript.Room b : m.bossRooms()) {
                if (b.isNone() || !prev.containsKey(key(b.x(), b.y()))) {
                    continue;
                }
                List<int[]> path = new ArrayList<>();
                long[] at = {b.x(), b.y()};
                while (at != null) {
                    path.add(0, new int[]{(int) at[0], (int) at[1]});
                    at = prev.get(key((int) at[0], (int) at[1]));
                }
                if (best.isEmpty() || path.size() < best.size()) {
                    best = path;
                }
            }
        }
        return best;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static String q(String s) {
        return "\"" + (s == null ? "" : s.replace("\\", "/").replace("\"", "'")) + "\"";
    }
}
