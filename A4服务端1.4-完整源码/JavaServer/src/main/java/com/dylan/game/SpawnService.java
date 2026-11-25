package com.dylan.game;

import com.dylan.net.ServerConfig;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.HellPartyTable;
import com.dylan.pvf.script.MapScript;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.random.RandomGenerator;

public final class SpawnService {

    public static final int SEQ_ID_BOUND = 60000;

    public static final int RANDOM_SEQ_BASE = -1;

    public static final int MAX_MONSTERS = 255;

    private final GameScripts scripts;

    public SpawnService(GameScripts scripts) {
        this.scripts = scripts;
    }

    public List<RoomMonster> spawn(int mapIndex, int basisLevel, RandomGenerator random) {
        return spawn(mapIndex, basisLevel, random, RANDOM_SEQ_BASE);
    }

    public List<RoomMonster> spawn(int mapIndex, int basisLevel, RandomGenerator random,
                                   int seqBase) {
        return spawn(mapIndex, basisLevel, random, seqBase, List.of());
    }

    public List<RoomMonster> spawn(int mapIndex, int basisLevel, RandomGenerator random,
                                   int seqBase, List<Integer> conditionMonsterIds) {
        if (scripts == null) {
            return List.of();
        }
        MapScript map = scripts.map(mapIndex).orElse(null);
        if (map == null) {
            throw new IllegalStateException("map.lst 里没有地图编号 " + mapIndex
                    + " —— 选图选出了一个不存在的编号");
        }
        return spawn(map, basisLevel, random, seqBase, conditionMonsterIds);
    }

    public List<RoomMonster> spawn(MapScript map, int basisLevel, RandomGenerator random) {
        return spawn(map, basisLevel, random, RANDOM_SEQ_BASE);
    }

    public List<RoomMonster> spawn(MapScript map, int basisLevel, RandomGenerator random,
                                   int seqBase) {
        return spawn(map, basisLevel, random, seqBase, List.of());
    }

    public List<RoomMonster> spawn(MapScript map, int basisLevel, RandomGenerator random,
                                   int seqBase, List<Integer> conditionMonsterIds) {
        return spawnMonsterRows(map, basisLevel,
                seqBase >= 0 ? seqBase : random.nextInt(SEQ_ID_BOUND),
                conditionRowsOf(map, conditionMonsterIds));
    }

    private static List<MapScript.MonsterSpawn> conditionRowsOf(
            MapScript map, List<Integer> conditionMonsterIds) {
        if (map == null || conditionMonsterIds.isEmpty()) {
            return List.of();
        }
        List<MapScript.MonsterSpawn> out = new ArrayList<>(conditionMonsterIds.size());
        for (int monsterId : conditionMonsterIds) {
            List<MapScript.MonsterSpawn> rows = map.conditionMonstersOf(monsterId);
            if (rows.isEmpty()) {
                continue;
            }
            out.addAll(rows);
        }
        return out;
    }

    private List<RoomMonster> spawnMonsterRows(MapScript map, int basisLevel, int seqId) {
        return spawnMonsterRows(map, basisLevel, seqId, List.of());
    }

    private List<RoomMonster> spawnMonsterRows(MapScript map, int basisLevel, int seqId,
                                               List<MapScript.MonsterSpawn> conditionRows) {
        if (map.monsters().isEmpty() && conditionRows.isEmpty()) {
            return List.of();
        }

        List<MapScript.MonsterSpawn> rows = map.monsters();
        int repeat = Math.max(1, ServerConfig.SPAWN_REPEAT);
        int count = Math.min(rows.size() * repeat, MAX_MONSTERS);
        if (rows.size() * repeat > count) {
            throw new IllegalStateException(map.path() + " 的怪有 " + rows.size() * repeat
                    + " 只（" + rows.size() + " 行 x " + repeat + " 遍），超过一个房间能发的 "
                    + MAX_MONSTERS + " 只");
        }

        List<RoomMonster> out = new ArrayList<>(count + conditionRows.size());
        for (int i = 0; i < count; i++) {
            MapScript.MonsterSpawn row = rows.get(i % rows.size());
            out.add(new RoomMonster(i % rows.size(), seqId++, row.monsterId(),
                    row.levelFor(basisLevel), row.wireType(),
                    row.x(), row.y(),
                    row.randomDropCount(), row.specifyDropCount(), row.camp(),
                    row.fakeBoss()));
        }
        for (int k = 0; k < conditionRows.size(); k++) {
            MapScript.MonsterSpawn row = conditionRows.get(k);
            out.add(new RoomMonster(rows.size() + k, seqId++, row.monsterId(),
                    row.levelFor(basisLevel), row.wireType(),
                    row.x(), row.y(),
                    row.randomDropCount(), row.specifyDropCount(), row.camp()));
        }
        if (out.size() > MAX_MONSTERS) {
            throw new IllegalStateException(map.path() + " 加上 " + conditionRows.size()
                    + " 只 [monster condition] 前置怪之后有 " + out.size()
                    + " 只，超过一个房间能发的 " + MAX_MONSTERS + " 只");
        }
        return out;
    }

