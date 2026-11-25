package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;

import java.util.ArrayList;
import java.util.List;

public record MapScript(String path, List<MonsterSpawn> monsters, Area startArea,
                        List<Greed> greeds, String type, List<Integer> dungeonIds,
                        List<TownPath> townPaths, List<NpcSpawn> npcs,
                        List<Area> rivalAreas, List<HellPartyGroup> hellPartyGroups,
                        List<AiCharacterSpawn> aiCharacters, int apcRandomPoint,
                        List<Area> eventMonsterPositions, List<MapItem> mapItems,
                        List<MonsterSpawn> conditionMonsters,
                        List<PassiveMonsterSpawn> passiveMonsters) {
    public MapScript(String path, List<MonsterSpawn> monsters, Area startArea,
                     List<Greed> greeds, String type, List<Integer> dungeonIds,
                     List<TownPath> townPaths, List<NpcSpawn> npcs, List<Area> rivalAreas,
                     List<HellPartyGroup> hellPartyGroups,
                     List<AiCharacterSpawn> aiCharacters, int apcRandomPoint,
                     List<Area> eventMonsterPositions, List<MapItem> mapItems,
                     List<MonsterSpawn> conditionMonsters) {
        this(path, monsters, startArea, greeds, type, dungeonIds, townPaths, npcs, rivalAreas,
                hellPartyGroups, aiCharacters, apcRandomPoint, eventMonsterPositions,
                mapItems, conditionMonsters, List.of());
    }

    public MapScript(String path, List<MonsterSpawn> monsters, Area startArea,
                     List<Greed> greeds, String type, List<Integer> dungeonIds,
                     List<TownPath> townPaths, List<NpcSpawn> npcs, List<Area> rivalAreas,
                     List<HellPartyGroup> hellPartyGroups,
                     List<AiCharacterSpawn> aiCharacters, int apcRandomPoint,
                     List<Area> eventMonsterPositions, List<MapItem> mapItems) {
        this(path, monsters, startArea, greeds, type, dungeonIds, townPaths, npcs, rivalAreas,
                hellPartyGroups, aiCharacters, apcRandomPoint, eventMonsterPositions,
                mapItems, List.of());
    }

    public MapScript(String path, List<MonsterSpawn> monsters, Area startArea,
                     List<Greed> greeds, String type, List<Integer> dungeonIds,
                     List<TownPath> townPaths, List<NpcSpawn> npcs, List<Area> rivalAreas,
                     List<HellPartyGroup> hellPartyGroups,
                     List<AiCharacterSpawn> aiCharacters, int apcRandomPoint,
                     List<Area> eventMonsterPositions) {
        this(path, monsters, startArea, greeds, type, dungeonIds, townPaths, npcs, rivalAreas,
                hellPartyGroups, aiCharacters, apcRandomPoint, eventMonsterPositions,
                List.of());
    }

    public MapScript(String path, List<MonsterSpawn> monsters, Area startArea,
                     List<Greed> greeds, String type, List<Integer> dungeonIds,
                     List<TownPath> townPaths, List<NpcSpawn> npcs, List<Area> rivalAreas,
                     List<HellPartyGroup> hellPartyGroups,
                     List<AiCharacterSpawn> aiCharacters, int apcRandomPoint) {
        this(path, monsters, startArea, greeds, type, dungeonIds, townPaths, npcs, rivalAreas,
                hellPartyGroups, aiCharacters, apcRandomPoint, List.of());
    }

    public MapScript(String path, List<MonsterSpawn> monsters, Area startArea,
                     List<Greed> greeds, String type, List<Integer> dungeonIds,
                     List<TownPath> townPaths) {
        this(path, monsters, startArea, greeds, type, dungeonIds, townPaths, List.of(),
                List.of());
    }

    public MapScript(String path, List<MonsterSpawn> monsters, Area startArea,
                     List<Greed> greeds, String type, List<Integer> dungeonIds,
                     List<TownPath> townPaths, List<NpcSpawn> npcs) {
        this(path, monsters, startArea, greeds, type, dungeonIds, townPaths, npcs, List.of());
    }

    public MapScript(String path, List<MonsterSpawn> monsters, Area startArea,
                     List<Greed> greeds, String type, List<Integer> dungeonIds,
                     List<TownPath> townPaths, List<NpcSpawn> npcs, List<Area> rivalAreas) {
        this(path, monsters, startArea, greeds, type, dungeonIds, townPaths, npcs, rivalAreas,
                List.of());
    }

    public MapScript(String path, List<MonsterSpawn> monsters, Area startArea,
                     List<Greed> greeds, String type, List<Integer> dungeonIds,
                     List<TownPath> townPaths, List<NpcSpawn> npcs, List<Area> rivalAreas,
                     List<HellPartyGroup> hellPartyGroups) {
        this(path, monsters, startArea, greeds, type, dungeonIds, townPaths, npcs, rivalAreas,
                hellPartyGroups, List.of(), 0);
    }

    public record HellPartyGroup(int groupIndex, int rate, int order) {
        public static final int MIN_ORDER = 1;
        public static final int MAX_ORDER = 9;

        public boolean orderInRange() {
            return order >= MIN_ORDER && order <= MAX_ORDER;
        }
    }

    public record MapItem(int assignIndex, int specifyDropCount, int randomDropCount,
                          int objectIndex) {
    }

    public record SpobjAction(int type, int p1, int p2, int p3, int p4, int p5) {
        public static final int TYPE_MONSTER = 0;

        public static final int TYPE_TRAP = 1;

        public static final int TYPE_ITEM = 2;

        public static final int TYPE_QUEST = 3;
    }

    public record SpecialPassiveObject(int objectId, int x, int y, int z,
                                       List<SpobjAction> actions, int objectIndex) {
        public SpecialPassiveObject {
            actions = List.copyOf(actions);
        }

        public static final int NO_OBJECT_INDEX = -1;
    }

    /**
     * [special passive object] 里的 {@code `[monster]` 怪物号 等级 分子 分母 -1}：物件打碎后冒出来的怪
     * （深渊房的封印柱 30518 → 1051，柱子 30519 → 1052；普通房的木桶也是这条）。
     *
     * <p>{@code objectIndex} 是这条物件在本图 [special passive object] 段里的顺序号（从 0 起，
     * 所有条目一起数，不管有没有动作）。台服 {@code CMap::set_map}：{@code area} 每个物件 +1，
     * 命中的怪 {@code insert_monster(monster, area)} → {@code m_champion=1, m_posX=area}。
     * 官方 A4 实抓 hell_whitenight 第 5 个物件 → NOTI 29 那只怪 named=1 group=5。
     */
    public record PassiveMonsterSpawn(int objectIndex, int monsterId, int level,
                                      int chanceNum, int chanceDen) {
    }

    public boolean hasHellParty() {
        return !hellPartyGroups.isEmpty();
    }

    public record AiCharacterSpawn(int apcIndex, int x, int y, int z,
                                   String camp, String roleType, int flag1, int flag2) {
        static final int NUMBERS_BEFORE_TAGS = 4;
        static final int NUMBERS_AFTER_TAGS = 2;

        public int campValue() {
            return switch (camp == null ? "" : camp.trim().toLowerCase()) {
                case "[monster]" -> CAMP_MONSTER;
                case "[neutral]" -> CAMP_NEUTRAL;
                default -> CAMP_CHARACTER;
            };
        }

        public int roleTypeValue() {
            return switch (roleType == null ? "" : roleType.trim().toLowerCase()) {
                case "[champion]" -> ROLE_CHAMPION;
                case "[super champion]" -> ROLE_SUPER_CHAMPION;
                case "[boss]" -> ROLE_BOSS;
                default -> ROLE_NORMAL;
            };
        }
    }

    public static final int CAMP_CHARACTER = 0;
    public static final int CAMP_MONSTER = 100;
    public static final int CAMP_NEUTRAL = 200;

    public static final int ROLE_NORMAL = 5;
    public static final int ROLE_CHAMPION = 6;
    public static final int ROLE_SUPER_CHAMPION = 7;
    public static final int ROLE_BOSS = 8;

    public boolean hasAiCharacters() {
        return !aiCharacters.isEmpty();
    }

    public int eventMonsterPositionCount() {
        return eventMonsterPositions.size();
    }

    public record Greed(int gridCode, int entrance) {}

    public static final int MAP_TYPE_NORMAL = 0;
    public static final int MAP_TYPE_BOSS = 1;
    public static final int MAP_TYPE_DUMMY = 2;

    public int mapType() {
        return switch (type == null ? "" : type.trim().toLowerCase()) {
            case TYPE_BOSS -> MAP_TYPE_BOSS;
            case TYPE_DUMMY -> MAP_TYPE_DUMMY;
            default -> MAP_TYPE_NORMAL;
        };
    }

    public boolean canBeStartMap() {
        return startArea != null && startArea.left() > 0;
    }

    public static final String TYPE_BOSS = "[boss]";

    public static final String TYPE_DUMMY = "[dummy]";

    public boolean isBoss() {
        return mapType() == MAP_TYPE_BOSS;
    }

    public record MonsterSpawn(int monsterId, int autoLevel, int level,
                               int x, int y, int z,
                               int randomDropCount, int specifyDropCount,
                               String copyType, String type, int camp, boolean fakeBoss) {
        public MonsterSpawn(int monsterId, int autoLevel, int level,
                            int x, int y, int z,
                            int randomDropCount, int specifyDropCount,
                            String copyType, String type, int camp) {
            this(monsterId, autoLevel, level, x, y, z, randomDropCount, specifyDropCount,
                    copyType, type, camp, false);
        }

        public MonsterSpawn(int monsterId, int autoLevel, int level,
                            int x, int y, int z,
                            int randomDropCount, int specifyDropCount,
                            String copyType, String type) {
            this(monsterId, autoLevel, level, x, y, z, randomDropCount, specifyDropCount,
                    copyType, type, CAMP_MONSTER);
        }

        public MonsterSpawn withCamp(int newCamp) {
            return new MonsterSpawn(monsterId, autoLevel, level, x, y, z,
                    randomDropCount, specifyDropCount, copyType, type, newCamp, fakeBoss);
        }

        MonsterSpawn asFakeBoss() {
            return new MonsterSpawn(monsterId, autoLevel, level, x, y, z,
                    randomDropCount, specifyDropCount, copyType, type, camp, true);
        }

        static final String CHAMPION_TAG = "[champion]";

        public int wireType() {
            return switch (type == null ? "" : type.toLowerCase()) {
                case CHAMPION_TAG -> TYPE_CHAMPION;
                case "[super champion]" -> TYPE_SUPER_CHAMPION;
                case "[boss]" -> TYPE_BOSS_MONSTER;
                default -> TYPE_NORMAL;
            };
        }

        public int levelFor(int basisLevel) {
            int resolved = autoLevel != 0 ? basisLevel + level : level;
            return Math.clamp(resolved, 1, 0xFF);
        }

        public boolean isBoss() {
            return wireType() == TYPE_BOSS_MONSTER;
        }
    }

    public static final int TYPE_NORMAL = 0;
    public static final int TYPE_CHAMPION = 1;
    public static final int TYPE_SUPER_CHAMPION = 2;
    public static final int TYPE_BOSS_MONSTER = 3;

    public record Area(int left, int top, int right, int bottom) {
        public static final Area UNKNOWN = new Area(0, 0, 0, 0);
    }

    public record TownPath(int x, int y, int width, int height, int town, int area) {
        static final int FIELDS = 6;
    }

    public record NpcSpawn(int npcId, String direction, int x, int y, int z) {
        static final int TRAILING_NUMBERS = 3;
    }

    public MapScript {
        monsters = List.copyOf(monsters);
        greeds = List.copyOf(greeds);
        dungeonIds = List.copyOf(dungeonIds);
        townPaths = List.copyOf(townPaths);
        npcs = List.copyOf(npcs);
        rivalAreas = List.copyOf(rivalAreas);
        hellPartyGroups = List.copyOf(hellPartyGroups);
        aiCharacters = List.copyOf(aiCharacters);
        eventMonsterPositions = List.copyOf(eventMonsterPositions);
        conditionMonsters = List.copyOf(conditionMonsters);
        passiveMonsters = List.copyOf(passiveMonsters);
    }

    public List<MonsterSpawn> conditionMonstersOf(int monsterId) {
        List<MonsterSpawn> out = new ArrayList<>(1);
        for (MonsterSpawn row : conditionMonsters) {
            if (row.monsterId() == monsterId) {
                out.add(row);
            }
        }
        return out;
    }

    public static MapScript load(PvfArchive archive, String path) {
        String normalized = com.dylan.pvf.PvfPath.normalize(path);
        return parse(normalized, archive.readText(normalized));
    }

    public static MapScript parse(String normalized, String text) {
        var tokens = ScriptReader.tokenize(text);

        List<MonsterSpawn> monsters = new ArrayList<>();
        List<MonsterSpawn> conditionMonsters = new ArrayList<>();
        Area startArea = Area.UNKNOWN;
        List<Greed> greeds = new ArrayList<>();
        String type = "";
        List<Integer> dungeonIds = new ArrayList<>();
        List<TownPath> townPaths = new ArrayList<>();
        List<NpcSpawn> npcs = new ArrayList<>();
        List<Area> rivalAreas = new ArrayList<>();
        List<HellPartyGroup> hellPartyGroups = new ArrayList<>();
        List<SpecialPassiveObject> specialPassiveObjects = new ArrayList<>();
        List<AiCharacterSpawn> aiCharacters = new ArrayList<>();
        List<Area> eventMonsterPositions = new ArrayList<>();
        int apcRandomPoint = 0;

        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            switch (token.text().toLowerCase()) {
                case "monster" -> readMonsters(tokens, i, monsters);
                case "monster condition" -> readMonsters(tokens, i, conditionMonsters);
                case "monster team" -> readMonsterTeams(tokens, i, monsters);
                case "dungeon start area" -> {
                    List<Integer> n = readNumbers(tokens, i, 4);
                    if (n.size() == 4) {
                        startArea = new Area(n.get(0), n.get(1), n.get(2), n.get(3));
                    }
                }
                case "pvp start area" -> readAreas(tokens, i, rivalAreas);
                case "event monster position" -> readTriples(tokens, i, eventMonsterPositions);
                case "ai character" -> readAiCharacters(tokens, i, aiCharacters);
                case "apc random point" -> {
                    List<Integer> n = readNumbers(tokens, i, 1);
                    if (!n.isEmpty()) {
                        apcRandomPoint = n.get(0);
                    }
                }
                case "special passive object" -> readSpecialPassiveObjects(
                        normalized, tokens, i, specialPassiveObjects, hellPartyGroups);
                case "dungeon" -> readNumbersUntilSection(tokens, i, dungeonIds);
                case "town movable area" -> readTownPaths(tokens, i, townPaths);
                case "npc" -> readNpcs(tokens, i, npcs);
                case "type" -> {
                    if (i + 1 < tokens.size()
                            && tokens.get(i + 1).kind() == ScriptReader.Kind.STRING) {
                        type = tokens.get(i + 1).text().trim();
                    }
                }
                case "greed" -> {
                    if (i + 1 < tokens.size()
                            && tokens.get(i + 1).kind() == ScriptReader.Kind.STRING) {
                        readGreeds(tokens.get(i + 1).text(), greeds);
                    }
                }
                default -> {
                }
            }
        }
        markDummyBosses(type, monsters);
        return new MapScript(normalized, monsters, startArea, greeds, type, dungeonIds,
                townPaths, npcs, rivalAreas, hellPartyGroups, aiCharacters, apcRandomPoint,
                eventMonsterPositions, mapItemsOf(specialPassiveObjects), conditionMonsters,
                passiveMonstersOf(specialPassiveObjects));
    }

    private static List<PassiveMonsterSpawn> passiveMonstersOf(
            List<SpecialPassiveObject> objects) {
        List<PassiveMonsterSpawn> out = new ArrayList<>();
        for (int index = 0; index < objects.size(); index++) {
            for (SpobjAction action : objects.get(index).actions()) {
                if (action.type() == SpobjAction.TYPE_MONSTER) {
                    out.add(new PassiveMonsterSpawn(index, action.p1(), action.p2(),
                            action.p3(), action.p4()));
                }
            }
        }
        return List.copyOf(out);
    }

    private static void markDummyBosses(String type, List<MonsterSpawn> monsters) {
        if (!TYPE_DUMMY.equalsIgnoreCase(type == null ? "" : type.trim())) {
            return;
        }
        for (int i = 0; i < monsters.size(); i++) {
            MonsterSpawn row = monsters.get(i);
            if (row.wireType() == TYPE_BOSS_MONSTER) {
                monsters.set(i, row.asFakeBoss());
            }
        }
    }

    private static List<MapItem> mapItemsOf(List<SpecialPassiveObject> objects) {
        List<MapItem> out = new ArrayList<>();
        for (SpecialPassiveObject obj : objects) {
            for (SpobjAction action : obj.actions()) {
                if (action.type() == SpobjAction.TYPE_ITEM) {
                    out.add(new MapItem(action.p1(), action.p2(), action.p3(),
                            obj.objectIndex()));
                }
            }
        }
        return List.copyOf(out);
    }

    private static void readAiCharacters(List<ScriptReader.Token> tokens, int at,
                                         List<AiCharacterSpawn> out) {
        int i = at + 1;
        int n = tokens.size();
        while (i < n) {
            var kind = tokens.get(i).kind();
            if (kind == ScriptReader.Kind.SECTION || kind == ScriptReader.Kind.SECTION_END) {
                return;
            }

            int[] head = new int[AiCharacterSpawn.NUMBERS_BEFORE_TAGS];
            int read = 0;
            while (read < head.length && i < n
                    && tokens.get(i).kind() == ScriptReader.Kind.WORD) {
                head[read++] = tokens.get(i++).asInt();
            }
            if (read < head.length || i + 1 >= n
                    || tokens.get(i).kind() != ScriptReader.Kind.STRING
                    || tokens.get(i + 1).kind() != ScriptReader.Kind.STRING) {
                return;
            }
            String camp = tokens.get(i++).text();
            String roleType = tokens.get(i++).text();

            int[] tail = new int[AiCharacterSpawn.NUMBERS_AFTER_TAGS];
            read = 0;
            while (read < tail.length && i < n
                    && tokens.get(i).kind() == ScriptReader.Kind.WORD) {
                tail[read++] = tokens.get(i++).asInt();
            }
            if (read < tail.length) {
                return;
            }
            out.add(new AiCharacterSpawn(head[0], head[1], head[2], head[3],
                    camp, roleType, tail[0], tail[1]));
        }
    }

    private static void readSpecialPassiveObjects(String path, List<ScriptReader.Token> tokens,
                                                  int at, List<SpecialPassiveObject> out,
                                                  List<HellPartyGroup> hellOut) {
        int n = tokens.size();
        int i = at + 1;
        int itemIndex = 0;
        while (i + 4 < n) {
            if (!isWord(tokens, i) || !isWord(tokens, i + 1) || !isWord(tokens, i + 2)
                    || !isWord(tokens, i + 3) || !isWord(tokens, i + 4)) {
                return;
            }
            int objectId = tokens.get(i).asInt();
            int x = tokens.get(i + 1).asInt();
            int y = tokens.get(i + 2).asInt();
            int z = tokens.get(i + 3).asInt();
            int actionCount = tokens.get(i + 4).asInt();
            i += 5;

            List<SpobjAction> actions = new ArrayList<>();
            boolean hasItem = false;
            for (int a = 0; a < actionCount; a++) {
                if (i >= n || tokens.get(i).kind() != ScriptReader.Kind.STRING) {
                    break;
                }
                String tag = tokens.get(i).text().trim();
                i++;
                if (HELL_PARTY_TAG.equalsIgnoreCase(tag)) {
                    i = readHellPartyGroups(path, tokens, i, hellOut);
                    continue;
                }
                int type = actionTypeOf(tag);
                if (type < 0) {
                    throw new com.dylan.pvf.PvfFormatException(path
                            + " 的 [special passive object] 里有认不出的动作 " + tag);
                }
                int[] p = new int[SPOBJ_ACTION_PARAMS];
                for (int k = 0; k < p.length; k++) {
                    if (!isWord(tokens, i)) {
                        throw new com.dylan.pvf.PvfFormatException(path
                                + " 的 [special passive object] 里动作 " + tag + " 只有 " + k
                                + " 个参数，官方读 " + SPOBJ_ACTION_PARAMS + " 个");
                    }
                    p[k] = tokens.get(i).asInt();
                    i++;
                }
                actions.add(new SpobjAction(type, p[0], p[1], p[2], p[3], p[4]));
                hasItem |= type == SpobjAction.TYPE_ITEM;
            }
            out.add(new SpecialPassiveObject(objectId, x, y, z, actions,
                    hasItem ? itemIndex++ : SpecialPassiveObject.NO_OBJECT_INDEX));
        }
    }

    private static int actionTypeOf(String tag) {
        return switch (tag.toLowerCase()) {
            case "[monster]" -> SpobjAction.TYPE_MONSTER;
            case "[trap]" -> SpobjAction.TYPE_TRAP;
            case "[item]" -> SpobjAction.TYPE_ITEM;
            case "[quest]" -> SpobjAction.TYPE_QUEST;
            default -> -1;
        };
    }

    private static int readHellPartyGroups(String path, List<ScriptReader.Token> tokens, int from,
                                           List<HellPartyGroup> out) {
        int i = from;
        while (isWord(tokens, i) && isWord(tokens, i + 1) && isWord(tokens, i + 2)) {
            HellPartyGroup group = new HellPartyGroup(tokens.get(i).asInt(),
                    tokens.get(i + 1).asInt(), tokens.get(i + 2).asInt());
            if (!group.orderInRange()) {
                throw new com.dylan.pvf.PvfFormatException(path
                        + " 的 [hellparty] 里组 " + group.groupIndex() + " 的波次是 "
                        + group.order() + "，官方只认 " + HellPartyGroup.MIN_ORDER
                        + ".." + HellPartyGroup.MAX_ORDER);
            }
            out.add(group);
            i += 3;
        }
        return i;
    }

    private static boolean isWord(List<ScriptReader.Token> tokens, int i) {
        return i < tokens.size() && tokens.get(i).kind() == ScriptReader.Kind.WORD;
    }

    private static final String HELL_PARTY_TAG = "[hellparty]";

    private static final int SPOBJ_ACTION_PARAMS = 5;

    private static void readTriples(List<ScriptReader.Token> tokens, int at, List<Area> out) {
        List<Integer> numbers = new ArrayList<>();
        for (int i = at + 1; i < tokens.size(); i++) {
            if (tokens.get(i).kind() != ScriptReader.Kind.WORD) {
                break;
            }
            numbers.add(tokens.get(i).asInt());
        }
        for (int i = 0; i + 2 < numbers.size(); i += 3) {
            out.add(new Area(numbers.get(i), numbers.get(i + 1), numbers.get(i + 2), 0));
        }
    }

    private static void readAreas(List<ScriptReader.Token> tokens, int at, List<Area> out) {
        List<Integer> numbers = new ArrayList<>();
        for (int i = at + 1; i < tokens.size(); i++) {
            if (tokens.get(i).kind() != ScriptReader.Kind.WORD) {
                break;
            }
            numbers.add(tokens.get(i).asInt());
        }
        for (int i = 0; i + 3 < numbers.size(); i += 4) {
            out.add(new Area(numbers.get(i), numbers.get(i + 1), numbers.get(i + 2),
                    numbers.get(i + 3)));
        }
    }

    private static void readGreeds(String body, List<Greed> out) {
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            int gridCode = GridCode.parse(parts[0]);
            if (gridCode < 0) {
                continue;
            }
            int entrance = parts.length > 1 ? GridCode.parse(parts[1]) : gridCode;
            out.add(new Greed(gridCode, entrance < 0 ? gridCode : entrance));
        }
    }

    private static void readNpcs(List<ScriptReader.Token> tokens, int at, List<NpcSpawn> out) {
        int i = at + 1;
        int n = tokens.size();
        while (i < n && tokens.get(i).kind() == ScriptReader.Kind.WORD) {
            int npcId = tokens.get(i++).asInt();
            if (i >= n || tokens.get(i).kind() != ScriptReader.Kind.STRING) {
                break;
            }
            String direction = tokens.get(i++).text().trim();
            int[] xyz = new int[NpcSpawn.TRAILING_NUMBERS];
            int read = 0;
            while (read < NpcSpawn.TRAILING_NUMBERS && i < n
                    && tokens.get(i).kind() == ScriptReader.Kind.WORD) {
                xyz[read++] = tokens.get(i++).asInt();
            }
            if (read < NpcSpawn.TRAILING_NUMBERS) {
                break;
            }
            out.add(new NpcSpawn(npcId, direction, xyz[0], xyz[1], xyz[2]));
        }
    }

    private static void readTownPaths(List<ScriptReader.Token> tokens, int at,
                                      List<TownPath> out) {
        int i = at + 1;
        int n = tokens.size();
        while (i < n && tokens.get(i).kind() == ScriptReader.Kind.WORD) {
            int[] f = new int[TownPath.FIELDS];
            int read = 0;
            while (read < TownPath.FIELDS && i < n
                    && tokens.get(i).kind() == ScriptReader.Kind.WORD) {
                f[read++] = tokens.get(i++).asInt();
            }
            if (read < TownPath.FIELDS) {
                break;
            }
            out.add(new TownPath(f[0], f[1], f[2], f[3], f[4], f[5]));
        }
    }

    private static void readMonsters(List<ScriptReader.Token> tokens, int at,
                                     List<MonsterSpawn> out) {
        int i = at + 1;
        int n = tokens.size();
        while (i < n) {
            var kind = tokens.get(i).kind();
            if (kind == ScriptReader.Kind.SECTION || kind == ScriptReader.Kind.SECTION_END) {
                break;
            }

            int[] numbers = new int[8];
            int read = 0;
            while (read < 8 && i < n && tokens.get(i).kind() == ScriptReader.Kind.WORD) {
                numbers[read++] = tokens.get(i++).asInt();
            }
            if (read < 8 || i + 1 >= n
                    || tokens.get(i).kind() != ScriptReader.Kind.STRING
                    || tokens.get(i + 1).kind() != ScriptReader.Kind.STRING) {
                break;
            }
            String copyType = tokens.get(i++).text();
            String type = tokens.get(i++).text();

            if (MonsterSpawn.CHAMPION_TAG.equalsIgnoreCase(type)
                    && i < n && tokens.get(i).kind() == ScriptReader.Kind.WORD) {
                int traits = tokens.get(i++).asInt();
                for (int t = 0; t < traits
                        && i < n && tokens.get(i).kind() == ScriptReader.Kind.STRING; t++) {
                    i++;
                }
            }

            out.add(new MonsterSpawn(numbers[0], numbers[1], numbers[2],
                    numbers[3], numbers[4], numbers[5], numbers[6], numbers[7],
                    copyType, type));
        }
    }

    private static void readMonsterTeams(List<ScriptReader.Token> tokens, int at,
                                         List<MonsterSpawn> monsters) {
        int i = at + 1;
        for (int row = 0; row < monsters.size(); row++) {
            if (i >= tokens.size() || tokens.get(i).kind() != ScriptReader.Kind.WORD) {
                break;
            }
            monsters.set(row, monsters.get(row).withCamp(tokens.get(i++).asInt()));
        }
    }

    private static void readNumbersUntilSection(List<ScriptReader.Token> tokens, int at,
                                                List<Integer> out) {
        for (int i = at + 1; i < tokens.size(); i++) {
            if (tokens.get(i).kind() != ScriptReader.Kind.WORD) {
                break;
            }
            out.add(tokens.get(i).asInt());
        }
    }

    private static List<Integer> readNumbers(List<ScriptReader.Token> tokens, int at, int count) {
        List<Integer> out = new ArrayList<>(count);
        for (int i = at + 1; i < tokens.size() && out.size() < count; i++) {
            if (tokens.get(i).kind() != ScriptReader.Kind.WORD) {
                break;
            }
            out.add(tokens.get(i).asInt());
        }
        return out;
    }
}
