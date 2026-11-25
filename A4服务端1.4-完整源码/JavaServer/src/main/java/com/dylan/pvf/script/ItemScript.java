package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;

public record ItemScript(int id, String path, String name, int durability, int stackLimit,
                         int cash, int minimumLevel, int rarity, int grade, int creationRate,
                         int attachType, boolean recipe,
                         int stackableType, int equipmentSlot, int price, int value,
                         int coolTimeMs, int finishPointPrice,
                         java.util.List<AvatarOption> avatarOptions,
                         java.util.List<AvatarType> avatarTypes,
                         int usableJobs, int requiredSkill, int subType, int hatchItemId,
                         int usablePeriod, LotteryInfo lottery, MultiBoxInfo multiBox,
                         CreatureInfo creature, RecipeInfo recipeInfo, EnchantEffect enchant,
                         NeedMaterial needMaterial, int guildExp, ContinuousOption continuous,
                         java.util.List<PackageItem> packageData,
                         java.util.List<java.util.List<PackageItem>> packageSelections,
                         boolean epicRouting,
                         BoosterInfo booster) {
    public record NeedMaterial(int itemId, int count) {
        public static final NeedMaterial NONE = new NeedMaterial(NO_MATERIAL, 0);

        public boolean required() {
            return itemId != NO_MATERIAL && count > 0;
        }
    }

    public static final int NO_MATERIAL = -1;

    public ItemScript {
        avatarOptions = java.util.List.copyOf(avatarOptions);
        avatarTypes = java.util.List.copyOf(avatarTypes);
        lottery = lottery == null ? LotteryInfo.EMPTY : lottery;
        multiBox = multiBox == null ? MultiBoxInfo.EMPTY : multiBox;
        creature = creature == null ? CreatureInfo.NONE : creature;
        recipeInfo = recipeInfo == null ? RecipeInfo.EMPTY : recipeInfo;
        enchant = enchant == null ? EnchantEffect.NONE : enchant;
        continuous = continuous == null ? ContinuousOption.NONE : continuous;
        packageData = packageData == null
                ? java.util.List.of() : java.util.List.copyOf(packageData);
        packageSelections = packageSelections == null
                ? java.util.List.of()
                : packageSelections.stream().map(java.util.List::copyOf).toList();
        booster = booster == null ? BoosterInfo.EMPTY : booster;
    }

    public ItemScript(int id, String path, String name, int durability, int stackLimit,
                      int cash, int minimumLevel, int rarity, int grade, int creationRate,
                      int attachType, boolean recipe,
                      int stackableType, int equipmentSlot, int price, int value,
                      int coolTimeMs, int finishPointPrice,
                      java.util.List<AvatarOption> avatarOptions,
                      java.util.List<AvatarType> avatarTypes,
                      int usableJobs, int requiredSkill, int subType, int hatchItemId,
                      int usablePeriod, LotteryInfo lottery, MultiBoxInfo multiBox,
                      CreatureInfo creature, RecipeInfo recipeInfo, EnchantEffect enchant,
                      NeedMaterial needMaterial, int guildExp, ContinuousOption continuous,
                      java.util.List<PackageItem> packageData, boolean epicRouting) {
        this(id, path, name, durability, stackLimit, cash, minimumLevel, rarity, grade,
                creationRate, attachType, recipe, stackableType, equipmentSlot, price, value,
                coolTimeMs, finishPointPrice, avatarOptions, avatarTypes, usableJobs,
                requiredSkill, subType, hatchItemId, usablePeriod, lottery, multiBox,
                creature, recipeInfo, enchant, needMaterial, guildExp, continuous,
                packageData, java.util.List.of(), epicRouting, BoosterInfo.EMPTY);
    }

    public ItemScript(int id, String path, String name, int durability, int stackLimit,
                      int cash, int minimumLevel, int rarity, int grade, int creationRate,
                      int attachType, boolean recipe,
                      int stackableType, int equipmentSlot, int price, int value,
                      int coolTimeMs, int finishPointPrice,
                      java.util.List<AvatarOption> avatarOptions,
                      java.util.List<AvatarType> avatarTypes,
                      int usableJobs, int requiredSkill, int subType, int hatchItemId,
                      int usablePeriod, LotteryInfo lottery, MultiBoxInfo multiBox,
                      CreatureInfo creature, RecipeInfo recipeInfo, EnchantEffect enchant,
                      NeedMaterial needMaterial, int guildExp, ContinuousOption continuous) {
        this(id, path, name, durability, stackLimit, cash, minimumLevel, rarity, grade,
                creationRate, attachType, recipe, stackableType, equipmentSlot, price, value,
                coolTimeMs, finishPointPrice, avatarOptions, avatarTypes, usableJobs,
                requiredSkill, subType, hatchItemId, usablePeriod, lottery, multiBox,
                creature, recipeInfo, enchant, needMaterial, guildExp, continuous,
                java.util.List.of(), false);
    }

    public ItemScript(int id, String path, String name, int durability, int stackLimit,
                      int cash, int minimumLevel, int rarity, int grade, int creationRate,
                      int attachType, boolean recipe,
                      int stackableType, int equipmentSlot, int price, int value,
                      int coolTimeMs, int finishPointPrice,
                      java.util.List<AvatarOption> avatarOptions,
                      java.util.List<AvatarType> avatarTypes,
                      int usableJobs, int requiredSkill, int subType, int hatchItemId,
                      int usablePeriod, LotteryInfo lottery, MultiBoxInfo multiBox,
                      CreatureInfo creature, RecipeInfo recipeInfo, EnchantEffect enchant,
                      NeedMaterial needMaterial, int guildExp, ContinuousOption continuous,
                      java.util.List<PackageItem> packageData) {
        this(id, path, name, durability, stackLimit, cash, minimumLevel, rarity, grade,
                creationRate, attachType, recipe, stackableType, equipmentSlot, price, value,
                coolTimeMs, finishPointPrice, avatarOptions, avatarTypes, usableJobs,
                requiredSkill, subType, hatchItemId, usablePeriod, lottery, multiBox,
                creature, recipeInfo, enchant, needMaterial, guildExp, continuous,
                packageData, false);
    }

    /** days = 礼包里这一件时装的期限天数，{@link #ITEM_DEFAULT_DAYS} 表示用物品自身的 usable period。 */
    public record PackageItem(int itemId, int count, int days) {
        public static final int MAX_COUNT = 0xFFFF;
        public static final int ITEM_DEFAULT_DAYS = -1;

        public PackageItem(int itemId, int count) {
            this(itemId, count, ITEM_DEFAULT_DAYS);
        }
    }

    /**
     * A4 礼包标签 [package data include usable period]：每行 物品、数量、期限码
     * （0 永久 / 1 7 天 / 2 30 天；官方买 7 天礼包后时装剩余 604800 秒）。ACT5 只有两列的 [package data]。
     */
    private static java.util.List<PackageItem> parsePackageDataWithPeriod(
            java.util.List<ScriptReader.Token> tokens, int at) {
        var flat = parseIntData(tokens, at);
        if (flat.isEmpty() || flat.size() % 3 != 0) {
            return java.util.List.of();
        }
        var out = new java.util.ArrayList<PackageItem>(flat.size() / 3);
        for (int i = 0; i + 2 < flat.size(); i += 3) {
            int itemId = flat.get(i);
            int count = flat.get(i + 1);
            int days = switch (flat.get(i + 2)) {
                case 0 -> 0;
                case 1 -> 7;
                case 2 -> 30;
                default -> -2;
            };
            if (itemId <= 0 || count <= 0 || count > PackageItem.MAX_COUNT || days < -1) {
                return java.util.List.of();
            }
            out.add(new PackageItem(itemId, count, days));
        }
        return java.util.List.copyOf(out);
    }

    public boolean isRecipeItem() {
        return isStackableItem() && stackableType == STACKABLE_RECIPE;
    }

    public boolean isExpertJobRecipe() {
        return isRecipeItem() && recipeInfo.expertJobRecipe();
    }

    public boolean isMonsterCard() {
        return isStackableItem() && stackableType == STACKABLE_MATERIAL_EXPERT_JOB
                && subType == RecipeInfo.MONSTER_CARD_SUB_TYPE;
    }

    public record CreatureInfo(int species, int minimumLevel, double expAmountRate) {
        public static final CreatureInfo NONE = new CreatureInfo(0, 0, 0.0);

        static CreatureInfo of(int species, int minimumLevel, double expAmountRate) {
            return species == 0 && minimumLevel == 0 && expAmountRate == 0.0
                    ? NONE : new CreatureInfo(species, minimumLevel, expAmountRate);
        }
    }

    public record ContinuousOption(boolean coolTime1, boolean coolTime2, boolean coolTimeProcess,
                                   boolean effect1, boolean effect2, boolean effectProcess,
                                   boolean effectTimer,
                                   int statChangeDurationMs, boolean statChangeDuration,
                                   boolean expAffect, double expAffectRatio) {
        public static final ContinuousOption NONE = new ContinuousOption(
                false, false, false, false, false, false, false, 0, false, false, 0.0);

        static ContinuousOption of(boolean coolTime1, boolean coolTime2, boolean coolTimeProcess,
                                   boolean effect1, boolean effect2, boolean effectProcess,
                                   boolean effectTimer,
                                   int statChangeDurationMs, boolean statChangeDuration,
                                   boolean expAffect, double expAffectRatio) {
            var made = new ContinuousOption(coolTime1, coolTime2, coolTimeProcess,
                    effect1, effect2, effectProcess, effectTimer,
                    statChangeDurationMs, statChangeDuration, expAffect, expAffectRatio);
            return NONE.equals(made) ? NONE : made;
        }
    }

    public static final int STACKABLE_TYPE_NONE = -1;

    public static final int JOB_ALL = 0x3F;

    public static final int NO_REQUIRED_SKILL = -1;

    static final java.util.Map<String, Integer> JOB_NAMES = java.util.Map.of(
            "[swordman]", 0,
            "[fighter]", 1,
            "[gunner]", 2,
            "[mage]", 3,
            "[priest]", 4,
            "[at gunner]", 5);

    public boolean usableByJob(int job) {
        return job < 0 || job > 31 || (usableJobs & (1 << job)) != 0;
    }

    public boolean needsSkill() {
        return requiredSkill != NO_REQUIRED_SKILL;
    }

    public static final int MAX_AVATAR_SLOT =
            com.dylan.protocol.body.EquipmentSlot.SKIN_AVATAR;

    public static final int MIN_CREATURE_SLOT = 19;

    public static final int STACKABLE_WASTE = 0;
    public static final int STACKABLE_MATERIAL = 1;
    public static final int STACKABLE_MATERIAL_EXPERT_JOB = 2;
    public static final int STACKABLE_RECIPE = 3;
    public static final int STACKABLE_THROW = 4;
    public static final int STACKABLE_SET = 5;
    public static final int STACKABLE_LEGACY = 6;
    public static final int STACKABLE_STACKABLE_LEGACY = 7;
    public static final int STACKABLE_QUEST = 8;
    public static final int STACKABLE_CREATURE = 9;
    public static final int STACKABLE_FEED = 10;
    public static final int STACKABLE_ETC = 11;
    public static final int STACKABLE_UPGRADABLE_LEGACY = 12;
    public static final int STACKABLE_CERA_PACKAGE = 13;
    public static final int STACKABLE_CERA_BOOSTER = 14;
    public static final int STACKABLE_BOOSTER = 15;
    public static final int STACKABLE_BOOSTER_RANDOM = 16;
    public static final int STACKABLE_UNLIMITED_WASTE = 17;
    public static final int STACKABLE_RANDOM_UPGRADABLE_LEGACY = 18;
    public static final int STACKABLE_RANDOM_SS_UPGRADABLE_LEGACY = 19;
    public static final int STACKABLE_RANDOM_GOBLIN_UPGRADABLE_LEGACY = 20;
    public static final int STACKABLE_QUEST_RECEIVE = 21;
    public static final int STACKABLE_AVATAR_JEWEL = 22;
    public static final int STACKABLE_UNLIMITED_ETC = 23;
    public static final int STACKABLE_DISGUISE = 24;
    public static final int STACKABLE_GLOBAL_EFFECT = 25;
    public static final int STACKABLE_EXPERT_TOWN_POTION = 26;
    public static final int STACKABLE_CONTRACT = 27;

    private static final java.util.Map<String, Integer> STACKABLE_TYPES = java.util.Map.ofEntries(
            java.util.Map.entry("[waste]", STACKABLE_WASTE),
            java.util.Map.entry("[material]", STACKABLE_MATERIAL),
            java.util.Map.entry("[material expert job]", STACKABLE_MATERIAL_EXPERT_JOB),
            java.util.Map.entry("[recipe]", STACKABLE_RECIPE),
            java.util.Map.entry("[throw]", STACKABLE_THROW),
            java.util.Map.entry("[set]", STACKABLE_SET),
            java.util.Map.entry("[legacy]", STACKABLE_LEGACY),
            java.util.Map.entry("[stackable legacy]", STACKABLE_STACKABLE_LEGACY),
            java.util.Map.entry("[quest]", STACKABLE_QUEST),
            java.util.Map.entry("[creature]", STACKABLE_CREATURE),
            java.util.Map.entry("[feed]", STACKABLE_FEED),
            java.util.Map.entry("[etc]", STACKABLE_ETC),
            java.util.Map.entry("[upgradable legacy]", STACKABLE_UPGRADABLE_LEGACY),
            java.util.Map.entry("[cera package]", STACKABLE_CERA_PACKAGE),
            java.util.Map.entry("[cera booster]", STACKABLE_CERA_BOOSTER),
            java.util.Map.entry("[booster]", STACKABLE_BOOSTER),
            java.util.Map.entry("[booster random]", STACKABLE_BOOSTER_RANDOM),
            java.util.Map.entry("[unlimited waste]", STACKABLE_UNLIMITED_WASTE),
            java.util.Map.entry("[random upgradable legacy]",
                    STACKABLE_RANDOM_UPGRADABLE_LEGACY),
            java.util.Map.entry("[random ss upgradable legacy]",
                    STACKABLE_RANDOM_SS_UPGRADABLE_LEGACY),
            java.util.Map.entry("[random goblin upgradable legacy]",
                    STACKABLE_RANDOM_GOBLIN_UPGRADABLE_LEGACY),
            java.util.Map.entry("[quest receive]", STACKABLE_QUEST_RECEIVE),
            java.util.Map.entry("[avatar jewel]", STACKABLE_AVATAR_JEWEL),
            java.util.Map.entry("[unlimited etc]", STACKABLE_UNLIMITED_ETC),
            java.util.Map.entry("[disguise]", STACKABLE_DISGUISE),
            java.util.Map.entry("[global effect]", STACKABLE_GLOBAL_EFFECT),
            java.util.Map.entry("[expert town potion]", STACKABLE_EXPERT_TOWN_POTION),
            java.util.Map.entry("[contract]", STACKABLE_CONTRACT));

    public record AvatarOption(String stat, String sign, int value, int level) {
        public static final String SKILL_LEVEL = "[SKILL_LEVEL]";
    }

    public static final java.util.List<String> AVATAR_STAT_KEYWORDS = java.util.List.of(
            "[none]", "[HP MAX]", "[MP MAX]", "[PHYSICAL_ATTACK]", "[PHYSICAL_DEFENSE]",
            "[MAGICAL_ATTACK]", "[MAGICAL_DEFENSE]", "[EQUIPMENT_PHYSICAL_ATTACK]",
            "[EQUIPMENT_PHYSICAL_DEFENSE]", "[EQUIPMENT_MAGICAL_ATTACK]",
            "[EQUIPMENT_MAGICAL_DEFENSE]", "[ELEMENT_TOLERANCE_FIRE]",
            "[ELEMENT_TOLERANCE_WATER]", "[ELEMENT_TOLERANCE_DARK]",
            "[ELEMENT_TOLERANCE_LIGHT]", "[ELEMENT_TOLERANCE_ALL]",
            "[ACTIVESTATUS_TOLERANCE_SLOW]", "[ACTIVESTATUS_TOLERANCE_FREEZE]",
            "[ACTIVESTATUS_TOLERANCE_POISON]", "[ACTIVESTATUS_TOLERANCE_STUN]",
            "[ACTIVESTATUS_TOLERANCE_CURSE]", "[ACTIVESTATUS_TOLERANCE_BLIND]",
            "[ACTIVESTATUS_TOLERANCE_LIGHTNING]", "[ACTIVESTATUS_TOLERANCE_STONE]",
            "[ACTIVESTATUS_TOLERANCE_SLEEP]", "[ACTIVESTATUS_TOLERANCE_BURN]",
            "[ACTIVESTATUS_TOLERANCE_WEAPON_BREAK]", "[ACTIVESTATUS_TOLERANCE_BLEEDING]",
            "[ACTIVESTATUS_TOLERANCE_PIERCE]", "[ACTIVESTATUS_TOLERANCE_STUCK]",
            "[ACTIVESTATUS_TOLERANCE_CONFUSE]", "[ACTIVESTATUS_TOLERANCE_HOLD]",
            "[ACTIVESTATUS_TOLERANCE_ALL]", "[INVENTORY_MAX_WEIGHT]", "[HP_REGENRATE]",
            "[MP_REGENRATE]", "[MP_REGENRATE_PERCENT_UP]", "[MOVE_SPEED]", "[ATTACK_SPEED]",
            "[CAST_SPEED]", "[HIT_RECOVERY]", "[JUMP_POWER]", "[CRITICAL]", "[SKILL_LEVEL]",
            "[PHYSICAL ABSOLUTE DEFENSE]", "[MAGICAL ABSOLUTE DEFENSE]");

    public record AvatarType(int days, int gold, int winPoint, int cera, int sockets) {
        public static final int PERMANENT = 0;

        public boolean permanent() {
            return days == PERMANENT;
        }
    }

    public int avatarOptionCount() {
        return avatarOptions.size();
    }

    public java.util.Optional<AvatarType> avatarTypeByPeriodCode(int periodCode) {
        int index = periodCode - 1;
        if (index < 0 || index >= avatarTypes.size()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(avatarTypes.get(index));
    }

    public int avatarTypeCount() {
        return avatarTypes.size();
    }

    public boolean hasAvatarOptions() {
        return !avatarOptions.isEmpty();
    }

    public boolean isAvatar() {
        return path.contains("/avatar/");
    }

    public boolean isCreature() {
        return path.startsWith("equipment/creature/");
    }

    public int creatureType() {
        return subType == 0 ? CREATURE_TYPE_BODY : CREATURE_TYPE_OTHER;
    }

    public static final int CREATURE_TYPE_BODY = 1;

    public static final int CREATURE_TYPE_OTHER = 0;

    public int hatchItemId() {
        return hatchItemId;
    }

    public static final int RARITY_COMMON = 0;
    public static final int RARITY_UNIQUE = 3;

    public static final int ATTACH_FREE = 0;

    public static final int ATTACH_TRADE = 1;

    public static final int ATTACH_TRADE_DELETE = 2;

    public static final int ATTACH_SEALING = 3;

    public static final int ATTACH_SEALING_TRADE = 4;

    private static final java.util.Map<String, Integer> ATTACH_TYPES = java.util.Map.of(
            "[free]", ATTACH_FREE,
            "[trade]", ATTACH_TRADE,
            "[trade delete]", ATTACH_TRADE_DELETE,
            "[sealing]", ATTACH_SEALING,
            "[sealing trade]", ATTACH_SEALING_TRADE);

    public String attachTypeLabel() {
        return switch (attachType) {
            case ATTACH_FREE -> "自由";
            case ATTACH_TRADE -> "交易后绑定";
            case ATTACH_TRADE_DELETE -> "交易即消失";
            case ATTACH_SEALING -> "封装";
            case ATTACH_SEALING_TRADE -> "封装可交易";
            default -> "未知(" + attachType + ")";
        };
    }

    public boolean isArtifact() {
        return equipmentSlot >= com.dylan.protocol.body.EquipmentSlot.FIRST_ARTIFACT
                && equipmentSlot < com.dylan.protocol.body.EquipmentSlot.COUNT;
    }

    public boolean isStackable() {
        return stackLimit > 0;
    }

    public boolean isStackableItem() {
        return stackableType != STACKABLE_TYPE_NONE;
    }

    public boolean isUnlimitedWaste() {
        return stackableType == STACKABLE_UNLIMITED_WASTE;
    }

    public static final int PERIOD_PERMANENT = 0;

    public boolean hasUsablePeriod() {
        return usablePeriod > PERIOD_PERMANENT;
    }

    public boolean isLotteryItem() {
        return stackableType == STACKABLE_LEGACY
                || stackableType == STACKABLE_STACKABLE_LEGACY
                || stackableType == STACKABLE_UPGRADABLE_LEGACY;
    }

    public boolean isMultiBoxItem() {
        return stackableType == STACKABLE_RANDOM_UPGRADABLE_LEGACY
                || stackableType == STACKABLE_RANDOM_SS_UPGRADABLE_LEGACY
                || stackableType == STACKABLE_RANDOM_GOBLIN_UPGRADABLE_LEGACY;
    }

    public boolean isBoosterItem() {
        return stackableType == STACKABLE_BOOSTER || stackableType == STACKABLE_BOOSTER_RANDOM;
    }

    public boolean isCeraPackageItem() {
        return stackableType == STACKABLE_CERA_PACKAGE;
    }

    public int clientContainer() {
        if (equipmentSlot != com.dylan.protocol.body.EquipmentSlot.NONE) {
            if (equipmentSlot <= MAX_AVATAR_SLOT) {
                return com.dylan.protocol.body.ItemListType.LIST_AVATAR;
            }
            return equipmentSlot < MIN_CREATURE_SLOT
                    ? com.dylan.protocol.body.ItemListType.LIST_MAIN
                    : com.dylan.protocol.body.ItemListType.LIST_CREATURE;
        }
        return stackableType == STACKABLE_CREATURE || stackableType == STACKABLE_FEED
                ? com.dylan.protocol.body.ItemListType.LIST_CREATURE
                : com.dylan.protocol.body.ItemListType.LIST_MAIN;
    }

    public int shopBasePrice() {
        return value > 0 ? value : Math.max(0, price);
    }

    public int shopPrice() {
        return finishPointPrice > 0 ? finishPointPrice : Math.max(0, price);
    }

    public int priceCurrency() {
        return finishPointPrice > 0 ? PRICE_IN_FINISH_POINT : PRICE_IN_GOLD;
    }

    public static final int PRICE_IN_GOLD = 0;

    public static final int PRICE_IN_FINISH_POINT = 3;

    public int freshDurability() {
        return durability;
    }

    public static ItemScript load(PvfArchive archive, int id, String path) {
        String normalized = com.dylan.pvf.PvfPath.normalize(path);
        return parse(id, normalized, archive.readText(normalized));
    }

    public static ItemScript parse(int id, String normalized, String text) {
        var tokens = ScriptReader.tokenize(text);

        String name = "";
        int durability = 0;
        int stackLimit = 0;
        int cash = 0;
        int minimumLevel = 0;
        int rarity = RARITY_COMMON;
        boolean epicRouting = false;
        int grade = 0;
        int creationRate = 0;
        int attachType = ATTACH_FREE;
        boolean recipe = false;
        int stackableType = STACKABLE_TYPE_NONE;
        int equipmentSlot = com.dylan.protocol.body.EquipmentSlot.NONE;
        int price = 0;
        int value = 0;
        int guildExp = 0;
        int coolTimeMs = 0;
        int finishPointPrice = 0;
        java.util.List<AvatarOption> avatarOptions = java.util.List.of();
        java.util.List<AvatarType> avatarTypes = java.util.List.of();
        int usableJobs = JOB_ALL;
        int requiredSkill = NO_REQUIRED_SKILL;
        int subType = 0;
        int hatchItemId = 0;
        int usablePeriod = PERIOD_PERMANENT;
        int creatureSpecies = 0;
        int creatureMinLevel = 0;
        double creatureExpRate = 0.0;
        java.util.List<java.util.List<Integer>> intDataPools = new java.util.ArrayList<>();
        java.util.List<Integer> sealingRemoval = java.util.List.of();
        java.util.List<PackageItem> packageData = java.util.List.of();
        java.util.List<java.util.List<PackageItem>> packageSelectionGroups =
                new java.util.ArrayList<>();
        BoosterInfo booster = BoosterInfo.EMPTY;
        java.util.List<String> stringDataTags = java.util.List.of();
        EnchantEffect.Parsed enchantSection = EnchantEffect.Parsed.EMPTY;
        NeedMaterial needMaterial = NeedMaterial.NONE;
        int recipeSuccessRate = 0;
        int recipeBroadcast = 0;
        int recipeMaintainAbility = 0;
        java.util.List<Integer> recipeFailOutput = java.util.List.of();
        java.util.List<Integer> recipeBeadItem = java.util.List.of();
        boolean coolTime1 = false;
        boolean coolTime2 = false;
        boolean coolTimeProcess = false;
        boolean effect1 = false;
        boolean effect2 = false;
        boolean effectProcess = false;
        boolean effectTimer = false;
        int statChangeDurationMs = 0;
        boolean statChangeDuration = false;
        boolean expAffect = false;
        double expAffectRatio = 0.0;

        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            switch (token.text().toLowerCase()) {
                case "name2" -> {
                    if (i + 1 < tokens.size()
                            && tokens.get(i + 1).kind() == ScriptReader.Kind.STRING) {
                        name = tokens.get(i + 1).text().trim();
                    }
                }
                case "durability" -> durability = nextInt(tokens, i, durability);
                case "stack limit" -> stackLimit = nextInt(tokens, i, stackLimit);
                case "cash" -> cash = nextInt(tokens, i, cash);
                case "minimum level" -> minimumLevel = nextInt(tokens, i, minimumLevel);
                case "rarity" -> rarity = nextInt(tokens, i, rarity);
                case "grade" -> grade = nextInt(tokens, i, grade);
                case "creation rate" -> creationRate = nextInt(tokens, i, creationRate);
                case "price" -> price = nextInt(tokens, i, price);
                case "value" -> value = nextInt(tokens, i, value);
                case "guild power war point" -> guildExp = nextInt(tokens, i, guildExp);
                case "cool time" -> coolTimeMs = nextInt(tokens, i, coolTimeMs);
                case "sub type" -> subType = nextInt(tokens, i, subType);
                case "output index" -> hatchItemId = nextInt(tokens, i, hatchItemId);
                case "finish point price" ->
                        finishPointPrice = nextInt(tokens, i, finishPointPrice);
                case "recipe" -> recipe = true;
                case "attach type" -> {
                    if (i + 1 < tokens.size()
                            && tokens.get(i + 1).kind() == ScriptReader.Kind.STRING) {
                        String label = tokens.get(i + 1).text().trim().toLowerCase();
                        Integer known = ATTACH_TYPES.get(label);
                        attachType = known == null ? ATTACH_FREE : known;
                    }
                }
                case "stackable type" -> {
                    if (i + 1 < tokens.size()
                            && tokens.get(i + 1).kind() == ScriptReader.Kind.STRING) {
                        String label = tokens.get(i + 1).text().trim().toLowerCase();
                        Integer known = STACKABLE_TYPES.get(label);
                        stackableType = known == null ? STACKABLE_TYPE_NONE : known;
                    }
                }
                case "equipment type" -> {
                    if (i + 1 < tokens.size()
                            && tokens.get(i + 1).kind() == ScriptReader.Kind.STRING) {
                        equipmentSlot = com.dylan.protocol.body.EquipmentSlot.of(
                                tokens.get(i + 1).text());
                    }
                }
                case "epic routing" -> epicRouting = nextInt(tokens, i, 0) == 1;
                case "avatar select ability" -> avatarOptions = parseAvatarOptions(tokens, i);
                case "avatar type select" -> avatarTypes = parseAvatarTypes(tokens, i, normalized);
                case "usable job" -> {
                    int mask = 0;
                    for (int j = i + 1; j < tokens.size(); j++) {
                        var t = tokens.get(j);
                        if (t.kind() == ScriptReader.Kind.SECTION) {
                            break;
                        }
                        if (t.kind() != ScriptReader.Kind.STRING) {
                            continue;
                        }
                        String job = t.text().trim().toLowerCase();
                        if ("[all]".equals(job)) {
                            mask = JOB_ALL;
                            break;
                        }
                        Integer no = JOB_NAMES.get(job);
                        if (no == null) {
                            continue;
                        }
                        mask |= 1 << no;
                    }
                    usableJobs = mask == 0 ? JOB_ALL : mask;
                }
                case "required skill" -> requiredSkill = nextInt(tokens, i, requiredSkill);
                case "need material" -> needMaterial = parseNeedMaterial(tokens, i, normalized);
                case "usable period" -> usablePeriod = nextInt(tokens, i, usablePeriod);
                case "int data" -> {
                    var pool = parseIntData(tokens, i);
                    if (!pool.isEmpty()) {
                        intDataPools.add(pool);
                    }
                }
                case "sealing removal item" -> sealingRemoval = parseIntData(tokens, i);
                case "package data" -> packageData = parsePackageData(tokens, i, normalized);
                case "package data include usable period" ->
                        packageData = parsePackageDataWithPeriod(tokens, i);
                case "package data selection" ->
                        packageSelectionGroups.add(parsePackageData(tokens, i, normalized));
                case "booster info" -> booster = BoosterInfo.parse(tokens, i, normalized);
                case "string data" -> stringDataTags = parseStringDataTags(tokens, i);
                case "enchant" -> enchantSection = EnchantEffect.parseSection(tokens, i);
                case "success rate" -> recipeSuccessRate = nextInt(tokens, i, recipeSuccessRate);
                case "broadcast type" -> recipeBroadcast = nextInt(tokens, i, recipeBroadcast);
                case "maintain ability" ->
                        recipeMaintainAbility = nextInt(tokens, i, recipeMaintainAbility);
                case "fail output" -> recipeFailOutput = parseIntData(tokens, i);
                case "bead item" -> recipeBeadItem = parseIntData(tokens, i);
                case "cooltime maintenance" -> {
                    boolean[] v = nextFlags(tokens, i, 3, normalized, "[cooltime maintenance]");
                    coolTime1 = v[0];
                    coolTime2 = v[1];
                    coolTimeProcess = v[2];
                }
                case "effect maintenance" -> {
                    boolean[] v = nextFlags(tokens, i, 4, normalized, "[effect maintenance]");
                    effect1 = v[0];
                    effect2 = v[1];
                    effectProcess = v[2];
                    effectTimer = v[3];
                }
                case "stat change duration" -> {
                    statChangeDurationMs = nextInt(tokens, i, statChangeDurationMs);
                    statChangeDuration = true;
                }
                case "exp bonus rate" -> {
                    expAffectRatio = nextDouble(tokens, i, expAffectRatio);
                    expAffect = true;
                }
                case "creature species" -> creatureSpecies = nextInt(tokens, i, creatureSpecies);
                case "creature minimum level" ->
                        creatureMinLevel = nextInt(tokens, i, creatureMinLevel);
                case "creature experience amount rate" ->
                        creatureExpRate = nextDouble(tokens, i, creatureExpRate);
                default -> {
                }
            }
        }
        return new ItemScript(id, normalized, name, durability, stackLimit, cash,
                minimumLevel, rarity, grade, creationRate, attachType, recipe,
                stackableType, equipmentSlot,
                price, value, coolTimeMs, finishPointPrice, avatarOptions, avatarTypes,
                usableJobs, requiredSkill, subType, hatchItemId,
                usablePeriod,
                lotteryOf(stackableType, intDataPools.isEmpty()
                        ? java.util.List.of() : intDataPools.get(0)),
                multiBoxOf(stackableType, intDataPools, sealingRemoval),
                CreatureInfo.of(creatureSpecies, creatureMinLevel, creatureExpRate),
                recipeOf(stackableType, intDataPools)
                        .withAdditional(recipeSuccessRate, recipeBroadcast,
                                recipeMaintainAbility, recipeFailOutput, recipeBeadItem),
                enchantOf(stackableType, enchantSection, stringDataTags),
                needMaterial, guildExp,
                ContinuousOption.of(coolTime1, coolTime2, coolTimeProcess,
                        effect1, effect2, effectProcess, effectTimer,
                        statChangeDurationMs, statChangeDuration, expAffect, expAffectRatio),
                packageDataOf(stackableType, packageData, normalized),
                stackableType == STACKABLE_CERA_PACKAGE
                        ? java.util.List.copyOf(packageSelectionGroups) : java.util.List.of(),
                epicRouting,
                booster);
    }

    private static java.util.List<PackageItem> packageDataOf(
            int stackableType, java.util.List<PackageItem> parsed, String path) {
        if (parsed.isEmpty()) {
            return java.util.List.of();
        }
        if (stackableType != STACKABLE_CERA_PACKAGE) {
            return java.util.List.of();
        }
        return parsed;
    }

    private static NeedMaterial parseNeedMaterial(java.util.List<ScriptReader.Token> tokens,
                                                  int at, String path) {
        int itemId = nextInt(tokens, at, NO_MATERIAL);
        int count = at + 2 < tokens.size()
                && tokens.get(at + 2).kind() == ScriptReader.Kind.WORD
                ? nextInt(tokens, at + 1, 0) : 0;
        if (itemId == NO_MATERIAL || count <= 0) {
            return NeedMaterial.NONE;
        }
        return new NeedMaterial(itemId, count);
    }

    private static EnchantEffect enchantOf(int stackableType, EnchantEffect.Parsed parsed,
                                           java.util.List<String> stringDataTags) {
        if (stackableType != STACKABLE_MATERIAL_EXPERT_JOB) {
            return EnchantEffect.NONE;
        }
        return parsed.toEffect(stringDataTags);
    }

    private static RecipeInfo recipeOf(int stackableType,
                                       java.util.List<java.util.List<Integer>> pools) {
        if (stackableType != STACKABLE_RECIPE || pools.isEmpty()) {
            return RecipeInfo.EMPTY;
        }
        return RecipeInfo.of(pools.get(0));
    }

    static java.util.List<String> parseStringDataTags(
            java.util.List<ScriptReader.Token> tokens, int at) {
        var out = new java.util.ArrayList<String>();
        for (int i = at + 1; i < tokens.size(); i++) {
            var t = tokens.get(i);
            if (t.kind() == ScriptReader.Kind.SECTION
                    || t.kind() == ScriptReader.Kind.SECTION_END) {
                break;
            }
            if (t.kind() != ScriptReader.Kind.STRING) {
                continue;
            }
            String s = t.text().trim();
            if (s.startsWith("[")) {
                out.add(s.toLowerCase(java.util.Locale.ROOT));
            }
        }
        return java.util.List.copyOf(out);
    }

    private static MultiBoxInfo multiBoxOf(int stackableType,
                                           java.util.List<java.util.List<Integer>> pools,
                                           java.util.List<Integer> sealing) {
        if (pools.isEmpty()) {
            return MultiBoxInfo.EMPTY;
        }
        return switch (stackableType) {
            case STACKABLE_RANDOM_UPGRADABLE_LEGACY,
                 STACKABLE_RANDOM_SS_UPGRADABLE_LEGACY,
                 STACKABLE_RANDOM_GOBLIN_UPGRADABLE_LEGACY -> MultiBoxInfo.of(pools, sealing);
            default -> MultiBoxInfo.EMPTY;
        };
    }

    private static LotteryInfo lotteryOf(int stackableType, java.util.List<Integer> intData) {
        if (intData.isEmpty()) {
            return LotteryInfo.EMPTY;
        }
        return switch (stackableType) {
            case STACKABLE_LEGACY -> LotteryInfo.plain(intData);
            case STACKABLE_STACKABLE_LEGACY, STACKABLE_UPGRADABLE_LEGACY ->
                    LotteryInfo.stackable(intData);
            default -> LotteryInfo.EMPTY;
        };
    }

    private static java.util.List<PackageItem> parsePackageData(
            java.util.List<ScriptReader.Token> tokens, int at, String path) {
        var flat = parseIntData(tokens, at);
        if (flat.isEmpty()) {
            return java.util.List.of();
        }
        if (flat.size() % 2 != 0) {
            return java.util.List.of();
        }
        var out = new java.util.ArrayList<PackageItem>(flat.size() / 2);
        for (int i = 0; i + 1 < flat.size(); i += 2) {
            int itemId = flat.get(i);
            int count = flat.get(i + 1);
            if (itemId <= 0 || count <= 0 || count > PackageItem.MAX_COUNT) {
                return java.util.List.of();
            }
            out.add(new PackageItem(itemId, count));
        }
        return java.util.List.copyOf(out);
    }

    static java.util.List<Integer> parseIntData(java.util.List<ScriptReader.Token> tokens, int at) {
        var out = new java.util.ArrayList<Integer>();
        for (int i = at + 1; i < tokens.size(); i++) {
            var t = tokens.get(i);
            if (t.kind() == ScriptReader.Kind.SECTION
                    || t.kind() == ScriptReader.Kind.SECTION_END) {
                break;
            }
            if (t.kind() != ScriptReader.Kind.WORD) {
                continue;
            }
            try {
                out.add(t.asInt());
            } catch (RuntimeException e) {
                return java.util.List.of();
            }
        }
        return out;
    }

    static java.util.List<AvatarType> parseAvatarTypes(
            java.util.List<ScriptReader.Token> tokens, int at, String normalized) {
        var out = new java.util.ArrayList<AvatarType>();
        int i = at + 1;
        while (i < tokens.size()) {
            var token = tokens.get(i);
            if (token.kind() == ScriptReader.Kind.SECTION
                    || token.kind() == ScriptReader.Kind.SECTION_END) {
                break;
            }
            if (token.kind() != ScriptReader.Kind.WORD) {
                break;
            }
            if (i + 4 >= tokens.size()) {
                break;
            }
            int days = tokens.get(i).asInt();
            int gold = tokens.get(i + 1).asInt();
            int winPoint = tokens.get(i + 2).asInt();
            int cera = tokens.get(i + 3).asInt();
            int sockets = tokens.get(i + 4).asInt();
            out.add(new AvatarType(days, gold, winPoint, cera, sockets));
            i += 5;
            for (int s = 0; s < sockets && i < tokens.size(); s++) {
                if (tokens.get(i).kind() != ScriptReader.Kind.STRING) {
                    break;
                }
                i++;
            }
        }
        return out;
    }

    static java.util.List<AvatarOption> parseAvatarOptions(
            java.util.List<ScriptReader.Token> tokens, int at) {
        var out = new java.util.ArrayList<AvatarOption>();
        int i = at + 1;
        while (i < tokens.size()) {
            var token = tokens.get(i);
            if (token.kind() == ScriptReader.Kind.SECTION
                    || token.kind() == ScriptReader.Kind.SECTION_END) {
                break;
            }
            String stat = token.text().trim();
            if (!isAvatarStatKeyword(stat)) {
                break;
            }
            boolean skill = AvatarOption.SKILL_LEVEL.equalsIgnoreCase(stat);
            int columns = skill ? 4 : 3;
            if (i + columns > tokens.size()) {
                break;
            }
            String sign = tokens.get(i + 1).text().trim();
            int value = intOrZero(tokens.get(i + 2));
            int level = skill ? intOrZero(tokens.get(i + 3)) : 0;
            out.add(new AvatarOption(stat, sign, value, level));
            i += columns;
        }
        return java.util.List.copyOf(out);
    }

    private static boolean isAvatarStatKeyword(String text) {
        for (String keyword : AVATAR_STAT_KEYWORDS) {
            if (keyword.equalsIgnoreCase(text)) {
                return true;
            }
        }
        return false;
    }

    private static int intOrZero(ScriptReader.Token token) {
        try {
            return token.asInt();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static int nextInt(java.util.List<ScriptReader.Token> tokens, int at, int fallback) {
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

    private static double nextDouble(java.util.List<ScriptReader.Token> tokens, int at,
                                     double fallback) {
        if (at + 1 < tokens.size()
                && tokens.get(at + 1).kind() == ScriptReader.Kind.WORD) {
            try {
                return tokens.get(at + 1).asDouble();
            } catch (RuntimeException e) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean[] nextFlags(java.util.List<ScriptReader.Token> tokens, int at,
                                       int count, String path, String section) {
        boolean[] out = new boolean[count];
        int read = 0;
        for (int j = at + 1; j < tokens.size() && read < count; j++) {
            var t = tokens.get(j);
            if (t.kind() == ScriptReader.Kind.SECTION) {
                break;
            }
            if (t.kind() != ScriptReader.Kind.WORD) {
                continue;
            }
            try {
                out[read++] = t.asInt() != 0;
            } catch (RuntimeException e) {
                break;
            }
        }
        return out;
    }
}
