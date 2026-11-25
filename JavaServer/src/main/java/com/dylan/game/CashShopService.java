package com.dylan.game;

import com.dylan.protocol.body.CashShopError;
import com.dylan.protocol.body.Cmd67BuyCeraShopItemRequest;
import com.dylan.protocol.body.Cmd67ReplyBody;
import com.dylan.protocol.body.ItemListType;
import com.dylan.pvf.script.CashShopCatalog;
import com.dylan.pvf.script.ItemScript;
import com.dylan.net.ServerConfig;
import com.dylan.store.GameStore;

import java.util.List;
import java.util.Random;

public final class CashShopService {

    public static final int CURRENCY_GOLD = 0;

    public static final int CURRENCY_COIN = 1;

    public static final int CURRENCY_WIN_POINT = 2;

    public static final long CURRENCY_MAX = 0xFFFF_FFFFL;

    private static final Random RANDOM = new Random();

    private final GameSession session;

    public CashShopService(GameSession session) {
        this.session = session;
    }

    public record Outcome(CashShopError error, CashShopCatalog.Entry entry,
                          long itemId, long price, long balance,
                          int listType, int slot, List<Cmd67ReplyBody.BundleItem> bundle,
                          List<Integer> touchedLists) {

        public static final int NO_SLOT = -1;

        public static final int NO_LIST = -1;

        public Outcome {
            bundle = bundle == null ? List.of() : List.copyOf(bundle);
            touchedLists = touchedLists == null ? List.of() : List.copyOf(touchedLists);
        }

        public Outcome(CashShopError error, CashShopCatalog.Entry entry,
                       long itemId, long price, long balance, int listType, int slot) {
            this(error, entry, itemId, price, balance, listType, slot, List.of(), List.of());
        }

        public static Outcome special(CashShopCatalog.Entry entry, long price, long balance) {
            return new Outcome(null, entry, entry.itemId(), price, balance, NO_LIST, NO_SLOT);
        }

        public boolean hasList() {
            return listType != NO_LIST;
        }

        public Outcome(CashShopError error, CashShopCatalog.Entry entry,
                       long itemId, long price, long balance) {
            this(error, entry, itemId, price, balance, ItemListType.LIST_MAIN, NO_SLOT);
        }

        public boolean ok() {
            return error == null;
        }

        public boolean hasSlot() {
            return slot != NO_SLOT;
        }

        public int category() {
            return entry == null ? CashShopCatalog.CATEGORY_UNKNOWN : entry.section().category();
        }
    }

    public long balance() {
        GameStore store = session.store();
        var account = session.account();
        if (store == null || account == null) {
            return session.cera();
        }
        return store.cera().cera(account.mId());
    }

