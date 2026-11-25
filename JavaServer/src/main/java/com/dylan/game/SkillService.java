package com.dylan.game;

import com.dylan.protocol.body.Noti19SkillInfoBody;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.CharacterScript;
import com.dylan.pvf.script.SkillScript;
import com.dylan.store.CharacterSkills;
import com.dylan.store.GameStore;
import com.dylan.store.blob.SkillSlots;

import java.util.ArrayList;
import java.util.List;

public final class SkillService {

    private final GameSession session;

    private int characNo;

    private CharacterSkills skills;

    private boolean dirty;

    public SkillService(GameSession session) {
        this.session = session;
    }

    private SkillService() {
        this.session = null;
    }

    public static SkillSlots initialSlots(GameScripts scripts, int job) {
        SkillSlots slots = SkillSlots.empty();
        if (scripts == null) {
            return slots;
        }
        var initial = scripts.initialSkills(job);
        if (initial.isEmpty()) {
            return slots;
        }
        var skills = scripts.skills();
        if (skills == null) {
            return slots;
        }

        var placed = new ArrayList<String>();
        for (CharacterScript.InitialSkill entry : initial) {
            var definition = skills.skill(job, entry.skillId());
            if (definition.isEmpty()) {
                continue;
            }
            SkillScript skill = definition.get();
            int level = Math.min(entry.level(), skill.maximumLevel());

            int slot = -1;
            String where = null;
            if (skill.isActive()) {
                slot = slots.learnInQuickSlot(entry.skillId(), level);
                where = "快捷栏";
            }
            if (slot < 0) {
                int group = skill.slotGroup();
                if (group == SkillScript.NO_GROUP) {
                    continue;
                }
                slot = slots.learn(group, entry.skillId(), level);
                where = "group " + group;
            }
            if (slot < 0) {
                continue;
            }
            placed.add("#" + entry.skillId() + "Lv" + level + "@" + slot + "(" + where + ")");
        }

        return slots;
    }

    public static List<com.dylan.protocol.body.Noti2PartyEntry.Skill> learnedSkills(
            SkillSlots slots) {
        java.util.Map<Integer, Integer> best = new java.util.LinkedHashMap<>();
        for (int slot = 0; slot < SkillSlots.SLOTS; slot++) {
            if (slots.isEmptyAt(slot)) {
                continue;
            }
            int skillId = slots.skillIndexAt(slot);
            int level = slots.levelAt(slot);
            if (skillId < 0 || skillId > 0xFF || level < 0 || level > 0xFF) {
                continue;
            }
            best.merge(skillId, level, Math::max);
        }
        List<com.dylan.protocol.body.Noti2PartyEntry.Skill> out = new ArrayList<>(best.size());
        best.forEach((skillId, level) ->
                out.add(new com.dylan.protocol.body.Noti2PartyEntry.Skill(skillId, level)));
        return out;
    }

    public static Noti19SkillInfoBody toSkillInfo(SkillSlots slots, int skillPoints) {
        List<Noti19SkillInfoBody.Skill> out = new ArrayList<>();
        for (int slot = 0; slot < SkillSlots.SLOTS; slot++) {
            if (slots.isEmptyAt(slot)) {
                continue;
            }
            out.add(new Noti19SkillInfoBody.Skill(
                    slot, slots.skillIndexAt(slot), slots.levelAt(slot)));
        }
        return new Noti19SkillInfoBody(skillPoints, out, List.of());
    }

    public void load(int characNo, int job) {
        this.characNo = characNo;
        this.dirty = false;
        GameStore store = session == null ? null : session.store();
        if (store == null) {
            skills = null;
            return;
        }
        CharacterSkills stored = store.skill().loadSkills(characNo).orElse(null);
        if (stored != null && stored.primary().learnedCount() > 0) {
            skills = stored;
            if (relayoutToCurrentPages(skills.primary(), lookup(job))) {
                dirty = true;
            }
            return;
        }
        SkillSlots fresh = initialSlots(session.scripts(), job);
        skills = new CharacterSkills(characNo, stored == null ? 0 : stored.remainSp(),
                fresh, stored == null ? SkillSlots.empty() : stored.secondary());
    }

    void attach(int characNo, CharacterSkills value) {
        this.characNo = characNo;
        this.skills = value;
        this.dirty = false;
    }

