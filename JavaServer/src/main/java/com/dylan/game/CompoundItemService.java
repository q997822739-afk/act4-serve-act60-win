package com.dylan.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.dylan.protocol.body.Cmd27CompoundItemRequest;
import com.dylan.protocol.body.Cmd27ReplyBody;
import com.dylan.protocol.body.ItemListType;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.ExpertJobScript;
import com.dylan.pvf.script.ItemScript;
import com.dylan.pvf.script.RecipeInfo;
import com.dylan.store.RecipeBitmap;
import com.dylan.store.blob.InvenItem;
import com.dylan.store.blob.ItemArray;

public final class CompoundItemService {

    public static final int PREMIUM_TYPE_RECIPE = 7;

    public static final int RARITY_NO_UPGRADE = 5;

    public static final int ERROR_TOO_MANY_EQUIP_MATERIAL = 1;

    public static final int ERROR_AMBIGUOUS_EQUIP_MATERIAL = 18;

    public static final int ERROR_UNIDENTIFIED_AMPLIFY = 19;

    public static final int ERROR_ENDURANCE_NOT_FULL = 216;

    public record EquipCondition(int upgrade, boolean hasAmplify, int amplifyType,
                                 int amplifyValue, boolean sealed, int resealCount,
                                 InvenItem template) {

        public static final EquipCondition NONE =
                new EquipCondition(0, false, 0, 0, false, 0, InvenItem.EMPTY);

        public boolean present() {
            return !template.isEmpty();
        }
    }

    private record EquipCheck(int error, EquipCondition cond, String why) {
    }

    private final GameSession session;
    private final Random random;

    public CompoundItemService(GameSession session) {
        this(session, new Random());
    }

    CompoundItemService(GameSession session, Random random) {
        this.session = session;
        this.random = random;
    }

    public record Result(int error, List<Cmd27ReplyBody.Change> changes,
                         List<Cmd27ReplyBody.Output> outputs, String detail) {

        public boolean ok() {
            return error == 0;
        }
    }

    private static Result fail(int error, String detail) {
        return new Result(error, List.of(), List.of(), detail);
    }

    public Result compound(Cmd27CompoundItemRequest req) {
        GameScripts scripts = session.scripts();
        if (scripts == null) {
            return fail(Cmd27ReplyBody.ERROR_CANNOT_MAKE, "没有脚本表");
        }
        InventoryService inventory = session.inventory();
        ItemArray bag = inventory.containerOf(ItemListType.LIST_MAIN);
        if (bag == null) {
            return fail(Cmd27ReplyBody.ERROR_CANNOT_MAKE, "没有主背包");
        }

        int recipeSlot = -1;
        ItemScript recipe;
        if (req.byItemId()) {
            recipe = scripts.item((int) req.slotOrItemId()).orElse(null);
        } else {
            if (req.slotOrItemId() >= bag.slots()) {
                return fail(Cmd27ReplyBody.ERROR_CANNOT_MAKE,
                        "槽号 " + req.slotOrItemId() + " 越界（背包 " + bag.slots() + " 格）");
            }
            recipeSlot = (int) req.slotOrItemId();
            InvenItem held = bag.get(recipeSlot);
            if (held.isEmpty()) {
                return fail(Cmd27ReplyBody.ERROR_CANNOT_MAKE, "槽 " + recipeSlot + " 是空的");
            }
            recipe = scripts.item((int) held.itemId()).orElse(null);
        }
        if (recipe == null) {
            return fail(Cmd27ReplyBody.ERROR_CANNOT_MAKE, "配方在 pvf 里查不到");
        }
        if (!recipe.isRecipeItem()) {
            return fail(Cmd27ReplyBody.ERROR_CANNOT_MAKE,
                    recipe.id() + "「" + recipe.name() + "」不是设计图");
        }
        RecipeInfo info = recipe.recipeInfo();

        if (isAvatarRecipe(scripts, info)) {
            return fail(Cmd27ReplyBody.ERROR_CANNOT_MAKE, "装扮配方这一版没做");
        }

        String skillReject = checkCompoundSkill(recipe, req.byItemId());
        if (skillReject != null) {
            return fail(Cmd27ReplyBody.ERROR_SKILL, skillReject);
        }

        int times = req.count();
        List<InventoryService.Stack> needed = multiply(info.allMaterials(), times);
        List<InventoryService.Stack> made = multiply(info.outputs(), times);

        for (InventoryService.Stack out : made) {
            ItemScript script = scripts.item(out.itemId()).orElse(null);
            if (script == null) {
                return fail(Cmd27ReplyBody.ERROR_MULTI_EQUIP,
                        "产出 " + out.itemId() + " 在 pvf 里查不到");
            }
            if (!script.isStackableItem() && out.count() > 1) {
                return fail(Cmd27ReplyBody.ERROR_MULTI_EQUIP,
                        "一次做不出 " + out.count() + " 件不可堆叠的 " + out.itemId());
            }
        }

        for (InventoryService.Stack need : needed) {
            long have = inventory.countMaterial(need.itemId());
            if (have < need.count()) {
                return fail(Cmd27ReplyBody.ERROR_NOT_ENOUGH_MATERIAL,
                        materialName(scripts, need.itemId()) + " 要 " + need.count()
                                + "，只有 " + have);
            }
        }

        EquipCheck equip = checkEquipmentItemCondition(bag, needed, scripts);
        if (equip.error() != 0) {
            return fail(equip.error(), equip.why());
        }

        if (!InventoryService.canFitAll(bag, needed, made,
                itemId -> scripts.item(itemId).orElse(null))) {
            return fail(Cmd27ReplyBody.ERROR_INVENTORY_FULL, "背包放不下产出");
        }
        for (InventoryService.Stack out : made) {
            ItemScript script = scripts.item(out.itemId()).orElse(null);
            if (script != null && (script.isAvatar()
                    || InventoryService.isCreatureItemType(InventoryService.itemTypeOf(script)))) {
                return fail(Cmd27ReplyBody.ERROR_CANNOT_MAKE, "产出不落主背包，这一版没做");
            }
        }

        if (info.expertJobRecipe()) {
            ExpertRoll roll = rollExpertJobCompound(recipe, times);
            if (roll.successCount() <= 0) {
                made = List.of();
            } else {
                made = multiply(info.outputs(), roll.successCount());
            }
            if (roll.successCount() > 0 && roll.expGain() > 0) {
                session.expertJob().increaseExp(session.expertJob().exp() + roll.expGain());
            }
        }

        boolean success = isSuccessCompound(info.successRate());
        List<InventoryService.Stack> give = made;
        if (!success) {
            give = multiply(info.failOutputs(), times);
        }

        return apply(recipe, recipeSlot, req, needed, give, success, equip.cond());
    }