    public Outcome buy(Cmd67BuyCeraShopItemRequest.Line line) {
        long commodityNo = line.commodityNo();
        var scripts = session.scripts();
        var catalog = scripts == null ? null : scripts.cashShop().orElse(null);
        if (catalog == null) {
            return fail(CashShopError.UNKNOWN_COMMODITY, null);
        }
        if (!session.hasCharacter()) {
            return fail(CashShopError.BAD_REQUEST, null);
        }
        if (commodityNo < 0 || commodityNo > Integer.MAX_VALUE) {
            return fail(CashShopError.UNKNOWN_COMMODITY, null);
        }
        var entry = catalog.byCommodityNo((int) commodityNo).orElse(null);
        if (entry == null) {
            return fail(CashShopError.UNKNOWN_COMMODITY, null);
        }
        // entry.purchasable() 只来自 newcashshop.etc 的 [not stackable buy]。脚本注释原文
        // 「다중구매할수 없는 아이템의 IPG 번호 리스트」= 不能一次买多个（客户端购买界面不给选数量），不是不能买。
        // A4 这一段只有 100075 无限猫头鹰，原来整个拒成错误 20「无法购买的物品」。
        // 依据：A4 官方实抓（pair_probe town 67 买 100075 单买 / 连买 / 一次两项）三次都成功、背包叠加。
        if (!entry.purchasable() && !ServerConfig.A4) {
            return fail(CashShopError.NOT_PURCHASABLE, entry);
        }
        if (session.pvpChannel() && CeraShopSpecialItems.bannedInPvpChannel(entry.itemId())) {
            return fail(CashShopError.PVP_CHANNEL, entry);
        }

        boolean currency = entry.itemId() >= CURRENCY_GOLD && entry.itemId() <= CURRENCY_WIN_POINT;

        ItemScript item = null;
        if (!currency) {
            item = scripts.item(entry.itemId()).orElse(null);
            if (item == null) {
                return fail(CashShopError.NO_SUCH_ITEM, entry);
            }
        } else if (entry.toAvatarBag()) {
            return fail(CashShopError.NOT_AVATAR_TYPE, entry);
        }

        var special = CeraShopSpecialItems.classify(item).orElse(null);
        if (special != null) {
            return buySpecial(entry, item, special);
        }

        if (item != null && item.isCeraPackageItem()) {
            return buyPackage(entry, item, line);
        }

        ItemScript.AvatarType avatarType = null;
        if (entry.toAvatarBag()) {
            avatarType = item.avatarTypeByPeriodCode(entry.periodCode()).orElse(null);
            // A4 时装脚本没有 ACT5 的 [avatar type] 期限表（限时时装原来回错误 23）；官方期限码 1=7 天、
            // 2=30 天、3=永久，价格 = [cash] × 倍率（probe_avatar.py 实测）
            if (avatarType == null && ServerConfig.A4 && item.avatarTypeCount() == 0) {
                avatarType = a4AvatarType(entry.periodCode(), item);
            }
            if (avatarType == null) {
                return fail(CashShopError.NOT_AVATAR_TYPE, entry);
            }
        }

        int abilityNo = 0;
        if (entry.toAvatarBag()) {
            if (!item.hasAvatarOptions()) {
                return fail(CashShopError.NOT_AVATAR_TYPE, entry);
            }
            abilityNo = resolveAbilityNo(line, item, commodityNo);
            if (abilityNo < 0) {
                return fail(CashShopError.BAD_REQUEST, entry);
            }
        }

        long price = CashShopPricing.ceraPrice(entry, item, ServerConfig.CASH_SHOP_DISCOUNT);
        if (price <= 0) {
            return fail(CashShopError.NOT_AVATAR_TYPE, entry);
        }

        var inventory = session.inventory();
        if (entry.toAvatarBag()) {
            if (inventory.firstFreeAvatarSlot() < 0) {
                return fail(CashShopError.INVENTORY_FULL, entry);
            }
        } else if (currency) {
            long held = currencyOf(entry.itemId());
            if (held + entry.count() > CURRENCY_MAX) {
                return fail(CashShopError.INVENTORY_FULL, entry);
            }
        } else {
            var array = inventory.containerOf(ItemListType.LIST_MAIN);
            if (array == null) {
                return fail(CashShopError.BILLING_ERROR, entry);
            }
            boolean fits = InventoryService.canFitAll(array, List.of(),
                    List.of(new InventoryService.Stack(entry.itemId(), entry.count())),
                    id -> scripts.item(id).orElse(null));
            if (!fits) {
                return fail(CashShopError.INVENTORY_FULL, entry);
            }
        }

        GameStore store = session.store();
        var account = session.account();
        if (store == null || account == null) {
            return fail(CashShopError.BILLING_ERROR, entry);
        }
        long mId = account.mId();
        if (!store.cera().spendCera(mId, price)) {
            long balance = store.cera().cera(mId);
            session.cera(balance);
            return new Outcome(CashShopError.NOT_ENOUGH_CERA, entry,
                    Cmd67ReplyBody.NO_ITEM, 0, balance);
        }

        try {
            if (entry.toAvatarBag()) {
                int slot = inventory.grantAvatarItem(entry.itemId(), avatarType.days(),
                        abilityNo, commodityNo);
                if (slot < 0) {
                    return refund(entry, mId, price, "时装入装扮栏失败");
                }
                long balance = refreshBalance(mId);
                return new Outcome(null, entry, entry.itemId(), price, balance,
                        ItemListType.LIST_AVATAR, slot);
            }

            if (currency) {
                currencyOf(entry.itemId());
                if (!addCurrency(entry.itemId(), entry.count())) {
                    return refund(entry, mId, price, "虚拟货币入账失败");
                }
                inventory.save();
                long balance = refreshBalance(mId);
                return new Outcome(null, entry, entry.itemId(), price, balance,
                        ItemListType.LIST_MAIN, entry.itemId());
            }

            InventoryService.Landing landing = inventory.addItem(item, entry.count());
            if (!landing.ok()) {
                return refund(entry, mId, price, "物品入背包失败");
            }
            inventory.save();
            long balance = refreshBalance(mId);
            return new Outcome(null, entry, entry.itemId(), price, balance,
                    landing.itemSpace(), landing.slot());
        } catch (RuntimeException e) {
            long balance = store.cera().grantCera(mId, price);
            session.cera(balance);
            throw e;
        }
    }

