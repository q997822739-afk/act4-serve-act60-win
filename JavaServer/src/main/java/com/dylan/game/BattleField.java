package com.dylan.game;

import com.dylan.protocol.body.Cmd48ReplyBody;
import com.dylan.protocol.body.Noti29Body;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.DungeonMaps;
import com.dylan.pvf.script.DungeonScript;
import com.dylan.pvf.script.GridCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

public final class BattleField {
    public static final int MAX_ROOM_X = 0x31;
    public static final int MAX_ROOM_Y = 0x32;

    private final DungeonScript dungeon;
    private final DungeonMaps maps;
    private final DungeonScript.Maze maze;
    private final RandomGenerator random;
    private final int basisLevel;

    private int curX;
    private int curY;

    private int lastX = -1;
    private int lastY = -1;

    private int bossX;
    private int bossY;

    private final int questX;
    private final int questY;

    private final int questStageMap;

    private final List<DungeonScript.Room> dummyRooms = new ArrayList<>();
    private int dummyCursor;

    private final Map<Long, RoomState> rooms = new HashMap<>();

    private final java.util.LinkedHashSet<Integer> pendingBosses = new java.util.LinkedHashSet<>();

    private boolean cleared;

    private final ClearConditions clearConditions;

    private boolean hellPartyAppeared;

    private int hellPartyDifficulty = HellPartyService.DIFFICULTY_NONE;

    private boolean killedAllHellGroups;

    private boolean hellPartyMapTooSmall;

    private boolean enteredHellRoom;

    private final int hellRoomMapIndex;
    private final int hellRoomX;
    private final int hellRoomY;

    public record RoomState(boolean visited, int mapIndex, int mode, boolean dummyBossRoom,
                            DungeonRoom room) {
        public RoomState(boolean visited, int mapIndex, int mode, DungeonRoom room) {
            this(visited, mapIndex, mode, false, room);
        }
    }

    public record MoveResult(int error, boolean revisit, int mapIndex, int mode,
                             DungeonRoom room) {
        public boolean ok() {
            return error == 0;
        }

        static MoveResult refused(int error) {
            return new MoveResult(error, false, 0, 0, null);
        }
    }

    public static BattleField of(GameScripts scripts, DungeonRun run, RandomGenerator random) {
        return of(scripts, run, random, HellPartyService.HellPartyEntry.NONE);
    }

    public static BattleField of(GameScripts scripts, DungeonRun run, RandomGenerator random,
                                 HellPartyService.HellPartyEntry hell) {
        if (scripts == null) {
            throw new IllegalStateException("没有 pvf 就没有迷宫网格，建不了副本场地");
        }
        DungeonScript dungeon = scripts.dungeon(run.dungeonIndex()).orElseThrow(
                () -> new IllegalStateException("dungeon.lst 里没有副本 " + run.dungeonIndex()));
        return of(dungeon, scripts.mapsFor(run.dungeonIndex()), run, random, hell);
    }

    public static BattleField of(DungeonScript dungeon, DungeonMaps maps, DungeonRun run,
                                 RandomGenerator random) {
        return of(dungeon, maps, run, random, HellPartyService.HellPartyEntry.NONE);
    }

    public static BattleField of(DungeonScript dungeon, DungeonMaps maps, DungeonRun run,
                                 RandomGenerator random,
                                 HellPartyService.HellPartyEntry hell) {
        if (run.mazeIndex() < 0 || run.mazeIndex() >= dungeon.mazes().size()) {
            throw new IllegalStateException(dungeon.path() + " 没有第 " + run.mazeIndex()
                    + " 套迷宫（一共 " + dungeon.mazes().size() + " 套）");
        }
        return new BattleField(dungeon, maps, dungeon.mazes().get(run.mazeIndex()), run, random,
                hell);
    }