    public void clear() {
        characNo = 0;
        skills = null;
        dirty = false;
    }

    public void save() {
        GameStore store = session == null ? null : session.store();
        if (store == null || characNo == 0 || skills == null || !dirty) {
            return;
        }
        try {
            store.skill().saveSkills(skills);
            dirty = false;
        } catch (RuntimeException e) {
            session.fail("保存角色 " + characNo + " 的技能表失败", e);
        }
    }

    public SkillSlots slots() {
        return skills == null ? null : skills.primary();
    }

    public int remainSp() {
        return skills == null ? 0 : (int) skills.remainSp();
    }

    public Noti19SkillInfoBody skillInfo() {
        SkillSlots current = slots();
        return current == null
                ? Noti19SkillInfoBody.empty(0)
                : toSkillInfo(current, remainSp());
    }

    private static final long SP_MAX = Integer.MAX_VALUE;

    public long adjustSp(int delta) {
        if (skills == null) {
            return 0L;
        }
        long before = skills.remainSp();
        long after = Math.clamp(before + delta, 0L, SP_MAX);
        if (after == before) {
            return before;
        }
        skills = new CharacterSkills(skills.characNo(), after,
                skills.primary(), skills.secondary());
        dirty = true;
        return after;
    }

    public void gainSp(int amount) {
        if (skills == null || amount <= 0) {
            return;
        }
        long before = skills.remainSp();
        long after = Math.min(SP_MAX, before + amount);
        skills = new CharacterSkills(skills.characNo(), after,
                skills.primary(), skills.secondary());
        dirty = true;
    }

    public int resetSkills(int level) {
        return resetSkills(level, true);
    }

    public int resetSkills(int level, boolean withExpertJob) {
        if (skills == null) {
            return -1;
        }
        SkillSlots fresh = initialSlots(session == null ? null : session.scripts(), job());
        var total = SkillChanger.totalSp(store(), scripts(), clearedQuests(), level);
        long sp = total.total();
        remainSp();
        fresh.learnedCount();
        skills = new CharacterSkills(skills.characNo(), sp, fresh, skills.secondary());
        dirty = true;

        restoreGrowTypeSkills(fresh);
        if (withExpertJob) {
            restoreExpertJobSkills(fresh);
        }

        return (int) sp;
    }

    private com.dylan.store.QuestClearFlags clearedQuests() {
        if (session == null) {
            return null;
        }
        if (session.quests() == null) {
            session.fail("要按已完成任务重算 SP，但这条连接上没有任务数据 —— "
                    + "洗点/转职会少给任务发过的那部分 SP，且不可逆");
            return null;
        }
        return session.quests().cleared();
    }

    private GameStore store() {
        return session == null ? null : session.store();
    }

    private GameScripts scripts() {
        return session == null ? null : session.scripts();
    }

    private List<Integer> restoreGrowTypeSkills(SkillSlots target) {
        GameScripts scripts = session == null ? null : session.scripts();
        int job = job();
        CharacterScript script = scripts == null ? null
                : scripts.characterScript(job).orElse(null);
        if (script == null) {
            return List.of();
        }

        int packed = growTypeByte();
        int firstGrow = StatService.firstGrow(packed);
        int secondGrow = StatService.secondGrow(packed);

        var give = new ArrayList<>(script.growTypeSkills(firstGrow));
        if (secondGrow > 0) {
            give.addAll(script.awakeningSkillsForGrowType(firstGrow, secondGrow));
        }

        List<Integer> out = new ArrayList<>(give.size());
        for (CharacterScript.InitialSkill entry : give) {
            if (growtypeSkill(target, scripts, job, entry.skillId(), entry.level()) >= 0) {
                out.add(entry.skillId());
            }
        }
        return out;
    }

    private List<Integer> restoreExpertJobSkills(SkillSlots target) {
        ExpertJobService expertJob = session == null ? null : session.expertJob();
        com.dylan.pvf.script.ExpertJobScript script =
                expertJob == null ? null : expertJob.script();
        if (script == null) {
            return List.of();
        }
        GameScripts scripts = session.scripts();
        if (scripts == null) {
            return List.of();
        }
        int level = expertJob.level();
        int job = job();
        List<Integer> out = new ArrayList<>(script.skills().size());
        for (int[] entry : script.skills()) {
            if (growtypeSkill(target, scripts, job, entry[0], level) >= 0) {
                out.add(entry[0]);
            }
        }
        return out;
    }

