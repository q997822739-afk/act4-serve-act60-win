package com.dylan.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.dylan.protocol.body.Cmd102CompoundAvatarRequest;
import com.dylan.protocol.body.Cmd102ReplyBody;
import com.dylan.protocol.body.ItemListType;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.CompoundAvatarTable;
import com.dylan.pvf.script.ItemScript;
import com.dylan.store.AvatarItemRow;
import com.dylan.store.blob.InvenItem;
import com.dylan.store.blob.ItemArray;

public final class CompoundAvatarService {

    private final GameSession session;
    private final Random random;

    public CompoundAvatarService(GameSession session) {
        this(session, new Random());
    }

    CompoundAvatarService(GameSession session, Random random) {
        this.session = session;
        this.random = random;
    }

    private static final int RATE_SCALE = 10000;

    private static final int BASE_GRADE = 1;

    private static final int MAX_RARITY = 1;

    private static final int MAX_ABILITY_NO = 99;

    private static final int MIN_REMAIN_SECONDS = 5;

    public record Outcome(int error, List<Cmd102ReplyBody.Change> changes, int slot, int itemId,
                          long remainSeconds, int abilityNo, String reason) {

        static Outcome rejected(int error, String reason) {
            return new Outcome(error, List.of(), 0, 0, 0, 0, reason);
        }

        public boolean ok() {
            return error == 0;
        }
    }

    private record Info(AvatarItemRow rowA, AvatarItemRow rowB,
                        ItemScript scriptA, ItemScript scriptB,
                        int materialItemId, int job, int grade, boolean bad) {}

