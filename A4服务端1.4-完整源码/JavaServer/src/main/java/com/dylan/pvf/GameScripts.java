package com.dylan.pvf;

import com.dylan.pvf.script.CharacterScript;
import com.dylan.pvf.script.DungeonMaps;
import com.dylan.pvf.script.DungeonScript;
import com.dylan.pvf.script.ItemScript;
import com.dylan.pvf.script.MapScript;
import com.dylan.pvf.script.MonsterScript;
import com.dylan.pvf.script.QuestGrade;
import com.dylan.pvf.script.QuestScript;
import com.dylan.pvf.script.ScriptList;
import com.dylan.pvf.script.SkillTable;
import com.dylan.pvf.script.StringTable;
import com.dylan.pvf.script.TownTable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class GameScripts implements Closeable {
    public static final String DUNGEON_LIST = "dungeon/dungeon.lst";

    public static final String MAP_LIST = "map/map.lst";

    public static final String QUEST_LIST = "quest/quest.lst";

    public static final String QUEST_STRINGS = "quest/quest.chn.str";

    public static final String ETC_STRINGS = "etc/etc.chn.str";

    public static final String CHARACTER_LIST = "character/character.lst";

    public static final String EQUIPMENT_LIST = "equipment/equipment.lst";
    public static final String STACKABLE_LIST = "stackable/stackable.lst";

    private final PvfArchive archive;
    private final ScriptList dungeons;
    private final ScriptList maps;
    private final ScriptList jobs;
    private final ScriptList questList;
    private final ScriptList monsterList;
    private final SkillTable skills;
    private final TownTable towns;
    private final StringTable questStrings;

    private final Map<Integer, QuestScript> quests;

    private final List<Integer> dailyQuestIds;

    private final Map<Integer, CharacterScript> characters;

    private final Map<Integer, String> itemPaths;

    private final Map<Integer, List<Integer>> mapsByDungeon;

    private final Map<Integer, DungeonMaps> dungeonMaps;

    private final Map<Integer, DungeonScript> dungeonCache = new ConcurrentHashMap<>();
    private final Map<Integer, MapScript> mapCache = new ConcurrentHashMap<>();
    private final Map<Integer, ItemScript> itemCache = new ConcurrentHashMap<>();
    private final Map<Integer, MonsterScript> monsterCache = new ConcurrentHashMap<>();

    private ScriptList aiCharacterList;

    private final Map<Integer, com.dylan.pvf.script.AiCharacterScript> aiCharacterCache =
            new ConcurrentHashMap<>();

    private com.dylan.pvf.script.AiCharacterList aiCharacters;

    private com.dylan.game.drop.MonsterDropGenerator monsterDrop;
    private com.dylan.game.drop.WorldDropGenerator worldDrop;
    private com.dylan.game.drop.DungeonDropGenerator dungeonDrop;
    private com.dylan.game.drop.ObjectDropGenerator objectDrop;

    private com.dylan.pvf.script.ExpTable expTable;

    private com.dylan.pvf.script.ServerParameter serverParameter;

    private com.dylan.pvf.script.CreatureScriptTable creatureScripts;

    private com.dylan.pvf.script.QuestParameterScript questParameter;

    private com.dylan.pvf.script.QuestMappingTable questMapping;

    private com.dylan.pvf.script.ClearRewardTable clearReward;

    private com.dylan.game.drop.ItemGenRateTable clearRewardPool;

    private com.dylan.pvf.script.DeathTowerTable deathTower =
            com.dylan.pvf.script.DeathTowerTable.empty();

    private com.dylan.pvf.script.PvpMapTable pvpMaps =
            com.dylan.pvf.script.PvpMapTable.empty();

    private com.dylan.pvf.script.WorldMapTable worldMaps =
            com.dylan.pvf.script.WorldMapTable.empty();

    private com.dylan.pvf.script.HellPartyTable hellParty =
            com.dylan.pvf.script.HellPartyTable.empty();

    private com.dylan.pvf.script.CashShopCatalog cashShop;

    private com.dylan.pvf.script.PremiumCatalog premium;

    private com.dylan.pvf.script.UpgradeTable upgradeTable;

    private com.dylan.pvf.script.DisjointTable disjointTable;

    private com.dylan.pvf.script.ResealTable resealTable;

    private com.dylan.pvf.script.EmotionTable emotions;

    private com.dylan.pvf.script.InvalidNameTable invalidName;

    private com.dylan.pvf.script.SlangNameTable slangName;

    private java.util.Map<Integer, com.dylan.pvf.script.ExpertJobScript> expertJobs =
            java.util.Map.of();

    private com.dylan.pvf.script.MagicEnchantScript magicEnchant;

    private com.dylan.pvf.script.ExpertJobEtcScript expertJobEtc;

    private com.dylan.pvf.script.CompoundAvatarTable compoundAvatarTable;

    private com.dylan.pvf.script.PriceTable priceTable =
            com.dylan.pvf.script.PriceTable.DEFAULT;

    private com.dylan.pvf.script.ItemShopTable itemShops =
            com.dylan.pvf.script.ItemShopTable.empty();

    private StringTable etcStrings = StringTable.empty();

    private GameScripts(PvfArchive archive, ScriptList dungeons, ScriptList maps,
                        ScriptList jobs, ScriptList questList, ScriptList monsterList,
                        SkillTable skills, TownTable towns, StringTable questStrings,
                        Map<Integer, QuestScript> quests, Map<Integer, String> itemPaths,
                        Map<Integer, List<Integer>> mapsByDungeon,
                        Map<Integer, DungeonMaps> dungeonMaps,
                        Map<Integer, CharacterScript> characters) {
        this.characters = characters;
        this.archive = archive;
        this.dungeons = dungeons;
        this.maps = maps;
        this.jobs = jobs;
        this.questList = questList;
        this.monsterList = monsterList;
        this.skills = skills;
        this.towns = towns;
        this.questStrings = questStrings;
        this.quests = quests;
        this.dailyQuestIds = quests.values().stream()
                .filter(q -> q.grade() == QuestGrade.DAILY)
                .map(QuestScript::id)
                .sorted()
                .toList();
        this.itemPaths = itemPaths;
        this.mapsByDungeon = mapsByDungeon;
        this.dungeonMaps = dungeonMaps;
    }

    public static GameScripts load(Path pvf) throws IOException {
        PvfArchive archive = PvfArchive.open(pvf);
        try {
            ScriptList dungeons = list(archive, DUNGEON_LIST, "副本相关功能不可用");
            ScriptList maps = list(archive, MAP_LIST, "进本时选不出地图");
            ScriptList jobs = list(archive, CHARACTER_LIST, "职业编号无法校验");

            Map<Integer, CharacterScript> characters = new HashMap<>();
            for (var e : jobs.entries().entrySet()) {
                try {
                    characters.put(e.getKey(),
                            CharacterScript.load(archive, e.getKey(), e.getValue()));
                } catch (RuntimeException ex) {
                }
            }
            ScriptList questList = list(archive, QUEST_LIST, "任务不可用");
            ScriptList monsterList = list(archive, MonsterScript.LIST_PATH, "怪物不可用");

            Map<Integer, QuestScript> quests = new HashMap<>();
            int unreadableQuests = 0;
            for (var e : questList.entries().entrySet()) {
                try {
                    quests.put(e.getKey(), QuestScript.load(archive, e.getKey(), e.getValue()));
                } catch (RuntimeException ex) {
                    unreadableQuests++;
                }
            }

            Map<Integer, String> itemPaths = new HashMap<>();
            itemPaths.putAll(list(archive, EQUIPMENT_LIST, "装备查不到").entries());
            itemPaths.putAll(list(archive, STACKABLE_LIST, "消耗品查不到").entries());

            Map<Integer, List<Integer>> mapsByDungeon = new HashMap<>();
            Map<Integer, DungeonMaps> dungeonMaps = new HashMap<>();
            Map<Integer, MapScript> mapPrefill = new HashMap<>();
            int missingMaps = 0;
            for (var e : maps.entries().entrySet()) {
                if (!archive.contains(com.dylan.pvf.PvfPath.normalize(e.getValue()))) {
                    missingMaps++;
                    continue;
                }
                try {
                    MapScript script = MapScript.load(archive, e.getValue());
                    mapPrefill.put(e.getKey(), script);
                    for (int dungeonId : script.dungeonIds()) {
                        mapsByDungeon.computeIfAbsent(dungeonId, k -> new ArrayList<>())
                                .add(e.getKey());
                        dungeonMaps.computeIfAbsent(dungeonId, k -> DungeonMaps.empty())
                                .insert(e.getKey(), script);
                    }
                } catch (RuntimeException ex) {
                    throw new PvfFormatException("地图 " + e.getKey() + "（" + e.getValue()
                            + "）解析失败：" + ex.getMessage(), ex);
                }
            }
            mapsByDungeon.values().forEach(l -> l.sort(null));

            SkillTable skills;
            try {
                skills = SkillTable.load(archive);
            } catch (RuntimeException ex) {
                skills = null;
            }

            TownTable towns;
            try {
                towns = TownTable.load(archive);
            } catch (RuntimeException ex) {
                towns = TownTable.empty();
            }

            StringTable questStrings;
            try {
                questStrings = StringTable.load(archive, QUEST_STRINGS);
            } catch (RuntimeException ex) {
                questStrings = StringTable.empty();
            }

            var scripts = new GameScripts(archive, dungeons, maps, jobs, questList, monsterList,
                    skills, towns, questStrings, Map.copyOf(quests), Map.copyOf(itemPaths),
                    Map.copyOf(mapsByDungeon), Map.copyOf(dungeonMaps), Map.copyOf(characters));
            scripts.mapCache.putAll(mapPrefill);
            scripts.aiCharacterList = list(archive,
                    com.dylan.pvf.script.AiCharacterScript.LIST_PATH,
                    "深渊派对的 APC 不掉东西");
            scripts.monsterDrop = buildMonsterDrop(archive, scripts);
            scripts.monsterDrop.setHellDrop(buildHellDrop(archive, scripts));
            scripts.worldDrop = buildWorldDrop(archive, scripts);
            scripts.dungeonDrop = new com.dylan.game.drop.DungeonDropGenerator(scripts);
            scripts.objectDrop = buildObjectDrop(archive, scripts);
            scripts.expTable = loadExpTable(archive);
            scripts.serverParameter = loadServerParameter(archive);
            scripts.etcStrings = loadEtcStrings(archive);
            scripts.questParameter = loadQuestParameter(archive);
            scripts.questMapping = loadQuestMapping(archive);
            scripts.clearReward = loadClearReward(archive);
            scripts.clearRewardPool = buildClearRewardPool(scripts);
            scripts.deathTower = loadDeathTower(archive);
            scripts.pvpMaps = loadPvpMaps(archive);
            scripts.worldMaps = loadWorldMaps(archive, questList);
            scripts.hellParty = loadHellParty(archive);
            scripts.cashShop = loadCashShop(archive);
            scripts.premium = loadPremium(archive);
            scripts.upgradeTable = loadUpgradeTable(archive);
            scripts.disjointTable = loadDisjointTable(archive);
            scripts.resealTable = loadResealTable(archive);
            scripts.emotions = loadEmotions(archive);
            scripts.invalidName = loadInvalidName(archive);
            scripts.slangName = loadSlangName(archive);
            scripts.expertJobs = loadExpertJobs(archive);
            scripts.magicEnchant = loadMagicEnchant(archive);
            if (!scripts.expertJobs.containsKey(com.dylan.pvf.script.ExpertJobScript.TYPE_ENCHANTER)
                    && scripts.magicEnchant != null) {
                // A4：没有 expertjob.lst，附魔师只在 expertjob.etc [magic enchant] 里（见 fromMagicEnchant）
                var expert = new java.util.LinkedHashMap<>(scripts.expertJobs);
                expert.put(com.dylan.pvf.script.ExpertJobScript.TYPE_ENCHANTER,
                        com.dylan.pvf.script.ExpertJobScript.fromMagicEnchant(scripts.magicEnchant));
                scripts.expertJobs = java.util.Map.copyOf(expert);
            }
            scripts.expertJobEtc = loadExpertJobEtc(archive);
            scripts.compoundAvatarTable = loadCompoundAvatarTable(archive);
            scripts.priceTable = loadPriceTable(archive);
            scripts.itemShops = loadItemShops(archive);
            scripts.creatureScripts = loadCreatureScripts(archive, scripts);

            return scripts;
        } catch (RuntimeException e) {
            archive.close();
            throw e;
        }
    }

    private static com.dylan.game.drop.MonsterDropGenerator buildMonsterDrop(
            PvfArchive archive, GameScripts scripts) {
        var dropInfo = com.dylan.pvf.script.DropInfoTable.load(
                archive, com.dylan.pvf.script.DropInfoTable.PATH_MONSTER);
        var goldTable = com.dylan.pvf.script.GoldDropTable.load(archive);
        var pool = com.dylan.game.drop.ItemGenRateTable.generateTable(
                dropInfo::levelWindow, scripts.allItems(), "怪物");
        return new com.dylan.game.drop.MonsterDropGenerator(dropInfo, goldTable, pool, scripts);
    }

    private static com.dylan.game.drop.ObjectDropGenerator buildObjectDrop(
            PvfArchive archive, GameScripts scripts) {
        com.dylan.pvf.script.DropInfoTable dropInfo;
        try {
            dropInfo = com.dylan.pvf.script.DropInfoTable.load(
                    archive, com.dylan.pvf.script.DropInfoTable.PATH_OBJECT);
        } catch (RuntimeException ex) {
            return null;
        }
        var pool = com.dylan.game.drop.ItemGenRateTable.generateTable(
                dropInfo::levelWindow, scripts.allItems(), "建筑物");
        var generator = new com.dylan.game.drop.MonsterDropGenerator(
                dropInfo, scripts.monsterDrop.goldTable(), pool, scripts);
        return new com.dylan.game.drop.ObjectDropGenerator(generator, scripts);
    }

    private static com.dylan.game.drop.HellDropGenerator buildHellDrop(
            PvfArchive archive, GameScripts scripts) {
        com.dylan.pvf.script.HellDropTable table;
        try {
            table = com.dylan.pvf.script.HellDropTable.load(archive);
        } catch (RuntimeException ex) {
            return null;
        }
        var pool = com.dylan.game.drop.ItemGenRateTable.generateTable(
                table::levelWindow, scripts.allItems(), "深渊");
        return new com.dylan.game.drop.HellDropGenerator(table, pool, scripts);
    }

    private static com.dylan.game.drop.WorldDropGenerator buildWorldDrop(
            PvfArchive archive, GameScripts scripts) {
        com.dylan.pvf.script.WorldDropScript table;
        try {
            table = com.dylan.pvf.script.WorldDropScript.load(archive);
        } catch (RuntimeException ex) {
            table = com.dylan.pvf.script.WorldDropScript.parse("");
        }
        var generator = new com.dylan.game.drop.WorldDropGenerator(table, scripts);
        return generator;
    }

    private static com.dylan.pvf.script.ExpTable loadExpTable(PvfArchive archive) {
        try {
            var table = com.dylan.pvf.script.ExpTable.load(archive);
            return table;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static StringTable loadEtcStrings(PvfArchive archive) {
        try {
            StringTable table = StringTable.load(archive, ETC_STRINGS);
            return table;
        } catch (RuntimeException ex) {
            return StringTable.empty();
        }
    }

    private static com.dylan.pvf.script.ServerParameter loadServerParameter(
            PvfArchive archive) {
        try {
            var params = com.dylan.pvf.script.ServerParameter.load(archive);
            return params;
        } catch (RuntimeException ex) {
            return com.dylan.pvf.script.ServerParameter.parse("");
        }
    }

    private static com.dylan.pvf.script.QuestParameterScript loadQuestParameter(
            PvfArchive archive) {
        try {
            var params = com.dylan.pvf.script.QuestParameterScript.load(archive);
            return params;
        } catch (RuntimeException ex) {
            return com.dylan.pvf.script.QuestParameterScript.empty();
        }
    }

    private static com.dylan.pvf.script.QuestMappingTable loadQuestMapping(
            PvfArchive archive) {
        try {
            var table = com.dylan.pvf.script.QuestMappingTable.load(archive);
            return table;
        } catch (RuntimeException ex) {
            return com.dylan.pvf.script.QuestMappingTable.empty();
        }
    }

    private static com.dylan.pvf.script.CashShopCatalog loadCashShop(PvfArchive archive) {
        try {
            var catalog = com.dylan.pvf.script.CashShopCatalog.load(archive);
            return catalog;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static com.dylan.pvf.script.PremiumCatalog loadPremium(PvfArchive archive) {
        try {
            var catalog = com.dylan.pvf.script.PremiumCatalog.load(archive);
            return catalog;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static com.dylan.pvf.script.UpgradeTable loadUpgradeTable(PvfArchive archive) {
        try {
            var table = com.dylan.pvf.script.UpgradeTable.load(archive);
            return table;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static com.dylan.pvf.script.DisjointTable loadDisjointTable(PvfArchive archive) {
        try {
            var table = com.dylan.pvf.script.DisjointTable.load(archive);
            return table;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static com.dylan.pvf.script.ResealTable loadResealTable(PvfArchive archive) {
        try {
            var table = com.dylan.pvf.script.ResealTable.load(archive);
            return table;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static com.dylan.pvf.script.EmotionTable loadEmotions(PvfArchive archive) {
        try {
            var table = com.dylan.pvf.script.EmotionTable.load(archive);
            return table;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static com.dylan.pvf.script.InvalidNameTable loadInvalidName(
            PvfArchive archive) {
        try {
            var table = com.dylan.pvf.script.InvalidNameTable.load(archive);
            return table;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static com.dylan.pvf.script.SlangNameTable loadSlangName(PvfArchive archive) {
        try {
            var table = com.dylan.pvf.script.SlangNameTable.load(archive);
            return table;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static java.util.Map<Integer, com.dylan.pvf.script.ExpertJobScript>
            loadExpertJobs(PvfArchive archive) {
        try {
            var list = ScriptList.load(archive,
                    com.dylan.pvf.script.ExpertJobScript.LIST_PATH);
            var out = new java.util.LinkedHashMap<Integer,
                    com.dylan.pvf.script.ExpertJobScript>();
            for (var e : list.entries().entrySet()) {
                try {
                    out.put(e.getKey(), com.dylan.pvf.script.ExpertJobScript.load(
                            archive, e.getKey(), e.getValue()));
                } catch (RuntimeException ex) {
                }
            }
            out.get(com.dylan.pvf.script.ExpertJobScript.TYPE_ENCHANTER);
            return java.util.Map.copyOf(out);
        } catch (RuntimeException ex) {
            return java.util.Map.of();
        }
    }

    private static com.dylan.pvf.script.MagicEnchantScript loadMagicEnchant(
            PvfArchive archive) {
        try {
            var script = com.dylan.pvf.script.MagicEnchantScript.load(archive);
            return script;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static com.dylan.pvf.script.ExpertJobEtcScript loadExpertJobEtc(
            PvfArchive archive) {
        try {
            var script = com.dylan.pvf.script.ExpertJobEtcScript.load(archive);
            return script;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static com.dylan.pvf.script.CompoundAvatarTable loadCompoundAvatarTable(
            PvfArchive archive) {
        try {
            var table = com.dylan.pvf.script.CompoundAvatarTable.load(archive);
            return table;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static com.dylan.pvf.script.PriceTable loadPriceTable(PvfArchive archive) {
        try {
            var table = com.dylan.pvf.script.PriceTable.load(archive);
            return table;
        } catch (RuntimeException ex) {
            return com.dylan.pvf.script.PriceTable.DEFAULT;
        }
    }

    private static com.dylan.pvf.script.ItemShopTable loadItemShops(PvfArchive archive) {
        try {
            var table = com.dylan.pvf.script.ItemShopTable.load(archive);
            return table;
        } catch (RuntimeException ex) {
            return com.dylan.pvf.script.ItemShopTable.empty();
        }
    }

    private static com.dylan.pvf.script.ClearRewardTable loadClearReward(
            PvfArchive archive) {
        try {
            var table = com.dylan.pvf.script.ClearRewardTable.load(archive);
            return table;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static com.dylan.pvf.script.DeathTowerTable loadDeathTower(PvfArchive archive) {
        try {
            var table = com.dylan.pvf.script.DeathTowerTable.load(archive);
            return table;
        } catch (RuntimeException ex) {
            return com.dylan.pvf.script.DeathTowerTable.empty();
        }
    }

    private static com.dylan.pvf.script.PvpMapTable loadPvpMaps(PvfArchive archive) {
        try {
            return com.dylan.pvf.script.PvpMapTable.load(archive);
        } catch (RuntimeException ex) {
            return com.dylan.pvf.script.PvpMapTable.empty();
        }
    }

    private static com.dylan.pvf.script.WorldMapTable loadWorldMaps(
            PvfArchive archive, ScriptList questList) {
        try {
            return com.dylan.pvf.script.WorldMapTable.load(archive);
        } catch (RuntimeException ex) {
            return com.dylan.pvf.script.WorldMapTable.empty();
        }
    }

    private static com.dylan.pvf.script.CreatureScriptTable loadCreatureScripts(
            PvfArchive archive, GameScripts scripts) {
        try {
            java.util.Map<Integer, Integer> itemIdByCreatureId = new java.util.LinkedHashMap<>();
            for (ItemScript item : scripts.allItems()) {
                if (item.equipmentSlot() != com.dylan.protocol.body.EquipmentSlot.CREATURE
                        || item.creatureType() != ItemScript.CREATURE_TYPE_BODY
                        || item.creature().species() == 0) {
                    continue;
                }
                itemIdByCreatureId.put(item.creature().species(), item.id());
            }
            var table = com.dylan.pvf.script.CreatureScriptTable.load(
                    archive, itemIdByCreatureId);
            return table;
        } catch (RuntimeException ex) {
            return com.dylan.pvf.script.CreatureScriptTable.empty();
        }
    }

    private static com.dylan.pvf.script.HellPartyTable loadHellParty(PvfArchive archive) {
        try {
            var table = com.dylan.pvf.script.HellPartyTable.load(archive);
            return table;
        } catch (RuntimeException ex) {
            return com.dylan.pvf.script.HellPartyTable.empty();
        }
    }

    private static com.dylan.game.drop.ItemGenRateTable buildClearRewardPool(
            GameScripts scripts) {
        if (scripts.clearReward == null) {
            return null;
        }
        var pool = com.dylan.game.drop.ItemGenRateTable.generateTable(
                scripts.clearReward::levelWindow, scripts.allItems(), "通关翻牌");
        return pool;
    }

    private static ScriptList list(PvfArchive archive, String path, String consequence) {
        try {
            return ScriptList.load(archive, path);
        } catch (RuntimeException ex) {
            return ScriptList.parse(path, "");
        }
    }

    public static Optional<GameScripts> tryLoad(Path pvf) {
        if (!Files.isRegularFile(pvf)) {
            return Optional.empty();
        }
        try {
            return Optional.of(load(pvf));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public ScriptList jobs() {
        return jobs;
    }

    public Optional<CharacterScript> characterScript(int job) {
        return Optional.ofNullable(characters.get(job));
    }

    public List<CharacterScript.InitialSkill> initialSkills(int job) {
        return characterScript(job).map(CharacterScript::initialSkills).orElse(List.of());
    }

    public SkillTable skills() {
        return skills;
    }

    public TownTable towns() {
        return towns;
    }

    public java.util.Optional<String> etcString(String key) {
        return etcStrings.get(key);
    }

    public Map<Integer, QuestScript> quests() {
        return quests;
    }

    public Optional<QuestScript> quest(int questId) {
        return Optional.ofNullable(quests.get(questId));
    }

    public List<Integer> dailyQuestIds() {
        return dailyQuestIds;
    }

    public com.dylan.pvf.script.QuestMappingTable questMapping() {
        return questMapping;
    }

    public Optional<DungeonScript> dungeon(int dungeonId) {
        DungeonScript cached = dungeonCache.get(dungeonId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<String> path = dungeons.path(dungeonId);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        DungeonScript parsed = DungeonScript.load(archive, dungeonId, path.get());
        dungeonCache.put(dungeonId, parsed);
        return Optional.of(parsed);
    }

    public Optional<MapScript> map(int mapId) {
        MapScript cached = mapCache.get(mapId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<String> path = maps.path(mapId);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        MapScript parsed = MapScript.load(archive, path.get());
        mapCache.put(mapId, parsed);
        return Optional.of(parsed);
    }

    public DungeonMaps mapsFor(int dungeonId) {
        return dungeonMaps.getOrDefault(dungeonId, DungeonMaps.empty());
    }

    public Optional<ItemScript> item(int itemId) {
        ItemScript cached = itemCache.get(itemId);
        if (cached != null) {
            return Optional.of(cached);
        }
        String path = itemPaths.get(itemId);
        if (path == null) {
            return Optional.empty();
        }
        ItemScript parsed = ItemScript.load(archive, itemId, path);
        itemCache.put(itemId, parsed);
        return Optional.of(parsed);
    }

    public int itemCount() {
        return itemPaths.size();
    }

    public com.dylan.game.drop.MonsterDropGenerator monsterDrop() {
        return monsterDrop;
    }

    public com.dylan.game.drop.WorldDropGenerator worldDrop() {
        return worldDrop;
    }

    public com.dylan.game.drop.DungeonDropGenerator dungeonDrop() {
        return dungeonDrop;
    }

    public com.dylan.game.drop.ObjectDropGenerator objectDrop() {
        return objectDrop;
    }

    public com.dylan.pvf.script.ExpTable expTable() {
        return expTable;
    }

    public com.dylan.pvf.script.ServerParameter serverParameter() {
        return serverParameter;
    }

    public com.dylan.pvf.script.CreatureScriptTable creatureScripts() {
        return creatureScripts == null
                ? com.dylan.pvf.script.CreatureScriptTable.empty() : creatureScripts;
    }

    public com.dylan.pvf.script.QuestParameterScript questParameter() {
        return questParameter;
    }

    public com.dylan.pvf.script.ClearRewardTable clearReward() {
        return clearReward;
    }

    public com.dylan.game.drop.ItemGenRateTable clearRewardPool() {
        return clearRewardPool;
    }

    public com.dylan.pvf.script.DeathTowerTable deathTower() {
        return deathTower;
    }

    public com.dylan.pvf.script.PvpMapTable pvpMaps() {
        return pvpMaps;
    }

    public com.dylan.pvf.script.WorldMapTable worldMaps() {
        return worldMaps;
    }

    public com.dylan.pvf.script.HellPartyTable hellParty() {
        return hellParty;
    }

    public com.dylan.pvf.script.UpgradeTable upgradeTable() {
        return upgradeTable;
    }

    public com.dylan.pvf.script.DisjointTable disjointTable() {
        return disjointTable;
    }

    public com.dylan.pvf.script.ResealTable resealTable() {
        return resealTable;
    }

    public com.dylan.pvf.script.EmotionTable emotions() {
        return emotions;
    }

    public com.dylan.pvf.script.InvalidNameTable invalidName() {
        return invalidName;
    }

    public com.dylan.pvf.script.SlangNameTable slangName() {
        return slangName;
    }

    public com.dylan.pvf.script.ExpertJobScript expertJob(int type) {
        return expertJobs.get(type);
    }

    public com.dylan.pvf.script.ExpertJobEtcScript expertJobEtc() {
        return expertJobEtc;
    }

    public com.dylan.pvf.script.CompoundAvatarTable compoundAvatarTable() {
        return compoundAvatarTable;
    }

    public com.dylan.pvf.script.PriceTable priceTable() {
        return priceTable;
    }

    public com.dylan.pvf.script.ItemShopTable itemShops() {
        return itemShops;
    }

    public Optional<com.dylan.pvf.script.CashShopCatalog> cashShop() {
        return Optional.ofNullable(cashShop);
    }

    public Optional<com.dylan.pvf.script.PremiumCatalog> premium() {
        return Optional.ofNullable(premium);
    }

    public com.dylan.pvf.script.GoldDropTable goldDropTable() {
        return monsterDrop == null ? null : monsterDrop.goldTable();
    }

    public Iterable<ItemScript> allItems() {
        List<ItemScript> out = new ArrayList<>(itemPaths.size());
        for (Integer id : itemPaths.keySet()) {
            item(id).ifPresent(out::add);
        }
        return out;
    }

    public Optional<MonsterScript> monster(int monsterId) {
        MonsterScript cached = monsterCache.get(monsterId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<String> path = monsterList.path(monsterId);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        MonsterScript parsed = MonsterScript.load(archive, monsterId, path.get());
        monsterCache.put(monsterId, parsed);
        return Optional.of(parsed);
    }

    public Optional<com.dylan.pvf.script.AiCharacterScript> aiCharacter(int index) {
        var cached = aiCharacterCache.get(index);
        if (cached != null) {
            return Optional.of(cached);
        }
        if (aiCharacterList == null) {
            return Optional.empty();
        }
        Optional<String> path = aiCharacterList.path(index);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        var parsed = com.dylan.pvf.script.AiCharacterScript.load(archive, index, path.get());
        aiCharacterCache.put(index, parsed);
        return Optional.of(parsed);
    }

    public synchronized com.dylan.pvf.script.AiCharacterList aiCharacters() {
        if (aiCharacters != null) {
            return aiCharacters;
        }
        if (aiCharacterList == null) {
            aiCharacters = com.dylan.pvf.script.AiCharacterList.empty();
            return aiCharacters;
        }
        List<com.dylan.pvf.script.AiCharacterScript> all =
                new ArrayList<>(aiCharacterList.size());
        for (Integer id : aiCharacterList.entries().keySet()) {
            aiCharacter(id).ifPresent(all::add);
        }
        aiCharacters = com.dylan.pvf.script.AiCharacterList.of(all);
        return aiCharacters;
    }

    @Override
    public void close() throws IOException {
        archive.close();
    }
}
