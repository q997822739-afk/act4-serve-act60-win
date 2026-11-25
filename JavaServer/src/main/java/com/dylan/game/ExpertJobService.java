package com.dylan.game;

import java.util.ArrayList;
import java.util.List;

import com.dylan.protocol.body.Cmd258GiveupExpertJobReplyBody;
import com.dylan.protocol.body.ItemListType;
import com.dylan.protocol.body.Noti19SkillInfoBody;
import com.dylan.protocol.body.Noti221ExpertJobInfoBody;
import com.dylan.protocol.body.QuestReplyBody;
import com.dylan.pvf.script.ExpertJobEtcScript;
import com.dylan.pvf.script.ExpertJobScript;
import com.dylan.pvf.script.ItemScript;
import com.dylan.store.CharacExpertJobRow;
import com.dylan.store.GameStore;
import com.dylan.store.RecipeBitmap;

public final class ExpertJobService {

    private final GameSession session;

    private int characNo;

    private int type = ExpertJobScript.TYPE_NONE;

    private int exp;

    private CharacExpertJobRow row;

    private boolean dirty;

    public ExpertJobService(GameSession session) {
        this.session = session;
    }

    public void load(int characNo) {
        this.characNo = characNo;
        this.dirty = false;
        GameStore store = session == null ? null : session.store();
        if (store == null) {
            this.type = ExpertJobScript.TYPE_NONE;
            this.exp = 0;
            this.row = CharacExpertJobRow.fresh(characNo);
            return;
        }
        this.type = store.expertJob().loadExpertJobType(characNo);
        this.exp = store.expertJob().loadExpertJobExp(characNo);
        this.row = store.expertJob().loadExpertJob(characNo);
    }

    /**
     * 选角时补救：交过 {@code [reward type] `[expert job]`} 任务、但库里还没有副职业（A4 在
     * GameScripts 补上附魔师之前交的 2702，任务已完成、副职业没转成）。按任务奖励参数补转一次。
     */
    public void repairFromClearedQuests() {
        if (type != ExpertJobScript.TYPE_NONE || session == null || session.scripts() == null
                || session.quests() == null) {
            return;
        }
        var cleared = session.quests().cleared();
        for (var quest : session.scripts().quests().values()) {
            if (quest.rewardType() == com.dylan.pvf.script.QuestRewardType.EXPERT_JOB
                    && cleared.isCleared(quest.id()) && script(quest.rewardParam()) != null) {
                changeTo(quest.rewardParam());
                return;
            }
        }
    }

    private ExpertJobScript script(int t) {
        return session.scripts().expertJob(t);
    }

    public void save() {
        if (!dirty) {
            return;
        }
        GameStore store = session == null ? null : session.store();
        if (store == null || row == null) {
            return;
        }
        store.expertJob().saveExpertJobType(characNo, type);
        store.expertJob().saveExpertJobExp(characNo, exp);
        store.expertJob().saveExpertJob(row);
        dirty = false;
    }

    void attach(int characNo, int type, int exp) {
        this.characNo = characNo;
        this.type = type;
        this.exp = exp;
        this.row = CharacExpertJobRow.fresh(characNo);
        this.dirty = false;
    }

    public void clear() {
        characNo = 0;
        type = ExpertJobScript.TYPE_NONE;
        exp = 0;
        row = null;
        dirty = false;
    }

    public int type() {
        return type;
    }

    public int exp() {
        return exp;
    }

    public int level() {
        ExpertJobScript script = script();
        return script == null ? 0 : script.level(exp);
    }

    public ExpertJobScript script() {
        if (type == ExpertJobScript.TYPE_NONE || session == null || session.scripts() == null) {
            return null;
        }
        return session.scripts().expertJob(type);
    }

    public int giveupCount() {
        return row == null ? 0 : row.giveupCount();
    }

    public RecipeBitmap recipes() {
        return row == null ? null : row.recipes();
    }

    public List<QuestReplyBody.GrantedSkill> changeTo(int newType) {
        if (session == null) {
            return List.of();
        }
        if (newType <= ExpertJobScript.TYPE_NONE) {
            return List.of();
        }
        ExpertJobScript script = session.scripts() == null ? null
                : session.scripts().expertJob(newType);
        if (script == null) {
            return List.of();
        }

        type = newType;
        RecipeBitmap fresh = RecipeBitmap.empty();
        int giveupCount = row == null ? 0 : row.giveupCount();
        row = new CharacExpertJobRow(characNo, giveupCount, 0, 0, fresh);
        exp = 0;
        dirty = true;

        List<SkillService.GrownSkill> grown = session.skills().giveExpertJobSkills(script);
        session.skills().save();
        save();

        List<QuestReplyBody.GrantedSkill> out = new ArrayList<>(grown.size());
        for (SkillService.GrownSkill skill : grown) {
            out.add(new QuestReplyBody.GrantedSkill(skill.slot(), skill.skillId(), skill.level()));
        }
        return out;
    }

