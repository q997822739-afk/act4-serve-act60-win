package com.dylan.game;

import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.DungeonMaps;
import com.dylan.pvf.script.DungeonScript;
import com.dylan.protocol.body.Noti28Body;
import com.dylan.protocol.body.Noti29Body;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public record DungeonRun(int dungeonIndex, int difficulty, int mazeIndex,
                         int startX, int startY, int bossX, int bossY,
                         int sealDoorX, int sealDoorY, int mapIndex, int basisLevel,
                         boolean fromScripts, int coinLimit, double expWeight,
                         int questX, int questY, int questStageMap,
                         com.dylan.pvf.script.DungeonScript.CoinQuota coinQuota) {

    public DungeonRun(int dungeonIndex, int difficulty, int mazeIndex,
                      int startX, int startY, int bossX, int bossY,
                      int sealDoorX, int sealDoorY, int mapIndex, int basisLevel,
                      boolean fromScripts, int coinLimit, double expWeight,
                      int questX, int questY, int questStageMap) {
        this(dungeonIndex, difficulty, mazeIndex, startX, startY, bossX, bossY,
                sealDoorX, sealDoorY, mapIndex, basisLevel, fromScripts, coinLimit, expWeight,
                questX, questY, questStageMap,
                com.dylan.pvf.script.DungeonScript.CoinQuota.NONE);
    }

    public int questX() {
        return questX;
    }

    public DungeonRun(int dungeonIndex, int difficulty, int mazeIndex,
                      int startX, int startY, int bossX, int bossY,
                      int sealDoorX, int sealDoorY, int mapIndex, int basisLevel,
                      boolean fromScripts, int coinLimit) {
        this(dungeonIndex, difficulty, mazeIndex, startX, startY, bossX, bossY,
                sealDoorX, sealDoorY, mapIndex, basisLevel, fromScripts, coinLimit,
                com.dylan.pvf.script.DungeonScript.DEFAULT_EXP_WEIGHT);
    }

    public DungeonRun(int dungeonIndex, int difficulty, int mazeIndex,
                      int startX, int startY, int bossX, int bossY,
                      int sealDoorX, int sealDoorY, int mapIndex, int basisLevel,
                      boolean fromScripts, int coinLimit, double expWeight) {
        this(dungeonIndex, difficulty, mazeIndex, startX, startY, bossX, bossY,
                sealDoorX, sealDoorY, mapIndex, basisLevel, fromScripts, coinLimit,
                expWeight, 0, 0, NO_QUEST_STAGE_MAP);
    }

    public static final int NO_QUEST_STAGE_MAP = 0;

    public int questStageMap() {
        return questStageMap;
    }

    public static final int COIN_LIMIT_NONE =
            com.dylan.pvf.script.DungeonScript.COIN_LIMIT_NONE;

    public DungeonRun(int dungeonIndex, int difficulty, int mazeIndex,
                      int startX, int startY, int bossX, int bossY,
                      int sealDoorX, int sealDoorY, int mapIndex, int basisLevel,
                      boolean fromScripts) {
        this(dungeonIndex, difficulty, mazeIndex, startX, startY, bossX, bossY,
                sealDoorX, sealDoorY, mapIndex, basisLevel, fromScripts, COIN_LIMIT_NONE);
    }

    public interface PartyQuests {

        boolean checkQuestConnection(int questId, int questType);

        int memberCount();

        default int questStageMap(int dungeonIndex, int difficulty, RandomGenerator random) {
            return NO_QUEST_STAGE_MAP;
        }

        PartyQuests SOLO_NO_QUEST = new PartyQuests() {
            @Override
            public boolean checkQuestConnection(int questId, int questType) {
                return false;
            }

            @Override
            public int memberCount() {
                return 1;
            }
        };
    }

    public static DungeonRun plan(GameScripts scripts, int dungeonIndex, int difficulty,
                                  int fallbackMap, RandomGenerator random) {
        return plan(scripts, dungeonIndex, difficulty, fallbackMap, random,
                PartyQuests.SOLO_NO_QUEST, false);
    }

    public static DungeonRun plan(GameScripts scripts, int dungeonIndex, int difficulty,
                                  int fallbackMap, RandomGenerator random,
                                  PartyQuests partyQuests, boolean hellAppeared) {
        if (scripts == null) {
            return fallback(dungeonIndex, difficulty, fallbackMap);
        }
        DungeonScript dungeon = scripts.dungeon(dungeonIndex).orElse(null);
        if (dungeon == null || dungeon.mazes().isEmpty()) {
            return fallback(dungeonIndex, difficulty, fallbackMap);
        }

        DungeonScript.Maze maze = chooseMaze(dungeon, difficulty, random, partyQuests,
                hellAppeared);
        if (maze == null) {
            return fallback(dungeonIndex, difficulty, fallbackMap);
        }

        DungeonScript.Room start = pick(maze.startRooms(), random);
        int startX = start == null ? 0 : start.x();
        int startY = start == null ? 0 : start.y();

        List<DungeonScript.Room> bossRooms = usable(maze.bossRooms());
        int bossPick = bossRooms.isEmpty() ? -1 : random.nextInt(bossRooms.size());
        DungeonScript.Room boss = bossPick < 0 ? null : bossRooms.get(bossPick);
        DungeonScript.Room quest = questRoom(bossRooms, bossPick);

        DungeonMaps maps = scripts.mapsFor(dungeonIndex);
        int mapIndex = maps.startMap(maze.gridCode(startX, startY), random);
        if (mapIndex < 0) {
            return fallback(dungeonIndex, difficulty, fallbackMap);
        }

        int questStageMap = partyQuests.questStageMap(dungeonIndex, difficulty, random);

        // A4 官方：迷宫没有封印门（pvf 无 [seal door map index]）时 NOTI 28 末两字节是 FF FF
        // （dungeon 1 实抓 `01 00 00 01 00 02 ff ff`）；ACT5 写 0,0 会被 A4 客户端当成 (0,0) 有封印门。
        // 有封印门但这局不是深渊也是 FF FF（天空之城 11 普通 `0b 00 00 00 03 02 ff ff`，
        // 深渊 `… 01 02`，probe_ticket.py）；否则普通模式也画出封印门，不扣破魔石就能进深渊房。
        boolean noSeal = com.dylan.net.ServerConfig.A4 && (maze.sealDoorMapIndex() <= 0
                || !hellAppeared
                || maze.width() + maze.height() <= BattleField.MAZE_TOO_SMALL_FOR_HELL);
        return new DungeonRun(dungeonIndex, difficulty, maze.index(),
                startX, startY,
                boss == null ? 0 : boss.x(), boss == null ? 0 : boss.y(),
                noSeal ? A4_NO_ROOM : maze.sealDoorX(), noSeal ? A4_NO_ROOM : maze.sealDoorY(),
                mapIndex, dungeon.basisLevel(), true,
                dungeon.coinLimit(), dungeon.expWeight(),
                quest == null ? 0 : quest.x(), quest == null ? 0 : quest.y(),
                questStageMap,
                dungeon.coinQuota());
    }

    static DungeonScript.Maze chooseMaze(DungeonScript dungeon, int difficulty,
                                         RandomGenerator random, PartyQuests partyQuests,
                                         boolean hellAppeared) {
        for (int type = DungeonScript.QuestConnection.TYPE_DOING;
                type <= DungeonScript.QuestConnection.TYPE_CLEARED; type++) {
            List<DungeonScript.Maze> pool = dungeon.questMazes(type);
            if (pool.isEmpty()) {
                continue;
            }
            List<DungeonScript.Maze> candidates = new ArrayList<>();
            for (DungeonScript.Maze maze : pool) {
                var connection = maze.questConnection();
                if (hellAppeared && maze.escapeHell()) {
                    continue;
                }
                if (!partyQuests.checkQuestConnection(connection.questId(), type)) {
                    continue;
                }
                if (type == DungeonScript.QuestConnection.TYPE_CLEARED
                        || difficulty >= connection.minDifficulty()) {
                    candidates.add(maze);
                }
            }
            if (!candidates.isEmpty()) {
                return candidates.get(random.nextInt(candidates.size()));
            }
        }
        return randMazeByPartyCount(dungeon, random);
    }

    private static DungeonScript.Maze randMazeByPartyCount(DungeonScript dungeon,
                                                           RandomGenerator random) {
        List<DungeonScript.Maze> pool = dungeon.normalMazes();
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(random.nextInt(pool.size()));
    }

    public static DungeonRun fallback(int dungeonIndex, int difficulty, int mapIndex) {
        return new DungeonRun(dungeonIndex, difficulty, 0, 0, 0, 0, 0, 0, 0, mapIndex, 1, false,
                COIN_LIMIT_NONE);
    }

    /** A4 官方没有地下城脚本（教程 10000）时 NOTI 28 的 Boss / 封印门坐标是 {@code FF FF FF FF}。 */
    private static final int A4_NO_ROOM = 0xFF;

    public Noti28Body dungeonInfo() {
        if (!fromScripts && com.dylan.net.ServerConfig.A4) {
            return new Noti28Body(dungeonIndex, difficulty, mazeIndex,
                    A4_NO_ROOM, A4_NO_ROOM, A4_NO_ROOM, A4_NO_ROOM);
        }
        return new Noti28Body(dungeonIndex, difficulty, mazeIndex,
                bossX, bossY, sealDoorX, sealDoorY);
    }

    public Noti29Body startMap(int mode) {
        return startMap(mode, List.of());
    }

    public Noti29Body startMap(int mode, List<RoomMonster> monsters) {
        return startMap(mode, monsters, 0);
    }

    public Noti29Body startMap(int mode, List<RoomMonster> monsters, int extraFlag) {
        return startMap(mode, monsters, extraFlag, com.dylan.game.HellPartyService
                .DIFFICULTY_NONE, false);
    }

    public Noti29Body startMap(int mode, List<RoomMonster> monsters, int extraFlag,
                               int hellPartyDifficulty, boolean hellPartyAppeared) {
        return startMap(mode, monsters, extraFlag, hellPartyDifficulty, hellPartyAppeared,
                List.of());
    }

    public Noti29Body startMap(int mode, List<RoomMonster> monsters, int extraFlag,
                               int hellPartyDifficulty, boolean hellPartyAppeared,
                               List<Noti29Body.MapObject> objects) {
        List<Noti29Body.Entity> entities = fromScripts
                ? monsters.stream().map(m -> m.toEntity(extraFlag)).toList()
                : List.of();
        return new Noti29Body(startX, startY, 0, hellPartyDifficulty, mode, mapIndex,
                entities, fromScripts ? objects : List.of(), hellPartyAppeared ? 1 : 0);
    }

    private static DungeonScript.Room pick(List<DungeonScript.Room> rooms,
                                           RandomGenerator random) {
        List<DungeonScript.Room> usable = usable(rooms);
        return usable.isEmpty() ? null : usable.get(random.nextInt(usable.size()));
    }

    private static List<DungeonScript.Room> usable(List<DungeonScript.Room> rooms) {
        return rooms.stream().filter(r -> !r.isNone()).toList();
    }

    private static DungeonScript.Room questRoom(List<DungeonScript.Room> bossRooms,
                                                int bossPick) {
        if (bossPick < 0) {
            return null;
        }
        DungeonScript.Room quest = null;
        if (bossPick - 1 >= 0) {
            quest = bossRooms.get(bossPick - 1);
        }
        if (bossPick + 1 <= bossRooms.size() - 1) {
            quest = bossRooms.get(bossPick + 1);
        }
        return quest;
    }
}