    public record GrownSkill(int slot, int skillId, int level) {}

    public List<GrownSkill> changeGrowType(int firstGrow, int secondGrow, int level) {
        if (skills == null) {
            return List.of();
        }
        GameScripts scripts = session == null ? null : session.scripts();
        int job = job();
        CharacterScript script = scripts == null ? null
                : scripts.characterScript(job).orElse(null);
        if (script == null) {
            return List.of();
        }

        SkillSlots target;
        List<CharacterScript.InitialSkill> give;
        if (secondGrow == 0) {
            target = initialSlots(scripts, job);
            var total = SkillChanger.totalSp(store(), scripts(), clearedQuests(), level);
            long sp = total.total();
            remainSp();
            skills = new CharacterSkills(skills.characNo(), sp, target, skills.secondary());
            give = script.growTypeSkills(firstGrow);
        } else {
            target = slots();
            give = script.awakeningSkillsForGrowType(firstGrow, secondGrow);
        }

        List<GrownSkill> out = new ArrayList<>(give.size());
        for (CharacterScript.InitialSkill entry : give) {
            int slot = growtypeSkill(target, scripts, job, entry.skillId(), entry.level());
            if (slot > 0) {
                out.add(new GrownSkill(slot, entry.skillId(), entry.level()));
            }
        }
        dirty = true;
        return out;
    }

    public List<GrownSkill> giveExpertJobSkills(
            com.dylan.pvf.script.ExpertJobScript script) {
        if (skills == null) {
            return List.of();
        }
        GameScripts scripts = session == null ? null : session.scripts();
        if (scripts == null || script == null) {
            return List.of();
        }
        SkillSlots target = slots();
        int job = job();
        List<GrownSkill> out = new ArrayList<>(script.skills().size());
        for (int[] entry : script.skills()) {
            int slot = growtypeSkill(target, scripts, job, entry[0], entry[1]);
            if (slot > 0) {
                out.add(new GrownSkill(slot, entry[0], entry[1]));
            }
        }
        dirty = true;
        return out;
    }

    public List<Integer> growExpertJobSkills(
            com.dylan.pvf.script.ExpertJobScript script, int level) {
        if (skills == null || script == null) {
            return List.of();
        }
        GameScripts scripts = session == null ? null : session.scripts();
        if (scripts == null) {
            return List.of();
        }
        SkillSlots target = slots();
        int job = job();
        List<Integer> grown = new ArrayList<>(script.skills().size());
        for (int[] entry : script.skills()) {
            if (growtypeSkill(target, scripts, job, entry[0], level) > 0) {
                grown.add(entry[0]);
            }
        }
        if (!grown.isEmpty()) {
            dirty = true;
        }
        return grown;
    }

    public List<Integer> removeExpertJobSkills(
            com.dylan.pvf.script.ExpertJobScript script) {
        if (skills == null) {
            return List.of();
        }
        if (script == null) {
            return List.of();
        }
        SkillSlots target = slots();
        List<Integer> removed = new ArrayList<>(script.skills().size());
        for (int[] entry : script.skills()) {
            int skillId = entry[0];
            int at = slotOf(target, skillId);
            if (at < 0) {
                continue;
            }
            target.clear(at);
            removed.add(skillId);
        }
        if (!removed.isEmpty()) {
            dirty = true;
        }
        return removed;
    }

    static int growtypeSkill(SkillSlots slots, GameScripts scripts, int job,
                             int skillId, int level) {
        int at = slotOf(slots, skillId);
        if (level <= 0) {
            if (at < 0) {
                return -1;
            }
            slots.clear(at);
            return at;
        }

        var definition = scripts.skills() == null
                ? java.util.Optional.<SkillScript>empty()
                : scripts.skills().skill(job, skillId);
        if (definition.isEmpty()) {
            return -1;
        }
        SkillScript skill = definition.get();
        int capped = Math.min(level, skill.maximumLevel());

        if (at >= 0) {
            slots.set(at, skillId, capped);
            return at;
        }
        int slot = -1;
        if (skill.isActive()) {
            slot = slots.learnInQuickSlot(skillId, capped);
        }
        if (slot < 0) {
            int group = skill.slotGroup();
            if (group == SkillScript.NO_GROUP) {
                return -1;
            }
            slot = slots.learn(group, skillId, capped);
        }
        return slot;
    }

