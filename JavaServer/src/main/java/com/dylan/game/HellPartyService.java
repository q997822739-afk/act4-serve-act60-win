package com.dylan.game;

import com.dylan.protocol.body.Cmd16ReplyBody;
import com.dylan.protocol.body.Cmd16SelectDungeonRequest;
import com.dylan.protocol.body.ItemListType;
import com.dylan.protocol.body.Noti27Body;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.DungeonScript;
import com.dylan.pvf.script.HellPartyTable;
import com.dylan.pvf.script.WorldMapScript;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

public final class HellPartyService {
    // 原版 A5 写死 true（深渊不查不扣破魔石）。A4 官方实抓（probe_ticket.py）：
    // 选深渊扣 worldmap [item condition] 1 个（天空之城 4150），没有回 REPLY 16 `00 04`。
    public static final boolean SKIP_SEAL_STONE_FOR_TEST = !com.dylan.net.ServerConfig.A4;

    public static final int ERROR_NO_SEAL_DOOR_ITEM = com.dylan.net.ServerConfig.A4 ? 4 : 27;

    private HellPartyService() {
    }

    public static Optional<WorldMapScript> worldMapOf(Party party, GameSession s) {
        int index = worldMapIndexAt(s);
        party.worldMapIndex(index);
        return worldMapByIndex(s, index);
    }

    /** 同 {@link #worldMapOf}，但不改 party 的世界地图记录（只查不写）。 */
    public static Optional<WorldMapScript> worldMapAt(GameSession s) {
        return worldMapByIndex(s, worldMapIndexAt(s));
    }

    private static Optional<WorldMapScript> worldMapByIndex(GameSession s, int index) {
        if (index <= 0) {
            return Optional.empty();
        }
        GameScripts scripts = s.scripts();
        return scripts == null ? Optional.empty() : scripts.worldMaps().byIndex(index);
    }

    private static int worldMapIndexAt(GameSession s) {
        GameScripts scripts = s.scripts();
        if (scripts == null || !scripts.towns().checkValidArea(s.townId(), s.areaId())) {
            return Party.NO_WORLD_MAP;
        }
        return scripts.towns().area(s.townId(), s.areaId())
                .map(com.dylan.pvf.script.TownScript.Area::worldMapIndex)
                .orElse(Party.NO_WORLD_MAP);
    }

    public static boolean checkHavePassItem(Party party, GameSession initiator, int dungeonType) {
        Optional<WorldMapScript> worldMap = worldMapOf(party, initiator);
        if (worldMap.isEmpty()) {
            return true;
        }
        if (!worldMap.get().isInHellDungeon()
                || dungeonType != Cmd16SelectDungeonRequest.DUNGEON_TYPE_HELL_PARTY) {
            return true;
        }

        if (SKIP_SEAL_STONE_FOR_TEST) {
            party.hellPassChecked(true);
            return true;
        }

        List<Integer> blockers = new ArrayList<>(Party.MAX_MEMBERS);
        boolean ok = checkDestroyConditionSealDoor(party, worldMap.get(), blockers);
        party.hellPassChecked(ok);
        if (ok) {
            return true;
        }
        sendNoPassItem(party, blockers);
        return false;
    }

    private static boolean checkDestroyConditionSealDoor(Party party, WorldMapScript worldMap,
                                                         List<Integer> blockers) {
        Party.View view = party.view();
        int valid = 0;
        int passed = 0;
        for (int slot = 0; slot < Party.MAX_MEMBERS; slot++) {
            GameSession member = view.at(slot);
            if (member == null) {
                continue;
            }
            valid++;
            if (checkFreepassItem(worldMap, member, false)) {
                passed++;
                continue;
            }
            if (hasAllSealStones(member, worldMap)) {
                passed++;
            } else {
                blockers.add(slot);
            }
        }
        return valid <= passed;
    }

    private static boolean hasAllSealStones(GameSession member, WorldMapScript worldMap) {
        for (WorldMapScript.ItemCondition cond : worldMap.itemConditions()) {
            if (countInFirstSlot(member, cond.itemId()) < cond.count()) {
                return false;
            }
        }
        return true;
    }