    public List<RoomMonster> addAiCharacters(MapScript map, List<RoomMonster> existing) {
        if (map == null || !map.hasAiCharacters() || scripts == null) {
            return existing;
        }
        int seqId = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).seqId() + 1;
        List<RoomMonster> out = new ArrayList<>(existing);
        int spawnIndex = 0;
        for (MapScript.AiCharacterSpawn row : map.aiCharacters()) {
            var apc = scripts.aiCharacter(row.apcIndex()).orElse(null);
            if (apc == null) {
                throw new IllegalStateException(map.path() + " 的 [ai character] 引用了 "
                        + com.dylan.pvf.script.AiCharacterScript.LIST_PATH
                        + " 里没有的 APC " + row.apcIndex());
            }
            if (out.size() >= MAX_MONSTERS) {
                throw new IllegalStateException(map.path() + " 已经有 " + out.size()
                        + " 只怪，放不下 [ai character] 剩下的了 —— 不许静默截断");
            }
            out.add(new RoomMonster(spawnIndex++, seqId++, row.apcIndex(),
                    Math.clamp(apc.level(), 1, 0xFF),
                    row.roleTypeValue(),
                    row.x(), row.y(),
                    row.flag1(), row.flag2(),
                    0, 0, 0, 0,
                    row.campValue()));
        }
        return out;
    }

    /** 台服 {@code CMap::set_map}：`[monster]` 动作等级不在 1..200 就不放这只怪。 */
    private static final int PASSIVE_MONSTER_MAX_LEVEL = 200;

    /**
     * 物件打碎后冒出来的怪（深渊房封印柱、木桶……）。原版 A5 只留 [special passive object] 的
     * `[item]` 动作，`[monster]` 整条丢掉，NOTI 29 没这只怪，客户端打碎柱子什么也不出。
     *
     * <p>照台服 {@code CMap::set_map}（建房时每个物件每条 `[monster]` 掷一次）：
     * 分母为 0 跳过；{@code rand(100) > 分子*100/分母} 跳过；等级 = 动作里的等级（不跟房间基准等级）。
     * 官方 A4 实抓两间深渊房（hell_whitenight 1051 Lv33、hell_underfoot 1052 Lv54）：
     * 排在普通怪后面，spawnIndex 0，type 0，named 1，namedGroup = 物件顺序号。
     */
    public static List<RoomMonster> addPassiveMonsters(MapScript map, List<RoomMonster> existing,
                                                       RandomGenerator random) {
        if (map == null || !ServerConfig.PASSIVE_MONSTERS || map.passiveMonsters().isEmpty()) {
            return existing;
        }
        int seqId = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).seqId() + 1;
        List<RoomMonster> out = new ArrayList<>(existing);
        for (MapScript.PassiveMonsterSpawn row : map.passiveMonsters()) {
            if (row.chanceDen() == 0
                    || random.nextInt(100) > row.chanceNum() * 100 / row.chanceDen()
                    || row.level() <= 0 || row.level() > PASSIVE_MONSTER_MAX_LEVEL
                    || row.objectIndex() > 0xFF || row.monsterId() <= 0
                    || row.monsterId() > 0xFFFF) {
                continue;
            }
            if (out.size() >= MAX_MONSTERS) {
                throw new IllegalStateException(map.path() + " 已经有 " + out.size()
                        + " 只怪，放不下物件 " + row.objectIndex() + " 的怪 —— 不许静默截断");
            }
            out.add(new RoomMonster(0, seqId++, row.monsterId(),
                    Math.min(row.level(), 0xFF), MapScript.TYPE_NORMAL, 0, 0, 0, 0,
                    0, 0, 0, 0).withPassiveObject(row.objectIndex()));
        }
        return out;
    }

    public static final int EVENT_MONSTER_SPAWN_INDEX_BASE = 10000;

    public static List<RoomMonster> addEventMonsters(
            MapScript map, List<RoomMonster> existing,
            List<BattleField.EventMonsterPlacement> placements, int standardLevel) {
        if (map == null || placements.isEmpty()) {
            return existing;
        }
        int positions = map.eventMonsterPositionCount();
        if (positions <= 0) {
            return existing;
        }
        int seqId = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).seqId() + 1;
        int spawnIndex = EVENT_MONSTER_SPAWN_INDEX_BASE;
        List<RoomMonster> out = new ArrayList<>(existing);
        for (BattleField.EventMonsterPlacement p : placements) {
            for (int i = 0; i < p.row().count(); i++) {
                if (out.size() >= MAX_MONSTERS) {
                    throw new IllegalStateException("事件怪：" + map.path() + " 已经有 "
                            + out.size() + " 只怪，放不下了 —— 不许静默截断");
                }
                out.add(new RoomMonster(spawnIndex, seqId++, p.row().monsterId(),
                        Math.clamp(standardLevel, 1, 0xFF), MapScript.TYPE_NORMAL, 0, 0,
                        p.row().randomDropCount(), p.row().specifyDropCount(),
                        0, 0, 0, 0));
                if (++spawnIndex >= EVENT_MONSTER_SPAWN_INDEX_BASE + positions) {
                    spawnIndex = EVENT_MONSTER_SPAWN_INDEX_BASE;
                }
            }
        }
        return out;
    }

    public static List<RoomMonster> addAdditionalChampions(List<RoomMonster> monsters, int count,
                                                           RandomGenerator random) {
        if (count <= 0 || monsters.isEmpty()) {
            return monsters;
        }
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < monsters.size(); i++) {
            RoomMonster m = monsters.get(i);
            if (m.type() == MapScript.TYPE_NORMAL && !m.isNamedMonster()
                    && !m.isPassiveBound()) {
                candidates.add(i);
            }
        }
        List<RoomMonster> out = new ArrayList<>(monsters);
        int promoted = 0;
        for (int i = 0; i < count && !candidates.isEmpty(); i++) {
            int pick = random.nextInt(candidates.size());
            int at = candidates.remove(pick);
            RoomMonster m = out.get(at);
            out.set(at, m.withType(MapScript.TYPE_CHAMPION));
            promoted++;
        }
        return out;
    }

    public static final int RANDOM_APC_SPAWN_INDEX_BASE = 64;

    private static final int MAX_APC_ROLLS = 1000;

    public List<RoomMonster> spawnTowerStage(MapScript map, int basisLevel,
                                             RandomGenerator random, int seqBase) {
        int seqId = seqBase >= 0 ? seqBase : random.nextInt(SEQ_ID_BOUND);
        List<RoomMonster> out = new ArrayList<>();
        Set<Integer> used = new HashSet<>();

        boolean hasRandomApc = map.apcRandomPoint() > 0;
        NavigableMap<Integer, Integer> counts = hasRandomApc
                ? scripts.aiCharacters().countByAppearancePoint()
                : new TreeMap<>();

        if (map.hasAiCharacters()) {
            int spawnIndex = 0;
            for (MapScript.AiCharacterSpawn row : map.aiCharacters()) {
                var apc = scripts.aiCharacter(row.apcIndex()).orElse(null);
                if (apc == null) {
                    throw new IllegalStateException(map.path() + " 的 [ai character] 引用了 "
                            + com.dylan.pvf.script.AiCharacterScript.LIST_PATH
                            + " 里没有的 APC " + row.apcIndex());
                }
                used.add(row.apcIndex());
                if (apc.canAppearRandomly()) {
                    counts.computeIfPresent(apc.appearancePoint(),
                            (point, count) -> Math.max(0, count - 1));
                }
                out.add(apcMonster(spawnIndex++, seqId++, row.apcIndex(), apc.level(),
                        row.campValue(), row.x(), row.y()));
            }
        }

        boolean spawnMonsterSection = true;
        if (hasRandomApc) {
            spawnMonsterSection = false;
            var list = scripts.aiCharacters();
            int budget = map.apcRandomPoint();
            int spawnIndex = RANDOM_APC_SPAWN_INDEX_BASE;
            out.size();
            for (int roll = 0; ; roll++) {
                if (roll >= MAX_APC_ROLLS) {
                    throw new IllegalStateException(map.path() + " 的随机 APC 转了 " + roll
                            + " 圈还没收敛（剩 " + budget + " 点预算）—— .aic 的出场点数表有问题");
                }
                if (list.minAppearancePoint() > budget) {
                    spawnMonsterSection = true;
                    break;
                }
                var apc = list.getByAP(random.nextInt(budget) + 1, random);
                if (apc == null) {
                    break;
                }
                if (used.contains(apc.id())) {
                    int affordable = 0;
                    for (Integer count : counts.headMap(budget, true).values()) {
                        affordable += count;
                    }
                    if (affordable == 0) {
                        spawnMonsterSection = true;
                        break;
                    }
                    continue;
                }
                used.add(apc.id());
                counts.computeIfPresent(apc.appearancePoint(),
                        (point, count) -> Math.max(0, count - 1));
                budget -= apc.appearancePoint();
                out.add(apcMonster(spawnIndex++, seqId++, apc.id(), apc.level(),
                        MapScript.CAMP_MONSTER, 0, 0));
            }
        }

        if (spawnMonsterSection) {
            out.addAll(spawnMonsterRows(map, basisLevel, seqId));
        }
        return out;
    }

    private static RoomMonster apcMonster(int spawnIndex, int seqId, int apcIndex, int level,
                                          int camp, int x, int y) {
        return new RoomMonster(spawnIndex, seqId, apcIndex,
                Math.clamp(level, 1, 0xFF), RoomMonster.TYPE_APC,
                x, y, 0, 0, 0, 0, 0, 0, camp);
    }

    public List<RoomMonster> addHellPartyMonsters(MapScript map, List<RoomMonster> normal,
                                                  int standardLevel, int difficulty,
                                                  RandomGenerator random,
                                                  Map<Integer, Integer> groupsOut) {
        return addHellPartyMonsters(map, normal, standardLevel, difficulty, random, groupsOut,
                scripts == null ? null : scripts.hellParty());
    }

    public static List<RoomMonster> addHellPartyMonsters(MapScript map, List<RoomMonster> normal,
                                                         int standardLevel, int difficulty,
                                                         RandomGenerator random,
                                                         Map<Integer, Integer> groupsOut,
                                                         HellPartyTable table) {
        if (map == null || !map.hasHellParty()) {
            return normal;
        }
        if (table == null || table.isEmpty()) {
            throw new IllegalStateException(map.path() + " 有 [hellparty] 段，但 "
                    + HellPartyTable.PATH + " 读不出来");
        }

        int spawnIndex = map.monsters().size();
        int seqId = normal.isEmpty() ? 0 : normal.get(normal.size() - 1).seqId() + 1;
        HellPartyTable.Difficulty grade = table.difficulty(difficulty);
        int specifyDropCount = grade == null ? 0 : grade.dropRoutineCount();

        List<RoomMonster> out = new ArrayList<>(normal);
        int accumulated = 0;
        for (Map.Entry<Integer, List<MapScript.HellPartyGroup>> wave
                : groupsByOrder(map).entrySet()) {
            int rateSum = 0;
            for (MapScript.HellPartyGroup entry : wave.getValue()) {
                if (gradeOf(table, entry, map) == difficulty) {
                    rateSum += entry.rate();
                }
            }
            int roll = rateSum <= 0 ? 0 : random.nextInt(rateSum);
            for (MapScript.HellPartyGroup entry : wave.getValue()) {
                if (gradeOf(table, entry, map) != difficulty) {
                    continue;
                }
                accumulated += entry.rate();
                if (accumulated < roll) {
                    continue;
                }
                HellPartyTable.MonsterGroup group = table.monsterGroups().get(entry.groupIndex());
                if (group == null) {
                    throw new IllegalStateException("深渊派对：" + map.path() + " 第 "
                            + wave.getKey() + " 波要的组 " + entry.groupIndex() + " 在 "
                            + HellPartyTable.PATH + " 里没有");
                }
                int spawned = 0;
                for (HellPartyTable.Monster one : group.monsters()) {
                    if (out.size() >= MAX_MONSTERS) {
                        throw new IllegalStateException("深渊派对：" + map.path() + " 已经有 "
                                + out.size() + " 只怪，放不下第 " + wave.getKey() + " 波的组 "
                                + entry.groupIndex() + " 了 —— 不许静默截断");
                    }
                    out.add(new RoomMonster(spawnIndex++, seqId++, one.index(), standardLevel,
                            one.kind() != 0 ? RoomMonster.TYPE_APC : MapScript.TYPE_NORMAL,
                            0, 0, HELL_RANDOM_DROP_COUNT, specifyDropCount,
                            wave.getKey(), entry.groupIndex(), RoomMonster.NAMED_HELL_PARTY,
                            difficulty));
                    spawned++;
                }
                groupsOut.putIfAbsent(entry.groupIndex(), spawned - 1);
                accumulated = 0;
                break;
            }
        }
        return out;
    }

    private static final int HELL_RANDOM_DROP_COUNT = 1;

    private static java.util.SortedMap<Integer, List<MapScript.HellPartyGroup>>
            groupsByOrder(MapScript map) {
        java.util.SortedMap<Integer, List<MapScript.HellPartyGroup>> waves =
                new java.util.TreeMap<>();
        for (MapScript.HellPartyGroup entry : map.hellPartyGroups()) {
            waves.computeIfAbsent(entry.order(), k -> new ArrayList<>()).add(entry);
        }
        return waves;
    }

    private static int gradeOf(HellPartyTable table, MapScript.HellPartyGroup entry,
                              MapScript map) {
        HellPartyTable.MonsterGroup group = table.monsterGroups().get(entry.groupIndex());
        if (group == null) {
            throw new IllegalStateException("深渊派对：" + map.path() + " 引用了 "
                    + HellPartyTable.PATH + " 里没有的组 " + entry.groupIndex());
        }
        return group.grade();
    }
}