    private Outcome buySpecial(CashShopCatalog.Entry entry, ItemScript item,
                               CeraShopSpecialItems.Special special) {
        var kind = special.kind();

        if (!kind.supported()) {
            return fail(CashShopError.NOT_PURCHASABLE, entry);
        }

        var premium = session.premium();
        if (kind.premium()) {
            var scripts = session.scripts();
            var catalog = scripts == null ? null : scripts.premium().orElse(null);
            if (catalog == null || catalog.byType(special.value()).isEmpty()) {
                return fail(CashShopError.NOT_PURCHASABLE, entry);
            }
        } else if (session.inventory().cargo() == null) {
            return fail(CashShopError.BILLING_ERROR, entry);
        }

        long price = CashShopPricing.ceraPrice(entry, item, ServerConfig.CASH_SHOP_DISCOUNT);
        if (price <= 0) {
            return fail(CashShopError.NOT_AVATAR_TYPE, entry);
        }

        GameStore store = session.store();
        var account = session.account();
        if (store == null || account == null) {
            return fail(CashShopError.BILLING_ERROR, entry);
        }
        long mId = account.mId();
        if (!store.cera().spendCera(mId, price)) {
            long balance = store.cera().cera(mId);
            session.cera(balance);
            return new Outcome(CashShopError.NOT_ENOUGH_CERA, entry,
                    Cmd67ReplyBody.NO_ITEM, 0, balance);
        }

        try {
            if (kind.premium()) {
                var grant = premium.grant(special.value(), entry.count());
                if (!grant.ok()) {
                    return refund(entry, mId, price, CashShopError.NOT_PURCHASABLE,
                            "会员 " + special.value() + " 开通失败");
                }
                long balance = refreshBalance(mId);
                premium.notifyGranted(kind.id(), grant);
                return Outcome.special(entry, price, balance);
            }

            var inventory = session.inventory();
            int before = inventory.cargoCapacity();
            int target = kind == CeraShopSpecialItems.Kind.CARGO
                    ? special.value() : before + special.value();
            if (!inventory.upgradeCargo(target)) {
                CashShopError error = kind == CeraShopSpecialItems.Kind.CARGO
                        ? CashShopError.NOT_AVATAR_TYPE : CashShopError.CANNOT_UPGRADE;
                return refund(entry, mId, price, error,
                        "金库扩不了（现在 " + before + " 格，想扩到 " + target + "）");
            }
            long balance = refreshBalance(mId);
            int now = inventory.cargoCapacity();
            var body = com.dylan.protocol.body.Noti66SpecialItemBody.cargo(kind.id(), now);
            session.send(com.dylan.protocol.body.Noti66SpecialItemBody.PACKET_ID,
                    body.toBytes());
            return Outcome.special(entry, price, balance);
        } catch (RuntimeException e) {
            long balance = store.cera().grantCera(mId, price);
            session.cera(balance);
            throw e;
        }
    }

