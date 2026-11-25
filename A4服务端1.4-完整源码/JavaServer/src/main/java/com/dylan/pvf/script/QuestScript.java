package com.dylan.pvf.script;

import com.dylan.protocol.body.QuestTrigger;
import com.dylan.pvf.PvfArchive;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import java.util.random.RandomGenerator;

public record QuestScript(
        int id,
        String path,
        QuestGrade grade,
        int npcIndex,
        int completeNpcIndex,
        List<TargetCharacter> targetCharacters,
        int jobChangeQuest,
        int minLevel,
        int maxLevel,
        int pvpRank,
        QuestType type,
        int subType,
        List<Integer> intData,
        boolean event,
        boolean exposedByNpc,
        boolean ignoreQuestLevel4Exp,
        char difficulty,
        int goldMultiple,
        int cantGiveup,
        int growNumber,
        String nameKey,
        String conditionKey,
        List<List<Integer>> preRequired,
        List<Integer> collision,
        QuestRewardType rewardType,
        int rewardParam,
        RewardTable rewardTable,
        List<Integer> collisionRewardQuests,
        boolean collisionRewardAny,
        RewardTable collisionRewardTable,
        List<Reward> selectableRewards,
        List<Reward> dependGiveItems,
        List<QuestDropEntry> clearRewardItems,
        List<QuestDropEntry> monsterRewardItems,
        AppearMap appearMap) {
    public static final String[] JOB_TAGS = {
            "[swordman]", "[fighter]", "[gunner]", "[mage]", "[priest]", "[at gunner]",
            "[thief]", "[at fighter]", "[at mage]", "[demonic swordman]", "[creator mage]",
    };

    public static final int JOB_ALL = 11;

    public static final int GROW_TYPE_ANY = -1;

    public static final int DEFAULT_GOLD_MULTIPLE = 100;

    public static final boolean DEFAULT_EXPOSED_BY_NPC = true;

    private static final int NO_COMPLETE_NPC = -1;

    public static final int GOLD_ITEM_ID = 0;

    public static final int HUNT_MONSTER_STRIDE = 4;

    public static final int HUNT_ENEMY_STRIDE = 5;

    public static final int SEEKING_STRIDE = 2;

    public static final int CLEAR_REWARD_STRIDE = 6;

    public static final int MONSTER_REWARD_STRIDE = 7;

    public static final int ENEMY_REWARD_STRIDE = 8;

    public static final int ANY_DUNGEON = -1;

    public static final int ANY_DIFFICULTY = -1;

    public static final int COND_UNDER_CLEAR_SUBTYPE_COUNTED = 6;

    public static final int ALWAYS_ACCEPTABLE_QUEST_ID = 1016;

    public static final int MAX_QUEST_ID = 29999;

    public record TargetCharacter(int jobId, int growType, int awakeningType) {
        public boolean jobMatches(int job) {
            return jobId == JOB_ALL || jobId == job;
        }

        public boolean matches(int job, int grow, int awakening) {
            return jobMatches(job)
                    && (growType == GROW_TYPE_ANY || growType == grow)
                    && (awakeningType == GROW_TYPE_ANY || awakeningType == awakening);
        }
    }

    public record Reward(int itemId, int count, int jobId, int growType) {
        public Reward(int itemId, int count) {
            this(itemId, count, JOB_ALL, GROW_TYPE_ANY);
        }

        public boolean appliesTo(int job, int grow) {
            boolean jobOk = jobId == JOB_ALL || job < 0 || jobId == job;
            boolean growOk = growType == GROW_TYPE_ANY || grow < 0 || growType == (grow & 0xF);
            return jobOk && growOk;
        }
    }

    public record RewardTable(List<Reward> items, boolean grantsGold,
                              int goldReward, int goldRewardIndex) {
        public static final RewardTable EMPTY = new RewardTable(List.of(), false, 0, -1);

        public RewardTable {
            items = List.copyOf(items);
        }

        public boolean isEmpty() {
            return items.isEmpty() && !grantsGold;
        }
    }

    public record QuestDropEntry(int monsterId, int enemyType, int dungeonId, int difficulty,
                                 int itemId, int count, int dropRate, int maxStack) {
        public static final int NO_MONSTER = -1;

        public static final int DEFAULT_ENEMY_TYPE = 1;

        public static final int NO_STACK_LIMIT = -1;

        static QuestDropEntry onClear(int dungeonId, int difficulty, int itemId,
                                      int count, int dropRate, int maxStack) {
            return new QuestDropEntry(NO_MONSTER, DEFAULT_ENEMY_TYPE, dungeonId, difficulty, itemId,
                    count, dropRate, maxStack);
        }

        public boolean isMonsterDrop() {
            return monsterId != NO_MONSTER;
        }

        public boolean matches(int inDungeon, int atDifficulty) {
            if (dungeonId != ANY_DUNGEON && dungeonId != inDungeon) {
                return false;
            }
            return difficulty == ANY_DIFFICULTY || atDifficulty < 0
                    || difficulty == atDifficulty;
        }

        public boolean matchesKill(int killedMonsterId, int killedEnemyType,
                                   int inDungeon, int atDifficulty) {
            return isMonsterDrop()
                    && monsterId == killedMonsterId
                    && enemyType == killedEnemyType
                    && matches(inDungeon, atDifficulty);
        }
    }

    public record Objective(int itemId, int count) {}

    public record HuntObjective(int dungeonId, int minDifficulty, int monsterId,
                                int count, int channel) {
        public boolean matches(int inDungeon, int atDifficulty, int killedMonsterId) {
            if (monsterId != killedMonsterId) {
                return false;
            }
            if (dungeonId != ANY_DUNGEON && dungeonId != inDungeon) {
                return false;
            }
            return minDifficulty == ANY_DIFFICULTY || atDifficulty < 0
                    || atDifficulty >= minDifficulty;
        }
    }

    public record AppearMap(int dungeonIndex, int difficulty, int mapIndex, int rate) {
        public static final AppearMap NONE = new AppearMap(-1, -1, -1, -1);

        public static final int ANY_DIFFICULTY = -1;

        public boolean isNone() {
            return mapIndex <= 0;
        }

        public int resolve(int dungeonIndex, int difficulty, RandomGenerator random) {
            if (isNone()) {
                return 0;
            }
            if (this.difficulty == ANY_DIFFICULTY) {
                if (this.dungeonIndex == dungeonIndex && rate >= random.nextInt(100)) {
                    return mapIndex;
                }
                return 0;
            }
            if (this.difficulty == dungeonIndex && this.difficulty == difficulty
                    && rate >= random.nextInt(100)) {
                return mapIndex;
            }
            return 0;
        }
    }

    public QuestScript {
        targetCharacters = List.copyOf(targetCharacters);
        intData = List.copyOf(intData);
        preRequired = preRequired.stream().map(List::copyOf).toList();
        collision = List.copyOf(collision);
        collisionRewardQuests = List.copyOf(collisionRewardQuests);
        selectableRewards = List.copyOf(selectableRewards);
        dependGiveItems = List.copyOf(dependGiveItems);
        clearRewardItems = List.copyOf(clearRewardItems);
        monsterRewardItems = List.copyOf(monsterRewardItems);
    }

    public boolean isAvailableTo(int level, int job, int grow, int awakening, int pvpGrade,
                                 Set<Integer> finished, Set<Integer> inProgress) {
        if (id == ALWAYS_ACCEPTABLE_QUEST_ID) {
            return !inProgress.contains(id) && !finished.contains(id);
        }
        if (inProgress.contains(id)) {
            return false;
        }
        if (!exposedByNpc) {
            return false;
        }
        if (!checkPossible(level, job, grow, awakening, pvpGrade)) {
            return false;
        }
        if (!isRepeatable() && finished.contains(id)) {
            return false;
        }
        if (!meetsPreRequired(finished)) {
            return false;
        }
        return meetsCollision(finished);
    }

    public boolean checkPossible(int level, int job, int grow, int awakening, int pvpGrade) {
        if (!checkPossibleJob(job)) {
            return false;
        }
        if (isNeededCheckGrowType(job)
                && jobChangeQuest != JOB_CHANGE_SKIP_GROW_A
                && jobChangeQuest != JOB_CHANGE_SKIP_GROW_B
                && !checkMatchedGrowType(job, grow)) {
            return false;
        }
        if (level < minLevel || level > maxLevel) {
            return false;
        }
        return pvpRank == 0 || pvpRank <= pvpGrade;
    }

    public static final int JOB_CHANGE_SKIP_GROW_A = 10;

    public static final int JOB_CHANGE_SKIP_GROW_B = 20;

    public boolean checkPossibleJob(int job) {
        for (TargetCharacter target : targetCharacters) {
            if (target.jobMatches(job)) {
                return true;
            }
        }
        return false;
    }

    public boolean isNeededCheckGrowType(int job) {
        for (TargetCharacter target : targetCharacters) {
            if (target.jobMatches(job) && target.growType() == GROW_TYPE_ANY) {
                return false;
            }
        }
        return true;
    }

    public boolean checkMatchedGrowType(int job, int grow) {
        for (TargetCharacter target : targetCharacters) {
            if (target.matches(job, grow, GROW_TYPE_ANY)) {
                return true;
            }
        }
        return false;
    }

    public boolean meetsPreRequired(Set<Integer> finished) {
        if (preRequired.isEmpty()) {
            return true;
        }
        for (List<Integer> group : preRequired) {
            boolean all = true;
            for (int required : group) {
                if (!finished.contains(required)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    public boolean meetsCollision(Set<Integer> finished) {
        for (int blocked : collision) {
            if (finished.contains(blocked)) {
                return false;
            }
        }
        return true;
    }

    public boolean isRepeatable() {
        return grade.repeatable();
    }

    public boolean cantGiveUp() {
        return cantGiveup == 1 || cantGiveup == 2;
    }

    public List<Objective> seekingObjectives() {
        if (type == null || !type.consumesItems()) {
            return List.of();
        }
        List<Objective> out = new ArrayList<>();
        for (int i = 0; i + SEEKING_STRIDE <= intData.size(); i += SEEKING_STRIDE) {
            int itemId = intData.get(i);
            int count = intData.get(i + 1);
            if (itemId >= GOLD_ITEM_ID && count > 0) {
                out.add(new Objective(itemId, count));
            }
        }
        return List.copyOf(out);
    }

    public List<Objective> consumedItems() {
        return seekingObjectives();
    }

    public List<HuntObjective> huntObjectives() {
        int stride = huntStride();
        if (stride == 0) {
            return List.of();
        }
        List<HuntObjective> out = new ArrayList<>();
        int channel = 0;
        for (int i = 0; i + stride <= intData.size(); i += stride) {
            int dungeonId = intData.get(i);
            int minDifficulty = intData.get(i + 1);
            int monsterId = intData.get(i + 2);
            int count = intData.get(i + stride - 1);
            if (count <= 0) {
                continue;
            }
            if (monsterId <= 0) {
                continue;
            }
            out.add(new HuntObjective(dungeonId, minDifficulty, monsterId, count, channel));
            channel++;
        }
        return List.copyOf(out.size() > QuestTrigger.CHANNELS
                ? out.subList(0, QuestTrigger.CHANNELS)
                : out);
    }

    private int huntStride() {
        if (type == QuestType.HUNT_MONSTER) {
            return HUNT_MONSTER_STRIDE;
        }
        return type == QuestType.HUNT_ENEMY ? HUNT_ENEMY_STRIDE : 0;
    }

    public OptionalInt clearMapTarget() {
        if (type != QuestType.CLEAR_MAP || intData.isEmpty()) {
            return OptionalInt.empty();
        }
        int target = intData.get(0);
        return target > 0 ? OptionalInt.of(target) : OptionalInt.empty();
    }

    public QuestTrigger initialTrigger() {
        if (type == QuestType.HUNT_MONSTER || type == QuestType.HUNT_ENEMY) {
            var objectives = huntObjectives();
            int[] remaining = new int[QuestTrigger.CHANNELS];
            for (var objective : objectives) {
                remaining[objective.channel()] =
                        clampToChannel(objective.count(), "要杀 " + objective.count() + " 只");
            }
            return objectives.isEmpty() ? QuestTrigger.SINGLE : QuestTrigger.of(remaining);
        }
        if (type == QuestType.SEEK_N_MEET_NPC) {
            return QuestTrigger.of(1, 1, 0);
        }
        if (type == QuestType.CONDITION_UNDER_CLEAR
                && subType == COND_UNDER_CLEAR_SUBTYPE_COUNTED
                && intData.size() == 3 && intData.get(2) > 0) {
            return new QuestTrigger(intData.get(2));
        }
        return QuestTrigger.SINGLE;
    }

    public boolean hasRewardSelection() {
        return !selectableRewards.isEmpty();
    }

    public boolean hasDependGiveItems() {
        return !dependGiveItems.isEmpty();
    }

    public boolean rewardsAreItems() {
        return rewardType.isItem();
    }

    public RewardTable userRewardItems(java.util.function.IntPredicate cleared) {
        if (collisionRewardQuests.isEmpty()) {
            return rewardTable;
        }
        if (collisionRewardAny) {
            for (int id : collisionRewardQuests) {
                if (cleared.test(id)) {
                    return collisionRewardTable;
                }
            }
            return rewardTable;
        }
        for (int id : collisionRewardQuests) {
            if (!cleared.test(id)) {
                return rewardTable;
            }
        }
        return collisionRewardTable;
    }

    public boolean grantsGold() {
        return rewardTable.grantsGold();
    }

    public int goldReward() {
        return rewardTable.goldReward();
    }

    public int goldRewardIndex() {
        return rewardTable.goldRewardIndex();
    }

    public int rewardParam() {
        return rewardParam;
    }

    public static QuestScript load(PvfArchive archive, int id, String path) {
        String normalized = com.dylan.pvf.PvfPath.normalize(path);
        return parse(id, normalized, archive.readText(normalized));
    }

    public static QuestScript parse(int id, String normalized, String text) {
        var tokens = ScriptReader.tokenize(text);

        QuestGrade grade = QuestGrade.NORMALY_REPEAT;
        int jobId = JOB_ALL;
        int growType = GROW_TYPE_ANY;
        int awakeningType = GROW_TYPE_ANY;
        int jobChangeQuest = 0;
        List<TargetCharacter> targets = new ArrayList<>();
        QuestType type = QuestType.SEEKING;
        int subType = 0;
        boolean event = false;
        boolean exposedByNpc = DEFAULT_EXPOSED_BY_NPC;
        boolean ignoreQuestLevel4Exp = false;
        char difficulty = 0;
        int goldMultiple = DEFAULT_GOLD_MULTIPLE;
        int cantGiveup = 0;
        int growNumber = 0;
        int pvpRank = 0;
        String nameKey = null;
        String conditionKey = null;
        List<Integer> intData = new ArrayList<>();
        int npcIndex = 0;
        int completeNpcIndex = NO_COMPLETE_NPC;
        int minLevel = 0;
        int maxLevel = 0;
        List<List<Integer>> preRequired = new ArrayList<>();
        List<Integer> collision = new ArrayList<>();
        String rewardTypeTag = "";
        int rewardIntDataAt = -1;
        int collisionRewardAt = -1;
        int rewardParam = 0;
        List<Reward> selectableRewards = new ArrayList<>();
        List<Reward> dependGiveItems = new ArrayList<>();
        List<QuestDropEntry> clearRewardItems = new ArrayList<>();
        List<QuestDropEntry> monsterRewardItems = new ArrayList<>();
        AppearMap appearMap = AppearMap.NONE;

        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            switch (token.text().toLowerCase(Locale.ROOT)) {
                case "grade" -> grade = QuestGrade.byTag(nextString(tokens, i, null));
                case "job" -> jobId = jobIdOf(nextString(tokens, i, null));
                case "grow type" -> growType = nextInt(tokens, i, growType);
                case "awakening type" -> awakeningType = nextInt(tokens, i, awakeningType);
                case "job change quest" -> jobChangeQuest = nextInt(tokens, i, jobChangeQuest);
                case "target character" -> readTargetCharacters(tokens, i, targets);
                case "type" -> {
                    QuestType parsed = QuestType.byTag(nextString(tokens, i, null));
                    if (parsed != null) {
                        type = parsed;
                    }
                }
                case "sub type" -> subType = nextInt(tokens, i, subType);
                case "event" -> event = nextInt(tokens, i, 0) == 1;
                case "exposed by npc" -> exposedByNpc = nextInt(tokens, i, 1) == 1;
                case "ignore quest level 4 exp" -> ignoreQuestLevel4Exp =
                        nextInt(tokens, i, 0) != 0;
                case "difficulty" -> {
                    String raw = nextString(tokens, i, "");
                    difficulty = raw.isEmpty() ? 0
                            : Character.toUpperCase(raw.charAt(0));
                }
                case "gold multiple" -> goldMultiple = nextInt(tokens, i, goldMultiple);
                case "cant giveup" -> cantGiveup = nextInt(tokens, i, cantGiveup);
                case "grow number" -> growNumber = nextInt(tokens, i, growNumber);
                case "pvp rank" -> pvpRank = nextInt(tokens, i, pvpRank);
                case "int data" -> readIds(tokens, i, intData);
                case "appear map" -> appearMap = readAppearMap(tokens, i);
                case "name" -> nameKey = nextReferenceKey(tokens, i, nameKey);
                case "condition message" ->
                        conditionKey = nextReferenceKey(tokens, i, conditionKey);
                case "npc index" -> npcIndex = nextInt(tokens, i, npcIndex);
                case "complete npc index" -> completeNpcIndex =
                        nextInt(tokens, i, completeNpcIndex);
                case "level" -> {
                    minLevel = nextInt(tokens, i, minLevel);
                    maxLevel = nextInt(tokens, i + 1, maxLevel);
                }
                case "pre required quest" -> {
                    List<Integer> group = new ArrayList<>();
                    readIds(tokens, i, group);
                    if (!group.isEmpty()) {
                        preRequired.add(group);
                    }
                }
                case "collision quest" -> readIds(tokens, i, collision);
                case "reward type" -> rewardTypeTag = nextString(tokens, i, rewardTypeTag);
                case "reward int data" -> rewardIntDataAt = i;
                case "collision reward int data" -> collisionRewardAt = i;
                case "reward selection int data" -> readItemPairs(tokens, i, normalized,
                        "[reward selection int data]", selectableRewards);
                case "depend give item" -> readItemPairs(tokens, i, normalized,
                        "[depend give item]", dependGiveItems);
                case "clear reward item" -> readDropEntries(tokens, i, normalized,
                        "[clear reward item]", CLEAR_REWARD_STRIDE, clearRewardItems);
                case "monster reward item" -> readDropEntries(tokens, i, normalized,
                        "[monster reward item]", MONSTER_REWARD_STRIDE, monsterRewardItems);
                case "enemy reward item" -> readDropEntries(tokens, i, normalized,
                        "[enemy reward item]", ENEMY_REWARD_STRIDE, monsterRewardItems);
                default -> {
                }
            }
        }

        QuestRewardType rewardType = QuestRewardType.byTag(rewardTypeTag);

        RewardTable rewardTable = RewardTable.EMPTY;
        RewardTable collisionRewardTable = RewardTable.EMPTY;
        List<Integer> collisionRewardQuests = new ArrayList<>();
        boolean collisionRewardAny = false;
        if (rewardIntDataAt >= 0) {
            if (rewardType.isItem()) {
                rewardTable = readRewards(tokens, rewardIntDataAt);
            } else {
                rewardParam = nextInt(tokens, rewardIntDataAt, 0);
            }
        }
        if (collisionRewardAt >= 0 && rewardType.isItem()) {
            var collision2 = readCollisionRewards(tokens, collisionRewardAt, normalized);
            collisionRewardQuests = collision2.quests();
            collisionRewardAny = collision2.any();
            collisionRewardTable = collision2.table();
        }

        if (completeNpcIndex == NO_COMPLETE_NPC) {
            completeNpcIndex = npcIndex;
        }
        addSyntheticTarget(targets, jobId, growType, awakeningType);

        return new QuestScript(id, normalized, grade, npcIndex, completeNpcIndex,
                targets, jobChangeQuest, minLevel, maxLevel, pvpRank,
                type, subType, intData, event, exposedByNpc, ignoreQuestLevel4Exp,
                difficulty, goldMultiple, cantGiveup, growNumber,
                nameKey, conditionKey, preRequired, collision,
                rewardType, rewardParam, rewardTable,
                collisionRewardQuests, collisionRewardAny, collisionRewardTable,
                selectableRewards, dependGiveItems, clearRewardItems, monsterRewardItems,
                appearMap);
    }

    private static AppearMap readAppearMap(List<ScriptReader.Token> tokens, int at) {
        int[] values = new int[4];
        for (int n = 0; n < values.length; n++) {
            int index = at + 1 + n;
            if (index >= tokens.size() || tokens.get(index).kind() != ScriptReader.Kind.WORD) {
                return AppearMap.NONE;
            }
            try {
                values[n] = tokens.get(index).asInt();
            } catch (RuntimeException e) {
                return AppearMap.NONE;
            }
        }
        return new AppearMap(values[0], values[1], values[2], values[3]);
    }

    private static void addSyntheticTarget(List<TargetCharacter> targets, int jobId,
                                           int growType, int awakeningType) {
        if (!targets.isEmpty() && jobId == JOB_ALL && growType == GROW_TYPE_ANY) {
            return;
        }
        TargetCharacter synthetic = new TargetCharacter(jobId, growType, awakeningType);
        if (!targets.contains(synthetic)) {
            targets.add(synthetic);
        }
    }

    public static int jobIdOf(String raw) {
        if (raw == null) {
            return JOB_ALL;
        }
        String normalized = raw.trim().replace("`", "").toLowerCase(Locale.ROOT);
        for (int i = 0; i < JOB_TAGS.length; i++) {
            if (JOB_TAGS[i].equals(normalized)) {
                return i;
            }
        }
        return JOB_ALL;
    }

    private static void readTargetCharacters(List<ScriptReader.Token> tokens, int at,
                                             List<TargetCharacter> out) {
        int i = at + 1;
        while (i + 2 < tokens.size()
                && tokens.get(i).kind() == ScriptReader.Kind.STRING
                && tokens.get(i + 1).kind() == ScriptReader.Kind.WORD
                && tokens.get(i + 2).kind() == ScriptReader.Kind.WORD) {
            int job = jobIdOf(tokens.get(i).text());
            int grow = tokens.get(i + 1).asInt();
            int awakening = tokens.get(i + 2).asInt();
            out.add(new TargetCharacter(job, grow, awakening));
            i += 3;
        }
    }

    private static String nextString(List<ScriptReader.Token> tokens, int at, String fallback) {
        if (at + 1 < tokens.size()
                && tokens.get(at + 1).kind() == ScriptReader.Kind.STRING) {
            return tokens.get(at + 1).text().trim();
        }
        return fallback;
    }

    private static String nextReferenceKey(List<ScriptReader.Token> tokens, int at,
                                           String fallback) {
        if (at + 1 >= tokens.size()
                || tokens.get(at + 1).kind() != ScriptReader.Kind.WORD) {
            return fallback;
        }
        return StringTable.keyOf(tokens.get(at + 1).text()).orElse(fallback);
    }

    private static int nextInt(List<ScriptReader.Token> tokens, int at, int fallback) {
        if (at + 1 < tokens.size()
                && tokens.get(at + 1).kind() == ScriptReader.Kind.WORD) {
            try {
                return tokens.get(at + 1).asInt();
            } catch (RuntimeException e) {
                return fallback;
            }
        }
        return fallback;
    }

    private static RewardTable readRewards(List<ScriptReader.Token> tokens, int at) {
        List<Reward> out = new ArrayList<>();
        boolean hasGold = false;
        int goldReward = 0;
        int goldIndex = -1;
        int i = at + 1;
        while (i < tokens.size()) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.WORD) {
                break;
            }
            int itemId = token.asInt();
            i++;
            int jobId = JOB_ALL;
            int growType = GROW_TYPE_ANY;
            if (i < tokens.size() && isJobMarker(tokens.get(i))) {
                i++;
                if (i + 2 >= tokens.size()
                        || tokens.get(i).kind() != ScriptReader.Kind.WORD
                        || tokens.get(i + 1).kind() != ScriptReader.Kind.WORD) {
                    break;
                }
                jobId = tokens.get(i++).asInt();
                growType = tokens.get(i++).asInt();
            }
            if (i >= tokens.size() || tokens.get(i).kind() != ScriptReader.Kind.WORD) {
                break;
            }
            int count = tokens.get(i++).asInt();

            if (itemId == GOLD_ITEM_ID) {
                if (!hasGold) {
                    goldIndex = out.size();
                }
                hasGold = true;
                goldReward += count;
                continue;
            }
            out.add(new Reward(itemId, count, jobId, growType));
        }
        return new RewardTable(out, hasGold, goldReward, goldIndex);
    }

    private record CollisionRewards(List<Integer> quests, boolean any, RewardTable table) {
    }

    private static CollisionRewards readCollisionRewards(List<ScriptReader.Token> tokens,
                                                         int at, String path) {
        List<Integer> quests = new ArrayList<>();
        int i = at + 1;
        boolean terminated = false;
        while (i < tokens.size() && tokens.get(i).kind() == ScriptReader.Kind.WORD) {
            int id = tokens.get(i++).asInt();
            if (id == -1) {
                terminated = true;
                break;
            }
            quests.add(id);
        }
        if (!terminated) {
            return new CollisionRewards(List.of(), false, RewardTable.EMPTY);
        }
        if (i >= tokens.size() || tokens.get(i).kind() != ScriptReader.Kind.STRING) {
            return new CollisionRewards(List.of(), false, RewardTable.EMPTY);
        }
        String mode = tokens.get(i++).text().trim().toLowerCase(Locale.ROOT);
        boolean any;
        if ("[and]".equals(mode)) {
            any = false;
        } else if ("[or]".equals(mode)) {
            any = true;
        } else {
            return new CollisionRewards(List.of(), false, RewardTable.EMPTY);
        }

        List<Integer> ints = new ArrayList<>();
        while (i < tokens.size() && tokens.get(i).kind() == ScriptReader.Kind.WORD) {
            ints.add(tokens.get(i++).asInt());
        }
        List<Reward> items = new ArrayList<>();
        boolean hasGold = false;
        int goldReward = 0;
        int goldIndex = -1;
        for (int k = 0; k + 2 <= ints.size(); k += 2) {
            int itemId = ints.get(k);
            int count = ints.get(k + 1);
            if (itemId == GOLD_ITEM_ID) {
                if (!hasGold) {
                    goldIndex = items.size();
                }
                hasGold = true;
                goldReward += count;
                continue;
            }
            items.add(new Reward(itemId, count));
        }
        return new CollisionRewards(quests, any,
                new RewardTable(items, hasGold, goldReward, goldIndex));
    }

    private static void readItemPairs(List<ScriptReader.Token> tokens, int at,
                                      String path, String section, List<Reward> out) {
        List<Integer> ints = new ArrayList<>();
        readIds(tokens, at, ints);
        for (int i = 0; i + 2 <= ints.size(); i += 2) {
            out.add(new Reward(ints.get(i), ints.get(i + 1)));
        }
    }

    private static boolean isJobMarker(ScriptReader.Token token) {
        return token.kind() == ScriptReader.Kind.STRING
                && "[job]".equalsIgnoreCase(token.text().trim());
    }

    private static void readDropEntries(List<ScriptReader.Token> tokens, int at, String path,
                                        String section, int stride, List<QuestDropEntry> out) {
        List<Integer> n = new ArrayList<>();
        readIds(tokens, at, n);
        for (int i = 0; i + stride <= n.size(); i += stride) {
            QuestDropEntry entry = switch (stride) {
                case ENEMY_REWARD_STRIDE -> new QuestDropEntry(n.get(i), n.get(i + 1),
                        n.get(i + 2), n.get(i + 3), n.get(i + 4), n.get(i + 5),
                        n.get(i + 6), n.get(i + 7));
                case MONSTER_REWARD_STRIDE -> new QuestDropEntry(n.get(i),
                        QuestDropEntry.DEFAULT_ENEMY_TYPE, n.get(i + 1), n.get(i + 2), n.get(i + 3),
                        n.get(i + 4), n.get(i + 5), n.get(i + 6));
                default -> QuestDropEntry.onClear(n.get(i), n.get(i + 1), n.get(i + 2),
                        n.get(i + 3), n.get(i + 4), n.get(i + 5));
            };
            if (entry.itemId() <= 0 || entry.count() <= 0 || entry.dropRate() <= 0) {
                continue;
            }
            out.add(entry);
        }
    }

    private static void readIds(List<ScriptReader.Token> tokens, int at, List<Integer> out) {
        for (int i = at + 1; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.WORD) {
                break;
            }
            out.add(token.asInt());
        }
    }

    private int clampToChannel(int value, String what) {
        if (value > QuestTrigger.CHANNEL_MAX) {
            return QuestTrigger.CHANNEL_MAX;
        }
        return value;
    }

}
