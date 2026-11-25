package com.dylan.game;

import java.util.Map;

import com.dylan.protocol.body.Cmd32ReplyBody;
import com.dylan.protocol.body.CombatStats;
import com.dylan.protocol.body.ItemListType;
import com.dylan.pvf.script.ItemScript;
import com.dylan.store.blob.InvenItem;
import com.dylan.store.blob.ItemArray;

public final class IncreaseStatusService {

    private final GameSession session;

    public IncreaseStatusService(GameSession session) {
        this.session = session;
    }

    public record Outcome(int error, boolean silent, int type, long value, int extra,
                          String detail, Runnable afterReply) {

        Outcome(int error, boolean silent, int type, long value, int extra, String detail) {
            this(error, silent, type, value, extra, detail, null);
        }

        public boolean ok() {
            return error == 0 && !silent;
        }
    }

    private static Outcome fail(int error, String detail) {
        return new Outcome(error, false, 0, 0, 0, detail);
    }

    private static Outcome silent(String detail) {
        return new Outcome(0, true, 0, 0, 0, detail);
    }

    private record Effect(int type, long value) {}

    private static final int TYPE_SFP = 17;

    private static final int TYPE_RESET_SFP_SKILL = 18;

    private static final int TYPE_POWER_WAR_POINT = 19;

    private static final int TYPE_ADJUST_STAT = 20;

    private static final int TYPE_RESET_SKILL_AND_QP = 24;

    private static final int TYPE_AVATAR_COIN = 25;

    private static final int TYPE_RESET_SKILL = Cmd32ReplyBody.TYPE_RESET_SKILL;

    private static final int TYPE_GUILD_EXP = Cmd32ReplyBody.TYPE_GUILD_EXP;

    private static final int TYPE_RECOVER_FATIGUE = Cmd32ReplyBody.TYPE_RECOVER_FATIGUE;

    private static final int TYPE_GUILD_EXP_BOOK = Cmd32ReplyBody.TYPE_GUILD_EXP_BOOK;

    private static final Map<Integer, Effect> EFFECTS = Map.ofEntries(
            Map.entry(1031, new Effect(Cmd32ReplyBody.TYPE_SKILL_POINT, 5)),
            Map.entry(1038, new Effect(Cmd32ReplyBody.TYPE_SKILL_POINT, 20)),
            Map.entry(1034, new Effect(Cmd32ReplyBody.TYPE_EXPERIENCE, 100)),
            Map.entry(1035, new Effect(Cmd32ReplyBody.TYPE_EXPERIENCE, 1_000)),
            Map.entry(1036, new Effect(Cmd32ReplyBody.TYPE_EXPERIENCE, 10_000)),
            Map.entry(1037, new Effect(Cmd32ReplyBody.TYPE_EXPERIENCE, 100_000)),
            Map.entry(1231, new Effect(Cmd32ReplyBody.TYPE_EXPERIENCE, 1_000_000)),
            Map.entry(1232, new Effect(Cmd32ReplyBody.TYPE_EXPERIENCE, 10_000_000)),
            Map.entry(ElixirStats.ITEM_MAX_HP,
                    new Effect(Cmd32ReplyBody.TYPE_MAX_HP, ElixirStats.VITAL_PER_STONE)),
            Map.entry(ElixirStats.ITEM_MAX_MP,
                    new Effect(Cmd32ReplyBody.TYPE_MAX_MP, ElixirStats.VITAL_PER_STONE)),
            Map.entry(ElixirStats.ITEM_STRENGTH,
                    new Effect(Cmd32ReplyBody.TYPE_STRENGTH, ElixirStats.STAT_PER_STONE)),
            Map.entry(ElixirStats.ITEM_VITALITY,
                    new Effect(Cmd32ReplyBody.TYPE_VITALITY, ElixirStats.STAT_PER_STONE)),
            Map.entry(ElixirStats.ITEM_INTELLIGENCE,
                    new Effect(Cmd32ReplyBody.TYPE_INTELLIGENCE, ElixirStats.STAT_PER_STONE)),
            Map.entry(ElixirStats.ITEM_SPIRIT,
                    new Effect(Cmd32ReplyBody.TYPE_SPIRIT, ElixirStats.STAT_PER_STONE)),
            Map.entry(ElixirStats.ITEM_MOVE_SPEED,
                    new Effect(Cmd32ReplyBody.TYPE_MOVE_SPEED, ElixirStats.MINOR_PER_STONE)),
            Map.entry(ElixirStats.ITEM_ALL_RESISTANCE,
                    new Effect(Cmd32ReplyBody.TYPE_ALL_RESISTANCE, ElixirStats.MINOR_PER_STONE)),
            Map.entry(7180, new Effect(Cmd32ReplyBody.TYPE_EVENT_COIN, 1)),
            Map.entry(7181, new Effect(Cmd32ReplyBody.TYPE_EVENT_COIN, 5)),
            Map.entry(42, new Effect(Cmd32ReplyBody.TYPE_COIN, 1)),
            Map.entry(2675021, new Effect(Cmd32ReplyBody.TYPE_COIN, 1)),
            Map.entry(1204, new Effect(TYPE_SFP, 1)),
            Map.entry(1205, new Effect(TYPE_SFP, 5)),
            Map.entry(1247, new Effect(TYPE_POWER_WAR_POINT, 10)),
            Map.entry(7958, new Effect(TYPE_ADJUST_STAT, 0)),
            Map.entry(690000301, new Effect(TYPE_AVATAR_COIN, 1)));