    private EquipCheck checkEquipmentItemCondition(ItemArray bag,
                                                   List<InventoryService.Stack> needed,
                                                   GameScripts scripts) {
        InvenItem template = InvenItem.EMPTY;
        for (InventoryService.Stack need : needed) {
            if (InventoryService.isCurrencyMaterial(need.itemId())) {
                continue;
            }
            ItemScript mat = scripts.item(need.itemId()).orElse(null);
            if (mat == null
                    || InventoryService.itemTypeOf(mat) != InventoryService.EQUIPMENT_ITEM_TYPE) {
                continue;
            }
            if (need.count() > 1) {
                return new EquipCheck(ERROR_TOO_MANY_EQUIP_MATERIAL, EquipCondition.NONE,
                        "装备材料 " + need.itemId() + " 一次要 " + need.count() + " 件");
            }
            int found = 0;
            for (int slot = BagLayout.FIRST_ITEM_SLOT; slot < bag.slots(); slot++) {
                if (!isEquipMaterialSlot(slot)) {
                    continue;
                }
                InvenItem held = bag.get(slot);
                if (held.isEmpty() || held.itemId() != need.itemId()) {
                    continue;
                }
                if (++found > 1) {
                    return new EquipCheck(ERROR_AMBIGUOUS_EQUIP_MATERIAL, EquipCondition.NONE,
                            "背包里有两件 " + need.itemId() + "，分不清用哪件");
                }
                template = held;
                if (session.itemLock().isLocked(ItemListType.LIST_MAIN, slot)) {
                    return new EquipCheck(Cmd27ReplyBody.ERROR_ITEM_LOCKED, EquipCondition.NONE,
                            "槽 " + slot + " 的材料装备被锁了");
                }
            }
        }
        if (template.isEmpty()) {
            return new EquipCheck(0, EquipCondition.NONE, null);
        }
        ItemScript script = scripts.item((int) template.itemId()).orElse(null);
        if (script == null) {
            return new EquipCheck(Cmd27ReplyBody.ERROR_CANNOT_MAKE, EquipCondition.NONE,
                    "材料装备 " + template.itemId() + " 在 pvf 里查不到");
        }
        var amplify = template.amplify();
        if (amplify.rawTypeByte() == (byte) 0x80) {
            return new EquipCheck(ERROR_UNIDENTIFIED_AMPLIFY, EquipCondition.NONE,
                    "材料装备的增幅还没鉴定");
        }
        if (template.endurance() != script.durability()) {
            return new EquipCheck(ERROR_ENDURANCE_NOT_FULL, EquipCondition.NONE,
                    "材料装备耐久 " + template.endurance() + " / " + script.durability());
        }
        boolean sealed = script.attachType() == 0 || template.attachType() != 0;
        return new EquipCheck(0, new EquipCondition(template.upgrade(),
                amplify.rawTypeByte() != 0, amplify.abilityType(), amplify.abilityValue(),
                sealed, template.reSealCount(), template), null);
    }