    private static boolean checkFreepassItem(WorldMapScript worldMap, GameSession member,
                                             boolean delete) {
        for (WorldMapScript.FreepassItem pass : worldMap.freepassItems()) {
            int slot = firstSlotOf(member, pass.itemId());
            if (slot < 0) {
                continue;
            }
            if (!delete) {
                return true;
            }
            takeFrom(member, pass.itemId(), pass.count(), "深渊免费通行证 " + pass.itemId());
            return true;
        }
        return false;
    }

    public static boolean useSealDoorItems(Party party, GameSession initiator) {
        if (SKIP_SEAL_STONE_FOR_TEST) {
            return true;
        }
        Optional<WorldMapScript> worldMap = worldMapOf(party, initiator);
        if (worldMap.isEmpty()) {
            return false;
        }
        WorldMapScript map = worldMap.get();
        Party.View view = party.view();
        for (int slot = 0; slot < Party.MAX_MEMBERS; slot++) {
            GameSession member = view.at(slot);
            if (member == null || checkFreepassItem(map, member, true)) {
                continue;
            }
            for (WorldMapScript.ItemCondition cond : map.itemConditions()) {
                if (countInFirstSlot(member, cond.itemId()) < cond.count()) {
                    return false;
                }
                takeFrom(member, cond.itemId(), cond.count(),
                        "深渊入场破魔石 " + cond.itemId());
            }
        }
        return true;
    }

    private static void sendNoPassItem(Party party, List<Integer> blockers) {
        byte[] body = Cmd16ReplyBody.failure(ERROR_NO_SEAL_DOOR_ITEM);
        List<GameSession> members = party.view().members();
        for (GameSession member : members) {
            int slot = party.slotOf(member);
            member.run(() -> {
                // A4 客户端只在 REPLY 16 里读错误码（00411F2D）；原来 send 发成了 NOTI 16，客户端一直等回包
                if (com.dylan.net.ServerConfig.A4) {
                    member.reply(Cmd16ReplyBody.PACKET_ID, body);
                } else {
                    member.send(Cmd16ReplyBody.PACKET_ID, body);
                }
                for (int blocked : blockers) {
                    GameSession who = party.view().at(blocked);
                    String name = who == null ? "第 " + blocked + " 槽的队员"
                            : (blocked == slot ? "你" : who.characName());
                    ChatService.system(member, "[" + name + "]因道具不足，无法开启深渊派对");
                }
                member.flush();
            });
        }
    }

    public static void initHellPartyValue(Party party, GameSession founder) {
        party.hellPartyValue(tableOf(founder).baseValue());
    }

    public static void calHellPartyValue(Party party, HellPartyTable table, DungeonScript dungeon,
                                         int difficulty, int levelDiffRate, int partyRate) {
        if (levelDiffRate == 0 || partyRate < 0
                || difficulty >= HellPartyTable.DIFFICULTY_RATE_SLOTS) {
            return;
        }
        double ancient = dungeon != null && dungeon.ancientDungeon()
                ? table.ancientDungeonAddRate() : 0.0;
        double before = party.hellPartyValue();
        double value = before + table.baseRisingValue()
                * table.levelDifferenceRate(levelDiffRate)
                * (table.difficultyRate(difficulty) + 1.0 + ancient
                        + table.partyRate(partyRate));
        if ((int) value > table.maxBaseValue()) {
            value = table.maxBaseValue();
        }
        party.hellPartyValue(value);
    }

    public static void subHellPartyValue(Party party, HellPartyTable table) {
        double before = party.hellPartyValue();
        double value = before * table.subBaseRate();
        if ((int) value < table.minBaseValue()) {
            value = table.minBaseValue();
        }
        party.hellPartyValue(value);
    }