    static int slotOf(SkillSlots slots, int skillId) {
        for (int slot = 0; slot < SkillSlots.SLOTS; slot++) {
            if (!slots.isEmptyAt(slot) && slots.skillIndexAt(slot) == skillId) {
                return slot;
            }
        }
        return -1;
    }

    private int job() {
        var row = session == null ? null : session.character();
        return row == null ? 0 : row.job();
    }

    private int growTypeByte() {
        var row = session == null ? null : session.character();
        return row == null ? 0 : row.growType();
    }

    private void spendSp(int amount) {
        skills = new CharacterSkills(skills.characNo(),
                Math.max(0, skills.remainSp() - amount),
                skills.primary(), skills.secondary());
        dirty = true;
    }

    public record MoveOutcome(int errorCode, String reason) {

        public boolean ok() {
            return errorCode == 0;
        }

        static MoveOutcome reject(int code, String reason) {
            return new MoveOutcome(code, reason);
        }

        static MoveOutcome done(String what) {
            return new MoveOutcome(0, what);
        }
    }

    public MoveOutcome moveSlot(int slotA, int slotB) {
        SkillSlots current = slots();
        if (current == null) {
            return MoveOutcome.reject(ERROR_CANNOT_MOVE, "这条连接还没有技能表");
        }
        int job = session.character() == null ? 0 : session.character().job();
        MoveOutcome outcome = evaluateMove(current, job, lookup(job), slotA, slotB);
        if (outcome.ok()) {
            dirty = true;
        }
        return outcome;
    }

    private java.util.function.IntFunction<SkillScript> lookup(int job) {
        var scripts = session == null ? null : session.scripts();
        var skillList = scripts == null ? null : scripts.skills();
        if (skillList == null) {
            return id -> null;
        }
        return id -> skillList.skill(job, id).orElse(null);
    }

    static boolean relayoutToCurrentPages(SkillSlots slots,
                                          java.util.function.IntFunction<SkillScript> lookup) {
        record Entry(int slot, int skillIndex, int level, int group) {}
        List<Entry> movable = new ArrayList<>();
        boolean misplaced = false;
        for (int slot = 0; slot < SkillSlots.SLOTS; slot++) {
            if (slots.isEmptyAt(slot) || SkillSlots.slotGroup(slot) == SkillSlots.GROUP_QUICK) {
                continue;
            }
            SkillScript skill = lookup == null ? null : lookup.apply(slots.skillIndexAt(slot));
            if (skill == null || skill.slotGroup() == SkillScript.NO_GROUP) {
                continue;
            }
            movable.add(new Entry(slot, slots.skillIndexAt(slot), slots.levelAt(slot),
                    skill.slotGroup()));
            if (skill.slotGroup() != SkillSlots.slotGroup(slot)) {
                misplaced = true;
            }
        }
        if (!misplaced) {
            return false;
        }
        for (Entry entry : movable) {
            slots.clear(entry.slot());
        }
        for (Entry entry : movable) {
            if (slots.learn(entry.group(), entry.skillIndex(), entry.level()) < 0) {
                slots.set(entry.slot(), entry.skillIndex(), entry.level());
            }
        }
        return true;
    }