    public int increaseExp(int newExp) {
        if (session == null || !session.hasCharacter()) {
            return exp;
        }
        ExpertJobScript script = script();
        if (script == null) {
            return exp;
        }

        int total = script.maxExp();
        int target = total > 0 ? Math.min(newExp, total) : newExp;

        int curLevel = script.level(exp);
        int newLevel = script.level(target);
        int characLevel = session.level();
        boolean canLevelUp = levelUpAllowed(script, target, curLevel, newLevel, characLevel);

        int capThisLevel = script.expertnessExpAt(curLevel - 1);
        if (!canLevelUp && capThisLevel >= 0 && target > capThisLevel) {
            target = capThisLevel;
        }

        if (!canLevelUp && exp == target) {
            return exp;
        }
        exp = target;
        dirty = true;
        save();

        if (canLevelUp) {
            int level = script.level(exp);
            CharacterService.resendOwnCharacterInfo(session, "副职业升到 " + level + " 级");
            CharacterService.refreshDetail(session, "副职业升级");
            session.skills().growExpertJobSkills(script, level);
            session.skills().save();
            var info = session.skills().skillInfo();
            session.sendNow(Noti19SkillInfoBody.PACKET_ID, info.toBytes());
            QuestService.sendAcceptableQuests(session, "副职业升级后刷新");
        } else {
            CharacterService.resendOwnCharacterInfoToSelf(session, "副职业熟练度变了");
        }
        return exp;
    }

    private boolean levelUpAllowed(ExpertJobScript script, int target,
                                   int curLevel, int newLevel, int characLevel) {
        ExpertJobEtcScript etc = session.scripts() == null ? null : session.scripts().expertJobEtc();
        if (etc == null) {
            return false;
        }
        if (etc.isEmpty()) {
            // A4 expertjob.etc 只有 [magic enchant]，没有 [expertjob level limit]：熟练度到档就升
            return newLevel > curLevel;
        }
        if (curLevel == newLevel) {
            return script.isBoundaryExp(target)
                    && etc.isExpertJobLimitLevel(characLevel)
                    && curLevel != MAX_EXPERT_JOB_LEVEL;
        }
        int required = etc.requiredCharacLevel(newLevel);
        return required != ExpertJobEtcScript.NO_LIMIT && required <= characLevel;
    }

    public static final int MAX_EXPERT_JOB_LEVEL = 11;

    public Cmd258GiveupExpertJobReplyBody giveup() {
        if (session == null || !session.hasCharacter() || row == null) {
            return Cmd258GiveupExpertJobReplyBody.rejected(3);
        }
        if (type == ExpertJobScript.TYPE_NONE) {
            return Cmd258GiveupExpertJobReplyBody.rejected(
                    Cmd258GiveupExpertJobReplyBody.ERROR_NO_EXPERT_JOB);
        }
        ExpertJobScript script = script();
        if (script == null) {
            return Cmd258GiveupExpertJobReplyBody.rejected(
                    Cmd258GiveupExpertJobReplyBody.ERROR_NO_EXPERT_JOB);
        }
        int giveupCount = row.giveupCount();
        int cost = script.giveupCost(giveupCount);
        if (cost == ExpertJobScript.NO_GIVEUP_COST) {
            return Cmd258GiveupExpertJobReplyBody.rejected(
                    Cmd258GiveupExpertJobReplyBody.ERROR_GIVEUP_LIMIT);
        }
        long money = session.inventory().money();
        if (money < cost) {
            return Cmd258GiveupExpertJobReplyBody.rejected(
                    Cmd258GiveupExpertJobReplyBody.ERROR_NOT_ENOUGH_MONEY);
        }
        Integer uncleared = QuestService.firstUnclearedOf(session, script.clearQuests());
        if (uncleared != null) {
            return Cmd258GiveupExpertJobReplyBody.rejected(
                    Cmd258GiveupExpertJobReplyBody.ERROR_NO_EXPERT_JOB);
        }

        QuestService.resetClearedQuests(session, script.clearQuests(), "放弃副职业 " + type);

        session.inventory().setMoney(money - cost);
        session.inventory().sendItemUpdate(ItemListType.LIST_MAIN,
                "放弃副职业扣 " + cost + " 金币", BagLayout.SLOT_GOLD);
        long moneyAfter = session.inventory().money();

        session.skills().removeExpertJobSkills(script);
        session.skills().save();
        var info = session.skills().skillInfo();
        session.sendNow(Noti19SkillInfoBody.PACKET_ID, info.toBytes());

        int before = type;

        type = ExpertJobScript.TYPE_NONE;
        exp = GIVEUP_EXP;

        QuestService.sendAcceptableQuests(session, "放弃副职业后刷新");

        int nextCount = Math.min(giveupCount + 1, script.maxGiveupCount());
        row = new CharacterExpertJobReset(characNo, nextCount).row();
        dirty = true;
        save();

        CharacterService.resendOwnCharacterInfo(session, "放弃副职业 " + before);
        CharacterService.refreshDetail(session, "放弃副职业");
        sendInfo("放弃副职业");

        return Cmd258GiveupExpertJobReplyBody.given(moneyAfter, nextCount);
    }