    public static int rateLevelDifference(DungeonScript dungeon, int memberMaxLevel) {
        if (dungeon == null) {
            return 0;
        }
        int d = Math.abs(dungeon.standardLevel() - memberMaxLevel);
        if (d <= 10) {
            return 1;
        }
        return d > 19 ? 3 : 2;
    }

    public static int memberMaxLevel(Party party) {
        int max = 0;
        for (GameSession member : party.view().members()) {
            max = Math.max(max, member.level());
        }
        return max;
    }

    public static void onClearDungeon(Party party, GameSession initiator, BattleField field) {
        int count = party.memberCount();
        if (count == 1) {
            HellPartyTable table = tableOf(initiator);
            party.hellPartyValue(table.baseValue());
            return;
        }
        if (field != null && (field.killedAllHellGroups() || field.hellPartyMapTooSmall()
                || field.hellPartyAppeared())) {
            return;
        }
        var instance = party.dungeon();
        DungeonScript dungeon = dungeonOf(initiator, instance);
        int difficulty = instance == null ? 0 : instance.run().difficulty();
        calHellPartyValue(party, tableOf(initiator), dungeon, difficulty,
                rateLevelDifference(dungeon, memberMaxLevel(party)), count - 1);
    }

    public static int hellPartyValuePercent(Party party, HellPartyTable table) {
        int total = Math.max(1, table.maxBaseValue());
        return (int) (party.hellPartyValue() * 100.0 / total);
    }

    public static int hellPartyValuePercent(Party party, GameSession any) {
        return hellPartyValuePercent(party, tableOf(any));
    }

    public static final boolean HELL_DUNGEON_ENABLED = true;

    public static Noti27Body startGame(Party party, GameSession initiator) {
        party.hellPartyEnabled(false);

        if (party.memberCount() == 1) {
            HellPartyTable table = tableOf(initiator);
            party.hellPartyValue(table.baseValue());
        }
        int percent = hellPartyValuePercent(party, initiator);

        Optional<WorldMapScript> worldMap = worldMapOf(party, initiator);
        if (worldMap.isEmpty() || !worldMap.get().isInHellDungeon() || !HELL_DUNGEON_ENABLED) {
            // A4 官方：不是深渊世界地图（格兰之森毕勒马克门口）NOTI 27 是 `00 00`，ACT5 这里给 1
            int flag = com.dylan.net.ServerConfig.A4 ? 0 : Noti27Body.ABYSS_OK;
            return new Noti27Body(percent, flag, List.of(), List.of(), 0);
        }

        WorldMapScript map = worldMap.get();
        List<Integer> questBlockers = checkClearQuestHellParty(party, map);
        int abyssFlag = questBlockers.isEmpty() ? Noti27Body.ABYSS_OK : 0;
        if (questBlockers.isEmpty()) {
            party.hellPartyEnabled(true);
        }

        List<Integer> itemBlockers = checkSealDoorItemBlockers(party, map);
        return new Noti27Body(percent, abyssFlag, itemBlockers, List.of(), 0);
    }

    public static List<Integer> checkClearQuestHellParty(Party party, WorldMapScript worldMap) {
        List<Integer> out = new ArrayList<>(Party.MAX_MEMBERS);
        if (worldMap == null || worldMap.hellQuests().isEmpty()) {
            return out;
        }
        Party.View view = party.view();
        for (int slot = 0; slot < Party.MAX_MEMBERS; slot++) {
            GameSession member = view.at(slot);
            if (member == null) {
                continue;
            }
            for (int quest : worldMap.hellQuests()) {
                if (!isClearQuest(member, quest)) {
                    out.add(slot);
                    break;
                }
            }
        }
        return out;
    }

    private static boolean isClearQuest(GameSession member, int questId) {
        var quests = member.quests();
        return quests != null && quests.cleared().isCleared(questId);
    }