    private static boolean isEquipMaterialSlot(int slot) {
        return BagLayout.QUICK.contains(slot) || BagLayout.TAB1.contains(slot);
    }

    private Result apply(ItemScript recipe, int recipeSlot, Cmd27CompoundItemRequest req,
                         List<InventoryService.Stack> needed,
                         List<InventoryService.Stack> give, boolean success,
                         EquipCondition equip) {
        InventoryService inventory = session.inventory();
        ItemArray bag = inventory.containerOf(ItemListType.LIST_MAIN);
        GameScripts scripts = session.scripts();
        var changes = new ArrayList<Cmd27ReplyBody.Change>();

        if (!req.byItemId()) {
            int taken = inventory.deleteItem(ItemListType.LIST_MAIN, recipeSlot,
                    recipe.id(), req.count());
            if (taken <= 0) {
                return fail(Cmd27ReplyBody.ERROR_CANNOT_MAKE,
                        "设计图删不掉（槽 " + recipeSlot + " × " + req.count() + "）");
            }
            changes.add(Cmd27ReplyBody.Change.main(recipeSlot, taken));
        }

        for (InventoryService.Stack need : needed) {
            var taken = inventory.consumeMaterial(bag, need.itemId(), need.count());
            if (taken.isEmpty() && InventoryService.isCurrencyMaterial(need.itemId())) {
                return fail(Cmd27ReplyBody.ERROR_NOT_ENOUGH_MATERIAL,
                        materialName(scripts, need.itemId()) + " 扣不出 " + need.count());
            }
            for (InventoryService.Taken t : taken) {
                changes.add(Cmd27ReplyBody.Change.main(t.slot(), t.count()));
            }
        }
        inventory.markItemsDirty();

        var outputs = new ArrayList<Cmd27ReplyBody.Output>();
        for (InventoryService.Stack out : give) {
            ItemScript script = scripts.item(out.itemId()).orElse(null);
            if (script == null) {
                return fail(Cmd27ReplyBody.ERROR_CANNOT_MAKE,
                        "产出 " + out.itemId() + " 在 pvf 里查不到");
            }
            InvenItem item = InventoryService.newItem(script, out.count());
            if (equip.present() && !script.isStackableItem()) {
                item = applyRecipeEquipmentOption(item, script, equip);
            } else {
                int upgrade = rollUpgrade(script);
                if (upgrade > 0) {
                    item = item.withUpgrade(upgrade);
                }
            }
            int slot = inventory.insertExisting(bag, item);
            if (slot < 0) {
                return fail(Cmd27ReplyBody.ERROR_INVENTORY_FULL, "产出放不进背包");
            }
            var amplify = item.amplify();
            outputs.add(new Cmd27ReplyBody.Output(slot, script.id(), out.count(),
                    item.upgrade(), item.endurance(),
                    amplify.abilityType(), amplify.abilityValue()));
        }
        inventory.save();

        String detail = success
                ? recipe.name() + " → " + describe(outputs)
                : recipe.name() + " → 失败（掷点没中）";
        return new Result(0, changes, outputs, detail);
    }

    private static InvenItem applyRecipeEquipmentOption(InvenItem item, ItemScript output,
                                                        EquipCondition equip) {
        InvenItem out = item;
        if (equip.hasAmplify()) {
            out = out.withAmplify(com.dylan.store.blob.AmplifyOption.of(
                    equip.amplifyType(), equip.amplifyValue(), true));
        }
        boolean sealable = output.attachType() == ItemScript.ATTACH_SEALING
                || output.attachType() == ItemScript.ATTACH_SEALING_TRADE;
        out = out.withAttachType(equip.sealed() && sealable ? 1 : 0);
        if (output.attachType() == ItemScript.ATTACH_SEALING) {
            out = out.withReSealCount(equip.resealCount());
        }
        return out;
    }

    private int rollUpgrade(ItemScript output) {
        if (output.isStackableItem()) {
            return 0;
        }
        if (output.rarity() == RARITY_NO_UPGRADE
                || output.stackableType() == ItemScript.STACKABLE_FEED) {
            return 0;
        }
        var params = session.scripts() == null ? null : session.scripts().serverParameter();
        return params == null ? 0 : params.rollCompoundUpgrade(random);
    }

    private boolean isSuccessCompound(int successRate) {
        return successRate == 0 || random.nextInt(100) < successRate;
    }

    record ExpertRoll(int successCount, int expGain, int ratePercent) {
    }