    static MoveOutcome evaluateMove(SkillSlots current, int job,
                                    java.util.function.IntFunction<SkillScript> lookup,
                                    int slotA, int slotB) {
        if (!checkMoveComboSkillSlot(job, slotA, slotB)) {
            return MoveOutcome.reject(ERROR_CANNOT_MOVE,
                    "职业 " + job + " 的连招技能不许进出快捷栏");
        }
        int groupA = SkillSlots.slotGroup(slotA);
        int groupB = SkillSlots.slotGroup(slotB);
        if (groupA == SkillSlots.GROUP_OUT_OF_RANGE || groupB == SkillSlots.GROUP_OUT_OF_RANGE) {
            return MoveOutcome.reject(ERROR_NO_SKILL,
                    "槽号越界：" + slotA + " / " + slotB + "（共 " + SkillSlots.SLOTS + " 槽）");
        }
        boolean emptyA = current.isEmptyAt(slotA);
        boolean emptyB = current.isEmptyAt(slotB);
        if (emptyA && emptyB) {
            return MoveOutcome.reject(ERROR_BOTH_EMPTY, "两个格子都是空的");
        }
        SkillScript skillA = skillAt(lookup, current, slotA);
        SkillScript skillB = skillAt(lookup, current, slotB);
        if (skillA == null && skillB == null) {
            return MoveOutcome.reject(ERROR_NO_SKILL, "两端的技能在 pvf 里都查不到");
        }
        if (groupA == SkillSlots.GROUP_LOCKED || groupB == SkillSlots.GROUP_LOCKED) {
            return MoveOutcome.reject(ERROR_CANNOT_MOVE,
                    "槽 " + (groupA == SkillSlots.GROUP_LOCKED ? slotA : slotB)
                            + " 在保留区 " + SkillSlots.LOCKED_START + ".."
                            + (SkillSlots.LOCKED_END - 1) + " 里");
        }
        if (!canSwap(skillA, skillB, groupA, groupB)) {
            return MoveOutcome.reject(ERROR_CANNOT_MOVE,
                    "段不匹配：槽 " + slotA + "(段 " + groupA + "，技能 "
                            + describeSkill(skillA) + ") ↔ 槽 " + slotB + "(段 " + groupB
                            + "，技能 " + describeSkill(skillB) + ")");
        }
        current.swap(slotA, slotB);
        return MoveOutcome.done("技能槽 " + slotA + " ←→ " + slotB);
    }

    private static boolean canSwap(SkillScript skillA, SkillScript skillB, int groupA, int groupB) {
        boolean quickA = groupA == SkillSlots.GROUP_QUICK;
        boolean quickB = groupB == SkillSlots.GROUP_QUICK;
        if (skillA != null && skillB != null) {
            if (quickA && quickB) {
                return true;
            }
            if (quickA && skillA.slotGroup() == groupB) {
                return skillB.isActive();
            }
            if (skillB.slotGroup() == groupA && quickB) {
                return skillA.isActive();
            }
            return skillB.slotGroup() == groupA && skillA.slotGroup() == groupB;
        }
        if (skillA != null) {
            return quickB ? skillA.isActive() : skillA.slotGroup() == groupB;
        }
        if (skillB != null) {
            return quickA ? skillB.isActive() : skillB.slotGroup() == groupA;
        }
        return false;
    }

    private static boolean checkMoveComboSkillSlot(int job, int slotA, int slotB) {
        return !(job == COMBO_JOB && (slotA <= 5 || slotB <= 5));
    }

    private static SkillScript skillAt(java.util.function.IntFunction<SkillScript> lookup,
                                       SkillSlots slots, int slot) {
        if (slots.isEmptyAt(slot) || lookup == null) {
            return null;
        }
        return lookup.apply(slots.skillIndexAt(slot));
    }

    private static String describeSkill(SkillScript skill) {
        if (skill == null) {
            return "无";
        }
        return "#" + skill.id() + (skill.isActive() ? " 主动" : " 被动") + " 段 " + skill.slotGroup();
    }

    private static final int COMBO_JOB = 9;

    public static final int ERROR_BOTH_EMPTY = 21;

    public static final int ERROR_NO_SKILL = 1;

    public static final int ERROR_CANNOT_MOVE = 19;

    public record BuyOutcome(int slot, int skillId, int newLevel, int spentSp,
                             int error, String reason) {

        public boolean ok() {
            return error >= 0;
        }

        static BuyOutcome fail(int skillId, int error, String reason) {
            return new BuyOutcome(-1, skillId, 0, 0, error, reason);
        }
    }

    public static final int BUY_ERROR_NO_SKILL = -1;
    public static final int BUY_ERROR_NOT_ENOUGH_SP = -2;
    public static final int BUY_ERROR_CANNOT_LEARN = -3;
    public static final int BUY_ERROR_BAD_DATA = -4;

    public static int wireError(int error) {
        return switch (error) {
            case -1 -> 21;
            case -2 -> 22;
            case -3 -> 18;
            case -4 -> 1;
            case -5 -> 91;
            case -6 -> 92;
            default -> 1;
        };
    }

