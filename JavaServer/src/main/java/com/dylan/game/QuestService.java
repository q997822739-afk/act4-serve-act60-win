package com.dylan.game;

import com.dylan.protocol.body.Cmd33AcceptQuestRequest;
import com.dylan.protocol.body.Cmd34GiveUpQuestRequest;
import com.dylan.protocol.body.Cmd35SetQuestTriggerRequest;
import com.dylan.protocol.body.Cmd36FinishQuestRequest;
import com.dylan.protocol.body.Noti21QuestListBody;
import com.dylan.protocol.body.QuestError;
import com.dylan.protocol.body.QuestReplyBody;
import com.dylan.protocol.body.QuestTrigger;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.QuestParameterScript;
import com.dylan.pvf.script.QuestRewardType;
import com.dylan.pvf.script.QuestScript;
import com.dylan.pvf.script.QuestType;
import com.dylan.store.CharacterInventory;
import com.dylan.store.CharacterQuests;
import com.dylan.store.CharacterRow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class QuestService {
    private QuestService() {
    }

    public static void load(GameSession s, int characNo) {
        if (s.store() == null || characNo < 0) {
            s.quests(CharacterQuests.empty(Math.max(characNo, 0)));
            return;
        }
        CharacterQuests quests = s.store().quest().loadQuests(characNo).orElse(null);
        if (quests == null) {
            s.fail("角色 " + characNo + " 在 new_charac_quest 表里没有行，任务整个不可用");
            return;
        }
        s.quests(quests);
    }

    private static void persist(GameSession s, String why) {
        if (s.store() == null || s.quests() == null || s.quests().characNo() <= 0) {
            return;
        }
        s.store().quest().saveQuests(s.quests());
    }

    public static void sendAcceptableQuests(GameSession s, String why) {
        int level = s.level();
        List<Integer> ids = com.dylan.net.ServerConfig.QUEST_LIST
                ? acceptableQuestIds(s) : List.of();
        byte[] body = new Noti21QuestListBody(clampLevel(level), ids).toBytes();
        s.send(Noti21QuestListBody.PACKET_ID, body);
    }

    public static List<Integer> acceptableQuestIds(GameSession s) {
        GameScripts scripts = s.scripts();
        CharacterQuests quests = s.quests();
        CharacterRow row = s.character();
        if (scripts == null || quests == null || row == null) {
            return List.of();
        }
        Set<Integer> finished = new HashSet<>(quests.cleared().clearedIds());
        Set<Integer> running = new HashSet<>(quests.runningIds());
        int job = row.job();
        int firstGrow = StatService.firstGrow(row.growType());
        int secondGrow = StatService.secondGrow(row.growType());
        int pvpGrade = 0;
        List<Integer> ids = new ArrayList<>();
        for (var quest : scripts.quests().values()) {
            if (quest.isAvailableTo(s.level(), job, firstGrow, secondGrow, pvpGrade,
                    finished, running)) {
                ids.add(quest.id());
            }
        }
        ids.sort(null);
        return ids;
    }

    public static List<com.dylan.protocol.body.Cmd4ReplyBody.QuestSlot> activeQuestSlots(
            CharacterQuests quests) {
        if (quests == null) {
            return List.of();
        }
        int limit = com.dylan.protocol.body.Cmd4ReplyBody.QUEST_SLOTS;
        var slots = new ArrayList<com.dylan.protocol.body.Cmd4ReplyBody.QuestSlot>();
        for (var e : quests.running().entrySet()) {
            if (slots.size() >= limit) {
                break;
            }
            slots.add(new com.dylan.protocol.body.Cmd4ReplyBody.QuestSlot(
                    e.getKey(), e.getValue()));
        }
        return List.copyOf(slots);
    }

    public static List<com.dylan.protocol.body.Cmd4ReplyBody.CompletedQuestSet>
            completionSets(GameSession s) {
        GameScripts scripts = s.scripts();
        CharacterQuests quests = s.quests();
        if (scripts == null || quests == null) {
            return List.of();
        }
        var mapping = scripts.questMapping();
        if (mapping == null || mapping.size() == 0) {
            return List.of();
        }
        var cleared = quests.cleared().clearedIds();
        if (cleared.isEmpty()) {
            return List.of();
        }
        var sets = new ArrayList<com.dylan.protocol.body.Cmd4ReplyBody.CompletedQuestSet>();
        for (var e : mapping.completionSets(cleared).entrySet()) {
            sets.add(new com.dylan.protocol.body.Cmd4ReplyBody.CompletedQuestSet(
                    e.getKey(), e.getValue()));
        }
        return List.copyOf(sets);
    }

    public static void accept(GameSession s, byte[] plain) {
        var req = Cmd33AcceptQuestRequest.parse(plain);
        CharacterQuests quests = s.quests();
        if (quests == null) {
            return;
        }
        QuestScript quest = questOf(s, req.questId());
        if (quest == null) {
            return;
        }

        int error = checkAccept(s, quests, quest);
        if (error != QuestError.NONE) {
            reject(s, Cmd33AcceptQuestRequest.PACKET_ID, "ACCEPT_QUEST", req.questId(), error);
            return;
        }

        int slot = quests.firstFreeSlot();
        QuestTrigger trigger = quest.initialTrigger();
        List<QuestReplyBody.GivenItem> given = grantDependGiveItems(s, quest);

        quests.cleared().reset(req.questId());
        quests.occupy(slot, req.questId(), trigger.packed());
        persist(s, "接取任务 " + req.questId());

        s.reply(Cmd33AcceptQuestRequest.PACKET_ID,
                QuestReplyBody.accepted(req.questId(), trigger.packed(), given));

        if (!given.isEmpty()) {
            CharacterService.resendItemLists(s, "接任务发的道具入账");
        }
    }

    private static int checkAccept(GameSession s, CharacterQuests quests, QuestScript quest) {
        int questId = quest.id();
        if (quests.isRunning(questId)) {
            return QuestError.ALREADY;
        }
        if (quests.cleared().isCleared(questId) && !quest.isRepeatable()) {
            return QuestError.ALREADY;
        }
        if (!acceptableQuestIds(s).contains(questId)) {
            s.hack().count(HackType.QUEST_NOT_IN_LIST, 0, questId,
                    "接了不在可接列表里的任务 " + questId);
            return QuestError.FAIL;
        }
        if (!dependGiveItemsFit(s, quest)) {
            return QuestError.INVENTORY_FULL;
        }
        if (quests.firstFreeSlot() < 0) {
            return QuestError.SLOT_FULL;
        }
        return QuestError.NONE;
    }

    public static void giveUp(GameSession s, byte[] plain) {
        var req = Cmd34GiveUpQuestRequest.parse(plain);
        CharacterQuests quests = s.quests();
        if (quests == null) {
            return;
        }
        QuestScript quest = questOf(s, req.questId());
        if (quest == null) {
            if (com.dylan.net.ServerConfig.A4) {
                // 依据：A4 官方实抓（sweep_town CMD 34，包体全 0 = 任务 0 在 pvf 里不存在）回 REPLY 34 `00 13`；
                // 0x13 = 19 = QuestError.NOT_RUNNING，和「任务没在跑」是同一个错误码（客户端读 u8 错误码弹文本）。
                // 原版 A5 这里静默丢，客户端「放弃任务」按钮会一直等。只补回包，任务数据没动。
                reject(s, Cmd34GiveUpQuestRequest.PACKET_ID, "GIVEUP_QUEST", req.questId(),
                        QuestError.NOT_RUNNING);
            }
            return;
        }
        int slot = quests.slotOf(req.questId());
        if (slot < 0) {
            reject(s, Cmd34GiveUpQuestRequest.PACKET_ID, "GIVEUP_QUEST", req.questId(),
                    QuestError.NOT_RUNNING);
            return;
        }
        if (quest.cantGiveUp() && quests.triggerAt(slot) == quest.initialTrigger().packed()) {
            reject(s, Cmd34GiveUpQuestRequest.PACKET_ID, "GIVEUP_QUEST", req.questId(),
                    QuestError.ALREADY);
            return;
        }

        quests.release(slot);
        persist(s, "放弃任务 " + req.questId());
        s.reply(Cmd34GiveUpQuestRequest.PACKET_ID, QuestReplyBody.givenUp(req.questId()));

        takeBackDependGiveItems(s, quest);

        sendAcceptableQuests(s, "放弃任务后刷新可接列表");
    }

    public static void setTrigger(GameSession s, byte[] plain) {
        var req = Cmd35SetQuestTriggerRequest.parse(plain);
        CharacterQuests quests = s.quests();
        if (quests == null) {
            return;
        }
        QuestScript quest = questOf(s, req.questId());
        if (quest == null) {
            return;
        }
        int slot = quests.slotOf(req.questId());
        if (slot < 0) {
            reject(s, Cmd35SetQuestTriggerRequest.PACKET_ID, "SET_QUEST_TRIGGER",
                    req.questId(), QuestError.NOT_CLEARABLE);
            return;
        }

        QuestTrigger before = new QuestTrigger((int) quests.triggerAt(slot));
        QuestTrigger after;
        if (req.triggerType() == Cmd35SetQuestTriggerRequest.TRIGGER_TYPE_WHOLE_INCREMENT) {
            after = before.incrementWhole();
        } else {
            if (!checkQuestCondition(s, quest)) {
                reject(s, Cmd35SetQuestTriggerRequest.PACKET_ID, "SET_QUEST_TRIGGER",
                        req.questId(), QuestError.NOT_CLEARABLE);
                return;
            }
            after = before.packed() > 0 ? subTriggerState(before, req.triggerType()) : before;
        }

        quests.trigger(slot, after.packed());
        if (after.packed() != before.packed()) {
            persist(s, "任务 " + req.questId() + " 的进度");
        }
        s.reply(Cmd35SetQuestTriggerRequest.PACKET_ID,
                QuestReplyBody.triggerSet(req.questId(), after.packed()));
    }

    static QuestTrigger subTriggerState(QuestTrigger current, int triggerType) {
        if (triggerType == Cmd35SetQuestTriggerRequest.TRIGGER_TYPE_WHOLE_DECREMENT) {
            return current.decrementWhole();
        }
        QuestTrigger result = current;
        int[] bits = Cmd35SetQuestTriggerRequest.CHANNEL_BITS;
        for (int channel = 0; channel < bits.length; channel++) {
            if ((triggerType & bits[channel]) != 0) {
                result = result.decrement(channel);
            }
        }
        return result;
    }

    public static boolean checkQuestCondition(GameSession s, QuestScript quest) {
        QuestType type = quest.type();
        if (type == null) {
            return false;
        }
        return switch (type) {
            case CLEAR_MAP -> matchesCurrentDungeon(s, quest);
            case PVP_RANK -> false;
            case SEEKING, CONDITION_UNDER_CLEAR, HUNT_MONSTER, MEET_NPC, HUNT_ENEMY,
                 AMPLIFY_ITEM, SEEK_N_MEET_NPC -> true;
            default -> {
                yield false;
            }
        };
    }

    private static boolean matchesCurrentDungeon(GameSession s, QuestScript quest) {
        var target = quest.clearMapTarget();
        if (target.isEmpty()) {
            return false;
        }
        int want = target.getAsInt();
        int cleared = s.lastClearedMapIndex();
        if (cleared <= 0) {
            return false;
        }
        if (want != cleared) {
            return false;
        }
        return true;
    }

    public static void finish(GameSession s, byte[] plain) {
        var req = Cmd36FinishQuestRequest.parse(plain);
        if (!req.valid()) {
            return;
        }
        boolean gmFree = s.gmQuestFlag();
        if (gmFree) {
            s.gmQuestFlag(false);
        }
        CharacterQuests quests = s.quests();
        if (quests == null) {
            return;
        }
        QuestScript quest = questOf(s, req.questId());
        if (quest == null) {
            return;
        }

        int rate = checkFinishRate(s);
        if (rate != QuestError.NONE) {
            reject(s, Cmd36FinishQuestRequest.PACKET_ID, "FINISH_QUEST", req.questId(), rate);
            return;
        }
        int slot = quests.slotOf(req.questId());
        if (slot < 0 || (!gmFree && quests.triggerAt(slot) != 0)) {
            reject(s, Cmd36FinishQuestRequest.PACKET_ID, "FINISH_QUEST", req.questId(),
                    QuestError.NOT_CLEARABLE);
            return;
        }
        if (quests.cleared().isCleared(req.questId()) && !quest.isRepeatable()) {
            reject(s, Cmd36FinishQuestRequest.PACKET_ID, "FINISH_QUEST", req.questId(),
                    QuestError.NOT_CLEARABLE);
            return;
        }
        if (req.multiplier() != Cmd36FinishQuestRequest.SINGLE && !quest.isRepeatable()) {
            reject(s, Cmd36FinishQuestRequest.PACKET_ID, "FINISH_QUEST", req.questId(),
                    QuestError.NOT_CLEARABLE);
            return;
        }
        if (quest.rewardType() == QuestRewardType.EXPERT_JOB && s.level() < quest.minLevel()) {
            reject(s, Cmd36FinishQuestRequest.PACKET_ID, "FINISH_QUEST", req.questId(),
                    QuestError.NOT_CLEARABLE);
            return;
        }

        settle(s, quests, quest, req, slot, gmFree);
    }

    private static void settle(GameSession s, CharacterQuests quests, QuestScript quest,
                               Cmd36FinishQuestRequest req, int slot, boolean gmFree) {
        int multiplier = req.multiplier();
        List<QuestScript.Objective> needed = quest.consumedItems();
        QuestScript.Reward chosen = pickSelectableReward(s, quest, req);

        CharacterInventory inv = loadInventory(s);
        if (inv == null) {
            return;
        }
        InventoryService bag = s.inventory();

        if (!gmFree) {
            for (var objective : needed) {
                long have = bag.countMaterial(objective.itemId());
                long want = (long) objective.count() * multiplier;
                if (have < want) {
                    reject(s, Cmd36FinishQuestRequest.PACKET_ID, "FINISH_QUEST", quest.id(),
                            QuestError.INVENTORY_FULL);
                    return;
                }
            }
        }

        QuestScript.RewardTable rewardTable = rewardTableFor(s, quest);
        List<InventoryService.Stack> incoming =
                rewardStacks(s, quest, rewardTable, chosen, multiplier);
        List<InventoryService.Stack> handedIn = gmFree ? List.of() : needed.stream()
                .map(o -> new InventoryService.Stack(o.itemId(), o.count() * multiplier))
                .toList();
        if (!InventoryService.canFitAll(inv.inventory(), handedIn, incoming,
                id -> s.scripts() == null ? null : s.scripts().item(id).orElse(null))) {
            reject(s, Cmd36FinishQuestRequest.PACKET_ID, "FINISH_QUEST", quest.id(),
                    QuestError.SLOT_FULL);
            return;
        }

        List<QuestReplyBody.Consumed> consumed = new ArrayList<>();
        for (var objective : needed) {
            var taken = bag.consumeMaterial(inv.inventory(),
                    objective.itemId(), objective.count() * multiplier);
            for (var t : taken) {
                consumed.add(QuestReplyBody.Consumed.fromBag(t.slot(), t.count()));
            }
        }
        List<QuestReplyBody.Reward> granted = new ArrayList<>();
        for (var stack : incoming) {
            var script = s.scripts() == null ? null
                    : s.scripts().item(stack.itemId()).orElse(null);
            if (script == null) {
                continue;
            }
            var landing = InventoryService.addItem(s, s.store(), s.characNo(), inv, script,
                    stack.count());
            if (!landing.ok()) {
                continue;
            }
            granted.add(new QuestReplyBody.Reward(landing.slot(), stack.itemId(), stack.count()));
        }

        long gold = rewardGold(questParameter(s), quest, rewardTable, s.level()) * multiplier;
        long moneyBefore = bag.money();
        long moneyAfter = Math.min(0xFFFF_FFFFL, moneyBefore + gold);
        long exp = rewardExp(s, quest) * multiplier;

        granted.size();
        addGoldReward(quest, rewardTable, granted, moneyAfter - moneyBefore);

        bag.setMoney(moneyAfter);
        saveInventory(s, inv, moneyAfter);

        quests.release(slot);
        if (!quest.isRepeatable()) {
            quests.cleared().setCleared(quest.id());
        }
        persist(s, "完成任务 " + quest.id());

        if (exp > 0) {
            int levelBefore = s.level();
            ExperienceService.grant(s, (int) Math.min(exp, Integer.MAX_VALUE));
            if (s.level() > levelBefore) {
                s.stamina().setFull("交任务升级（官方 quest_reward）");
                if (!s.inDungeon()) {
                    CharacterService.sendDetailToSelf(s, "交任务升到 " + s.level() + " 级");
                }
            }
        }

        List<QuestReplyBody.GrantedSkill> grownSkills = applySpecialReward(s, quest);

        s.reply(Cmd36FinishQuestRequest.PACKET_ID, QuestReplyBody.finished(
                quest.id(), quest.type(), exp, consumed, quest.rewardType(), granted,
                quest.rewardParam(), grownSkills));

        if (!granted.isEmpty() || !consumed.isEmpty() || gold > 0) {
            CharacterService.resendItemLists(s, "交任务的收支入账");
        }
        sendAcceptableQuests(s, "交任务后刷新");
    }

    private static String materialName(int itemId) {
        return switch (itemId) {
            case InventoryService.MATERIAL_MONEY -> "金币";
            case InventoryService.MATERIAL_WIN_POINT -> "决斗胜点";
            default -> "物品 " + itemId;
        };
    }

    private static final int MAX_FIRST_GROW = 5;

    private static final int MAX_SECOND_GROW = 2;

    private static List<QuestReplyBody.GrantedSkill> applySpecialReward(GameSession s,
                                                                       QuestScript quest) {
        QuestRewardType kind = quest.rewardType();
        if (kind == QuestRewardType.EXPERT_JOB) {
            return applyExpertJobReward(s, quest);
        }
        if (kind != QuestRewardType.GROW_TYPE && kind != QuestRewardType.AWAKENING) {
            return List.of();
        }

        CharacterRow row = s.character();
        if (row == null) {
            return List.of();
        }
        var store = s.store();
        if (store == null) {
            return List.of();
        }

        int param = quest.rewardParam();
        int before = row.growType();
        int firstGrow = kind == QuestRewardType.GROW_TYPE ? param : StatService.firstGrow(before);
        int secondGrow = kind == QuestRewardType.GROW_TYPE
                ? StatService.secondGrow(before) : param;
        if (firstGrow < 0 || firstGrow > MAX_FIRST_GROW
                || secondGrow < 0 || secondGrow > MAX_SECOND_GROW) {
            return List.of();
        }

        int packed = StatService.packGrowType(firstGrow, secondGrow);
        if (!store.character().saveGrowType(s.characNo(), packed)) {
            return List.of();
        }
        s.character(new CharacterRow(row.characNo(), row.characName(), row.job(),
                row.lev(), packed, row.sex(), row.guildId()));

        var grown = s.skills().changeGrowType(firstGrow, secondGrow, row.lev());
        s.skills().save();
        if (secondGrow == 0) {
            var info = s.skills().skillInfo();
            s.sendNow(com.dylan.protocol.body.Noti19SkillInfoBody.PACKET_ID, info.toBytes());
        }

        s.stats(CharacterService.combatStats(s, s.character()));
        CharacterService.resendOwnCharacterInfo(s, "转职到一转 " + firstGrow + " 觉醒 " + secondGrow);
        CharacterService.refreshDetail(s, "转职");

        List<QuestReplyBody.GrantedSkill> out = new ArrayList<>(grown.size());
        for (SkillService.GrownSkill skill : grown) {
            out.add(new QuestReplyBody.GrantedSkill(skill.slot(), skill.skillId(), skill.level()));
        }
        return out;
    }

    private static List<QuestReplyBody.GrantedSkill> applyExpertJobReward(GameSession s,
                                                                          QuestScript quest) {
        if (s.character() == null) {
            return List.of();
        }
        var granted = s.expertJob().changeTo(quest.rewardParam());
        if (granted.isEmpty() && s.expertJob().type() != quest.rewardParam()) {
            return List.of();
        }
        var info = s.skills().skillInfo();
        s.sendNow(com.dylan.protocol.body.Noti19SkillInfoBody.PACKET_ID, info.toBytes());
        CharacterService.resendOwnCharacterInfo(s, "转副职业 " + quest.rewardParam());
        CharacterService.refreshDetail(s, "转副职业");
        s.expertJob().sendInfo("转副职业");
        return granted;
    }

    static Integer firstUnclearedOf(GameSession s, List<Integer> questIds) {
        CharacterQuests quests = s.quests();
        if (quests == null) {
            return questIds.isEmpty() ? null : questIds.get(0);
        }
        for (int questId : questIds) {
            if (!quests.cleared().isCleared(questId)) {
                return questId;
            }
        }
        return null;
    }

    static void resetClearedQuests(GameSession s, List<Integer> questIds, String why) {
        CharacterQuests quests = s.quests();
        if (quests == null || questIds.isEmpty()) {
            return;
        }
        for (int questId : questIds) {
            quests.cleared().reset(questId);
        }
        persist(s, why);
    }

    public static void resetDailyQuest(GameSession s) {
        GameScripts scripts = s.scripts();
        CharacterQuests quests = s.quests();
        if (scripts == null || quests == null) {
            return;
        }
        List<Integer> reset = new ArrayList<>();
        for (int questId : scripts.dailyQuestIds()) {
            if (quests.cleared().isCleared(questId)) {
                quests.cleared().reset(questId);
                reset.add(questId);
            }
        }
        if (reset.isEmpty()) {
            return;
        }
        persist(s, "日常任务跨日重置");
    }

    static int checkFinishRate(GameSession s) {
        long now = System.currentTimeMillis();
        if (now - s.lastQuestFinishAt() > QuestError.FINISH_RATE_WINDOW_MILLIS) {
            s.resetQuestFinishWindow(now);
            return QuestError.NONE;
        }
        int count = s.countQuestFinish();
        if (count > QuestError.FINISH_RATE_ALLOWANCE) {
            s.hack().count(HackType.QUEST_FINISH_TOO_FAST, 0, count + 1,
                    "一秒内第 " + (count + 1) + " 次交任务（官方允许 "
                            + QuestError.FINISH_RATE_ALLOWANCE + " 次）");
            return QuestError.FAIL;
        }
        return QuestError.NONE;
    }

    private static QuestScript.Reward pickSelectableReward(GameSession s, QuestScript quest,
                                                           Cmd36FinishQuestRequest req) {
        if (!quest.hasRewardSelection() || !req.hasSelection()) {
            return null;
        }
        CharacterRow row = s.character();
        int job = row == null ? -1 : row.job();
        int grow = row == null ? -1 : StatService.firstGrow(row.growType());
        int visible = 0;
        var rewards = quest.selectableRewards();
        for (QuestScript.Reward reward : rewards) {
            if (!reward.appliesTo(job, grow)) {
                continue;
            }
            if (visible == req.rewardSelection()) {
                return reward;
            }
            visible++;
        }
        return null;
    }

    private static List<InventoryService.Stack> rewardStacks(GameSession s, QuestScript quest,
                                                             QuestScript.RewardTable table,
                                                             QuestScript.Reward chosen,
                                                             int multiplier) {
        if (!quest.rewardsAreItems()) {
            return List.of();
        }
        CharacterRow row = s.character();
        int job = row == null ? -1 : row.job();
        int grow = row == null ? -1 : StatService.firstGrow(row.growType());
        List<InventoryService.Stack> out = new ArrayList<>();
        for (QuestScript.Reward reward : table.items()) {
            if (reward.appliesTo(job, grow)) {
                out.add(new InventoryService.Stack(reward.itemId(),
                        reward.count() * multiplier));
            }
        }
        if (chosen != null) {
            out.add(new InventoryService.Stack(chosen.itemId(), chosen.count() * multiplier));
        }
        return out;
    }

    static void addGoldReward(QuestScript quest, QuestScript.RewardTable table,
                              List<QuestReplyBody.Reward> granted, long gained) {
        if (!quest.rewardsAreItems() || !table.grantsGold()) {
            return;
        }
        int at = table.goldRewardIndex();
        int index = at < 0 ? granted.size() : Math.min(at, granted.size());
        granted.add(index, new QuestReplyBody.Reward(
                MONEY_SLOT, QuestScript.GOLD_ITEM_ID, (int) Math.min(gained, 0x7FFF_FFFFL)));
    }

    private static final int MONEY_SLOT = 0;

    public static long rewardExp(GameSession s, QuestScript quest) {
        return rewardExp(questParameter(s), quest, s.level());
    }

    public static long rewardExp(QuestParameterScript params, QuestScript quest, int level) {
        if (quest.isRepeatable()) {
            return 0;
        }
        boolean ignoreQuestLevel = quest.ignoreQuestLevel4Exp();
        int penalty = ignoreQuestLevel ? 100 : params.levelPenalty(level - quest.minLevel());
        int weight = params.rewardWeight(quest.difficulty());
        int base = params.rewardExp(ignoreQuestLevel || quest.event() ? level : quest.minLevel());
        return (long) penalty * ((long) weight * base / 100) / 100;
    }

    public static long rewardGold(GameSession s, QuestScript quest) {
        return rewardGold(questParameter(s), quest, rewardTableFor(s, quest), s.level());
    }

    public static long rewardGold(QuestParameterScript params, QuestScript quest,
                                  QuestScript.RewardTable table, int characterLevel) {
        if (!quest.rewardsAreItems() || !table.grantsGold()) {
            return 0;
        }
        boolean ignoreQuestLevel = quest.ignoreQuestLevel4Exp();
        int penalty = ignoreQuestLevel ? 100
                : params.levelPenalty(characterLevel - quest.minLevel());
        int level = ignoreQuestLevel ? characterLevel : quest.minLevel();
        return (long) quest.goldMultiple() * ((long) penalty * params.rewardGold(level) / 100)
                / 100;
    }

    static QuestScript.RewardTable rewardTableFor(GameSession s, QuestScript quest) {
        var quests = s.quests();
        if (quests == null) {
            return quest.userRewardItems(id -> false);
        }
        return quest.userRewardItems(quests.cleared()::isCleared);
    }

    private static QuestParameterScript questParameter(GameSession s) {
        if (s.scripts() == null || s.scripts().questParameter() == null) {
            return QuestParameterScript.empty();
        }
        return s.scripts().questParameter();
    }

    private static boolean dependGiveItemsFit(GameSession s, QuestScript quest) {
        if (!quest.hasDependGiveItems()) {
            return true;
        }
        CharacterInventory inv = s.store() == null || s.characNo() < 0 ? null
                : s.inventory().inventory();
        if (inv == null) {
            return true;
        }
        List<InventoryService.Stack> incoming = quest.dependGiveItems().stream()
                .map(r -> new InventoryService.Stack(r.itemId(), r.count()))
                .toList();
        return InventoryService.canFitAll(inv.inventory(), List.of(), incoming,
                id -> s.scripts() == null ? null : s.scripts().item(id).orElse(null));
    }

    private static List<QuestReplyBody.GivenItem> grantDependGiveItems(GameSession s,
                                                                       QuestScript quest) {
        if (!quest.hasDependGiveItems()) {
            return List.of();
        }
        CharacterInventory inv = loadInventory(s);
        if (inv == null) {
            return List.of();
        }
        List<QuestReplyBody.GivenItem> given = new ArrayList<>();
        for (var item : quest.dependGiveItems()) {
            var script = s.scripts() == null ? null
                    : s.scripts().item(item.itemId()).orElse(null);
            if (script == null) {
                continue;
            }
            var landing = InventoryService.addItem(s, s.store(), s.characNo(), inv, script,
                    item.count());
            if (!landing.ok()) {
                continue;
            }
            given.add(new QuestReplyBody.GivenItem(landing.slot(), item.itemId(), item.count()));
        }
        if (!given.isEmpty()) {
            saveInventory(s, inv, inv.money());
        }
        return given;
    }

    private static void takeBackDependGiveItems(GameSession s, QuestScript quest) {
        if (!quest.hasDependGiveItems()) {
            return;
        }
        CharacterInventory inv = loadInventory(s);
        if (inv == null) {
            return;
        }
        int touched = 0;
        for (var item : quest.dependGiveItems()) {
            long held = InventoryService.countOf(inv.inventory(), item.itemId());
            int removing = (int) Math.min(item.count(), held);
            if (removing <= 0) {
                continue;
            }
            touched += InventoryService.consume(inv.inventory(), item.itemId(), removing).size();
        }
        if (touched == 0) {
            return;
        }
        saveInventory(s, inv, inv.money());
        CharacterService.resendItemLists(s, "放弃任务收回道具");
    }

    private static CharacterInventory loadInventory(GameSession s) {
        if (s.store() == null || s.characNo() < 0) {
            return null;
        }
        CharacterInventory inv = s.inventory().inventory();
        if (inv == null) {
            s.fail("角色 " + s.characNo() + " 在 inventory 表里没有行，任务的收支做不了");
        }
        return inv;
    }

    private static void saveInventory(GameSession s, CharacterInventory inv, long money) {
        if (s.store() == null) {
            return;
        }
        s.store().inventory().saveInventory(new CharacterInventory(inv.characNo(), money,
                inv.coin(), inv.payCoin(), inv.eventCoin(),
                inv.equipslot(), inv.inventory(), inv.creature()));
    }

    public record GmClearResult(List<Integer> cleared, List<Integer> already,
                                List<Integer> skipped, Integer unknown) {
        public boolean aborted() {
            return unknown != null;
        }
    }

    public static GmClearResult gmClearQuests(GameSession s, List<Integer> ids) {
        CharacterQuests quests = s.quests();
        CharacterRow row = s.character();
        List<Integer> cleared = new ArrayList<>();
        List<Integer> already = new ArrayList<>();
        List<Integer> skipped = new ArrayList<>();
        Integer unknown = null;
        int job = row == null ? 0 : row.job();
        int grow = row == null ? 0 : StatService.firstGrow(row.growType());
        int awakening = row == null ? 0 : StatService.secondGrow(row.growType());
        for (int id : ids) {
            QuestScript quest = questOf(s, id);
            if (quest == null) {
                unknown = id;
                break;
            }
            if (!quest.exposedByNpc()
                    || !quest.checkPossible(s.level(), job, grow, awakening, 0)) {
                skipped.add(id);
                continue;
            }
            if (quests.cleared().isCleared(id)) {
                already.add(id);
                continue;
            }
            quests.cleared().setCleared(id);
            cleared.add(id);
        }
        if (!cleared.isEmpty()) {
            persist(s, "GM 标记 " + cleared.size() + " 个任务为已完成");
            sendAcceptableQuests(s, "GM 标记任务完成后刷新");
        }
        return new GmClearResult(cleared, already, skipped, unknown);
    }

    public static List<Integer> gmClearableQuestIds(GameSession s) {
        GameScripts scripts = s.scripts();
        CharacterRow row = s.character();
        if (scripts == null || row == null) {
            return List.of();
        }
        int job = row.job();
        int grow = StatService.firstGrow(row.growType());
        int awakening = StatService.secondGrow(row.growType());
        List<Integer> ids = new ArrayList<>();
        for (var quest : scripts.quests().values()) {
            if (quest.exposedByNpc()
                    && quest.checkPossible(s.level(), job, grow, awakening, 0)) {
                ids.add(quest.id());
            }
        }
        ids.sort(null);
        return ids;
    }

    public record GmQuestDoneResult(List<Integer> readied, List<Integer> notRunning) {}

    public static GmQuestDoneResult gmQuestDone(GameSession s, List<Integer> ids) {
        CharacterQuests quests = s.quests();
        List<Integer> wanted = ids.isEmpty() ? quests.runningIds() : ids;
        List<Integer> readied = new ArrayList<>();
        List<Integer> notRunning = new ArrayList<>();
        s.gmQuestFlag(true);
        for (int id : wanted) {
            int slot = quests.slotOf(id);
            if (slot < 0) {
                notRunning.add(id);
                continue;
            }
            quests.trigger(slot, 0);
            readied.add(id);
            s.replyNow(Cmd35SetQuestTriggerRequest.PACKET_ID,
                    QuestReplyBody.triggerSet(id, 0));
        }
        if (!readied.isEmpty()) {
            persist(s, "GM 把 " + readied.size() + " 个任务的进度清零");
        }
        return new GmQuestDoneResult(readied, notRunning);
    }

    private static QuestScript questOf(GameSession s, int questId) {
        return s.scripts() == null ? null : s.scripts().quest(questId).orElse(null);
    }

    private static void reject(GameSession s, int packetId, String name, int questId, int error) {
        s.reply(packetId, QuestReplyBody.rejected(error));
    }

    private static String describe(QuestScript quest) {
        return "任务 " + quest.id() + "（" + quest.path() + "，" + quest.type() + "）";
    }

    private static int clampLevel(int level) {
        return Math.max(0, Math.min(0xFF, level));
    }

    public record RescueQuestMap(int questId, int mapIndex) {
        public static final RescueQuestMap NONE =
                new RescueQuestMap(0, DungeonRun.NO_QUEST_STAGE_MAP);

        public boolean isNone() {
            return mapIndex <= 0;
        }
    }

    public static RescueQuestMap rescueQuestMap(
            CharacterQuests quests,
            java.util.function.IntFunction<java.util.Optional<QuestScript>> lookup,
            int dungeonIndex, int difficulty,
            java.util.random.RandomGenerator random) {
        if (quests == null) {
            return RescueQuestMap.NONE;
        }
        for (int slot = 0; slot < CharacterQuests.SLOTS; slot++) {
            int questId = quests.questIdAt(slot);
            if (questId <= 0 || quests.triggerAt(slot) == 0) {
                continue;
            }
            QuestScript quest = lookup.apply(questId).orElse(null);
            if (quest == null) {
                return RescueQuestMap.NONE;
            }
            int map = quest.appearMap().resolve(dungeonIndex, difficulty, random);
            if (map > 0) {
                return new RescueQuestMap(questId, map);
            }
        }
        return RescueQuestMap.NONE;
    }
}