    private BattleField(DungeonScript dungeon, DungeonMaps maps, DungeonScript.Maze maze,
                        DungeonRun run, RandomGenerator random,
                        HellPartyService.HellPartyEntry hell) {
        this.dungeon = dungeon;
        this.maps = maps;
        this.maze = maze;
        this.random = random;
        this.basisLevel = run.basisLevel();
        this.curX = run.startX();
        this.curY = run.startY();
        this.bossX = run.bossX();
        this.bossY = run.bossY();
        this.questX = run.questX();
        this.questY = run.questY();
        this.questStageMap = run.questStageMap();
        this.hellPartyAppeared = hell.appeared();
        this.hellPartyDifficulty = hell.difficulty();
        this.hellRoomMapIndex = maze.sealDoorMapIndex();
        this.hellRoomX = maze.sealDoorX();
        this.hellRoomY = maze.sealDoorY();
        this.clearConditions = ClearConditions.of(maze);
        this.championCount = addChampionCount(run.difficulty());
        this.dungeonDiff = run.difficulty();
        applyMazeTooSmallForHell();
        planDummyRooms(run);
        planEventMonsters();
        planBossRoomConditions();
    }

    private int addChampionCount(int difficulty) {
        int base = dungeon.championCountFor(maze.index());
        return switch (difficulty) {
            case 1 -> 150 * base / 100;
            case 2 -> 250 * base / 100;
            case 3 -> 500 * base / 100;
            default -> base;
        };
    }

    private int rollChampionCount() {
        int cells = Math.max(1, maze.width() * maze.height());
        int percent = 100 * championCount / cells;
        return percent > random.nextInt(100) ? 1 : 0;
    }

    private final int championCount;

    private final int dungeonDiff;

    public SpawnRequest startRoomSpawnRequest(int mapIndex) {
        return new SpawnRequest(mapIndex, basisLevel,
                hellPartyAppeared ? hellPartyDifficulty : HellPartyService.DIFFICULTY_NONE,
                rollChampionCount(), eventMonstersAt(curX, curY), dungeon.id(), dungeonDiff,
                conditionMonstersAt(curX, curY));
    }

    public static void lockIntoObjects(DungeonRoom room, SpawnOutcome spawned) {
        for (ObjectDropService.ObjectDrop drop : spawned.objectDrops()) {
            room.dropFromObject(drop.item(), drop.objectIndex());
        }
    }

    public ClearConditions clearConditions() {
        return clearConditions;
    }

    public boolean reportClearCondition(int type, int id) {
        return clearConditions.report(type, id);
    }

    private void applyMazeTooSmallForHell() {
        if (maze.width() + maze.height() <= MAZE_TOO_SMALL_FOR_HELL) {
            hellPartyAppeared = false;
            hellPartyMapTooSmall = true;
            return;
        }
        if (hellPartyAppeared && hellRoomMapIndex <= 0) {
            hellPartyAppeared = false;
            hellPartyMapTooSmall = true;
        }
    }

    public static final int MAZE_TOO_SMALL_FOR_HELL = 3;

    public record EventMonsterPlacement(int x, int y, DungeonScript.EventMonster row) {}

    private final List<EventMonsterPlacement> eventMonsters = new ArrayList<>();

    public static final int EVENT_MONSTER_PROB_BASE = 10000;

    private void planEventMonsters() {
        if (dungeon.tryGenEventMonsterCount() <= 0 || dungeon.eventMonsters().isEmpty()) {
            return;
        }
        if (!com.dylan.game.event.EventService.isRunning(
                com.dylan.game.event.EventNo.GOLDEN_GOBLIN)) {
            return;
        }
        List<DungeonScript.Room> cells = new ArrayList<>();
        for (int y = 0; y < maze.height(); y++) {
            for (int x = 0; x < maze.width(); x++) {
                if (maze.gridCode(x, y) != GridCode.EMPTY) {
                    cells.add(new DungeonScript.Room(x, y));
                }
            }
        }
        if (cells.isEmpty()) {
            return;
        }
        int total = dungeon.eventMonsterTotalProb();
        for (int k = 0; k < dungeon.tryGenEventMonsterCount(); k++) {
            if (dungeon.tryGenEventMonsterProb() < random.nextInt(EVENT_MONSTER_PROB_BASE)) {
                continue;
            }
            if (total <= 0) {
                return;
            }
            int roll = random.nextInt(total);
            int accumulated = 0;
            for (DungeonScript.EventMonster row : dungeon.eventMonsters()) {
                accumulated += row.rate();
                if (roll >= accumulated) {
                    continue;
                }
                DungeonScript.Room cell = cells.get(random.nextInt(cells.size()));
                boolean taken = eventMonsters.stream()
                        .anyMatch(p -> p.x() == cell.x() && p.y() == cell.y());
                if (!taken) {
                    eventMonsters.add(new EventMonsterPlacement(cell.x(), cell.y(), row));
                }
                break;
            }
        }
    }