    public static List<Integer> checkSealDoorItemBlockers(Party party, WorldMapScript worldMap) {
        List<Integer> out = new ArrayList<>(Party.MAX_MEMBERS);
        if (SKIP_SEAL_STONE_FOR_TEST) {
            return out;
        }
        if (worldMap == null || worldMap.itemConditions().isEmpty()) {
            return out;
        }
        Party.View view = party.view();
        for (int slot = 0; slot < Party.MAX_MEMBERS; slot++) {
            GameSession member = view.at(slot);
            if (member == null || checkFreepassItem(worldMap, member, false)) {
                continue;
            }
            if (!hasAllSealStones(member, worldMap)) {
                out.add(slot);
            }
        }
        return out;
    }

    public static HellPartyTable tableOf(GameSession s) {
        GameScripts scripts = s == null ? null : s.scripts();
        return scripts == null ? HellPartyTable.empty() : scripts.hellParty();
    }

    private static DungeonScript dungeonOf(GameSession s, DungeonInstance instance) {
        GameScripts scripts = s == null ? null : s.scripts();
        if (scripts == null || instance == null) {
            return null;
        }
        return scripts.dungeon(instance.run().dungeonIndex()).orElse(null);
    }

    public static final int DIFFICULTY_NONE = 0;

    private static final int DIFFICULTY_ROLL_BOUND = 100;

    public record HellPartyEntry(boolean appeared, int difficulty) {
        public static final HellPartyEntry NONE = new HellPartyEntry(false, DIFFICULTY_NONE);
    }

    public static int getHellPartyDifficulty(HellPartyTable table, int dungeonType,
                                             RandomGenerator random) {
        boolean sealStone = dungeonType == Cmd16SelectDungeonRequest.DUNGEON_TYPE_HELL_PARTY;
        int sum = 0;
        int roll = random.nextInt(DIFFICULTY_ROLL_BOUND);
        for (int grade = 1; grade <= HellPartyTable.MAX_DIFFICULTY_GRADES; grade++) {
            HellPartyTable.Difficulty d = table.difficulty(grade);
            sum += d == null ? 0 : (sealStone ? d.sealStoneProb() : d.randomProb());
            if (sum >= roll) {
                return grade;
            }
        }
        return DIFFICULTY_NONE;
    }

    public static boolean checkHellPartyCondition(Party party, HellPartyTable table,
                                                  RandomGenerator random) {
        int value = (int) party.hellPartyValue();
        int roll = random.nextInt(DIFFICULTY_ROLL_BOUND);
        boolean hit = value >= roll && value >= table.guideLineValue();
        return hit;
    }

    public static boolean checkRandomAppearHellDungeon(Party party, GameSession initiator,
                                                       DungeonScript dungeon, int dungeonType,
                                                       int quickParty) {
        boolean canAppear = false;
        Optional<WorldMapScript> worldMap = worldMapOf(party, initiator);
        if (worldMap.isPresent()) {
            if (HELL_DUNGEON_ENABLED && worldMap.get().isInHellDungeon()) {
                int maxLevel = 1;
                int minLevel = 200;
                int count = 0;
                Party.View view = party.view();
                for (int slot = 0; slot < Party.MAX_MEMBERS; slot++) {
                    GameSession member = view.at(slot);
                    if (member == null) {
                        continue;
                    }
                    int level = Math.max(1, member.level());
                    maxLevel = Math.max(maxLevel, level);
                    minLevel = Math.min(minLevel, level);
                    count++;
                }
                int standard = dungeon == null ? 0 : dungeon.standardLevel();
                if (maxLevel - standard <= MAX_LEVEL_OVER_STANDARD
                        && (count <= 1 || maxLevel - minLevel <= MAX_LEVEL_GAP)) {
                    canAppear = true;
                }
                boolean hellParty = dungeonType
                        == Cmd16SelectDungeonRequest.DUNGEON_TYPE_HELL_PARTY;
                boolean levelGapTooWide = maxLevel - minLevel > MAX_LEVEL_GAP && count > 1;
                boolean questNotDone = !party.hellPartyEnabled() && hellParty;
                boolean passNotChecked = !party.hellPassChecked() && hellParty;
                if (levelGapTooWide || questNotDone || passNotChecked) {
                    party.worldMapIndex(Party.NO_WORLD_MAP);
                }
            }
        }
        if (quickParty == Cmd16SelectDungeonRequest.QUICK_PARTY_ON) {
            party.worldMapIndex(Party.NO_WORLD_MAP);
            canAppear = false;
        }
        return canAppear;
    }

