package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public record DungeonScript(int id, String path, int minimumLevel, int basisLevel,
                            int dummyAppearCount, List<Maze> mazes, WarRoom warRoom,
                            String dungeonType, Tower tower,
                            int coinLimit, List<RequiredItem> requiredItems,
                            List<MonsterScript.DropEntry> dungeonDrop,
                            boolean ancientDungeon, boolean hellDungeon,
                            List<Integer> championCounts,
                            int eventMonsterTryCount, int eventMonsterProb,
                            List<EventMonster> eventMonsters,
                            double expWeight, List<AssignItem> assignItems,
                            BossRoomCondition bossRoomCondition,
                            CoinQuota coinQuota) {
    public DungeonScript(int id, String path, int minimumLevel, int basisLevel,
                         int dummyAppearCount, List<Maze> mazes, WarRoom warRoom,
                         String dungeonType, Tower tower,
                         int coinLimit, List<RequiredItem> requiredItems,
                         List<MonsterScript.DropEntry> dungeonDrop,
                         boolean ancientDungeon, boolean hellDungeon,
                         List<Integer> championCounts,
                         int eventMonsterTryCount, int eventMonsterProb,
                         List<EventMonster> eventMonsters, double expWeight,
                         List<AssignItem> assignItems) {
        this(id, path, minimumLevel, basisLevel, dummyAppearCount, mazes, warRoom, dungeonType,
                tower, coinLimit, requiredItems, dungeonDrop, ancientDungeon, hellDungeon,
                championCounts, eventMonsterTryCount, eventMonsterProb, eventMonsters,
                expWeight, assignItems, BossRoomCondition.NONE);
    }

    public DungeonScript(int id, String path, int minimumLevel, int basisLevel,
                         int dummyAppearCount, List<Maze> mazes, WarRoom warRoom,
                         String dungeonType, Tower tower,
                         int coinLimit, List<RequiredItem> requiredItems,
                         List<MonsterScript.DropEntry> dungeonDrop,
                         boolean ancientDungeon, boolean hellDungeon,
                         List<Integer> championCounts,
                         int eventMonsterTryCount, int eventMonsterProb,
                         List<EventMonster> eventMonsters, double expWeight) {
        this(id, path, minimumLevel, basisLevel, dummyAppearCount, mazes, warRoom, dungeonType,
                tower, coinLimit, requiredItems, dungeonDrop, ancientDungeon, hellDungeon,
                championCounts, eventMonsterTryCount, eventMonsterProb, eventMonsters,
                expWeight, List.of());
    }

    public record BossRoomCondition(int type, List<Entry> entries) {
        public static final int TYPE_NONE = 0;

        public static final int TYPE_HUNT_MONSTER = 1;

        public static final int TYPE_SEEKING = 2;

        public static final BossRoomCondition NONE = new BossRoomCondition(TYPE_NONE, List.of());

        public BossRoomCondition {
            entries = List.copyOf(entries);
        }

        public boolean present() {
            return type == TYPE_HUNT_MONSTER && !entries.isEmpty();
        }

        public record Entry(int monsterId, int appearCount, int requiredKill) {}

        @Override
        public String toString() {
            return switch (type) {
                case TYPE_HUNT_MONSTER -> "[hunt monster] " + entries;
                case TYPE_SEEKING -> "[seeking] " + entries;
                default -> "(无)";
            };
        }
    }

    public record AssignItem(int level, List<MonsterScript.DropEntry> items) {
        public AssignItem {
            items = List.copyOf(items);
        }

        public static final int INHERIT_DUNGEON_LEVEL = -1;

        public int levelOr(int dungeonStandardLevel) {
            return level == INHERIT_DUNGEON_LEVEL ? dungeonStandardLevel : level;
        }
    }

    public static final double DEFAULT_EXP_WEIGHT = 1.0;

    public record CoinInfo(int limit, boolean charged) {
    }

    public record CoinQuota(int hellCoinLimit, int partyMemberCoinLimit, int dimensionPossible,
                            Map<Integer, CoinInfo> coinInfo) {
        public CoinQuota {
            coinInfo = Map.copyOf(coinInfo);
        }

        public static final CoinQuota NONE =
                new CoinQuota(COIN_LIMIT_NONE, 0, 0, Map.of());

        public static final int MEMBER_LIMIT_NONE = 0;

        public Integer limitCoinDiff(int difficulty) {
            CoinInfo info = coinInfo.get(difficulty);
            return info == null ? null : info.limit();
        }

        public boolean freeRevival(int difficulty) {
            CoinInfo info = coinInfo.get(difficulty);
            return info != null && !info.charged();
        }
    }

    public DungeonScript(int id, String path, int minimumLevel, int basisLevel,
                         int dummyAppearCount, List<Maze> mazes, WarRoom warRoom,
                         String dungeonType, Tower tower,
                         int coinLimit, List<RequiredItem> requiredItems,
                         List<MonsterScript.DropEntry> dungeonDrop,
                         boolean ancientDungeon, boolean hellDungeon,
                         List<Integer> championCounts,
                         int eventMonsterTryCount, int eventMonsterProb,
                         List<EventMonster> eventMonsters,
                         double expWeight, List<AssignItem> assignItems,
                         BossRoomCondition bossRoomCondition) {
        this(id, path, minimumLevel, basisLevel, dummyAppearCount, mazes, warRoom, dungeonType,
                tower, coinLimit, requiredItems, dungeonDrop, ancientDungeon, hellDungeon,
                championCounts, eventMonsterTryCount, eventMonsterProb, eventMonsters,
                expWeight, assignItems, bossRoomCondition, CoinQuota.NONE);
    }

    public DungeonScript(int id, String path, int minimumLevel, int basisLevel,
                         int dummyAppearCount, List<Maze> mazes, WarRoom warRoom,
                         String dungeonType, Tower tower,
                         int coinLimit, List<RequiredItem> requiredItems,
                         List<MonsterScript.DropEntry> dungeonDrop,
                         boolean ancientDungeon, boolean hellDungeon,
                         List<Integer> championCounts,
                         int eventMonsterTryCount, int eventMonsterProb,
                         List<EventMonster> eventMonsters) {
        this(id, path, minimumLevel, basisLevel, dummyAppearCount, mazes, warRoom, dungeonType,
                tower, coinLimit, requiredItems, dungeonDrop, ancientDungeon, hellDungeon,
                championCounts, eventMonsterTryCount, eventMonsterProb, eventMonsters,
                DEFAULT_EXP_WEIGHT);
    }

    public DungeonScript(int id, String path, int minimumLevel, int basisLevel,
                         int dummyAppearCount, List<Maze> mazes, WarRoom warRoom,
                         String dungeonType, Tower tower,
                         int coinLimit, List<RequiredItem> requiredItems,
                         List<MonsterScript.DropEntry> dungeonDrop,
                         boolean ancientDungeon, boolean hellDungeon,
                         List<Integer> championCounts) {
        this(id, path, minimumLevel, basisLevel, dummyAppearCount, mazes, warRoom, dungeonType,
                tower, coinLimit, requiredItems, dungeonDrop, ancientDungeon, hellDungeon,
                championCounts, 0, 0, List.of());
    }

    public DungeonScript(int id, String path, int minimumLevel, int basisLevel,
                         int dummyAppearCount, List<Maze> mazes, WarRoom warRoom,
                         String dungeonType, Tower tower,
                         int coinLimit, List<RequiredItem> requiredItems,
                         List<MonsterScript.DropEntry> dungeonDrop,
                         boolean ancientDungeon) {
        this(id, path, minimumLevel, basisLevel, dummyAppearCount, mazes, warRoom, dungeonType,
                tower, coinLimit, requiredItems, dungeonDrop, ancientDungeon, false);
    }

    public DungeonScript(int id, String path, int minimumLevel, int basisLevel,
                         int dummyAppearCount, List<Maze> mazes, WarRoom warRoom,
                         String dungeonType, Tower tower,
                         int coinLimit, List<RequiredItem> requiredItems,
                         List<MonsterScript.DropEntry> dungeonDrop,
                         boolean ancientDungeon, boolean hellDungeon) {
        this(id, path, minimumLevel, basisLevel, dummyAppearCount, mazes, warRoom, dungeonType,
                tower, coinLimit, requiredItems, dungeonDrop, ancientDungeon, hellDungeon,
                List.of());
    }

    public int championCountFor(int mazeIndex) {
        return mazeIndex >= 0 && mazeIndex < championCounts.size()
                ? championCounts.get(mazeIndex) : 0;
    }

    public record WarRoom(int mapIndex, List<Integer> spawnProb, List<Integer> spawnCost,
                          List<List<Integer>> spawnIndex, int spawnStepMax,
                          List<Integer> resourcePool, List<Double> intervalRate,
                          int spawnBaseInterval, int spawnRandomInterval, int maxMonster,
                          double killCountConst, int joinCostGold, int goldDropProb,
                          List<Integer> itemDropProb, List<Integer> expConst,
                          List<List<Integer>> itemDropList, int playerKc,
                          int battleSpawnTimeMs, int fatigue, int coinLimit,
                          double expIncreasingPoint) {
        public static final int TYPES = 4;

        public static final int TYPE_COMMON = 0;

        public static final int TYPE_CHAMPION = 1;

        public static final int TYPE_SUPER_CHAMPION = 2;

        public static final int TYPE_BOSS = 3;

        public WarRoom {
            spawnProb = List.copyOf(spawnProb);
            spawnCost = List.copyOf(spawnCost);
            spawnIndex = List.copyOf(spawnIndex);
            resourcePool = List.copyOf(resourcePool);
            intervalRate = List.copyOf(intervalRate);
            itemDropProb = List.copyOf(itemDropProb);
            expConst = List.copyOf(expConst);
            itemDropList = List.copyOf(itemDropList);
        }

        private static int at(List<Integer> list, int i, int fallback) {
            return i >= 0 && i < list.size() ? list.get(i) : fallback;
        }

        public List<Integer> spawnIndex(int type) {
            return type >= 0 && type < spawnIndex.size() ? spawnIndex.get(type) : List.of();
        }
    }

    public record ClearCondition(int type, int id, int count) {}

    public static final int TYPE_DESTROY_OBJECT = 0;

    public static final int TYPE_CLEAR_MAP = 1;

    public static final int TYPE_HUNT_MONSTER = 2;

    public static final int TYPE_HUNT_APC = 3;

    public static final int TYPE_HUNT_BOSS = 4;

    public static int clearConditionType(String tag) {
        String t = tag == null ? "" : tag.trim().toLowerCase();
        if (t.startsWith("[") && t.endsWith("]")) {
            t = t.substring(1, t.length() - 1).trim();
        }
        return switch (t) {
            case "destroy object" -> TYPE_DESTROY_OBJECT;
            case "clear map" -> TYPE_CLEAR_MAP;
            case "hunt monster" -> TYPE_HUNT_MONSTER;
            case "hunt apc" -> TYPE_HUNT_APC;
            case "hunt boss" -> TYPE_HUNT_BOSS;
            default -> -1;
        };
    }

    public record EventMonster(int monsterId, int count, int rate,
                               int randomDropCount, int specifyDropCount) {
        static final int FIELDS = 5;
    }

    public int tryGenEventMonsterCount() {
        return eventMonsterTryCount;
    }

    public int tryGenEventMonsterProb() {
        return eventMonsterProb;
    }

    public int eventMonsterTotalProb() {
        int sum = 0;
        for (EventMonster e : eventMonsters) {
            sum += e.rate();
        }
        return sum;
    }

    public record Room(int x, int y) {
        public boolean isNone() {
            return x < 0 || y < 0;
        }
    }

    public record QuestConnection(int type, int questId, int minDifficulty) {
        public static final int TYPE_DOING = 0;

        public static final int TYPE_CLEARED = 1;

        public static final int MAX_TYPE = 1;

        public static final QuestConnection NONE = new QuestConnection(-1, 0, 0);

        public boolean present() {
            return type >= TYPE_DOING;
        }
    }

    public record Maze(int index, int width, int height, List<Integer> gridCodes,
                       Map<Long, List<Integer>> mapSpecs,
                       Map<Long, List<Integer>> bossMapSpecs,
                       List<Room> startRooms, List<Room> bossRooms,
                       int sealDoorMapIndex, int sealDoorX, int sealDoorY,
                       List<ClearCondition> clearConditions,
                       QuestConnection questConnection, boolean escapeHell) {
        public Maze(int index, int width, int height, List<Integer> gridCodes,
                    Map<Long, List<Integer>> mapSpecs,
                    Map<Long, List<Integer>> bossMapSpecs,
                    List<Room> startRooms, List<Room> bossRooms,
                    int sealDoorMapIndex, int sealDoorX, int sealDoorY) {
            this(index, width, height, gridCodes, mapSpecs, bossMapSpecs, startRooms, bossRooms,
                    sealDoorMapIndex, sealDoorX, sealDoorY, List.of());
        }

        public Maze(int index, int width, int height, List<Integer> gridCodes,
                    Map<Long, List<Integer>> mapSpecs,
                    Map<Long, List<Integer>> bossMapSpecs,
                    List<Room> startRooms, List<Room> bossRooms,
                    int sealDoorMapIndex, int sealDoorX, int sealDoorY,
                    List<ClearCondition> clearConditions) {
            this(index, width, height, gridCodes, mapSpecs, bossMapSpecs, startRooms, bossRooms,
                    sealDoorMapIndex, sealDoorX, sealDoorY, clearConditions,
                    QuestConnection.NONE, false);
        }

        public boolean isQuestMaze() {
            return questConnection.present();
        }

        public Maze {
            gridCodes = List.copyOf(gridCodes);
            mapSpecs = Map.copyOf(mapSpecs);
            bossMapSpecs = Map.copyOf(bossMapSpecs);
            startRooms = List.copyOf(startRooms);
            bossRooms = List.copyOf(bossRooms);
            clearConditions = List.copyOf(clearConditions);
        }

        public int gridCode(int x, int y) {
            if (x < 0 || y < 0 || x >= width || y >= height) {
                return GridCode.EMPTY;
            }
            int at = x + y * width;
            return at < gridCodes.size() ? gridCodes.get(at) : GridCode.EMPTY;
        }

        public List<Integer> specifiedMaps(int x, int y) {
            return mapSpecs.getOrDefault(key(x, y), List.of());
        }

        public List<Integer> bossSpecifiedMaps(int x, int y) {
            return bossMapSpecs.getOrDefault(key(x, y), List.of());
        }

        static long key(int x, int y) {
            return ((long) x << 32) | (y & 0xFFFFFFFFL);
        }
    }

    public record RequiredItem(int itemId, int count, boolean consume) {
        static final int FIELDS = 3;
    }

    public record Tower(int maxStage, SortedMap<Integer, Integer> floorMaps,
                        int maxClearItemNum, boolean limitOfStackableItem,
                        boolean itemDrop, boolean fpCubePiece,
                        List<Recovery> recoveries, int aiCharacterAppearRate) {
        public static final Tower NONE =
                new Tower(0, new TreeMap<>(), 0, false, false, false, List.of(), 0);

        public Tower {
            floorMaps = Collections.unmodifiableSortedMap(new TreeMap<>(floorMaps));
            recoveries = List.copyOf(recoveries);
        }

        public int mapIndex(int floor) {
            Integer map = floorMaps.get(floor);
            return map == null ? -1 : map;
        }

        public boolean empty() {
            return floorMaps.isEmpty() && maxStage <= 0;
        }

        public record Recovery(int fromFloor, int toFloor, int rate) {
            static final int FIELDS = 3;
        }
    }

    public static final String TYPE_TOWER = "tower of death";

    public static final int KIND_NORMAL = 0;

    public static final int KIND_DEATH_TOWER = 1;

    public static final int COIN_LIMIT_NONE = -1;

    public DungeonScript {
        mazes = List.copyOf(mazes);
        tower = tower == null ? Tower.NONE : tower;
        requiredItems = List.copyOf(requiredItems);
        dungeonDrop = List.copyOf(dungeonDrop);
    }

    public DungeonScript(int id, String path, int minimumLevel, int basisLevel,
                         int dummyAppearCount, List<Maze> mazes) {
        this(id, path, minimumLevel, basisLevel, dummyAppearCount, mazes, null,
                "", Tower.NONE, COIN_LIMIT_NONE, List.of(), List.of(), false);
    }

    public DungeonScript(int id, String path, int minimumLevel, int basisLevel,
                         int dummyAppearCount, List<Maze> mazes, WarRoom warRoom) {
        this(id, path, minimumLevel, basisLevel, dummyAppearCount, mazes, warRoom,
                "", Tower.NONE, COIN_LIMIT_NONE, List.of(), List.of(), false);
    }

    public int standardLevel() {
        return basisLevel;
    }

    public boolean isTower() {
        return dungeonKind() == KIND_DEATH_TOWER || !tower.empty();
    }

    public int dungeonKind() {
        return switch (dungeonType == null ? "" : dungeonType.trim().toLowerCase()) {
            case TYPE_TOWER -> KIND_DEATH_TOWER;
            case "power dungeon" -> 2;
            case "tower of boss" -> 3;
            case "advance altar" -> 4;
            default -> KIND_NORMAL;
        };
    }

    public static DungeonScript load(PvfArchive archive, int id, String path) {
        String normalized = com.dylan.pvf.PvfPath.normalize(path);
        return parse(archive.readText(normalized), id, normalized);
    }

    public static DungeonScript parse(String text, int id, String path) {
        String normalized = com.dylan.pvf.PvfPath.normalize(path);
        var tokens = ScriptReader.tokenize(text);

        int minimumLevel = 1;
        int basisLevel = 1;
        int dummyAppearCount = 0;
        double expWeight = DEFAULT_EXP_WEIGHT;
        List<Maze> mazes = new ArrayList<>();

        int width = 0;
        int height = 0;
        List<Integer> codes = new ArrayList<>();
        Map<Long, List<Integer>> mapSpecs = new HashMap<>();
        Map<Long, List<Integer>> bossMapSpecs = new HashMap<>();
        List<ClearCondition> clearConditions = new ArrayList<>();
        List<Integer> championCounts = new ArrayList<>();
        List<EventMonster> eventMonsters = new ArrayList<>();
        int[] eventMonsterHeader = {0, 0};
        List<Room> startRooms = new ArrayList<>();
        List<Room> bossRooms = new ArrayList<>();
        int sealDoorMapIndex = 0;
        int sealDoorX = 0;
        int sealDoorY = 0;
        QuestConnection questConnection = QuestConnection.NONE;
        boolean escapeHell = false;
        boolean inMaze = false;

        String dungeonType = "";
        SortedMap<Integer, Integer> towerFloors = new TreeMap<>();
        int coinLimit = COIN_LIMIT_NONE;
        int[] hellCoinLimit = {COIN_LIMIT_NONE};
        int[] partyMemberCoinLimit = {CoinQuota.MEMBER_LIMIT_NONE};
        int[] dimensionPossible = {0};
        Map<Integer, CoinInfo> coinInfo = new HashMap<>();
        boolean ancientDungeon = false;
        boolean hellDungeon = false;
        TowerBuilder towerBuilder = new TowerBuilder();
        List<RequiredItem> requiredItems = new ArrayList<>();
        List<MonsterScript.DropEntry> dungeonDrop = new ArrayList<>();
        List<AssignItem> assignItems = new ArrayList<>();
        BossRoomCondition bossRoomCondition = BossRoomCondition.NONE;

        WarRoomBuilder war = new WarRoomBuilder();

        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            String name = token.text().toLowerCase();

            switch (name) {
                case "special passive object item" ->
                        readAssignItems(normalized, tokens, i, assignItems);
                case "minimum required level" -> minimumLevel = nextInt(tokens, i, normalized);
                case "basis level" -> basisLevel = nextInt(tokens, i, normalized);
                case "ancient dungeon" -> ancientDungeon = nextInt(tokens, i, normalized) != 0;
                case "hell dungeon" -> hellDungeon = nextInt(tokens, i, normalized) != 0;
                case "dummy appear count" -> dummyAppearCount = nextInt(tokens, i, normalized);
                case "warroom map index" -> war.mapIndex = nextInt(tokens, i, normalized);
                case "monster type spawn prob" -> readInts(tokens, i, war.spawnProb);
                case "monster type spawn cost" -> readInts(tokens, i, war.spawnCost);
                case "spawn common monster index" ->
                        readInts(tokens, i, war.spawnIndex.get(WarRoom.TYPE_COMMON));
                case "spawn common champion index" ->
                        readInts(tokens, i, war.spawnIndex.get(WarRoom.TYPE_CHAMPION));
                case "spawn super champion index" ->
                        readInts(tokens, i, war.spawnIndex.get(WarRoom.TYPE_SUPER_CHAMPION));
                case "spawn boss index" ->
                        readInts(tokens, i, war.spawnIndex.get(WarRoom.TYPE_BOSS));
                case "spawn step max" -> war.spawnStepMax = nextInt(tokens, i, normalized);
                case "spawn step resource pool" -> readInts(tokens, i, war.resourcePool);
                case "monster type spawn interval rate" ->
                        readDoubles(tokens, i, war.intervalRate);
                case "monster spawn base interval" ->
                        war.spawnBaseInterval = nextInt(tokens, i, normalized);
                case "monster spawn random interval" ->
                        war.spawnRandomInterval = nextInt(tokens, i, normalized);
                case "max monster" -> war.maxMonster = nextInt(tokens, i, normalized);
                case "kill count const" -> war.killCountConst = nextDouble(tokens, i);
                case "join cost gold" -> war.joinCostGold = nextInt(tokens, i, normalized);
                case "gold drop prob" -> war.goldDropProb = nextInt(tokens, i, normalized);
                case "common monster item drop prob" ->
                        war.itemDropProb.set(WarRoom.TYPE_COMMON, nextInt(tokens, i, normalized));
                case "common champion item drop prob" ->
                        war.itemDropProb.set(WarRoom.TYPE_CHAMPION, nextInt(tokens, i, normalized));
                case "super champion item drop prob" -> war.itemDropProb.set(
                        WarRoom.TYPE_SUPER_CHAMPION, nextInt(tokens, i, normalized));
                case "boss item drop prob" ->
                        war.itemDropProb.set(WarRoom.TYPE_BOSS, nextInt(tokens, i, normalized));
                case "common monster exp const" ->
                        war.expConst.set(WarRoom.TYPE_COMMON, nextInt(tokens, i, normalized));
                case "common champion exp const" ->
                        war.expConst.set(WarRoom.TYPE_CHAMPION, nextInt(tokens, i, normalized));
                case "super champion exp const" -> war.expConst.set(
                        WarRoom.TYPE_SUPER_CHAMPION, nextInt(tokens, i, normalized));
                case "boss exp const" ->
                        war.expConst.set(WarRoom.TYPE_BOSS, nextInt(tokens, i, normalized));
                case "common monster item drop list" ->
                        readInts(tokens, i, war.itemDropList.get(WarRoom.TYPE_COMMON));
                case "common champion item drop list" ->
                        readInts(tokens, i, war.itemDropList.get(WarRoom.TYPE_CHAMPION));
                case "super champion item drop list" ->
                        readInts(tokens, i, war.itemDropList.get(WarRoom.TYPE_SUPER_CHAMPION));
                case "boss item drop list" ->
                        readInts(tokens, i, war.itemDropList.get(WarRoom.TYPE_BOSS));
                case "player kc" -> war.playerKc = nextInt(tokens, i, normalized);
                case "battle spawn time" -> war.battleSpawnTimeMs = nextInt(tokens, i, normalized);
                case "fatigue" -> war.fatigue = nextInt(tokens, i, normalized);
                case "coin limit" -> {
                    coinLimit = nextInt(tokens, i, normalized);
                    war.coinLimit = coinLimit;
                }
                case "hell coin limit" ->
                        hellCoinLimit[0] = nextInt(tokens, i, normalized);
                case "party member coin limit" ->
                        partyMemberCoinLimit[0] = nextInt(tokens, i, normalized);
                case "dimension possible" ->
                        dimensionPossible[0] = nextInt(tokens, i, normalized);
                case "coin info" -> readCoinInfo(tokens, i, coinInfo);
                case "experience increasing point" -> {
                    expWeight = nextDouble(tokens, i);
                    war.expIncreasingPoint = expWeight;
                }
                case "maze info" -> {
                    if (inMaze) {
                        mazes.add(new Maze(mazes.size(), width, height, codes, mapSpecs,
                                bossMapSpecs, startRooms, bossRooms,
                                sealDoorMapIndex, sealDoorX, sealDoorY, clearConditions,
                                questConnection, escapeHell));
                    }
                    inMaze = true;
                    width = 0;
                    height = 0;
                    codes = new ArrayList<>();
                    mapSpecs = new HashMap<>();
                    bossMapSpecs = new HashMap<>();
                    clearConditions = new ArrayList<>();
                    startRooms = new ArrayList<>();
                    bossRooms = new ArrayList<>();
                    sealDoorMapIndex = 0;
                    sealDoorX = 0;
                    sealDoorY = 0;
                    questConnection = QuestConnection.NONE;
                    escapeHell = false;
                }
                case "quest connection" -> {
                    if (inMaze) {
                        questConnection = readQuestConnection(normalized, tokens, i);
                    }
                }
                case "escape hell" -> {
                    if (inMaze) {
                        escapeHell = nextInt(tokens, i, normalized) != 0;
                    }
                }
                case "size" -> {
                    if (inMaze) {
                        width = nextInt(tokens, i, normalized);
                        height = nextInt(tokens, i + 1, normalized);
                    }
                }
                case "greed" -> {
                    if (inMaze && i + 1 < tokens.size()
                            && tokens.get(i + 1).kind() == ScriptReader.Kind.STRING) {
                        readGrid(tokens.get(i + 1).text(), codes);
                    }
                }
                case "map specification" -> readMapSpec(tokens, i, mapSpecs);
                case "boss map specification" -> readMapSpec(tokens, i, bossMapSpecs);
                case "clear condition" ->
                        readClearConditions(normalized, tokens, i, clearConditions);
                case "champion" -> readInts(tokens, i, championCounts);
                case "event monster" ->
                        readEventMonsters(tokens, i, eventMonsterHeader, eventMonsters);
                case "start map" -> readRooms(tokens, i, startRooms);
                case "boss map" -> readRooms(tokens, i, bossRooms);
                case "dungeon type" -> {
                    if (i + 1 < tokens.size()
                            && tokens.get(i + 1).kind() == ScriptReader.Kind.STRING) {
                        dungeonType = tokens.get(i + 1).text().trim();
                    }
                }
                case "death tower map indexes" ->
                        towerBuilder.maxStage = readTowerFloors(tokens, i, towerFloors);
                case "tower max clear item num" ->
                        towerBuilder.maxClearItemNum = nextInt(tokens, i, normalized);
                case "tower limit of stackable item" ->
                        towerBuilder.limitOfStackableItem = nextInt(tokens, i, normalized) != 0;
                case "tower item drop" ->
                        towerBuilder.itemDrop = nextInt(tokens, i, normalized) != 0;
                case "tower fp cubepiece" ->
                        towerBuilder.fpCubePiece = nextInt(tokens, i, normalized) != 0;
                case "tower recovery" -> readTowerRecovery(tokens, i, towerBuilder.recoveries);
                case "ai character appear rate" ->
                        towerBuilder.aiCharacterAppearRate = nextInt(tokens, i, normalized);
                case "required item" -> readRequiredItems(tokens, i, requiredItems);
                case "dungeon drop" -> readDropEntries(tokens, i, dungeonDrop);
                case "boss room entrance condition" ->
                        bossRoomCondition = readBossRoomCondition(normalized, tokens, i);
                case "seal door map index" -> sealDoorMapIndex = nextInt(tokens, i, normalized);
                case "seal door pos" -> {
                    sealDoorX = nextInt(tokens, i, normalized);
                    sealDoorY = nextInt(tokens, i + 1, normalized);
                }
                default -> {
                }
            }
        }
        if (inMaze) {
            mazes.add(new Maze(mazes.size(), width, height, codes, mapSpecs, bossMapSpecs,
                    startRooms, bossRooms, sealDoorMapIndex, sealDoorX, sealDoorY,
                    clearConditions, questConnection, escapeHell));
        }
        if (mazes.isEmpty() && towerFloors.isEmpty()
                && !TYPE_TOWER.equalsIgnoreCase(dungeonType)) {
            throw new PvfFormatException(normalized + " 里没有 [maze info]，也不是塔类副本");
        }
        return new DungeonScript(id, normalized, minimumLevel, basisLevel, dummyAppearCount,
                mazes, war.build(), dungeonType, towerBuilder.build(towerFloors), coinLimit,
                requiredItems, dungeonDrop, ancientDungeon, hellDungeon, championCounts,
                eventMonsterHeader[0], eventMonsterHeader[1], eventMonsters, expWeight,
                assignItems, bossRoomCondition,
                new CoinQuota(hellCoinLimit[0], partyMemberCoinLimit[0], dimensionPossible[0],
                        coinInfo));
    }

    private static void readCoinInfo(List<ScriptReader.Token> tokens, int at,
                                     Map<Integer, CoinInfo> out) {
        for (int i = at + 1; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            int difficulty = switch (token.text().toLowerCase()) {
                case "easy" -> 0;
                case "medium" -> 1;
                case "hard" -> 2;
                case "ultimate" -> 3;
                default -> -1;
            };
            if (difficulty < 0) {
                return;
            }
            if (i + 2 >= tokens.size()
                    || tokens.get(i + 1).kind() != ScriptReader.Kind.WORD
                    || tokens.get(i + 2).kind() != ScriptReader.Kind.WORD) {
                return;
            }
            out.put(difficulty, new CoinInfo(tokens.get(i + 1).asInt(),
                    tokens.get(i + 2).asInt() != 0));
            i += 2;
        }
    }

    private static BossRoomCondition readBossRoomCondition(String path,
                                                           List<ScriptReader.Token> tokens,
                                                           int at) {
        int i = at + 1;
        if (i >= tokens.size() || tokens.get(i).kind() != ScriptReader.Kind.STRING) {
            throw new PvfFormatException(path
                    + " 的 [boss room entrance condition] 后面没有类型串");
        }
        String typeTag = tokens.get(i++).text().trim();
        int type = switch (typeTag) {
            case "[hunt monster]" -> BossRoomCondition.TYPE_HUNT_MONSTER;
            case "[seeking]" -> BossRoomCondition.TYPE_SEEKING;
            default -> throw new PvfFormatException(path
                    + " 的 [boss room entrance condition] 类型串是 " + typeTag
                    + "，官方只认 [hunt monster] 和 [seeking]");
        };
        if (i >= tokens.size() || tokens.get(i).kind() != ScriptReader.Kind.WORD) {
            throw new PvfFormatException(path
                    + " 的 [boss room entrance condition] 少了条数");
        }
        int count = tokens.get(i++).asInt();
        int width = type == BossRoomCondition.TYPE_HUNT_MONSTER ? 3 : 2;
        List<BossRoomCondition.Entry> entries = new ArrayList<>(Math.max(0, count));
        for (int k = 0; k < count; k++) {
            int[] fields = new int[width];
            for (int f = 0; f < width; f++) {
                if (i >= tokens.size() || tokens.get(i).kind() != ScriptReader.Kind.WORD) {
                    throw new PvfFormatException(path
                            + " 的 [boss room entrance condition] 说有 " + count
                            + " 条，第 " + (k + 1) + " 条只读到 " + f + " 个数");
                }
                fields[f] = tokens.get(i++).asInt();
            }
            entries.add(new BossRoomCondition.Entry(fields[0], fields[1],
                    width == 3 ? fields[2] : 0));
        }
        return new BossRoomCondition(type, entries);
    }

    private static void readAssignItems(String path, List<ScriptReader.Token> tokens, int at,
                                        List<AssignItem> out) {
        int i = at + 1;
        while (i + 2 < tokens.size() && isWord(tokens, i)
                && isWord(tokens, i + 1) && isWord(tokens, i + 2)) {
            int index = tokens.get(i).asInt();
            int level = tokens.get(i + 1).asInt();
            int count = tokens.get(i + 2).asInt();
            i += 3;
            if (index != out.size()) {
                throw new PvfFormatException(path
                        + " 的 [special passive object item] 第 " + out.size()
                        + " 条写的下标是 " + index
                        + " —— 官方按顺序定位，下标和顺序对不上时 .map 的 `[item]` 会索引错行");
            }
            List<MonsterScript.DropEntry> items = new ArrayList<>(Math.max(count, 0));
            for (int k = 0; k < count; k++) {
                if (!isWord(tokens, i) || !isWord(tokens, i + 1)) {
                    throw new PvfFormatException(path
                            + " 的 [special passive object item] 第 " + out.size()
                            + " 条说有 " + count + " 件候选，实际只读到 " + k + " 件");
                }
                items.add(new MonsterScript.DropEntry(tokens.get(i).asInt(),
                        tokens.get(i + 1).asInt()));
                i += 2;
            }
            out.add(new AssignItem(level, items));
        }
    }

    private static boolean isWord(List<ScriptReader.Token> tokens, int i) {
        return i < tokens.size() && tokens.get(i).kind() == ScriptReader.Kind.WORD;
    }

    private static void readEventMonsters(List<ScriptReader.Token> tokens, int at,
                                          int[] header, List<EventMonster> out) {
        List<Integer> numbers = new ArrayList<>();
        for (int i = at + 1; i < tokens.size(); i++) {
            if (tokens.get(i).kind() != ScriptReader.Kind.WORD) {
                break;
            }
            numbers.add(tokens.get(i).asInt());
        }
        if (numbers.size() < 2) {
            return;
        }
        header[0] = numbers.get(0);
        header[1] = numbers.get(1);
        for (int i = 2; i + EventMonster.FIELDS - 1 < numbers.size();
                i += EventMonster.FIELDS) {
            out.add(new EventMonster(numbers.get(i), numbers.get(i + 1), numbers.get(i + 2),
                    numbers.get(i + 3), numbers.get(i + 4)));
        }
    }

    private static void readClearConditions(String path, List<ScriptReader.Token> tokens, int at,
                                            List<ClearCondition> out) {
        int i = at + 1;
        int n = tokens.size();
        while (i < n) {
            var token = tokens.get(i);
            if (token.kind() == ScriptReader.Kind.SECTION_END) {
                return;
            }
            if (token.kind() != ScriptReader.Kind.SECTION
                    && token.kind() != ScriptReader.Kind.STRING) {
                return;
            }
            int type = clearConditionType(token.text());
            if (type < 0) {
                if (token.kind() == ScriptReader.Kind.SECTION) {
                    return;
                }
                throw new PvfFormatException(path + " 的 [clear condition] 里有个不认识的标签「"
                        + token.text() + "」");
            }
            if (i + 2 >= n
                    || tokens.get(i + 1).kind() != ScriptReader.Kind.WORD
                    || tokens.get(i + 2).kind() != ScriptReader.Kind.WORD) {
                throw new PvfFormatException(path + " 的 [clear condition] 里「"
                        + token.text() + "」后面凑不齐 id 和次数");
            }
            int id = tokens.get(i + 1).asInt();
            int count = tokens.get(i + 2).asInt();
            out.add(new ClearCondition(type, id, type == TYPE_CLEAR_MAP ? 1 : count));
            i += 3;
        }
    }

    private static void readDropEntries(List<ScriptReader.Token> tokens, int at,
                                        List<MonsterScript.DropEntry> out) {
        List<Integer> numbers = new ArrayList<>();
        for (int i = at + 1; i < tokens.size(); i++) {
            if (tokens.get(i).kind() != ScriptReader.Kind.WORD) {
                break;
            }
            numbers.add(tokens.get(i).asInt());
        }
        for (int i = 0; i + 1 < numbers.size(); i += 2) {
            int itemId = numbers.get(i);
            int weight = numbers.get(i + 1);
            if (itemId > 0 && weight > 0) {
                out.add(new MonsterScript.DropEntry(itemId, weight));
            }
        }
    }

    private static final class TowerBuilder {
        int maxStage;
        int maxClearItemNum;
        boolean limitOfStackableItem;
        boolean itemDrop = true;
        boolean fpCubePiece;
        int aiCharacterAppearRate;
        final List<Tower.Recovery> recoveries = new ArrayList<>();

        Tower build(SortedMap<Integer, Integer> floorMaps) {
            if (maxStage <= 0 && floorMaps.isEmpty() && recoveries.isEmpty()
                    && maxClearItemNum == 0) {
                return Tower.NONE;
            }
            return new Tower(maxStage, floorMaps, maxClearItemNum, limitOfStackableItem,
                    itemDrop, fpCubePiece, recoveries, aiCharacterAppearRate);
        }
    }

    private static void readTowerRecovery(List<ScriptReader.Token> tokens, int at,
                                          List<Tower.Recovery> out) {
        List<Integer> numbers = new ArrayList<>(Tower.Recovery.FIELDS);
        for (int i = at + 1; i < tokens.size() && numbers.size() < Tower.Recovery.FIELDS; i++) {
            if (tokens.get(i).kind() != ScriptReader.Kind.WORD) {
                break;
            }
            numbers.add(tokens.get(i).asInt());
        }
        if (numbers.size() == Tower.Recovery.FIELDS) {
            out.add(new Tower.Recovery(numbers.get(0), numbers.get(1), numbers.get(2)));
        }
    }

    private static void readRequiredItems(List<ScriptReader.Token> tokens, int at,
                                          List<RequiredItem> out) {
        List<Integer> numbers = new ArrayList<>(RequiredItem.FIELDS);
        for (int i = at + 1; i < tokens.size() && numbers.size() < RequiredItem.FIELDS; i++) {
            if (tokens.get(i).kind() != ScriptReader.Kind.WORD) {
                break;
            }
            numbers.add(tokens.get(i).asInt());
        }
        if (numbers.size() == RequiredItem.FIELDS) {
            out.add(new RequiredItem(numbers.get(0), numbers.get(1), numbers.get(2) != 0));
        }
    }

    private static final class WarRoomBuilder {
        int mapIndex;
        final List<Integer> spawnProb = new ArrayList<>();
        final List<Integer> spawnCost = new ArrayList<>();
        final List<List<Integer>> spawnIndex = newLists(WarRoom.TYPES);
        int spawnStepMax;
        final List<Integer> resourcePool = new ArrayList<>();
        final List<Double> intervalRate = new ArrayList<>();
        int spawnBaseInterval;
        int spawnRandomInterval;
        int maxMonster;
        double killCountConst;
        int joinCostGold;
        int goldDropProb;
        final List<Integer> itemDropProb = zeros(WarRoom.TYPES);
        final List<Integer> expConst = zeros(WarRoom.TYPES);
        final List<List<Integer>> itemDropList = newLists(WarRoom.TYPES);
        int playerKc;
        int battleSpawnTimeMs;
        int fatigue;
        int coinLimit;
        double expIncreasingPoint = 1.0;

        private static List<List<Integer>> newLists(int n) {
            List<List<Integer>> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                out.add(new ArrayList<>());
            }
            return out;
        }

        private static List<Integer> zeros(int n) {
            List<Integer> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                out.add(0);
            }
            return out;
        }

        WarRoom build() {
            if (mapIndex <= 0) {
                return null;
            }
            return new WarRoom(mapIndex, spawnProb, spawnCost, spawnIndex, spawnStepMax,
                    resourcePool, intervalRate, spawnBaseInterval, spawnRandomInterval,
                    maxMonster, killCountConst, joinCostGold, goldDropProb, itemDropProb,
                    expConst, itemDropList, playerKc, battleSpawnTimeMs, fatigue, coinLimit,
                    expIncreasingPoint);
        }
    }

    private static void readInts(List<ScriptReader.Token> tokens, int at, List<Integer> out) {
        for (int i = at + 1; i < tokens.size(); i++) {
            if (tokens.get(i).kind() != ScriptReader.Kind.WORD) {
                return;
            }
            out.add(tokens.get(i).asInt());
        }
    }

    private static void readDoubles(List<ScriptReader.Token> tokens, int at, List<Double> out) {
        for (int i = at + 1; i < tokens.size(); i++) {
            if (tokens.get(i).kind() != ScriptReader.Kind.WORD) {
                return;
            }
            out.add(parseDouble(tokens.get(i).text()));
        }
    }

    private static double nextDouble(List<ScriptReader.Token> tokens, int at) {
        if (at + 1 >= tokens.size()
                || tokens.get(at + 1).kind() != ScriptReader.Kind.WORD) {
            return 0.0;
        }
        return parseDouble(tokens.get(at + 1).text());
    }

    private static double parseDouble(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new PvfFormatException("期望一个小数，读到 \"" + text + "\"");
        }
    }

    private static void readGrid(String body, List<Integer> out) {
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            for (int i = 0; i + 1 < trimmed.length(); i += 2) {
                int code = GridCode.parse(trimmed.substring(i, i + 2));
                out.add(code < 0 ? GridCode.EMPTY : code);
            }
        }
    }

    private static void readMapSpec(List<ScriptReader.Token> tokens, int at,
                                    Map<Long, List<Integer>> out) {
        List<Integer> numbers = new ArrayList<>();
        for (int i = at + 1; i < tokens.size(); i++) {
            if (tokens.get(i).kind() != ScriptReader.Kind.WORD) {
                break;
            }
            numbers.add(tokens.get(i).asInt());
        }
        if (numbers.size() < 3) {
            return;
        }
        out.computeIfAbsent(Maze.key(numbers.get(0), numbers.get(1)), k -> new ArrayList<>())
                .addAll(numbers.subList(2, numbers.size()));
    }

    private static void readRooms(List<ScriptReader.Token> tokens, int at, List<Room> out) {
        List<Integer> numbers = new ArrayList<>();
        for (int i = at + 1; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.WORD) {
                break;
            }
            numbers.add(token.asInt());
        }
        for (int i = 0; i + 1 < numbers.size(); i += 2) {
            out.add(new Room(numbers.get(i), numbers.get(i + 1)));
        }
    }

    private static int readTowerFloors(List<ScriptReader.Token> tokens, int at,
                                       Map<Integer, Integer> out) {
        List<Integer> numbers = new ArrayList<>();
        for (int i = at + 1; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.WORD) {
                break;
            }
            numbers.add(token.asInt());
        }
        if (numbers.isEmpty()) {
            return 0;
        }
        for (int i = 1; i + 1 < numbers.size(); i += 2) {
            out.put(numbers.get(i), numbers.get(i + 1));
        }
        return numbers.get(0);
    }

    private static QuestConnection readQuestConnection(String path,
                                                       List<ScriptReader.Token> tokens, int at) {
        int type = nextInt(tokens, at, path);
        int questId = nextInt(tokens, at + 1, path);
        if (type > QuestConnection.MAX_TYPE || type < -1) {
            throw new PvfFormatException(path + " 的 [quest connection] 写着 maze quest type "
                    + type + " —— 官方只认 -1 / 0 / 1，整份脚本作废");
        }
        int minDifficulty = type == QuestConnection.TYPE_DOING
                ? nextInt(tokens, at + 2, path)
                : 0;
        return new QuestConnection(type, questId, minDifficulty);
    }

    private static int nextInt(List<ScriptReader.Token> tokens, int at, String path) {
        if (at + 1 >= tokens.size()
                || tokens.get(at + 1).kind() != ScriptReader.Kind.WORD) {
            throw new PvfFormatException(path + " 里 " + tokens.get(at).text()
                    + " 后面没有跟一个数字");
        }
        return tokens.get(at + 1).asInt();
    }

    public List<Maze> normalMazes() {
        List<Maze> out = new ArrayList<>();
        for (Maze maze : mazes) {
            if (!maze.isQuestMaze()) {
                out.add(maze);
            }
        }
        return out;
    }

    public List<Maze> questMazes(int type) {
        List<Maze> out = new ArrayList<>();
        for (Maze maze : mazes) {
            if (maze.questConnection().type() == type) {
                out.add(maze);
            }
        }
        return out;
    }

    public String directory() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash + 1);
    }
}