    public List<EventMonsterPlacement> eventMonstersAt(int x, int y) {
        List<EventMonsterPlacement> out = new ArrayList<>();
        for (EventMonsterPlacement p : eventMonsters) {
            if (p.x() == x && p.y() == y) {
                out.add(p);
            }
        }
        return out;
    }

    static final class ConditionStatus {
        final int monsterId;
        final List<DungeonScript.Room> cells = new ArrayList<>();
        final int required;
        int killed;

        ConditionStatus(int monsterId, int required) {
            this.monsterId = monsterId;
            this.required = required;
        }

        boolean satisfied() {
            return killed >= required;
        }

        @Override
        public String toString() {
            return monsterId + " 号 " + killed + "/" + required + " 摆在 " + cells;
        }
    }

    private final List<ConditionStatus> bossRoomConditions = new ArrayList<>();

    private void planBossRoomConditions() {
        DungeonScript.BossRoomCondition condition = dungeon.bossRoomCondition();
        if (!condition.present()) {
            return;
        }
        List<DungeonScript.Room> cells = new ArrayList<>();
        for (int y = 0; y < maze.height(); y++) {
            for (int x = 0; x < maze.width(); x++) {
                if ((x == bossX && y == bossY) || (x == curX && y == curY)) {
                    continue;
                }
                List<Integer> specified = maze.specifiedMaps(x, y);
                if (specified.isEmpty() || !allMapsHaveConditionMonsters(specified)) {
                    continue;
                }
                cells.add(new DungeonScript.Room(x, y));
            }
        }
        if (cells.isEmpty()) {
            return;
        }
        for (DungeonScript.BossRoomCondition.Entry entry : condition.entries()) {
            ConditionStatus status = new ConditionStatus(entry.monsterId(), entry.requiredKill());
            for (int n = 0; n < entry.appearCount(); n++) {
                status.cells.add(cells.get(random.nextInt(cells.size())));
            }
            bossRoomConditions.add(status);
        }
    }