    public BuyOutcome masterNewSkill(int skillId) {
        SkillSlots current = slots();
        if (current == null) {
            return BuyOutcome.fail(skillId, BUY_ERROR_BAD_DATA, "这条连接还没有技能表");
        }
        var row = session == null ? null : session.character();
        int job = row == null ? 0 : row.job();
        int packed = row == null ? 0 : Math.max(0, row.growType());
        int firstGrow = StatService.firstGrow(packed);
        int secondGrow = StatService.secondGrow(packed);
        var lookup = lookup(job);

        SkillScript skill = lookup.apply(skillId);
        if (skill == null) {
            return BuyOutcome.fail(skillId, BUY_ERROR_NO_SKILL,
                    "职业 " + job + " 的技能表里没有 #" + skillId);
        }
        int group = skill.slotGroup();
        if (group == SkillScript.NO_GROUP) {
            return BuyOutcome.fail(skillId, BUY_ERROR_BAD_DATA,
                    "技能 #" + skillId + " 的 [skill class] 不合法，定不了它该进哪一段");
        }

        int slot = skillSlotNo(current, skillId, group, skill.isActive());
        int curLevel = slot < 0 ? 0 : current.levelAt(slot);
        int target = curLevel + 1;

        int overSkill = session == null || session.premium() == null
                ? 0 : session.premium().overSkillLevel();
        int effectiveLevel = (session == null ? 1 : session.level()) + overSkill;

        if (!skill.canLearn(effectiveLevel, firstGrow, secondGrow, target)) {
            return BuyOutcome.fail(skillId, BUY_ERROR_CANNOT_LEARN,
                    "技能 #" + skillId + " 点到 " + target + " 级不满足 can_learn："
                            + "角色等级 " + effectiveLevel
                            + (overSkill > 0 ? "（含越级 " + overSkill + "）" : "")
                            + "，一转分支 " + firstGrow + " / 觉醒 " + secondGrow
                            + " 的上限 " + skill.maxLevelFor(firstGrow, secondGrow)
                            + "，门槛 " + skill.requiredLevelFor(target));
        }

        int needSp = skill.needSpForLevel(curLevel, target);
        if (needSp < 0) {
            return BuyOutcome.fail(skillId, BUY_ERROR_BAD_DATA,
                    "技能 #" + skillId + " 的 [purchase cost] 是空的（" + curLevel
                            + " → " + target + " 级算不出价格）");
        }
        if (remainSp() < needSp) {
            return BuyOutcome.fail(skillId, BUY_ERROR_NOT_ENOUGH_SP,
                    "技能 #" + skillId + " 第 " + target + " 级要 " + needSp
                            + " SP，只剩 " + remainSp());
        }
        for (SkillScript.PreRequisite pre : skill.preRequiredSkills()) {
            if (!checkSkillMastered(current, lookup, pre.skillId(), pre.level())) {
                return BuyOutcome.fail(skillId, BUY_ERROR_CANNOT_LEARN,
                        "技能 #" + skillId + " 的前置 #" + pre.skillId()
                                + " 还没点到 " + pre.level() + " 级");
            }
        }

        spendSp(needSp);
        if (slot < 0) {
            slot = skill.isActive() ? current.learnInQuickSlot(skillId, target) : -1;
            if (slot < 0) {
                slot = current.learn(group, skillId, target);
            }
            if (slot < 0) {
                gainSp(needSp);
                return BuyOutcome.fail(skillId, BUY_ERROR_BAD_DATA,
                        "技能 #" + skillId + " 该进的第 " + group + " 页满了");
            }
        } else {
            current.set(slot, skillId, target);
        }
        dirty = true;
        return new BuyOutcome(slot, skillId, target, needSp, 0,
                "#" + skillId + " → " + target + " 级");
    }

    public record SetLevelOutcome(int slot, int level, String reason) {

        public boolean ok() {
            return reason == null;
        }

        static SetLevelOutcome reject(String reason) {
            return new SetLevelOutcome(-1, 0, reason);
        }
    }