    public Outcome compound(Cmd102CompoundAvatarRequest request) {
        GameScripts scripts = session.scripts();
        CompoundAvatarTable table = scripts == null ? null : scripts.compoundAvatarTable();
        if (table == null) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_FAILED,
                    CompoundAvatarTable.LIST_PATH + " 没读出来");
        }
        ItemArray bag = session.inventory().containerOf(ItemListType.LIST_MAIN);
        if (bag == null) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_FAILED, "主背包还没装载");
        }

        Info info = makeInfo(request, table, bag);
        CompoundAvatarTable.Grade row = table.at(info.job(), info.grade());

        Outcome gate = checkCondition(request, info, row, bag);
        if (gate != null) {
            return gate;
        }
        return procCore(request, info, row, bag);
    }

    private Info makeInfo(Cmd102CompoundAvatarRequest request, CompoundAvatarTable table,
                          ItemArray bag) {
        InventoryService inventory = session.inventory();
        GameScripts scripts = session.scripts();

        AvatarItemRow rowA = inventory.avatarBagRowAt(request.slotA());
        AvatarItemRow rowB = inventory.avatarBagRowAt(request.slotB());
        ItemScript scriptA = scripts.item(request.itemIdA()).orElse(null);
        ItemScript scriptB = scripts.item(request.itemIdB()).orElse(null);

        int materialItemId = 0;
        if (request.materialSlot() >= 0 && request.materialSlot() < bag.slots()) {
            InvenItem material = bag.get(request.materialSlot());
            if (!material.isEmpty()) {
                materialItemId = (int) material.itemId();
            }
        }

        boolean bad = scriptA == null || scriptB == null
                || rowA == null || rowB == null
                || rowA.itemId() != request.itemIdA()
                || rowB.itemId() != request.itemIdB();

        int job = CompoundAvatarTable.JOB_NONE;
        int grade = 0;
        if (!bad) {
            for (int j = 0; j < CompoundAvatarTable.JOBS; j++) {
                if (scriptA.usableByJob(j) && scriptB.usableByJob(j)) {
                    job = j;
                    break;
                }
            }
            if (job != CompoundAvatarTable.JOB_NONE) {
                grade = table.gradeUsing(job, materialItemId);
            }
        }
        return new Info(rowA, rowB, scriptA, scriptB, materialItemId, job, grade, bad);
    }

    private Outcome checkCondition(Cmd102CompoundAvatarRequest request, Info info,
                                   CompoundAvatarTable.Grade row, ItemArray bag) {
        if (request.eventType() != Cmd102CompoundAvatarRequest.EVENT_NONE) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_NOT_ENOUGH_MATERIAL,
                    "活动档 " + request.eventType() + " 在这一版没有数据（[material_upper] / "
                            + "[material_lower] / [material_ancient] 都不存在）");
        }
        if (request.abilityNo() > MAX_ABILITY_NO) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_NOT_AVATAR,
                    "属性下标 " + request.abilityNo() + " 超过 " + MAX_ABILITY_NO);
        }
        if (info.bad()) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_NOT_AVATAR,
                    "两件装扮对不上：槽 " + request.slotA() + " / " + request.slotB()
                            + " 上不是客户端报的 " + request.itemIdA() + " / " + request.itemIdB());
        }
        if (info.job() == CompoundAvatarTable.JOB_NONE) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_BAD_PAIR, "两件装扮没有共同职业");
        }
        if (info.rowA().uiId() == AvatarItemRow.NOT_PERSISTED
                || info.rowB().uiId() == AvatarItemRow.NOT_PERSISTED) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_FAILED, "有一件还没落库");
        }
        if (!info.scriptA().isAvatar() || !info.scriptB().isAvatar()) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_NOT_AVATAR, "有一件不是时装");
        }
        int part = info.scriptA().equipmentSlot();
        if (part != info.scriptB().equipmentSlot()) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_BAD_PAIR, "两件不是同一个部位");
        }
        if (row == null) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_BAD_PAIR,
                    "材料 " + info.materialItemId() + " 不是职业 " + info.job() + " 的合成材料");
        }
        if (info.scriptA().rarity() > MAX_RARITY || info.scriptB().rarity() > MAX_RARITY) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_BAD_PAIR, "稀有度太高，合不了");
        }
        if (info.scriptA().grade() != info.scriptB().grade()) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_BAD_PAIR, "两件的等级不一样");
        }

        int itemGrade = info.scriptA().grade();
        if (!request.isRare()) {
            Outcome bad = checkTarget(request, info, row, part, itemGrade);
            if (bad != null) {
                return bad;
            }
        }
        if (itemGrade < info.grade()) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_BAD_PAIR,
                    "装扮等级 " + itemGrade + " 低于这一档要的 " + info.grade());
        }
        if (itemGrade > row.upperGrade()) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_BAD_PAIR,
                    "装扮等级 " + itemGrade + " 高过这一档的上限 " + row.upperGrade());
        }
        var charac = session.character();
        int characJob = charac == null ? -1 : charac.job();
        if (!info.scriptA().usableByJob(characJob) || !info.scriptB().usableByJob(characJob)) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_BAD_PAIR,
                    "职业 " + characJob + " 用不了这两件");
        }

        if (row.material().isEmpty()) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_NOT_ENOUGH_MATERIAL,
                    "这一档的 [material] 是空的");
        }
        for (CompoundAvatarTable.Need need : row.material()) {
            if (InventoryService.countOf(bag, need.itemId()) < need.count()) {
                return Outcome.rejected(Cmd102ReplyBody.ERROR_NOT_ENOUGH_MATERIAL,
                        "材料 " + need.itemId() + " 不够 " + need.count() + " 个");
            }
        }

        Instant now = Instant.now();
        boolean permanentA = info.rowA().permanent();
        boolean permanentB = info.rowB().permanent();
        if (permanentA != permanentB) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_PERIOD_MISMATCH,
                    "一件永久一件有期限");
        }
        if (!permanentA) {
            long remainA = info.rowA().remainingSeconds(now);
            long remainB = info.rowB().remainingSeconds(now);
            if (remainA <= MIN_REMAIN_SECONDS || remainB <= MIN_REMAIN_SECONDS) {
                return Outcome.rejected(Cmd102ReplyBody.ERROR_ALMOST_EXPIRED,
                        "有一件只剩 " + Math.min(remainA, remainB) + " 秒");
            }
        }
        return null;
    }

    private Outcome checkTarget(Cmd102CompoundAvatarRequest request, Info info,
                                CompoundAvatarTable.Grade row, int part, int itemGrade) {
        if (itemGrade != row.grade() && itemGrade != row.upperGrade()) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_FAILED,
                    "装扮等级 " + itemGrade + " 既不是 [grade] 也不是 [upper grade]");
        }
        for (CompoundAvatarTable.Entry e : row.normalPool(part)) {
            if (e.itemId() == request.targetItemId()) {
                return null;
            }
        }
        return Outcome.rejected(Cmd102ReplyBody.ERROR_FAILED,
                "点名要的 " + request.targetItemId() + " 不在部位 " + part + " 的池子里");
    }

    private Outcome procCore(Cmd102CompoundAvatarRequest request, Info info,
                             CompoundAvatarTable.Grade row, ItemArray bag) {
        int part = info.scriptA().equipmentSlot();
        int itemGrade = info.scriptA().grade();
        int itemId;

        if (!request.isRare()) {
            itemId = request.targetItemId();
        } else {
            if (row.rareTotal(part) == 0 || row.rarePool(part).isEmpty()) {
                return Outcome.rejected(Cmd102ReplyBody.ERROR_FAILED,
                        "部位 " + part + " 的稀有池是空的");
            }
            int rate = rareRateFor(row, part, itemGrade);
            itemId = random.nextInt(RATE_SCALE + 1) < rate
                    ? pick(row.rarePool(part), row.rareTotal(part))
                    : 0;
        }
        if (itemId == 0) {
            itemId = pick(row.normalPool(part), row.normalTotal(part));
        }
        if (itemId == 0) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_FAILED, "普通池也抽不出东西");
        }
        ItemScript result = session.scripts().item(itemId).orElse(null);
        if (result == null) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_FAILED,
                    "抽到的 " + itemId + " 在 pvf 里查不到");
        }

        Instant now = Instant.now();
        long remainSeconds = info.rowA().remainingSeconds(now) + info.rowB().remainingSeconds(now);
        Instant expireAt = remainSeconds <= 0 ? null : now.plusSeconds(remainSeconds);

        List<Cmd102ReplyBody.Change> changes = new ArrayList<>();
        for (CompoundAvatarTable.Need need : row.material()) {
            for (InventoryService.Taken taken : InventoryService.consume(bag, need.itemId(),
                    need.count())) {
                changes.add(new Cmd102ReplyBody.Change(ItemListType.LIST_MAIN, taken.slot(),
                        taken.count()));
            }
        }
        InventoryService inventory = session.inventory();
        inventory.markItemsDirty();
        inventory.consumeAvatarRow(info.rowA());
        changes.add(new Cmd102ReplyBody.Change(ItemListType.LIST_AVATAR, request.slotA(), 1));
        inventory.consumeAvatarRow(info.rowB());
        changes.add(new Cmd102ReplyBody.Change(ItemListType.LIST_AVATAR, request.slotB(), 1));

        String ipgAgencyNo = generateIpgNo(now);
        int slot = inventory.grantCompoundAvatar(itemId, expireAt, request.abilityNo(),
                ipgAgencyNo);
        if (slot < 0) {
            return Outcome.rejected(Cmd102ReplyBody.ERROR_NOT_AVATAR, "装扮栏放不下合成出来的那件");
        }
        return new Outcome(0, changes, slot, itemId, remainSeconds, request.abilityNo(), null);
    }

    static int rareRateFor(CompoundAvatarTable.Grade row, int part, int itemGrade) {
        int base = row.rareRate(part);
        return itemGrade == BASE_GRADE ? base : base + row.upperRareRate(part);
    }

    private int pick(List<CompoundAvatarTable.Entry> pool, int total) {
        if (pool.isEmpty() || total <= 0) {
            return 0;
        }
        int r = random.nextInt(total + 1);
        for (CompoundAvatarTable.Entry e : pool) {
            if (e.cumulative() > r) {
                return e.itemId();
            }
        }
        return 0;
    }

    private String generateIpgNo(Instant now) {
        var date = now.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        return String.format("C%02d%02d%02d%02d%010d", 0, date.getYear() % 2000,
                date.getMonthValue(), date.getDayOfMonth(), session.characNo());
    }
}