    public static final int GIVEUP_EXP = -1;

    private record CharacterExpertJobReset(int characNo, int giveupCount) {
        CharacExpertJobRow row() {
            return new CharacExpertJobRow(characNo, giveupCount, 0, 0, RecipeBitmap.empty());
        }
    }

    public static final int LEARNED = 0;

    public static final int NOT_A_RECIPE = 1;

    public static final int NOT_LEARNED = 2;

    public int onUseStackableItem(ItemScript item) {
        if (item == null || !item.isExpertJobRecipe()) {
            return NOT_A_RECIPE;
        }
        ExpertJobScript script = script();
        if (script == null) {
            return NOT_LEARNED;
        }
        if (row == null) {
            return NOT_LEARNED;
        }

        int index = script.recipeIndex(item.id());
        if (index == ExpertJobScript.NO_RECIPE) {
            return NOT_LEARNED;
        }
        ExpertJobScript.RecipeEntry entry = script.recipe(index);
        int myLevel = script.level(exp);
        if (!script.canLearn(entry, myLevel)) {
            return NOT_LEARNED;
        }
        if (!row.recipes().set(index)) {
            return NOT_LEARNED;
        }
        dirty = true;
        save();
        sendInfo("学会配方 " + item.id());
        return LEARNED;
    }

    public void gmClearType() {
        type = ExpertJobScript.TYPE_NONE;
        dirty = true;
        save();
    }

    public boolean gmLearnRecipe(int index) {
        if (row == null || !row.recipes().set(index)) {
            return false;
        }
        dirty = true;
        save();
        sendInfo("GM 学配方序号 " + index);
        return true;
    }

    public int gmLearnAllRecipes() {
        ExpertJobScript script = script();
        if (script == null || row == null) {
            return 0;
        }
        int learned = 0;
        for (ExpertJobScript.RecipeEntry entry : script.recipes()) {
            if (row.recipes().set(entry.index())) {
                learned++;
            }
        }
        if (learned > 0) {
            dirty = true;
            save();
        }
        sendInfo("GM 学全部配方");
        return learned;
    }

    public void sendInfo(String why) {
        if (session == null || !session.hasCharacter()) {
            return;
        }
        byte[] body = body();
        if (body == null) {
            return;
        }
        session.sendNow(Noti221ExpertJobInfoBody.PACKET_ID, body);
    }

    private byte[] body() {
        if (row == null) {
            return null;
        }
        int giveup = Math.clamp(row.giveupCount(), 0, 0xFF);
        if (type == ExpertJobScript.TYPE_NONE) {
            return Noti221ExpertJobInfoBody.none(giveup);
        }
        if (type == ExpertJobScript.TYPE_DISJOINTER) {
            return Noti221ExpertJobInfoBody.disjointer(giveup, row.infoEx(), row.info());
        }
        ExpertJobScript script = script();
        List<Integer> recipeItemIds = new ArrayList<>();
        if (script != null) {
            for (int index : row.recipes().indices()) {
                ExpertJobScript.RecipeEntry entry = script.recipe(index);
                if (entry != null && entry.recipeItemId() != 0) {
                    recipeItemIds.add(entry.recipeItemId());
                }
            }
        }
        return Noti221ExpertJobInfoBody.withRecipes(giveup, type, recipeItemIds);
    }
}