    public SetLevelOutcome setSkillLevel(int skillId, int level) {
        SkillSlots current = slots();
        if (current == null) {
            return SetLevelOutcome.reject("这条连接还没有技能表");
        }
        var row = session == null ? null : session.character();
        int job = row == null ? 0 : row.job();
        int packed = row == null ? 0 : Math.max(0, row.growType());
        int firstGrow = StatService.firstGrow(packed);
        int secondGrow = StatService.secondGrow(packed);
        SkillScript skill = lookup(job).apply(skillId);
        if (skill == null) {
            return SetLevelOutcome.reject("职业 " + job + " 的技能表里没有 #" + skillId);
        }
        int group = skill.slotGroup();
        if (group == SkillScript.NO_GROUP) {
            return SetLevelOutcome.reject(
                    "技能 #" + skillId + " 的 [skill class] 不合法，定不了它该进哪一段");
        }
        int max = skill.maxLevelFor(firstGrow, secondGrow);
        int target = Math.clamp(level, 0, Math.max(0, max));

        int slot = skillSlotNo(current, skillId, group, skill.isActive());
        if (target == 0) {
            if (slot < 0) {
                return new SetLevelOutcome(-1, 0, null);
            }
            current.clear(slot);
            dirty = true;
            return new SetLevelOutcome(slot, 0, null);
        }
        if (slot < 0) {
            slot = skill.isActive() ? current.learnInQuickSlot(skillId, target) : -1;
            if (slot < 0) {
                slot = current.learn(group, skillId, target);
            }
            if (slot < 0) {
                return SetLevelOutcome.reject(
                        "技能 #" + skillId + " 该进的第 " + group + " 页满了");
            }
        } else {
            current.set(slot, skillId, target);
        }
        dirty = true;
        return new SetLevelOutcome(slot, target, null);
    }

    private static int skillSlotNo(SkillSlots slots, int skillId, int group, boolean active) {
        if (active) {
            for (int i = SkillSlots.QUICK_SLOT_START; i < SkillSlots.QUICK_SLOT_END; i++) {
                if (slots.skillIndexAt(i) == skillId) {
                    return i;
                }
            }
            for (int i = SkillSlots.QUICK_SLOT_2ND_START; i < SkillSlots.QUICK_SLOT_2ND_END; i++) {
                if (slots.skillIndexAt(i) == skillId) {
                    return i;
                }
            }
        }
        for (int i = SkillSlots.QUICK_SLOT_END; i < SkillSlots.LOCKED_START; i++) {
            if (SkillSlots.slotGroup(i) == group && slots.skillIndexAt(i) == skillId) {
                return i;
            }
        }
        return -1;
    }

    private static boolean checkSkillMastered(SkillSlots slots,
                                              java.util.function.IntFunction<SkillScript> lookup,
                                              int skillId, int level) {
        SkillScript pre = lookup.apply(skillId);
        if (pre == null) {
            return false;
        }
        int group = pre.slotGroup();
        if (group == SkillScript.NO_GROUP) {
            return false;
        }
        int slot = skillSlotNo(slots, skillId, group, pre.isActive());
        return slot >= 0 && slots.skillIndexAt(slot) == skillId && slots.levelAt(slot) >= level;
    }

    public boolean isValidUsingSkill(int job, int firstGrow, int secondGrow, int skillId) {
        SkillScript skill = lookup(job).apply(skillId);
        if (skill == null) {
            return false;
        }
        return matchesFirstGrowType(skill, firstGrow)
                || skill.secondTypeMaxLevel(firstGrow, secondGrow) > 0;
    }

    static boolean matchesFirstGrowType(SkillScript skill, int growType) {
        var fit = skill.fitnessGrowTypes();
        for (int i = 0; i < FIT_GROW_TYPE_SLOTS; i++) {
            int value = i < fit.size() ? fit.get(i) : 0;
            if (value == growType) {
                return true;
            }
        }
        return false;
    }

    private static final int FIT_GROW_TYPE_SLOTS = 6;

    public static String describe(SkillSlots slots) {
        var sb = new StringBuilder();
        for (int slot = 0; slot < SkillSlots.SLOTS; slot++) {
            if (slots.isEmptyAt(slot)) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(slot).append('=').append('#').append(slots.skillIndexAt(slot))
                    .append("Lv").append(slots.levelAt(slot));
        }
        return sb.toString();
    }
}