    private Outcome buyPackage(CashShopCatalog.Entry entry, ItemScript pkg,
                               Cmd67BuyCeraShopItemRequest.Line request) {
        int commodityNo = entry.commodityNo();
        var scripts = session.scripts();
        if (pkg.packageData().isEmpty()) {
            return fail(CashShopError.NO_SUCH_ITEM, entry);
        }
        var contents = mergeSelections(pkg, request, commodityNo);

        var resolved = new java.util.ArrayList<ItemScript>(contents.size());
        var specials = new java.util.ArrayList<CeraShopSpecialItems.Special>(contents.size());
        for (var line : contents) {
            if (InventoryService.isCurrencyPlaceholder(line.itemId())) {
                resolved.add(null);
                specials.add(null);
                continue;
            }
            ItemScript one = scripts.item(line.itemId()).orElse(null);
            if (one == null) {
                return fail(CashShopError.NO_SUCH_ITEM, entry);
            }
            resolved.add(one);
            var special = SpecialItemService.deliverable(one).orElse(null);
            if (special != null) {
                String why = SpecialItemService.cannotApply(session, special);
                if (why != null) {
                    return fail(CashShopError.NOT_PURCHASABLE, entry);
                }
            }
            specials.add(special);
        }

        long price = CashShopPricing.ceraPrice(entry, pkg, ServerConfig.CASH_SHOP_DISCOUNT);
        if (price <= 0) {
            return fail(CashShopError.NOT_AVATAR_TYPE, entry);
        }

        var inventory = session.inventory();
        int avatarNeeded = 0;
        var mainIncoming = new java.util.ArrayList<InventoryService.Stack>();
        var creatureIncoming = new java.util.ArrayList<InventoryService.Stack>();
        long[] currencyIncoming = new long[CURRENCY_WIN_POINT + 1];
        for (int i = 0; i < contents.size(); i++) {
            var line = contents.get(i);
            ItemScript one = resolved.get(i);
            if (one == null) {
                int which = (int) line.itemId();
                currencyIncoming[which] += line.count();
                long held = currencyOf(which);
                if (held + currencyIncoming[which] > CURRENCY_MAX) {
                    return fail(CashShopError.INVENTORY_FULL, entry);
                }
                continue;
            }
            if (specials.get(i) != null) {
                continue;
            }
            switch (one.clientContainer()) {
                case ItemListType.LIST_AVATAR -> avatarNeeded++;
                case ItemListType.LIST_CREATURE -> creatureIncoming.add(
                        new InventoryService.Stack(line.itemId(), line.count()));
                default -> mainIncoming.add(
                        new InventoryService.Stack(line.itemId(), line.count()));
            }
        }
        if (avatarNeeded > inventory.freeAvatarSlots()) {
            return fail(CashShopError.INVENTORY_FULL, entry);
        }
        if (!fitsIn(ItemListType.LIST_MAIN, mainIncoming, commodityNo)
                || !fitsIn(ItemListType.LIST_CREATURE, creatureIncoming, commodityNo)) {
            return fail(CashShopError.INVENTORY_FULL, entry);
        }

        GameStore store = session.store();
        var account = session.account();
        if (store == null || account == null) {
            return fail(CashShopError.BILLING_ERROR, entry);
        }
        long mId = account.mId();
        if (!store.cera().spendCera(mId, price)) {
            long balance = store.cera().cera(mId);
            session.cera(balance);
            return new Outcome(CashShopError.NOT_ENOUGH_CERA, entry,
                    Cmd67ReplyBody.NO_ITEM, 0, balance);
        }

        try {
            var bundle = new java.util.ArrayList<Cmd67ReplyBody.BundleItem>(contents.size());
            var touched = new java.util.LinkedHashSet<Integer>();
            var picked = new java.util.ArrayList<>(request.avatarOptions());
            for (int i = 0; i < contents.size(); i++) {
                var line = contents.get(i);
                ItemScript one = resolved.get(i);
                if (one == null) {
                    int which = (int) line.itemId();
                    currencyOf(which);
                    if (!addCurrency(which, line.count())) {
                        return refund(entry, mId, price,
                                "礼包里的虚拟货币 " + which + " x" + line.count() + " 入账失败");
                    }
                    touched.add(ItemListType.LIST_MAIN);
                    bundle.add(new Cmd67ReplyBody.BundleItem(line.itemId(), line.count()));
                    continue;
                }
                if (specials.get(i) != null) {
                    SpecialItemService.apply(session, one, specials.get(i),
                            line.count());
                    bundle.add(new Cmd67ReplyBody.BundleItem(line.itemId(), line.count()));
                    continue;
                }
                if (one.clientContainer() == ItemListType.LIST_AVATAR) {
                    int abilityNo = Cmd67BuyCeraShopItemRequest.Line.takeAbilityNo(
                            picked, one.id());
                    int days = line.days() == ItemScript.PackageItem.ITEM_DEFAULT_DAYS
                            ? one.usablePeriod() : line.days();
                    int slot = inventory.grantAvatarItem(one.id(), days, abilityNo, commodityNo);
                    if (slot < 0) {
                        return refund(entry, mId, price,
                                "礼包里的时装 " + one.id() + " 入装扮栏失败");
                    }
                    touched.add(ItemListType.LIST_AVATAR);
                } else {
                    var landing = inventory.addItem(one, line.count());
                    if (!landing.ok()) {
                        return refund(entry, mId, price,
                                "礼包里的 " + one.id() + " x" + line.count() + " 入包失败");
                    }
                    touched.add(landing.itemSpace());
                }
                bundle.add(new Cmd67ReplyBody.BundleItem(line.itemId(), line.count()));
            }
            inventory.save();
            long balance = refreshBalance(mId);
            return new Outcome(null, entry, entry.itemId(), price, balance,
                    touched.isEmpty() ? Outcome.NO_LIST : touched.iterator().next(),
                    Outcome.NO_SLOT, bundle, List.copyOf(touched));
        } catch (RuntimeException e) {
            long balance = store.cera().grantCera(mId, price);
            session.cera(balance);
            throw e;
        }
    }

