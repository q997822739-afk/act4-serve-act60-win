package com.dylan.game;

import com.dylan.game.event.EventDefinition;
import com.dylan.game.event.EventScheduler;
import com.dylan.game.event.EventService;
import com.dylan.game.event.EventState;
import com.dylan.protocol.body.Cmd65DebugCommandRequest;
import com.dylan.protocol.body.Cmd65ReplyBody;
import com.dylan.protocol.body.GmSubCommand;
import com.dylan.protocol.body.ItemListType;
import com.dylan.protocol.body.Noti138GmRequestMidBody;
import com.dylan.pvf.script.ExpertJobScript;
import com.dylan.store.GameStore;
import com.dylan.store.GmStore;
import com.dylan.store.blob.InvenItem;
import com.dylan.store.blob.ItemArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GmService {

    private GmService() {
    }

    private static final int DEFAULT_GRANTED_LEVEL = 1;

    private static final int DEFAULT_ITEM_COUNT = 100;

    private static final int MID_MAX_NAME_BYTES = 29;

    private static final int MAX_FIRST_GROW_TYPE = 5;

    public static byte[] onCommand(GameSession s, Cmd65DebugCommandRequest request) {
        return onCommand(s, request, true);
    }

    public static byte[] onCommand(GameSession s, Cmd65DebugCommandRequest request,
                                   boolean clientGetsReply) {
        if (request.sub() == null) {
            return Cmd65ReplyBody.refused();
        }
        GmSubCommand sub = request.sub();

        long accountId = s.account() == null ? 0L : s.account().mId();
        int level = GmManager.gmLevel(accountId);
        String rejection = authorize(s, sub, accountId);
        if (rejection != null) {
            audit(s, accountId, level, sub, request.detail(), false, rejection);
            ChatService.system(s, "无法使用该命令。");
            return Cmd65ReplyBody.refused();
        }

        Outcome outcome;
        try {
            outcome = execute(s, sub, request);
        } catch (RuntimeException e) {
            audit(s, accountId, level, sub, request.detail(), true, "执行出错：" + e);
            ChatService.system(s, "命令执行失败。");
            return Cmd65ReplyBody.refused();
        }

        audit(s, accountId, level, sub, request.detail(), true, outcome.log());
        int echoed = replySubCommandId(sub, outcome);
        boolean clientSpeaks = clientGetsReply && outcome.done()
                && echoed <= Cmd65ReplyBody.MAX_TALKING_SUB_COMMAND;
        String notice = outcome.notice();
        if (notice == null && !clientSpeaks) {
            notice = outcome.log();
        }
        if (notice != null) {
            ChatService.system(s, notice);
        }
        return outcome.done() ? Cmd65ReplyBody.done(echoed) : Cmd65ReplyBody.refused();
    }

    private static int replySubCommandId(GmSubCommand sub, Outcome outcome) {
        return outcome.replyAs() == null ? sub.id() : outcome.replyAs().id();
    }

    private static String authorize(GameSession s, GmSubCommand sub, long accountId) {
        if (sub != GmSubCommand.GM_MODE && !GmManager.inGmMode(accountId)) {
            return "账号 " + accountId + " 还没有用 #ehdgusrlwn 进入 GM 模式";
        }
        if (!s.hasCharacter()) {
            return "还没选角色";
        }
        if (!GmManager.isGm(accountId)) {
            return "账号 " + accountId + " 不在 gm_manifest 里";
        }
        if (sub != GmSubCommand.GM_MODE && !inGmMode(s)) {
            return "这条连接还没有开 GM 模式（官方 isGMUser 那道）";
        }
        return null;
    }

    private static boolean inGmMode(GameSession s) {
        return s.gmMode();
    }

    private static void audit(GameSession s, long accountId, int level, GmSubCommand sub,
                              String detail, boolean accepted, String result) {
        GameStore store = s.store();
        if (store == null) {
            return;
        }
        store.gm().logGmCommand(accountId, level, s.characNo(), s.characName(),
                sub.display(), detail, accepted, result);
    }

    private static Outcome execute(GameSession s, GmSubCommand sub,
                                   Cmd65DebugCommandRequest r) {
        return switch (sub) {
            case GM_MODE -> turnGmMode(s);
            case LEVEL_UP -> levelUpDown(s, +1);
            case LEVEL_DOWN -> levelUpDown(s, -1);
            case GROW -> setGrowType(s, r.a(), r.b());
            case CLEAR_ALL_SKILL -> clearSkill(s);
            case ADD_SKILL -> setSkill(s, r.a(), r.b());
            case ADD_ITEM -> addItem(s, r.unsignedA(), r.b());
            case LEVEL_TO -> setLevel(s, r.a());
            case PVP_RANK -> noSuchSystem("决斗场");
            case UPGRADE_SUCCESS -> setUpgradeMode(s, UpgradeService.GmMode.ALWAYS_SUCCESS);
            case UPGRADE_FAIL -> setUpgradeMode(s, UpgradeService.GmMode.ALWAYS_FAIL);
            case UPGRADE_RANDOM -> setUpgradeMode(s, UpgradeService.GmMode.RANDOM);
            case KICK -> userRepel(s, r.text());
            case WARROOM_TIME -> noSuchSystem("决斗房");
            case CREATURE_EXP -> creatureExp(s, r.a());
            case CREATURE_FILL -> creatureStomach(s, r.a());
            case CREATURE_EVOLUTION -> creatureEvolute(s);
            case ADD_AVATAR -> addAvatar(s, r.unsignedA(), r.b(), r.c(), r.d());
            case HELL -> enableHellDungeon(s);
            case SET_GM_ADD -> setGm(s, r.unsignedA());
            case SET_GM_REMOVE -> removeGm(s, r.unsignedA());
            case HIDE -> invisible(s);
            case SHOW -> visible(s);
            case MID -> requestMid(s, r.text());
            case LUCK_POINT -> setLuckPoint(s, r.a());
            case SET_AGE -> noSuchSystem("游戏时长限制（韩服的 SetAge）");
            case SET_ENCHANT -> setEnchant(s, r.a(), r.b(), r.c());
            case SET_ADD_INFO -> setAddInfo(s, r.a(), r.b(), r.c());
            case GRADE_PROBE -> gradeProbe(s, r.unsignedA(), r.b(), r.c());
            case EXPERT_JOB_EXP -> expertJobExp(s, r.a());
            case EXPERT_JOB_TYPE -> expertJobType(s, r.a());
            case LEARN_RECIPE -> learnRecipe(s, r.text());
            case SET_USER_DEFINE -> Outcome.failedQuietly("官方这一条是空桩，什么都不做");
            case GUILD_LEVEL_UP, CARGO_CAPACITY, GUILD_POWERWAR_POINT -> noSuchSystem("公会");
            case POWER_WAR_START, POWER_WAR_END, POWER_UP_ON, POWER_UP_OFF,
                 USER_POWERWAR_POINT_UP, USER_POWERWAR_POINT_INIT,
                 POWERWAR_POINT_UP -> noSuchSystem("势力战");
            case SET_BLACK_COUNT -> noSuchSystem("问题用户计数");
            case FATIGUE -> setFatigue(s, r.a());
            case USE_COIN -> useCoin(s, r.a());
            case GM_MODE_OFF -> gmModeOff(s);
            case CLEAR_AVATAR_INVEN -> clearAvatarInven(s);
            case CLEAR_INVEN -> clearInven(s);
            case SET_MONEY -> setMoney(s, r.unsignedA());
            case UPDATE_CRANE_SEED -> noSuchSystem("抓娃娃机");
            case EVENT_LIST -> eventList(s);
            case START_EVENT -> startEvent(s, r.a(), r.b(), r.c());
            case END_EVENT -> endEvent(s, r.a());
            case CLEAR_QUEST -> clearQuest(s, r.text());
            case QUEST_DONE -> questDone(s, r.text());
        };
    }

    private static String selfManagedReason(int no) {
        if (no == com.dylan.game.event.EventNo.WEEKEND_BONUS) {
            return "活动 " + no + "（周末疲劳奖励）由服务端按周六 6 点~周一 6 点自己开关，"
                    + "手工开会和定时器每分钟互相翻转。";
        }
        return null;
    }

    private static Outcome eventList(GameSession s) {
        List<Integer> running = EventService.runningNumbers();
        ChatService.system(s, "有定义的活动 " + EventDefinition.count() + " 个，现在开着 "
                + running.size() + " 个：");
        for (EventDefinition def : EventDefinition.all()) {
            EventState state = EventService.get(def.no());
            boolean on = state != null && state.running();
            String line = (on ? "● " : "○ ") + def.no() + " " + def.label();
            if (on) {
                line += "（param " + state.param1() + "/" + state.param2() + "）";
            } else if (def.needsParam()) {
                line += "（" + def.paramMeaning() + "）";
            }
            if (!com.dylan.game.event.EventNo.clientSpeaks(def.no())) {
                line += " ⚠客户端无提示";
            }
            ChatService.system(s, line);
        }
        return Outcome.done("列出 " + EventDefinition.count() + " 个活动槽，开着 " + running);
    }

    private static Outcome startEvent(GameSession s, int no, int param1, int param2) {
        EventDefinition def = EventDefinition.of(no);
        if (def == null) {
            return Outcome.failed("这一版没有活动 " + no + " —— 敲 #event list 看有哪些。");
        }
        String selfManaged = selfManagedReason(no);
        if (selfManaged != null) {
            return Outcome.failed(selfManaged);
        }
        if (param1 < 0 || param1 > 0xFFFF || param2 < 0 || param2 > 0xFFFF) {
            return Outcome.failed("参数只能是 0..65535（官方那两句 get_ushort 的宽度）。");
        }
        GameStore store = s.store();
        if (store == null) {
            return Outcome.failed("没有数据库，活动开了也留不住。");
        }
        long mId = s.account() == null ? 0L : s.account().mId();
        store.event().startEvent(no, param1, param2, 0, mId,
                "GM " + s.characName() + " 通过 #startevent 开启");
        if (!EventService.start(no, param1, param2)) {
            return Outcome.failed("活动 " + no + " 开不起来（槽是空的）。");
        }
        return Outcome.told("已开启活动 " + def.describe() + "，param " + param1 + "/" + param2
                + (com.dylan.game.event.EventNo.clientSpeaks(no)
                        ? "" : "（⚠ 客户端没有这个号的提示文案，玩家看不到）"));
    }

    private static Outcome endEvent(GameSession s, int no) {
        EventDefinition def = EventDefinition.of(no);
        if (def == null) {
            return Outcome.failed("这一版没有活动 " + no + " —— 敲 #event list 看有哪些。");
        }
        String selfManaged = selfManagedReason(no);
        if (selfManaged != null) {
            return Outcome.failed(selfManaged);
        }
        GameStore store = s.store();
        if (store == null) {
            return Outcome.failed("没有数据库，关不了。");
        }
        int closed = store.event().endEvent(no, EventScheduler.serverId());
        boolean wasRunning = EventService.isRunning(no);
        EventService.end(no);
        if (!wasRunning && closed == 0) {
            return Outcome.failed("活动 " + no + " 本来就没开着。");
        }
        return Outcome.told("已结束活动 " + def.describe() + "（库里关掉 " + closed + " 行）");
    }

    private static Outcome turnGmMode(GameSession s) {
        long accountId = s.account() == null ? 0L : s.account().mId();
        if (GmManager.gmLevel(accountId) <= 0) {
            return Outcome.failed("这个账号的 gm_manifest.level 是 0，开不了 GM 模式。");
        }
        boolean on = GmManager.turnGmMode(accountId);
        if (!on) {
            return debugCommandOff(s).replyingAs(GmSubCommand.GM_MODE_OFF);
        }
        s.gmMode(true);
        return debugCommandOn(s);
    }

    private static Outcome debugCommandOn(GameSession s) {
        s.fatigue().recoverFatigue(0);
        s.fatigue().sendFatigue("进入 GM 模式");
        CharacterService.resendItemListsForGmMode(s, "进入 GM 模式（官方 _debugCommandOn）");
        return Outcome.done("进入 GM 模式");
    }

    private static Outcome debugCommandOff(GameSession s) {
        if (!inGmMode(s)) {
            return Outcome.failedQuietly("这条连接本来就不在 GM 模式里");
        }
        s.gmMode(false);
        return Outcome.done("退出 GM 模式");
    }

    private static Outcome gmModeOff(GameSession s) {
        GmManager.leaveGmMode(s.account() == null ? 0L : s.account().mId());
        return debugCommandOff(s);
    }

    private static Outcome levelUpDown(GameSession s, int delta) {
        return changeLevel(s, s.level() + delta, delta > 0 ? "升一级" : "降一级");
    }

    private static Outcome setLevel(GameSession s, int target) {
        return changeLevel(s, target, "设为 " + target + " 级");
    }

    private static Outcome changeLevel(GameSession s, int target, String why) {
        int before = s.level();
        ExperienceService.SetLevelResult result = ExperienceService.setLevel(s, target, why);
        return switch (result.status()) {
            case NO_TABLE -> Outcome.failed("没有升级门槛表，等级改不了。");
            case OUT_OF_RANGE -> Outcome.failed(
                    "等级 " + target + " 超出 1.." + result.maxLevel() + " 的范围。");
            case UNCHANGED -> Outcome.failed("已经是 " + before + " 级了。");
            case DONE -> Outcome.done("等级 " + before + " → " + result.level()
                    + "，SP " + (result.spDelta() >= 0 ? "+" : "") + result.spDelta());
        };
    }

    private static Outcome setGrowType(GameSession s, int stage, int growType) {
        if (stage != 1 && stage != 2) {
            return Outcome.failed("第一个参数只能是 1（一转）或 2（觉醒）。");
        }
        int before = s.character() == null ? 0 : Math.max(0, s.character().growType());
        int firstGrow;
        int secondGrow;
        if (stage == 1) {
            firstGrow = growType;
            secondGrow = 0;
        } else {
            firstGrow = StatService.firstGrow(before);
            if (firstGrow == 0) {
                return Outcome.failed("还没一转，觉醒不了 —— 官方同一处也是 return 0。");
            }
            secondGrow = growType;
        }
        if (firstGrow < 0 || firstGrow > MAX_FIRST_GROW_TYPE) {
            return Outcome.failed("转职编号 " + firstGrow + " 超出官方的 0..5。");
        }
        if (secondGrow < 0 || secondGrow > 1) {
            return Outcome.failed("觉醒编号 " + secondGrow + " 超出官方的 0..1。");
        }
        GameStore store = s.store();
        if (store == null) {
            return Outcome.failed("没有数据库，转职改不了。");
        }
        int packed = StatService.packGrowType(firstGrow, secondGrow);
        if (!store.character().saveGrowType(s.characNo(), packed)) {
            return Outcome.failed("角色 " + s.characNo() + " 在 charac_info 里没有行。");
        }
        if (s.character() != null) {
            s.character(new com.dylan.store.CharacterRow(
                    s.character().characNo(), s.character().characName(), s.character().job(),
                    s.character().lev(), packed, s.character().sex(), s.character().guildId()));
        }
        var grown = s.skills().changeGrowType(firstGrow, secondGrow, s.level());
        s.skills().save();
        s.stats(CharacterService.combatStats(s, s.character()));
        CharacterService.resendOwnCharacterInfo(s, "GM 转职");
        CharacterService.refreshDetail(s, "GM 转职");
        sendSkillInfo(s, "GM 转职");
        return Outcome.done("grow_type " + before + " → " + packed
                + "（一转 " + firstGrow + " 觉醒 " + secondGrow + "），技能重建 "
                + grown.size() + " 条");
    }

    private static Outcome clearSkill(GameSession s) {
        int remain = s.skills().resetSkills(s.level(), false);
        if (remain < 0) {
            return Outcome.failed("技能表还没装载，初始化不了。");
        }
        sendSkillInfo(s, "GM 技能初始化");
        return Outcome.done("技能回到初始，SP 重算为 " + remain);
    }

    private static Outcome setSkill(GameSession s, int skillId, int level) {
        SkillService.SetLevelOutcome outcome = s.skills().setSkillLevel(skillId, level);
        if (!outcome.ok()) {
            return Outcome.failed("技能设定失败：" + outcome.reason());
        }
        sendSkillInfo(s, "GM 设技能等级");
        return Outcome.done("技能 " + skillId + " 设为 " + level + " 级（槽 "
                + outcome.slot() + "）");
    }

    private static void sendSkillInfo(GameSession s, String why) {
        var info = s.skills().skillInfo();
        s.sendNow(com.dylan.protocol.body.Noti19SkillInfoBody.PACKET_ID, info.toBytes());
    }

    private static Outcome addItem(GameSession s, long itemId, int count) {
        if (itemId == 0xFFFF_FFFFL) {
            return noSuchSystem("PC 房（官方这一支是 handleSetUserPCRoom）");
        }
        var scripts = s.scripts();
        if (scripts == null) {
            return Outcome.failed("没有物品脚本，发不了物品。");
        }
        var script = scripts.item((int) itemId).orElse(null);
        if (script == null) {
            return Outcome.failed("物品 " + itemId + " 不在 pvf 里。");
        }
        if (InventoryService.isCurrencyPlaceholder(itemId)) {
            return Outcome.failed("物品 " + itemId + " 是虚拟货币格的占位脚本，不是实物"
                    + "（发出来是个用不掉的死物）。金币用 #money；"
                    + "复活币用 #add item 42 <数量> 再右键用掉 —— 42 是 coin_general.stk，"
                    + "一个换一个（官方 increase_status 的 case 21）。");
        }
        if (script.isAvatar()) {
            return Outcome.failed("这是时装，用 #add avatar。");
        }
        if (s.inventory().inventory() == null) {
            return Outcome.failed("背包还没装载。");
        }
        int want = count <= 0 ? DEFAULT_ITEM_COUNT : count;
        if (script.isStackable() && script.stackLimit() < want) {
            want = script.stackLimit();
        }
        InventoryService.Landing landing = s.inventory().addItem(script, want);
        if (!landing.ok()) {
            return Outcome.failed("物品 " + itemId + " 发不出去：背包满了。");
        }
        s.inventory().save();
        CharacterService.resendItemLists(s, "GM #add item");
        return Outcome.done("物品 " + itemId + " ×" + want + " → 容器 " + landing.itemSpace()
                + " 槽 " + landing.slot());
    }

    private static Outcome addAvatar(GameSession s, long itemId, int slotKind, int period,
                                     int color) {
        var scripts = s.scripts();
        if (scripts == null) {
            return Outcome.failed("没有物品脚本，发不了时装。");
        }
        var script = scripts.item((int) itemId).orElse(null);
        if (script == null || !script.isAvatar()) {
            return Outcome.failed("物品 " + itemId + " 不是时装。");
        }
        int days = (period == 99 || period == 0) ? 0 : period;
        int slot = s.inventory().grantAvatarItem((int) itemId, days, color, 0L);
        if (slot < 0) {
            return Outcome.failed("时装 " + itemId + " 发不出去：装扮栏满了。");
        }
        return Outcome.done("时装 " + itemId + " → 装扮栏槽 " + slot
                + "（期限 " + (days == 0 ? "物品自带" : days + " 天") + "，颜色 " + color + "）");
    }

    private static Outcome clearInven(GameSession s) {
        ItemArray bag = s.inventory().containerOf(ItemListType.LIST_MAIN);
        if (bag == null) {
            return Outcome.failed("背包还没装载。");
        }
        int cleared = 0;
        for (int slot = 0; slot < bag.slots(); slot++) {
            if (!bag.get(slot).isEmpty()) {
                bag.set(slot, InvenItem.EMPTY);
                cleared++;
            }
        }
        if (cleared == 0) {
            return Outcome.told("背包本来就是空的。");
        }
        s.inventory().markItemsDirty();
        s.inventory().save();
        CharacterService.resendItemLists(s, "GM 清空背包");
        return Outcome.done("清掉 " + cleared + " 格背包");
    }

    private static Outcome clearAvatarInven(GameSession s) {
        int cleared = s.inventory().clearAvatarBag("GM #dirty avatar");
        if (cleared == 0) {
            return Outcome.told("装扮栏本来就是空的。");
        }
        CharacterService.resendItemLists(s, "GM 清空装扮栏");
        return Outcome.done("清掉 " + cleared + " 件时装");
    }

    private static Outcome setEnchant(GameSession s, int listType, int slot, int value) {
        ItemArray array = s.inventory().containerOf(listType);
        if (array == null) {
            return Outcome.failed("容器号 " + listType + " 不认识。");
        }
        if (slot < 0 || slot >= array.slots()) {
            return Outcome.failed("槽 " + slot + " 超出容器 " + listType
                    + " 的 0.." + (array.slots() - 1) + "。");
        }
        InvenItem item = array.get(slot);
        if (item.isEmpty()) {
            return Outcome.failed("容器 " + listType + " 的槽 " + slot + " 是空的。");
        }
        array.set(slot, item.withEnchant(Integer.toUnsignedLong(value)));
        s.inventory().markItemsDirty();
        s.inventory().save();
        CharacterService.resendItemLists(s, "GM 设附魔");
        return Outcome.done("容器 " + listType + " 槽 " + slot + " 的 enchant 设为 "
                + Integer.toUnsignedLong(value));
    }

    private static Outcome setAddInfo(GameSession s, int listType, int slot, int value) {
        ItemArray array = s.inventory().containerOf(listType);
        if (array == null) {
            return Outcome.failed("容器号 " + listType + " 不认识。");
        }
        if (slot < 0 || slot >= array.slots()) {
            return Outcome.failed("槽 " + slot + " 超出容器 " + listType
                    + " 的 0.." + (array.slots() - 1) + "。");
        }
        InvenItem item = array.get(slot);
        if (item.isEmpty()) {
            return Outcome.failed("容器 " + listType + " 的槽 " + slot + " 是空的。");
        }
        long before = item.addInfo();
        long after = Integer.toUnsignedLong(value);
        if (before == after) {
            return Outcome.failed("容器 " + listType + " 槽 " + slot + "（物品 " + item.itemId()
                    + "）的 add_info 本来就是 " + before + " —— 这一条什么都没改。"
                    + "拿它做对照实验会得到假阴性，换一个不同的值再来。");
        }
        array.set(slot, item.withAddInfo(after));
        s.inventory().markItemsDirty();
        s.inventory().save();
        s.inventory().sendItemRebuild(listType, "GM 设 add_info", slot);
        return Outcome.done("容器 " + listType + " 槽 " + slot + "（物品 " + item.itemId()
                + "）的 add_info " + before + " → " + after
                + (item.equipable() ? "  ⚠ 这是不可堆叠物品，add_info 不是数量" : ""));
    }

    public static final int MAX_GRADE_PROBE = 20;

    private static Outcome gradeProbe(GameSession s, long itemId, int start, int count) {
        var scripts = s.scripts();
        if (scripts == null) {
            return Outcome.failed("没有物品脚本，发不了物品。");
        }
        var script = scripts.item((int) itemId).orElse(null);
        if (script == null) {
            return Outcome.failed("物品 " + itemId + " 不在 pvf 里。");
        }
        if (script.isAvatar()) {
            return Outcome.failed("时装没有品级，探不出东西。");
        }
        if (script.isStackableItem()) {
            return Outcome.failed("物品 " + itemId + " 是可堆叠物品，它的 add_info 是数量不是"
                    + "品级种子。要挑装备（部位 9..18 那一批）。");
        }
        if (!com.dylan.game.drop.EquipGradeGenerator.hasGrade(script)) {
            return Outcome.failed("物品 " + itemId + " 不在「有品级」那一批（部位 9..18），"
                    + "神器那一族的 add_info 恒为 0。");
        }
        if (s.inventory().inventory() == null) {
            return Outcome.failed("背包还没装载。");
        }
        int want = Math.max(1, Math.min(count, MAX_GRADE_PROBE));
        var placed = new java.util.LinkedHashMap<Integer, Long>();
        int container = -1;
        for (int i = 0; i < want; i++) {
            InventoryService.Landing landing = s.inventory().addItem(script, 1);
            if (!landing.ok()) {
                break;
            }
            container = landing.itemSpace();
            var array = s.inventory().containerOf(container);
            InvenItem item = array.get(landing.slot());
            long seed = Integer.toUnsignedLong(start + i);
            array.set(landing.slot(), item.withAddInfo(seed));
            placed.put(landing.slot(), seed);
        }
        if (placed.isEmpty()) {
            return Outcome.failed("一件都放不进去：背包满了。先 #dirty 清一下。");
        }
        s.inventory().markItemsDirty();
        s.inventory().save();
        CharacterService.resendItemLists(s, "GM 品级探针");
        int firstSlot = placed.keySet().iterator().next();
        int lastSlot = 0;
        for (var e : placed.entrySet()) {
            lastSlot = e.getKey();
        }
        return Outcome.done("摆了 " + placed.size() + " 件物品 " + itemId
                + "：容器 " + container + " 槽 " + firstSlot + ".." + lastSlot
                + "，add_info 依次 " + start + ".." + (start + placed.size() - 1)
                + "。按槽号从小到大逐格看品级，测完 #dirty 清背包。");
    }

    private static Outcome setMoney(GameSession s, long amount) {
        if (amount == 0) {
            return Outcome.failedQuietly("官方这一条在参数为 0 时什么都不做");
        }
        long before = s.inventory().money();
        s.inventory().setMoney(amount);
        return Outcome.done("金币 " + before + " → " + amount);
    }

    private static Outcome useCoin(GameSession s, int amount) {
        if (amount <= 0) {
            return Outcome.failedQuietly("官方这一条只在数量 > 0 时动手");
        }
        long before = s.inventory().coinTotal();
        String spent = null;
        for (int i = 0; i < amount; i++) {
            spent = s.inventory().useCoin("GM #coin");
            if (spent == null) {
                break;
            }
        }
        long after = s.inventory().coinTotal();
        if (after == before) {
            return Outcome.failed("一个复活币都没有。");
        }
        return Outcome.done("复活币 " + before + " → " + after
                + (spent == null ? "（中途扣光了）" : ""));
    }

    private static Outcome setFatigue(GameSession s, int amount) {
        if (amount < 0) {
            return Outcome.failedQuietly("官方这一条对负数什么都不做");
        }
        int before = s.fatigue().remaining();
        if (amount > 0) {
            s.fatigue().fatigueUp(amount, "GM #fatigue");
        } else {
            s.fatigue().recoverFatigue(0);
        }
        s.fatigue().sendFatigue("GM #fatigue");
        return Outcome.done("疲劳余量 " + before + " → " + s.fatigue().remaining());
    }

    private static Outcome invisible(GameSession s) {
        if (s.invisible()) {
            return Outcome.told("已经是隐身状态了。");
        }
        s.invisible(true);
        CharacterService.refreshDetail(s, "GM 隐身");
        return Outcome.done("已隐身。");
    }

    private static Outcome visible(GameSession s) {
        if (!s.invisible()) {
            return Outcome.told("本来就不是隐身状态。");
        }
        s.invisible(false);
        if (!s.inDungeon()) {
            CharacterService.resendOwnCharacterInfo(s, "GM 解除隐身");
        }
        return Outcome.done("已现身。");
    }

    private static Outcome creatureExp(GameSession s, int amount) {
        var worn = s.inventory().equippedCreature();
        if (worn == null) {
            return Outcome.failed("没有带着宠物。");
        }
        if (amount <= 0) {
            return Outcome.failedQuietly("#creature exp 的值要是正数，给的是 " + amount);
        }
        int before = worn.level();
        CreatureService.gainExp(s, amount);
        return Outcome.told("宠物 +" + amount + " 经验：等级 " + before + " → " + worn.level()
                + "，累计 " + worn.exp());
    }

    private static Outcome expertJobExp(GameSession s, int value) {
        if (s.expertJob().type() == ExpertJobScript.TYPE_NONE) {
            return Outcome.failed("这个角色没有副职业，熟练度无处可放。"
                    + "先 #expertjob type 1 转成附魔师。");
        }
        if (value < 0) {
            return Outcome.failedQuietly("#expertjob 的熟练度要 >= 0，给的是 " + value);
        }
        int before = s.expertJob().exp();
        int beforeLevel = s.expertJob().level();
        int after = s.expertJob().increaseExp(value);
        int afterLevel = s.expertJob().level();
        String tail = after == value ? ""
                : "（要的是 " + value + "，被官方那两道钳位挡在这儿 ——"
                        + " ACT5 缺 [expertjob level limit]，一次最多升一级）";
        return Outcome.told("副职业熟练度 " + before + " → " + after
                + "，等级 " + beforeLevel + " → " + afterLevel + tail);
    }

    private static Outcome expertJobType(GameSession s, int type) {
        int before = s.expertJob().type();
        if (type == ExpertJobScript.TYPE_NONE) {
            s.expertJob().gmClearType();
            return Outcome.told("副职业 " + before + " → 0（卸职）。"
                    + "⚠ 照官方这一支：技能没收回、配方位图没清、放弃次数没加、没收费。");
        }
        var granted = s.expertJob().changeTo(type);
        if (s.expertJob().type() != type) {
            return Outcome.failed("转不成副职业 " + type + "（没有这份 .exj，"
                    + "expertjob.lst 里查不到）。ACT5 只上了附魔师 = 1。");
        }
        CharacterService.resendOwnCharacterInfo(s, "GM 转副职业 " + type);
        CharacterService.refreshDetail(s, "GM 转副职业");
        s.expertJob().sendInfo("GM 转副职业");
        return Outcome.told("副职业 " + before + " → " + type
                + "，送了 " + granted.size() + " 个技能，配方位图已清零、熟练度归 0。");
    }

    private static Outcome learnRecipe(GameSession s, String arg) {
        if (s.expertJob().type() == ExpertJobScript.TYPE_NONE) {
            return Outcome.failed("这个角色没有副职业。先 #expertjob type 1 转成附魔师。");
        }
        ExpertJobScript script = s.expertJob().script();
        if (script == null) {
            return Outcome.failed("副职业 " + s.expertJob().type() + " 没有 .exj。");
        }
        String text = arg == null ? "" : arg.trim();
        if (text.isEmpty()) {
            return Outcome.failedQuietly("用法：#learnrecipe <配方itemId> 或 #learnrecipe all");
        }
        if ("all".equalsIgnoreCase(text)) {
            int learned = s.expertJob().gmLearnAllRecipes();
            return Outcome.told("学会了 " + learned + " 张新配方，现在共 "
                    + s.expertJob().recipes().count() + " 张（[items] 一共 "
                    + script.recipes().size() + " 条）。");
        }
        int itemId;
        try {
            itemId = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return Outcome.failedQuietly("认不出「" + text + "」——要么是配方物品编号，要么是 all");
        }
        int index = script.recipeIndex(itemId);
        if (index == ExpertJobScript.NO_RECIPE) {
            return Outcome.failed("配方 " + itemId + " 不在副职业 "
                    + s.expertJob().type() + " 的 [items] 里。");
        }
        if (!s.expertJob().gmLearnRecipe(index)) {
            return Outcome.told("配方 " + itemId + "（序号 " + index + "）本来就学过了。");
        }
        return Outcome.told("学会配方 " + itemId + "（序号 " + index + "），现在共 "
                + s.expertJob().recipes().count() + " 张。");
    }

    private static Outcome clearQuest(GameSession s, String arg) {
        if (s.quests() == null) {
            return Outcome.failed("还没选角色，没有任务状态可以改。");
        }
        String text = arg == null ? "" : arg.trim();
        if (text.isEmpty()) {
            return Outcome.failedQuietly("用法：#clearquest <任务号> [任务号…] 或 #clearquest all");
        }
        List<Integer> ids;
        if ("all".equalsIgnoreCase(text)) {
            ids = QuestService.gmClearableQuestIds(s);
            if (ids.isEmpty()) {
                return Outcome.failed("这个角色一个够得着的任务都没有 —— pvf 没载入，"
                        + "或者职业和等级把 quest.lst 里的都挡住了。");
            }
        } else {
            ids = new ArrayList<>();
            for (String token : text.split("\\s+")) {
                try {
                    ids.add(Integer.parseInt(token));
                } catch (NumberFormatException e) {
                    return Outcome.failedQuietly("认不出「" + token + "」——要么是任务号，要么是 all");
                }
            }
        }
        var result = QuestService.gmClearQuests(s, ids);
        StringBuilder sb = new StringBuilder();
        sb.append("标记完成 ").append(result.cleared().size()).append(" 个任务");
        if (!result.already().isEmpty()) {
            sb.append("，本来就完成了 ").append(result.already().size()).append(" 个");
        }
        if (!result.skipped().isEmpty()) {
            sb.append("，").append(result.skipped().size())
                    .append(" 个过不了官方那道闸（职业/等级/转职分支对不上）");
        }
        sb.append("。");
        if (result.aborted()) {
            sb.append("⚠ 任务 ").append(result.unknown())
                    .append(" 在 quest.lst 里查不到，照官方在这里整批中断了，")
                    .append("它后面的号一个都没处理。");
        }
        if (!result.cleared().isEmpty()) {
            sb.append("🔴 客户端的已完成表只在选角时收，靠前置任务开门的副本要重选一次角色才出现。");
        }
        return Outcome.told(sb.toString());
    }

    private static Outcome questDone(GameSession s, String arg) {
        if (s.quests() == null) {
            return Outcome.failed("还没选角色，没有任务状态可以改。");
        }
        String text = arg == null ? "" : arg.trim();
        List<Integer> ids = new ArrayList<>();
        if (!text.isEmpty()) {
            for (String token : text.split("\\s+")) {
                try {
                    ids.add(Integer.parseInt(token));
                } catch (NumberFormatException e) {
                    return Outcome.failedQuietly("认不出「" + token + "」——这条命令要任务号，"
                            + "不带参数就是所有进行中的任务");
                }
            }
        }
        if (ids.isEmpty() && s.quests().runningCount() == 0) {
            return Outcome.failed("一个进行中的任务都没有。先在任务书里接一个。");
        }
        var result = QuestService.gmQuestDone(s, ids);
        StringBuilder sb = new StringBuilder();
        if (result.readied().isEmpty()) {
            sb.append("没有一个任务号在进行中的槽里");
            if (!result.notRunning().isEmpty()) {
                sb.append("（").append(result.notRunning()).append("）");
            }
            sb.append("。不过交任务免检已经打开了 —— 进度本来就满、只差材料的那种，"
                    + "现在直接点完成就行。");
            return Outcome.told(sb.toString());
        }
        sb.append(result.readied().size()).append(" 个任务的进度已经清零，")
                .append("在任务书里直接点「完成」就能交：").append(result.readied()).append("。");
        if (!result.notRunning().isEmpty()) {
            sb.append("（").append(result.notRunning()).append(" 不在进行中，没动。）");
        }
        sb.append("🔴 材料也免了，但只免这一次 —— 照官方，交完一次任务免检就自动关。");
        return Outcome.told(sb.toString());
    }

    private static Outcome creatureStomach(GameSession s, int value) {
        if (!CreatureService.setStomach(s, value)) {
            return Outcome.failed("没有带着宠物。");
        }
        return Outcome.told("宠物的饱食度设成了 " + value + "。");
    }

    private static Outcome creatureEvolute(GameSession s) {
        var worn = s.inventory().equippedCreature();
        if (worn == null) {
            return Outcome.failed("没有带着宠物。");
        }
        var script = worn.script();
        if (script == null) {
            return Outcome.failed("这只宠物在 creature.lst 里查不到脚本，进化不了。");
        }
        if (script.evolutionCreatureId() <= 0) {
            return Outcome.failed("这只宠物没有下一形态（[evolution creature id] 是 0）。");
        }
        int before = worn.row().itemId();
        CreatureService.evolute(s, worn);
        var after = s.inventory().equippedCreature();
        if (after == null || after.row().itemId() == before) {
            return Outcome.failed("进化失败，看服务端日志。");
        }
        return Outcome.told("宠物进化了：" + before + " → " + after.row().itemId() + "。");
    }

    private static Outcome setUpgradeMode(GameSession s, UpgradeService.GmMode mode) {
        s.upgrades().gmMode(mode);
        return Outcome.told("下一次强化：" + mode.display() + "。");
    }

    private static Outcome userRepel(GameSession s, String name) {
        if (name.isBlank()) {
            return Outcome.failed("要踢谁？用法：#kick <角色名>");
        }
        GameSession target = SessionRegistry.findByName(name);
        if (target == null) {
            return Outcome.told("找不到在线的角色「" + name + "」。");
        }
        if (target == s) {
            return Outcome.told("不能踢自己。");
        }
        target.run(() -> {
            ChatService.system(target, "你已被管理员断开连接。");
            target.fail("被 GM " + s.characName() + " 用 #kick 踢下线");
        });
        return Outcome.told("已断开「" + name + "」的连接。");
    }

    private static Outcome requestMid(GameSession s, String name) {
        if (name.isBlank()) {
            return Outcome.told("要查谁？用法：#mid <角色名>");
        }
        if (name.getBytes(java.nio.charset.Charset.forName("GBK")).length > MID_MAX_NAME_BYTES) {
            return Outcome.told("名字超过 " + MID_MAX_NAME_BYTES + " 字节，官方这一支不发包。");
        }
        GameStore store = s.store();
        if (store == null) {
            sendMid(s, Noti138GmRequestMidBody.notFound());
            return Outcome.done("没有数据库，查不了");
        }
        Optional<GmStore.CharacterOwner> owner = store.gm().characterOwner(name);
        if (owner.isEmpty()) {
            sendMid(s, Noti138GmRequestMidBody.notFound());
            return Outcome.done("找不到角色「" + name + "」");
        }
        sendMid(s, Noti138GmRequestMidBody.found(owner.get().accountId(), owner.get().name()));
        return Outcome.done("角色「" + owner.get().name() + "」→ 账号 "
                + owner.get().accountId());
    }

    private static void sendMid(GameSession s, Noti138GmRequestMidBody body) {
        s.sendNow(Noti138GmRequestMidBody.PACKET_ID, body.toBytes());
    }

    private static Outcome setGm(GameSession s, long targetAccountId) {
        GameStore store = s.store();
        if (store == null) {
            return Outcome.failed("没有数据库，改不了 GM 名单。");
        }
        if (targetAccountId <= 0) {
            return Outcome.failed("账号编号 " + targetAccountId + " 不合法。");
        }
        store.gm().setGmLevel(targetAccountId, DEFAULT_GRANTED_LEVEL);
        GmManager.setGmLevel(targetAccountId, DEFAULT_GRANTED_LEVEL);
        return Outcome.told("账号 " + targetAccountId + " 现在是 GM（level "
                + DEFAULT_GRANTED_LEVEL + "）；别的频道进程要重启才认。");
    }

    private static Outcome removeGm(GameSession s, long targetAccountId) {
        GameStore store = s.store();
        if (store == null) {
            return Outcome.failed("没有数据库，改不了 GM 名单。");
        }
        boolean was = store.gm().clearGmLevel(targetAccountId);
        GmManager.removeGm(targetAccountId);
        return Outcome.told(was
                ? "账号 " + targetAccountId + " 的 GM 资格已撤销。"
                : "账号 " + targetAccountId + " 本来就不是 GM。");
    }

    private static Outcome setLuckPoint(GameSession s, int value) {
        GameStore store = s.store();
        if (store == null) {
            return Outcome.failed("没有数据库，改不了幸运点。");
        }
        if (!store.character().saveLuckPoint(s.characNo(), Integer.toUnsignedLong(value))) {
            return Outcome.failed("角色 " + s.characNo() + " 在 charac_stat 里没有行。");
        }
        return Outcome.told("幸运点设为 " + Integer.toUnsignedLong(value)
                + "（ACT5 的掉落链上没有一处读它，只是落库）。");
    }

    private static Outcome enableHellDungeon(GameSession s) {
        return HellPartyService.HELL_DUNGEON_ENABLED
                ? Outcome.told("深渊派对在这一版本来就是开着的（官方那个全局恒 true），"
                        + "出不出深渊还是看破魔石、深渊值和等级门禁。")
                : Outcome.failed("深渊派对在这一版是关的。");
    }

    private static Outcome noSuchSystem(String what) {
        return Outcome.told("这一版没有" + what + "，这条命令用不了。");
    }

    private record Outcome(boolean done, String notice, String log, GmSubCommand replyAs) {

        static Outcome done(String log) {
            return new Outcome(true, null, log, null);
        }

        static Outcome told(String notice) {
            return new Outcome(true, notice, notice, null);
        }

        static Outcome failed(String why) {
            return new Outcome(false, why, why, null);
        }

        static Outcome failedQuietly(String log) {
            return new Outcome(false, null, log, null);
        }

        Outcome replyingAs(GmSubCommand other) {
            return new Outcome(done, notice, log, other);
        }
    }
}