    private boolean allMapsHaveConditionMonsters(List<Integer> specified) {
        for (int mapId : specified) {
            var map = maps.map(mapId);
            if (map == null || map.conditionMonsters().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public List<Integer> conditionMonstersAt(int x, int y) {
        if (bossRoomConditions.isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>(2);
        for (ConditionStatus status : bossRoomConditions) {
            for (DungeonScript.Room cell : status.cells) {
                if (cell.x() == x && cell.y() == y) {
                    out.add(status.monsterId);
                }
            }
        }
        return out;
    }

    public void reportBossRoomConditionKill(RoomMonster monster) {
        if (bossRoomConditions.isEmpty() || monster == null) {
            return;
        }
        for (ConditionStatus status : bossRoomConditions) {
            if (status.monsterId != monster.monsterId()) {
                continue;
            }
            status.killed++;
        }
    }

    private int checkBossMap(int toX, int toY) {
        if (toX != bossX || toY != bossY || bossRoomConditions.isEmpty()) {
            return 0;
        }
        for (ConditionStatus status : bossRoomConditions) {
            if (!status.satisfied()) {
                return Cmd48ReplyBody.ERROR_BOSS_CONDITION;
            }
        }
        return 0;
    }

    private void planDummyRooms(DungeonRun run) {
        int count = dungeon.dummyAppearCount();
        List<DungeonScript.Room> candidates = new ArrayList<>();
        for (DungeonScript.Room room : maze.bossRooms()) {
            if (!room.isNone()) {
                candidates.add(room);
            }
        }
        int bossPick = -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).x() == bossX && candidates.get(i).y() == bossY) {
                bossPick = i;
                break;
            }
        }
        if (count <= 0 || bossPick < 0 || candidates.size() <= 1 || !maps.hasDummyMaps()) {
            return;
        }
        int previous = bossPick;
        for (int i = 0; i < count; i++) {
            int pick = random.nextInt(candidates.size() - 1);
            if (pick >= previous) {
                pick++;
            }
            dummyRooms.add(candidates.get(pick));
            previous = pick;
        }
    }

    public int curX() {
        return curX;
    }

    public int curY() {
        return curY;
    }

    public int bossX() {
        return bossX;
    }

    public int bossY() {
        return bossY;
    }

    public DungeonScript.Maze maze() {
        return maze;
    }

    public RoomState current() {
        return rooms.get(key(curX, curY));
    }

    public DungeonRoom currentRoom() {
        RoomState state = current();
        return state == null ? DungeonRoom.empty() : state.room();
    }

    public boolean isEndPoint(int x, int y) {
        return bossX >= 0 && x == bossX && y == bossY;
    }

    public boolean atEndPoint() {
        return isEndPoint(curX, curY);
    }

    public record BossDieCheck(boolean known, int remaining, boolean sweep) {}

    public static final int BOSS_NOT_IN_LIST = 21;

    public BossDieCheck reportBossDie(int seqId) {
        boolean known = pendingBosses.remove(seqId);
        return new BossDieCheck(known, pendingBosses.size(), pendingBosses.isEmpty());
    }

    private void registerBosses(DungeonRoom room, boolean dummyBossRoom) {
        if (dummyBossRoom) {
            return;
        }
        for (RoomMonster m : room.living()) {
            if (m.isBoss()) {
                pendingBosses.add(m.seqId());
            }
        }
    }

    public boolean markCleared() {
        if (cleared) {
            return false;
        }
        cleared = true;
        return true;
    }

    public boolean isCleared() {
        return cleared;
    }

    public boolean hellPartyAppeared() {
        return hellPartyAppeared;
    }

    public boolean killedAllHellGroups() {
        return killedAllHellGroups;
    }

    public boolean hellPartyMapTooSmall() {
        return hellPartyMapTooSmall;
    }

    public int hellPartyDifficulty() {
        return hellPartyDifficulty;
    }

    public boolean isHellRoom(int x, int y) {
        return hellPartyAppeared && x == hellRoomX && y == hellRoomY;
    }

    public DungeonRoom.HellKill killHellPartyMonster(RoomMonster monster) {
        if (monster == null || !monster.isHellParty() || killedAllHellGroups) {
            return DungeonRoom.HellKill.NONE;
        }
        DungeonRoom room = currentRoom();
        DungeonRoom.HellKill outcome = room.killHellPartyGroup(monster.hellGroupId());
        if (!outcome.known()) {
            return outcome;
        }
        if (outcome.allCleared()) {
            killedAllHellGroups = true;
        }
        return outcome;
    }

    public void enterStartRoom(int mapIndex, DungeonRoom room) {
        rooms.put(key(curX, curY),
                new RoomState(false, mapIndex, Noti29Body.MODE_PLAIN, room));
        registerBosses(room, false);
    }

    public record SpawnOutcome(List<RoomMonster> monsters, Map<Integer, Integer> hellGroups,
                               List<ObjectDropService.ObjectDrop> objectDrops) {
        public SpawnOutcome(List<RoomMonster> monsters, Map<Integer, Integer> hellGroups) {
            this(monsters, hellGroups, List.of());
        }

        public static SpawnOutcome of(List<RoomMonster> monsters) {
            return new SpawnOutcome(monsters, Map.of(), List.of());
        }
    }

    @FunctionalInterface
    public interface Spawner {
        SpawnOutcome spawn(SpawnRequest request);
    }

    public record SpawnRequest(int mapIndex, int basisLevel, int hellDifficulty,
                               int championCount,
                               List<EventMonsterPlacement> eventMonsters,
                               int dungeonIndex, int dungeonDiff,
                               List<Integer> conditionMonsterIds) {
        public SpawnRequest {
            eventMonsters = List.copyOf(eventMonsters);
            conditionMonsterIds = List.copyOf(conditionMonsterIds);
        }

        public SpawnRequest(int mapIndex, int basisLevel, int hellDifficulty,
                            int championCount, List<EventMonsterPlacement> eventMonsters,
                            int dungeonIndex, int dungeonDiff) {
            this(mapIndex, basisLevel, hellDifficulty, championCount, eventMonsters,
                    dungeonIndex, dungeonDiff, List.of());
        }

        public SpawnRequest(int mapIndex, int basisLevel, int hellDifficulty,
                            int championCount, List<EventMonsterPlacement> eventMonsters) {
            this(mapIndex, basisLevel, hellDifficulty, championCount, eventMonsters,
                    NO_DUNGEON, 0);
        }

        public static SpawnRequest of(int mapIndex, int basisLevel) {
            return new SpawnRequest(mapIndex, basisLevel, HellPartyService.DIFFICULTY_NONE,
                    0, List.of());
        }

        public static final int NO_DUNGEON = -1;
    }

    public MoveResult moveMap(int toX, int toY, Spawner spawner) {
        DungeonRoom room = currentRoom();
        int blocking = room.clearableLivingCount();
        if (blocking > 0) {
            return MoveResult.refused(Cmd48ReplyBody.ERROR_NOT_CLEARED);
        }

        if (toX > MAX_ROOM_X || toY >= MAX_ROOM_Y) {
            return MoveResult.refused(Cmd48ReplyBody.ERROR_BAD_REQUEST);
        }

        if (toX == curX && toY == curY) {
            return MoveResult.refused(Cmd48ReplyBody.ERROR_SAME_ROOM);
        }

        if (toX < 0 || toY < 0 || toX >= maze.width() || toY >= maze.height()) {
            return MoveResult.refused(Cmd48ReplyBody.ERROR_NO_SUCH_ROOM);
        }

        int bossCondition = checkBossMap(toX, toY);
        if (bossCondition != 0) {
            return MoveResult.refused(bossCondition);
        }

        int dx = Math.abs(toX - curX);
        int dy = Math.abs(toY - curY);
        if (dx > 1 || dy > 1 || (dx == 1 && dy == 1)) {
            return MoveResult.refused(Cmd48ReplyBody.ERROR_NO_SUCH_ROOM);
        }

        int gridCode = maze.gridCode(toX, toY);
        if (gridCode == GridCode.EMPTY) {
            return MoveResult.refused(Cmd48ReplyBody.ERROR_NO_SUCH_ROOM);
        }

        RoomState target = rooms.get(key(toX, toY));
        if (target != null && target.visited()) {
            leaveCurrentRoom(true);
            swapCurPos(toX, toY);
            return new MoveResult(0, true, target.mapIndex(), Noti29Body.MODE_VISITED,
                    target.room());
        }

        int direction = GridCode.invadeDirection(curX, curY, toX, toY);
        DummyBossPick dummy = chooseRandomBossMapFromDummyMaps(toX, toY, direction);
        int mapIndex = dummy.mapIndex();
        if (mapIndex == NO_MAP) {
            mapIndex = getCandidatorMap(toX, toY, gridCode, direction);
        }
        if (mapIndex <= 0) {
            return MoveResult.refused(Cmd48ReplyBody.ERROR_NO_SUCH_ROOM);
        }

        SpawnOutcome spawned = spawner.spawn(new SpawnRequest(mapIndex, basisLevel,
                hellPartyAppeared ? hellPartyDifficulty : HellPartyService.DIFFICULTY_NONE,
                rollChampionCount(), eventMonstersAt(toX, toY), dungeon.id(), dungeonDiff,
                conditionMonstersAt(toX, toY)));
        RoomState prior = rooms.get(key(toX, toY));
        DungeonRoom fresh = prior == null
                ? DungeonRoom.of(spawned.monsters())
                : prior.room().respawn(spawned.monsters());
        fresh.resetHellGroups(spawned.hellGroups());
        lockIntoObjects(fresh, spawned);
        leaveCurrentRoom(dummy.leavingVisited());
        swapCurPos(toX, toY);
        int mode = prior == null ? Noti29Body.MODE_PLAIN : prior.mode();
        rooms.put(key(toX, toY),
                new RoomState(false, mapIndex, mode, dummy.dummyBossRoom(), fresh));
        registerBosses(fresh, dummy.dummyBossRoom());
        return new MoveResult(0, false, mapIndex, mode, fresh);
    }

    private int chooseQuestStageMap(int x, int y) {
        if (questStageMap <= 0 || x != questX || y != questY) {
            return 0;
        }
        return questStageMap;
    }

    private int chooseHellRoomMap(int x, int y) {
        if (!isHellRoom(x, y) || hellRoomMapIndex <= 0) {
            return 0;
        }
        enteredHellRoom = true;
        return hellRoomMapIndex;
    }

    private void leaveCurrentRoom(boolean visited) {
        RoomState state = rooms.get(key(curX, curY));
        if (state == null) {
            rooms.put(key(curX, curY),
                    new RoomState(visited, 0, Noti29Body.MODE_PLAIN, DungeonRoom.empty()));
            return;
        }
        rooms.put(key(curX, curY), new RoomState(visited, state.mapIndex(), state.mode(),
                state.dummyBossRoom(), state.room()));
    }

    private void swapCurPos(int toX, int toY) {
        this.lastX = curX;
        this.lastY = curY;
        this.curX = toX;
        this.curY = toY;
    }

    private static final int NO_MAP = -1;

    private record DummyBossPick(int mapIndex, boolean dummyBossRoom, boolean leavingVisited) {
        static final DummyBossPick NONE = new DummyBossPick(NO_MAP, false, true);
    }

    private DummyBossPick chooseRandomBossMapFromDummyMaps(int x, int y, int direction) {
        if (dummyRooms.isEmpty() || x != bossX || y != bossY) {
            return DummyBossPick.NONE;
        }
        if (dummyCursor >= dummyRooms.size()) {
            return DummyBossPick.NONE;
        }

        DungeonScript.Room next = dummyRooms.get(dummyCursor++);
        bossX = next.x();
        bossY = next.y();

        RoomState target = rooms.get(key(bossX, bossY));
        if (target != null && target.visited()) {
            rooms.put(key(bossX, bossY), new RoomState(false, target.mapIndex(),
                    Noti29Body.MODE_EXTRA, target.dummyBossRoom(), target.room()));
        }

        boolean leavingVisited = true;
        if (bossX == curX && bossY == curY) {
            leavingVisited = false;
            RoomState here = rooms.get(key(curX, curY));
            if (here != null) {
                rooms.put(key(curX, curY), new RoomState(here.visited(), here.mapIndex(),
                        Noti29Body.MODE_EXTRA, here.dummyBossRoom(), here.room()));
            }
        }

        int mapIndex = maps.chooseRandomMap(DungeonMaps.TYPE_DUMMY, maze.gridCode(x, y),
                direction, random);
        return new DummyBossPick(mapIndex > 0 ? mapIndex : NO_MAP, true, leavingVisited);
    }

    private int getCandidatorMap(int x, int y, int gridCode, int direction) {
        if (isEndPoint(x, y)) {
            List<Integer> bossSpec = maze.bossSpecifiedMaps(x, y);
            if (!bossSpec.isEmpty()) {
                return pickSpecified(bossSpec, x, y, direction, "[boss map specification]");
            }
            int bossMap = maps.chooseRandomMap(DungeonMaps.TYPE_BOSS, gridCode, direction, random);
            return bossMap;
        }

        int questMap = chooseQuestStageMap(x, y);
        if (questMap > 0) {
            return questMap;
        }

        int hellMap = chooseHellRoomMap(x, y);
        if (hellMap > 0) {
            return hellMap;
        }

        List<Integer> specified = maze.specifiedMaps(x, y);
        if (!specified.isEmpty()) {
            return pickSpecified(specified, x, y, direction, "[map specification]");
        }
        return maps.chooseRandomMap(DungeonMaps.TYPE_NORMAL, gridCode, direction, random);
    }

    private int pickSpecified(List<Integer> specified, int x, int y, int direction,
                              String section) {
        List<Integer> candidates = new ArrayList<>(specified.size());
        for (int mapId : specified) {
            if (direction == 0 || maps.checkEntrance(mapId, direction)) {
                candidates.add(mapId);
            }
        }
        if (!candidates.isEmpty()) {
            return candidates.get(random.nextInt(candidates.size()));
        }
        return 0;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