    private static final java.util.Set<Integer> LETHE_ITEMS =
            java.util.Set.of(3, 28, 109, 161, 162,
                    916, 960, 963, 1206, 1253, 2660396);

    private static final java.util.Set<Integer> FATIGUE_HAMMERS =
            java.util.Set.of(201, 2670008);

    private static final Map<Integer, Long> GUILD_EXP_SEALS = Map.of(
            3204, 10L,
            7105, 50L);

    private static final java.util.Set<Integer> GUILD_EXP_BOOKS =
            java.util.Set.of(1200, 1201, 1202, 690000220);

    private static final java.util.Set<Integer> PVP_SKILLTREE_ITEMS =
            java.util.Set.of(2660171, 2660172, 2660232);

    private static final Map<Integer, Long> EVENT_COINS_IN_TOWN = Map.of(
            7101, 8L,
            7298, 2L);

    public Outcome use(int slot) {

        InventoryService inventory = session.inventory();
        ItemArray bag = inventory == null ? null : inventory.containerOf(ItemListType.LIST_MAIN);
        if (bag == null || slot < 0 || slot >= bag.slots()) {
            if (com.dylan.net.ServerConfig.A4) {
                // 依据：A4 官方实抓（sweep_town CMD 32 包体 05000000 = 越界/空槽）回 REPLY 32 `00 02`；
                // 客户端 00415086 读 u8 错误码后解锁道具栏。原版 A5 静默丢，连点最后一个城镇道具时客户端会等。
                // 只把「越界槽」这一条的静默换成失败回包，槽号校验本身没动。
                return fail(Cmd32ReplyBody.ERROR_UNUSABLE, "槽号 " + slot + " 越界");
            }
            return silent("槽号 " + slot + " 越界");
        }
        InvenItem item = bag.get(slot);
        if (item.isEmpty()) {
            if (com.dylan.net.ServerConfig.A4) {
                // 依据：同一条官方实抓（包体 05000000 落在空槽上）= REPLY 32 `00 02`；空槽照样不解锁，只告诉客户端失败。
                return fail(Cmd32ReplyBody.ERROR_UNUSABLE, "槽 " + slot + " 是空的");
            }
            return silent("槽 " + slot + " 是空的");
        }
        int itemId = (int) item.itemId();

        if (com.dylan.net.ServerConfig.A4 && itemId == A4_FATIGUE_POTION) {
            return a4FatiguePotion(slot, itemId);
        }

        Object classified = classify(itemId);
        if (classified instanceof Outcome refused) {
            return refused;
        }
        Effect effect = (Effect) classified;

        var scripts = session.scripts();
        ItemScript script = scripts == null ? null : scripts.item(itemId).orElse(null);
        if (script == null) {
            return silent("物品 " + itemId + " 在 pvf 里查不到");
        }
        if (!consume(script, slot, itemId)) {
            return fail(Cmd32ReplyBody.ERROR_CANNOT_USE, "槽 " + slot + " 的东西扣不掉");
        }

        return applyEffect(effect, script, slot, itemId);
    }

    private Object classify(int itemId) {
        if (LETHE_ITEMS.contains(itemId)) {
            return letheEffect(itemId);
        }
        if (FATIGUE_HAMMERS.contains(itemId)) {
            return hammerEffect();
        }
        if (GUILD_EXP_SEALS.containsKey(itemId) || GUILD_EXP_BOOKS.contains(itemId)) {
            return fail(Cmd32ReplyBody.ERROR_NO_GUILD, "单人版没有公会");
        }
        if (PVP_SKILLTREE_ITEMS.contains(itemId)) {
            return fail(Cmd32ReplyBody.ERROR_PVP_SKILLTREE_CHANNEL_ONLY,
                    "ACT5 没有决斗技能树频道");
        }
        Long inTown = EVENT_COINS_IN_TOWN.get(itemId);
        if (inTown != null) {
            if (session.inDungeon()) {
                return fail(Cmd32ReplyBody.ERROR_BAD_STATE, "这枚活动币只能在城镇里用");
            }
            return new Effect(Cmd32ReplyBody.TYPE_EVENT_COIN, inTown);
        }
        Effect effect = EFFECTS.get(itemId);
        if (effect != null) {
            return effect;
        }
        return fail(Cmd32ReplyBody.ERROR_CANNOT_USE,
                "物品 " + itemId + " 不在分派表里（ACT5 没有 [increase status type] 段）");
    }