    private List<ItemScript.PackageItem> mergeSelections(
            ItemScript pkg, Cmd67BuyCeraShopItemRequest.Line request, int commodityNo) {
        var contents = new java.util.ArrayList<>(pkg.packageData());
        if (request.packageSelections().isEmpty()) {
            return List.copyOf(contents);
        }

        var groups = pkg.packageSelections();
        for (var pick : request.packageSelections()) {
            if (pick.itemId() != pkg.id()) {
                continue;
            }
            if (pick.groupIndex() >= groups.size()
                    || pick.itemIndex() >= groups.get(pick.groupIndex()).size()) {
                break;
            }
            var chosen = groups.get(pick.groupIndex()).get(pick.itemIndex());
            contents.add(chosen);
        }
        return List.copyOf(contents);
    }

    private boolean fitsIn(int itemSpace, List<InventoryService.Stack> incoming,
                           int commodityNo) {
        if (incoming.isEmpty()) {
            return true;
        }
        var scripts = session.scripts();
        var array = session.inventory().containerOf(itemSpace);
        if (array == null) {
            return false;
        }
        if (!InventoryService.canFitAll(array, List.of(), incoming,
                id -> scripts.item(id).orElse(null))) {
            return false;
        }
        return true;
    }

    private int resolveAbilityNo(Cmd67BuyCeraShopItemRequest.Line line, ItemScript item,
                                 long commodityNo) {
        int count = item.avatarOptionCount();
        if (!line.hasAvatarOption() && line.avatarOptions().isEmpty()) {
            int picked = RANDOM.nextInt(count);
            return picked;
        }
        int option = line.avatarOption();
        if (option < 0 || option >= count
                || option > Cmd67BuyCeraShopItemRequest.Line.MAX_AVATAR_OPTION) {
            return -1;
        }
        return option;
    }

    /**
     * A4 时装脚本没有 ACT5 的 [avatar type] 期限表，只有 [cash]。官方成品服实测（probe_avatar.py）：
     * 期限码 1 = 7 天、2 = 30 天、3 = 永久；价格 = [cash] × 期限倍率（350 / 660 / 1980），
     * 与 {@link CashShopPricing} 没有期限表时的算法一致，所以这里只补天数，cera 填 0 不参与计价。
     */
    private static ItemScript.AvatarType a4AvatarType(int periodCode, ItemScript item) {
        int days = switch (periodCode) {
            case 1 -> 7;
            case CashShopCatalog.PERIOD_30_DAYS -> 30;
            case CashShopCatalog.PERIOD_PERMANENT -> ItemScript.AvatarType.PERMANENT;
            default -> -1;
        };
        return days < 0 ? null : new ItemScript.AvatarType(days, 0, 0, 0, 0);
    }

    private long currencyOf(int itemId) {
        var inventory = session.inventory();
        return switch (itemId) {
            case CURRENCY_GOLD -> inventory.money();
            case CURRENCY_COIN -> inventory.coinTotal();
            case CURRENCY_WIN_POINT -> session.winPoint();
            default -> 0L;
        };
    }

    private boolean addCurrency(int itemId, int count) {
        var inventory = session.inventory();
        switch (itemId) {
            case CURRENCY_GOLD -> inventory.setMoney(inventory.money() + count);
            case CURRENCY_COIN -> {
                var items = inventory.inventory();
                if (items == null) {
                    return false;
                }
                inventory.setCoins(items.coin(), items.payCoin() + count, items.eventCoin());
            }
            case CURRENCY_WIN_POINT -> {
                return false;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private long refreshBalance(long mId) {
        long balance = session.store().cera().cera(mId);
        session.cera(balance);
        return balance;
    }

    private Outcome refund(CashShopCatalog.Entry entry, long mId, long price, String why) {
        return refund(entry, mId, price, CashShopError.INVENTORY_FULL, why);
    }

    private Outcome refund(CashShopCatalog.Entry entry, long mId, long price,
                           CashShopError error, String why) {
        long balance = session.store().cera().grantCera(mId, price);
        session.cera(balance);
        return new Outcome(error, entry, Cmd67ReplyBody.NO_ITEM, 0, balance);
    }

    private Outcome fail(CashShopError error, CashShopCatalog.Entry entry) {
        return new Outcome(error, entry, Cmd67ReplyBody.NO_ITEM, 0, balance());
    }
}