    private ExpertRoll rollExpertJobCompound(ItemScript recipe, int times) {
        ExpertJobScript.CompoundRate rate = getSuccessRate(recipe);
        if (rate == null) {
            return new ExpertRoll(0, 0, 0);
        }
        int successCount = 0;
        int expGain = 0;
        for (int i = 0; i < times; i++) {
            if (rate.rate() <= random.nextInt(100)) {
                continue;
            }
            int span = rate.expMax() - rate.expMin() + 1;
            expGain += rate.expMin() + (span > 0 ? random.nextInt(span) : 0);
            successCount++;
        }
        return new ExpertRoll(successCount, expGain, rate.rate());
    }

    private ExpertJobScript.CompoundRate getSuccessRate(ItemScript recipe) {
        ExpertJobService expert = session.expertJob();
        ExpertJobScript script = expert.script();
        if (script == null) {
            return null;
        }
        int index = script.recipeIndex(recipe.id());
        if (index == ExpertJobScript.NO_RECIPE) {
            return null;
        }
        ExpertJobScript.RecipeEntry entry = script.recipe(index);
        if (entry == null) {
            return null;
        }
        return script.compoundRate(entry.level() - expert.level());
    }

    private String checkCompoundSkill(ItemScript recipe, boolean byItemId) {
        RecipeInfo info = recipe.recipeInfo();
        if (info.needsPremium() && !session.premium().isAffected(PREMIUM_TYPE_RECIPE)) {
            return "这张配方要会员权益 " + PREMIUM_TYPE_RECIPE;
        }
        if (!info.expertJobRecipe() && byItemId) {
            return null;
        }
        String missing = checkSkillList(info.skills());
        if (missing != null) {
            return missing;
        }
        if (info.expertJobRecipe()) {
            return isLearnRecipe(recipe);
        }
        return null;
    }

    private String isLearnRecipe(ItemScript recipe) {
        ExpertJobService expert = session.expertJob();
        ExpertJobScript script = expert.script();
        if (script == null) {
            return "还没有副职业，做不了副职业配方";
        }
        int index = script.recipeIndex(recipe.id());
        if (index == ExpertJobScript.NO_RECIPE) {
            return "配方 " + recipe.id() + "「" + recipe.name() + "」不在副职业 "
                    + expert.type() + " 的 [items] 里";
        }
        RecipeBitmap bitmap = expert.recipes();
        if ((bitmap == null || !bitmap.has(index)) && !session.gmMode()) {
            return "还没学会配方 " + recipe.id() + "「" + recipe.name() + "」（序号 " + index + "）";
        }
        return null;
    }

    private String checkSkillList(List<RecipeInfo.Material> skills) {
        if (skills.isEmpty()) {
            return null;
        }
        var slots = session.skills().slots();
        for (RecipeInfo.Material need : skills) {
            int level = 0;
            for (int slot = 0; slot < com.dylan.store.blob.SkillSlots.SLOTS; slot++) {
                if (!slots.isEmptyAt(slot) && slots.skillIndexAt(slot) == need.itemId()) {
                    level = Math.max(level, slots.levelAt(slot));
                }
            }
            if (level < need.count()) {
                return "技能 " + need.itemId() + " 要 " + need.count() + " 级，现在 " + level;
            }
        }
        return null;
    }

    private static boolean isAvatarRecipe(GameScripts scripts, RecipeInfo info) {
        for (RecipeInfo.Material out : info.outputs()) {
            ItemScript script = scripts.item(out.itemId()).orElse(null);
            if (script != null && script.isAvatar()) {
                return true;
            }
        }
        return false;
    }

    private static List<InventoryService.Stack> multiply(List<RecipeInfo.Material> list,
                                                        int times) {
        var out = new ArrayList<InventoryService.Stack>(list.size());
        for (RecipeInfo.Material m : list) {
            out.add(new InventoryService.Stack(m.itemId(), m.count() * times));
        }
        return out;
    }

    private static String materialName(GameScripts scripts, int itemId) {
        if (itemId == InventoryService.MATERIAL_MONEY) {
            return "金币";
        }
        if (itemId == InventoryService.MATERIAL_WIN_POINT) {
            return "决斗胜点";
        }
        ItemScript script = scripts == null ? null : scripts.item(itemId).orElse(null);
        return script == null ? ("物品 " + itemId) : (script.name() + "（" + itemId + "）");
    }

    private static String describe(List<Cmd27ReplyBody.Output> outputs) {
        var sb = new StringBuilder();
        for (Cmd27ReplyBody.Output o : outputs) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(o.itemId()).append(" × ").append(o.count()).append("（槽 ")
                    .append(o.slot()).append('）');
        }
        return sb.length() == 0 ? "什么都没有" : sb.toString();
    }
}