    private static final int LETHE_PREMIUM = 33;

    private Object letheEffect(int itemId) {
        if (itemId == 1206 || itemId == 1253) {
            if (session.level() <= LETHE_SFP_MIN_LEVEL) {
                return silent("一日忘却要 " + (LETHE_SFP_MIN_LEVEL + 1) + " 级以上");
            }
            boolean hasContract = session.premium() != null
                    && session.premium().isAffected(LETHE_PREMIUM);
            return hasContract
                    ? fail(Cmd32ReplyBody.ERROR_NO_LETHE_PREMIUM, "已经有一日忘却契约了")
                    : new Effect(TYPE_RESET_SFP_SKILL, 1);
        }
        if (itemId == 916) {
            return new Effect(TYPE_RESET_SKILL, 1);
        }
        if (itemId == 960) {
            return new Effect(TYPE_RESET_SKILL_AND_QP, 1);
        }
        long both = (itemId == 963 || itemId == 2660396) ? 1 : 0;
        return new Effect(TYPE_RESET_SKILL, both);
    }

    private static final int LETHE_SFP_MIN_LEVEL = 49;

    private Object hammerEffect() {
        FatigueService fatigue = session.fatigue();
        if (fatigue == null || fatigue.totalFatigue() == 0) {
            return fail(Cmd32ReplyBody.ERROR_FATIGUE_ALREADY_FULL, "疲劳是满的，没得恢复");
        }
        return new Effect(TYPE_RECOVER_FATIGUE, 0);
    }

    /** A4 商城 101001「魔界抗疲劳秘药」：脚本是 [etc]、没有任何效果字段，效果按物品号写死在服务端。 */
    static final int A4_FATIGUE_POTION = 416;

    /** 能用的门槛：剩余疲劳 ≤ 48（说明文字写「49 点以下」，官方实测剩 49 不给用、剩 48 给用）。 */
    static final int A4_FATIGUE_POTION_MAX_REMAINING = 48;

    static final int A4_FATIGUE_POTION_RECOVER = 50;

    /** 官方成功回包的 type 字节。 */
    static final int A4_FATIGUE_POTION_TYPE = 0xFF;

    /**
     * 依据：A4 官方实抓（probe_fatigue_potion.py，满级假人已用 0/100/107/108/120/156）：
     * 剩余 ≥ 49 时不回包（日志「疲劳药拒绝 … 仅剩余 48 以下可用」）、不扣药；
     * 剩余 ≤ 48 时 REPLY 32 = {@code 01 <槽 u16> FF 00000000 0000}，扣 1 个，已用疲劳 −50（不低于 0），
     * 随后 NOTI 36。原来走 ACT5 分派表查不到 416，回错误 17「缺少可增加经验值/SP/属性值的物品」。
     */
    private Outcome a4FatiguePotion(int slot, int itemId) {
        FatigueService fatigue = session.fatigue();
        if (fatigue == null || fatigue.remaining() > A4_FATIGUE_POTION_MAX_REMAINING) {
            return silent("疲劳药：剩余 " + (fatigue == null ? -1 : fatigue.remaining())
                    + " 点，要 " + A4_FATIGUE_POTION_MAX_REMAINING + " 以下才能用");
        }
        var scripts = session.scripts();
        ItemScript script = scripts == null ? null : scripts.item(itemId).orElse(null);
        if (script == null || !consume(script, slot, itemId)) {
            return fail(Cmd32ReplyBody.ERROR_CANNOT_USE, "槽 " + slot + " 的疲劳药扣不掉");
        }
        return new Outcome(0, false, A4_FATIGUE_POTION_TYPE, 0, 0, "疲劳药：恢复 "
                + A4_FATIGUE_POTION_RECOVER,
                () -> fatigue.gainFatigue(A4_FATIGUE_POTION_RECOVER, "用了魔界抗疲劳秘药"));
    }

    private boolean consume(ItemScript script, int slot, int itemId) {
        if (script.stackableType() == ItemScript.STACKABLE_UNLIMITED_ETC) {
            return true;
        }
        int taken = session.inventory().deleteItem(ItemListType.LIST_MAIN, slot, itemId, 1);
        return taken > 0;
    }

