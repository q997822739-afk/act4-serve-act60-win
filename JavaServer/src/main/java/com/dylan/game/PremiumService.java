package com.dylan.game;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Noti66SpecialItemBody;
import com.dylan.pvf.script.PremiumCatalog;
import com.dylan.store.GameStore;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class PremiumService {
    private static final long SECONDS_PER_DAY = 86400L;

    public static final int SERVER_ID = 1;

    public static final int EVENT_ID = 0;

    public static final int PREMIUM_PRIVATE_STORE = 8;

    public static final int PREMIUM_PC_ROOM_BONUS = 12;

    /**
     * 黑钻会员的会员类型。ACT5 是 42；A4 的 etc/premiumlist.etc 只到 29，没有 42，
     * 黑钻是 29（「프리미엄 피시방 보너스」fatigue 21 / exp 5）。依据：A4 官方实抓 CMD 68（黑钻免费开通）
     * 之后的 NOTI 66 = {@code 02 00 1D 00 8D 27 00}（类型 0x1D、剩 2592000 秒）。
     */
    public static final int PREMIUM_BLACK_DIAMOND = ServerConfig.A4 ? 29 : 42;

    /** A4 CMD 68 黑钻免费开通给的天数：官方两次连点剩余都是 2592000 秒（重置为 30 天，不累加）。 */
    public static final int A4_FREE_BLACK_DIAMOND_DAYS = 30;

    public enum State {
        ACTIVE,
        PENDING,
        ENDED
    }

    public record Row(int type, long start, long end, int eventId, State state) {
        public long remaining(long now) {
            return Math.max(0, end - now);
        }
    }

    private final GameSession session;

    private final Map<Integer, Row> rows = new LinkedHashMap<>();

    private int advantageFatigueRate;
    private int advantageExpRate;
    private int advantageCoin;
    private boolean goldBonus;
    private boolean unlimitFatigue;
    private int overSkillLevel;
    private int cerashopDiscountRate;
    private int inventoryLimitRate;
    private final Map<Integer, Integer> overEquipLevel = new LinkedHashMap<>();
    private final Map<Integer, Integer> returnItemRate = new LinkedHashMap<>();

    public PremiumService(GameSession session) {
        this.session = session;
    }

    public void load(long mId) {
        rows.clear();
        var catalog = catalog();
        GameStore store = session.store();
        if (store == null || catalog == null) {
            recalcAdvantage();
            return;
        }
        long now = now();
        for (var row : store.premium().premiumRows(mId, SERVER_ID)) {
            if (catalog.byType(row.preType()).isEmpty()) {
                continue;
            }
            addPremium(row.preType(), row.start(), row.end(), row.eventId(), now);
        }
        syncBlackDiamond(mId);
        recalcAdvantage();
    }

    public void clear() {
        rows.clear();
        recalcAdvantage();
    }

    public record Grant(boolean ok, int premiumType, long start, long end, long remaining) {
        static Grant failed(int premiumType) {
            return new Grant(false, premiumType, 0, 0, 0);
        }
    }

    public Grant grant(int premiumType, int count) {
        var entry = entryOf(premiumType);
        if (entry == null) {
            return Grant.failed(premiumType);
        }

        long now = now();
        long days = (long) entry.termDays() * Math.max(1, count);
        long start = now;
        long end = now + SECONDS_PER_DAY * days;

        Row current = rows.get(premiumType);
        if (current != null && current.end() > now) {
            start = current.start();
            end = current.end() + SECONDS_PER_DAY * days;
        }
        return commit(entry, start, end, now, true,
                days + " 天（" + entry.termDays() + " × " + Math.max(1, count) + "）");
    }

    private Grant commit(PremiumCatalog.Entry entry, long start, long end, long now,
                         boolean effects, String why) {
        int premiumType = entry.type();
        GameStore store = session.store();
        var account = session.account();
        if (store == null || account == null) {
            return Grant.failed(premiumType);
        }
        if (entry.expireTime() != 0 && entry.expireTime() > start) {
            end = Math.min(end, entry.expireTime());
        }

        store.premium().savePremium(account.mId(), premiumType, start, end, EVENT_ID, SERVER_ID);
        addPremium(premiumType, start, end, EVENT_ID, now);
        recalcAdvantage();
        if (effects) {
            applyEffect(premiumType);
            if (premiumType == PREMIUM_PRIVATE_STORE) {
                CharacterService.resendOwnCharacterInfo(session, "买到阿拉德商人证，摆摊许可位变了");
            }
        }

        long remaining = end - now;
        return new Grant(true, premiumType, start, end, remaining);
    }

    public void syncBlackDiamond(long mId) {
        GameStore store = session.store();
        if (store == null || mId <= 0 || entryOf(PREMIUM_BLACK_DIAMOND) == null) {
            return;
        }
        long subscription;
        try {
            subscription = store.premium().blackDiamondEnd(mId);
        } catch (RuntimeException e) {
            return;
        }
        Row current = rows.get(PREMIUM_BLACK_DIAMOND);
        long now = now();
        long end = syncEnd(subscription, current == null ? 0 : current.end(), now);
        if (end == 0) {
            return;
        }
        commit(entryOf(PREMIUM_BLACK_DIAMOND),
                current != null && current.end() > now ? current.start() : now,
                end, now, false, "黑钻订阅同步");
    }

    /**
     * A4 CMD 68「黑钻免费开通」：到期改成现在 + 30 天（写 member_info.black_diamond_end，再走
     * {@link #syncBlackDiamond}，下次登录同步出来的也是这个值）。
     *
     * @return 开通前黑钻是否没生效（官方只在新开通时补发 NOTI 36）；PVF 里没有黑钻类型时返回 null
     */
    public Boolean grantFreeBlackDiamond() {
        GameStore store = session.store();
        var account = session.account();
        if (store == null || account == null || entryOf(PREMIUM_BLACK_DIAMOND) == null) {
            return null;
        }
        boolean wasActive = hasBlackDiamond();
        long end = now() + SECONDS_PER_DAY * A4_FREE_BLACK_DIAMOND_DAYS;
        store.premium().saveBlackDiamondEnd(account.mId(), end);
        syncBlackDiamond(account.mId());
        return !wasActive;
    }

    static long syncEnd(long subscription, long currentEnd, long now) {
        if (subscription <= now) {
            return 0;
        }
        return subscription == currentEnd ? 0 : subscription;
    }

    public void notifyGranted(int kind, Grant grant) {
        int wireType = wireType(grant.premiumType());
        var body = Noti66SpecialItemBody.premium(kind, wireType, grant.remaining());
        session.send(Noti66SpecialItemBody.PACKET_ID, body.toBytes());
    }

    private void addPremium(int type, long start, long end, int eventId, long now) {
        var entry = entryOf(type);
        State state = State.ACTIVE;
        if (entry != null && entry.termDays() != 0) {
            if (start > now) {
                state = State.PENDING;
            } else if (end <= now) {
                rows.put(type, new Row(type, start, end, eventId, State.ENDED));
                return;
            }
        }
        rows.put(type, new Row(type, start, end, eventId, state));
    }

    private void recalcAdvantage() {
        advantageFatigueRate = 0;
        advantageExpRate = 0;
        advantageCoin = 0;
        goldBonus = false;
        unlimitFatigue = false;
        overSkillLevel = 0;
        cerashopDiscountRate = 0;
        inventoryLimitRate = 0;
        overEquipLevel.clear();
        returnItemRate.clear();

        int dayOfWeek = today().getDayOfWeek().getValue() % 7;
        for (var row : rows.values()) {
            var entry = entryOf(row.type());
            if (entry == null || !entry.bonus() || row.state() != State.ACTIVE
                    || !entry.appliesOn(dayOfWeek)) {
                continue;
            }
            unlimitFatigue |= entry.unlimitFatigue();
            goldBonus |= entry.goldBonus();
            overSkillLevel = Math.max(overSkillLevel, entry.overSkillLevel());
            cerashopDiscountRate = Math.max(cerashopDiscountRate, entry.cerashopDiscountRate());
            advantageFatigueRate += entry.fatigueRate();
            advantageExpRate += entry.expRate();
            advantageCoin += entry.coin();
            inventoryLimitRate += entry.inventoryLimit();
            for (var over : entry.overEquip()) {
                overEquipLevel.merge(over.slot(), over.level(), Math::max);
            }
            for (var back : entry.returnItems()) {
                returnItemRate.merge(back.itemId(), back.rate(), Integer::sum);
            }
        }
    }

    private void applyEffect(int premiumType) {
        var entry = entryOf(premiumType);
        if (entry == null) {
            return;
        }
        var inventory = session.inventory();
        var scripts = session.scripts();
        for (var grant : entry.dailyItems()) {
            var item = scripts == null ? null : scripts.item(grant.itemId()).orElse(null);
            if (item == null) {
                continue;
            }
            InventoryService.Landing landing = inventory.addItem(item, grant.count());
            if (!landing.ok()) {
                continue;
            }
            inventory.sendItemUpdate(landing.itemSpace(), "会员每日物品", landing.slot());
        }
        if (entry.coin() > 0) {
            var items = inventory.inventory();
            if (items != null) {
                inventory.setCoins(items.coin() + entry.coin(), items.payCoin(), items.eventCoin());
            }
        }
        if (entry.dailyItems().isEmpty() && entry.coin() <= 0) {
            inventory.save();
        }
        session.fatigue().refreshPremiumCap();
    }

    public boolean hasBlackDiamond() {
        return isAffected(PREMIUM_BLACK_DIAMOND);
    }

    public static int nameBadgeFor(boolean blackDiamond, int override) {
        if (override >= 0) {
            return override;
        }
        return blackDiamond ? NAME_BADGE_BLACK_DIAMOND : 0;
    }

    public static final int NAME_BADGE_BLACK_DIAMOND = 1;

    public static int pcRoomNoFor(int override) {
        return Math.max(override, 0);
    }

    public boolean isAffected(int premiumType) {
        Row row = rows.get(premiumType);
        return row != null && row.state() == State.ACTIVE;
    }

    public boolean canOpenPrivateStore() {
        return isAffected(PREMIUM_PRIVATE_STORE);
    }

    public int advantageFatigueRate() {
        return ServerConfig.FATIGUE_PREMIUM_RATE > 0
                ? ServerConfig.FATIGUE_PREMIUM_RATE : advantageFatigueRate;
    }

    public int advantageExpRate() {
        return advantageExpRate;
    }

    public boolean goldBonus() {
        return goldBonus;
    }

    public boolean unlimitFatigue() {
        return unlimitFatigue;
    }

    public int overSkillLevel() {
        return overSkillLevel;
    }

    public int overEquipableLevel(int slot) {
        return overEquipLevel.getOrDefault(slot, 0);
    }

    public int cerashopDiscountRate() {
        return cerashopDiscountRate;
    }

    public int returnItemRate(int itemId) {
        return returnItemRate.getOrDefault(itemId, 0);
    }

    public Map<Integer, Row> rows() {
        return Map.copyOf(rows);
    }

    public int wireType(int premiumType) {
        var catalog = catalog();
        return catalog == null ? premiumType : catalog.effectSourceType(premiumType);
    }

    public Map<Integer, Long> wirePremiums(long now) {
        Map<Integer, Long> out = new LinkedHashMap<>();
        for (var row : rows.values()) {
            if (row.state() != State.ACTIVE) {
                continue;
            }
            long remaining = row.remaining(now);
            if (remaining <= 0) {
                continue;
            }
            out.merge(wireType(row.type()), remaining, Math::max);
        }
        return out;
    }

    private PremiumCatalog catalog() {
        var scripts = session.scripts();
        return scripts == null ? null : scripts.premium().orElse(null);
    }

    private PremiumCatalog.Entry entryOf(int premiumType) {
        var catalog = catalog();
        return catalog == null ? null : catalog.byType(premiumType).orElse(null);
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.systemDefault());
    }

    private String describe(long now) {
        StringBuilder sb = new StringBuilder();
        for (var row : rows.values()) {
            var entry = entryOf(row.type());
            if (!sb.isEmpty()) {
                sb.append("，");
            }
            sb.append(row.type());
            if (entry != null && !entry.name().isEmpty()) {
                sb.append('「').append(entry.name()).append('」');
            }
            sb.append(' ').append(row.state()).append(" 剩 ")
                    .append(row.remaining(now) / 3600).append(" 小时");
        }
        return sb.toString();
    }

    public Optional<Row> any() {
        return rows.values().stream().findFirst();
    }
}