    public static final int MAX_LEVEL_OVER_STANDARD = 5;

    public static final int MAX_LEVEL_GAP = 8;

    public static HellPartyEntry selectDungeon(Party party, GameSession initiator,
                                               DungeonScript dungeon, int dungeonType,
                                               boolean randomAppear, RandomGenerator random) {
        HellPartyEntry entry = HellPartyEntry.NONE;
        Optional<WorldMapScript> worldMap = worldMapOf(party, initiator);
        if (HELL_DUNGEON_ENABLED && worldMap.isPresent() && worldMap.get().isInHellDungeon()) {
            HellPartyTable table = tableOf(initiator);
            if (com.dylan.net.ServerConfig.A4 && table.isEmpty()) {
                // A4 的 PVF 没有 etc/hellparty.etc，深渊是 60 版封印门（.dgn [seal door map index]，
                // 打开后进 hell_*.map），没有难度档、不随机出现。官方日志：选深渊 option=1 → hell=True，
                // 普通 option=0 → hell=False。原来这里掷不出难度返回 null，选深渊直接断线。
                entry = dungeonType == Cmd16SelectDungeonRequest.DUNGEON_TYPE_HELL_PARTY
                        ? new HellPartyEntry(true, DIFFICULTY_NONE) : HellPartyEntry.NONE;
            } else if (dungeonType == Cmd16SelectDungeonRequest.DUNGEON_TYPE_HELL_PARTY) {
                subHellPartyValue(party, table);
                int difficulty = getHellPartyDifficulty(table, dungeonType, random);
                if (difficulty == DIFFICULTY_NONE) {
                    return null;
                }
                entry = new HellPartyEntry(true, difficulty);
            } else if (randomAppear) {
                if (party.memberCount() != 1 && checkHellPartyCondition(party, table, random)) {
                    subHellPartyValue(party, table);
                    int difficulty = getHellPartyDifficulty(table, dungeonType, random);
                    if (difficulty == DIFFICULTY_NONE) {
                        return null;
                    }
                    entry = new HellPartyEntry(true, difficulty);
                }
            }
        }
        if (entry.appeared() && (dungeon == null || !dungeon.hellDungeon())) {
            entry = new HellPartyEntry(false, entry.difficulty());
        }
        return entry;
    }

    private static int firstSlotOf(GameSession member, int itemId) {
        var array = itemsOf(member);
        if (array == null) {
            return -1;
        }
        for (int slot = BagLayout.FIRST_ITEM_SLOT; slot < array.slots(); slot++) {
            var item = array.get(slot);
            if (!item.isEmpty() && item.itemId() == itemId) {
                return slot;
            }
        }
        return -1;
    }

    private static long countInFirstSlot(GameSession member, int itemId) {
        int slot = firstSlotOf(member, itemId);
        if (slot < 0) {
            return 0;
        }
        var item = itemsOf(member).get(slot);
        return item.stackCount();
    }

    private static void takeFrom(GameSession member, int itemId, int count, String why) {
        member.run(() -> {
            var inv = member.inventory();
            var taken = InventoryService.consume(inv.inventory().inventory(), itemId, count);
            if (taken.isEmpty()) {
                return;
            }
            inv.markItemsDirty();
            inv.save();
            int[] slots = taken.stream().mapToInt(InventoryService.Taken::slot).toArray();
            inv.sendItemUpdate(ItemListType.LIST_MAIN, why, slots);
            member.flush();
        });
    }

    private static com.dylan.store.blob.ItemArray itemsOf(GameSession member) {
        var inv = member.inventory();
        if (inv == null || inv.inventory() == null) {
            return null;
        }
        return inv.inventory().inventory();
    }
}