    private Outcome applyEffect(Effect effect, ItemScript script, int slot, int itemId) {
        int type = effect.type();
        long value = effect.value();
        String name = script.name() == null || script.name().isEmpty()
                ? String.valueOf(itemId) : script.name();

        switch (type) {
            case Cmd32ReplyBody.TYPE_SKILL_POINT -> {
                session.skills().gainSp((int) value);
                return done(type, value, 0, name + "：SP +" + value
                        + "，现在 " + session.skills().remainSp());
            }
            case Cmd32ReplyBody.TYPE_EXPERIENCE -> {
                int spBefore = session.skills().remainSp();
                ExperienceService.grant(session, (int) Math.min(Integer.MAX_VALUE, value));
                int gainedSp = session.skills().remainSp() - spBefore;
                return done(type, value, Math.clamp(gainedSp, 0, 0xFFFF),
                        name + "：经验 +" + value
                                + (gainedSp > 0 ? "，升级发了 " + gainedSp + " SP" : ""));
            }
            case Cmd32ReplyBody.TYPE_MAX_HP, Cmd32ReplyBody.TYPE_MAX_MP,
                 Cmd32ReplyBody.TYPE_STRENGTH, Cmd32ReplyBody.TYPE_VITALITY,
                 Cmd32ReplyBody.TYPE_INTELLIGENCE, Cmd32ReplyBody.TYPE_SPIRIT,
                 Cmd32ReplyBody.TYPE_MOVE_SPEED, Cmd32ReplyBody.TYPE_ALL_RESISTANCE -> {
                return applyElixir(type, value, itemId, name);
            }
            case TYPE_RESET_SKILL -> {
                int remain = session.skills().resetSkills(session.level());
                if (remain < 0) {
                    return fail(Cmd32ReplyBody.ERROR_CANNOT_USE, "技能表没装载，洗不了点");
                }
                sendSkillInfo("洗点，SP 重算成 " + remain);
                CharacterService.refreshDetail(session, "洗点");
                return done(type, value, 0, name + "：洗点完成，SP " + remain);
            }
            case Cmd32ReplyBody.TYPE_EVENT_COIN -> {
                return addCoin(type, value, name, false);
            }
            case Cmd32ReplyBody.TYPE_COIN -> {
                return addCoin(type, value, name, true);
            }
            case TYPE_RECOVER_FATIGUE -> {
                session.fatigue().recoverFatigue((int) value);
                session.fatigue().sendFatigue("用了 " + name);
                return done(type, value, 0, name + "：疲劳恢复满");
            }
            case TYPE_GUILD_EXP, TYPE_GUILD_EXP_BOOK,
                 TYPE_SFP, TYPE_RESET_SFP_SKILL, TYPE_POWER_WAR_POINT, TYPE_ADJUST_STAT,
                 TYPE_RESET_SKILL_AND_QP, TYPE_AVATAR_COIN -> {
                return silent("type " + type + " 没有对应系统");
            }
            default -> {
                return silent("没实现的 type " + type);
            }
        }
    }

    private Outcome applyElixir(int type, long value, int itemId, String name) {
        if (session.store() != null && session.characNo() > 0) {
            session.store().elixir().addElixirUse(session.characNo(), itemId, 1);
        }
        CombatStats before = session.stats();
        if (before != null) {
            CombatStats after = before.plus(ElixirStats.apply(itemId, 1));
            session.stats(after);
            CharacterService.refreshDetail(session, "吃了 " + name);
        }
        return done(type, value, 0, name + "：" + ElixirStats.describe(ElixirStats.apply(itemId, 1)));
    }

    private Outcome addCoin(int type, long value, String name, boolean revival) {
        InventoryService inventory = session.inventory();
        var items = inventory.inventory();
        if (items == null) {
            return silent("背包还没装载");
        }
        long before = inventory.coinTotal();
        if (revival) {
            inventory.setCoins(items.coin() + value, items.payCoin(), items.eventCoin());
        } else {
            inventory.setCoins(items.coin(), items.payCoin(), items.eventCoin() + value);
        }
        inventory.save();
        inventory.sendItemUpdate(ItemListType.LIST_MAIN, "用了 " + name,
                BagLayout.SLOT_COIN);
        return done(type, value, 0, name + "：" + (revival ? "复活币" : "活动币")
                + " +" + value + "，那一格 " + before + " → " + inventory.coinTotal());
    }

    private void sendSkillInfo(String why) {
        var info = session.skills().skillInfo();
        byte[] body = info.toBytes();
        session.send(com.dylan.protocol.body.Noti19SkillInfoBody.PACKET_ID, body);
    }

    private static Outcome done(int type, long value, int extra, String detail) {
        return new Outcome(0, false, type, value, extra, detail);
    }

}
