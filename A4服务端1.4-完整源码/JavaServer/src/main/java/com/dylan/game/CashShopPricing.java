package com.dylan.game;

import com.dylan.pvf.script.CashShopCatalog;
import com.dylan.pvf.script.ItemScript;

public final class CashShopPricing {

    private static final double DISCOUNT_RATE = 0.8f;

    private static final double MULTIPLIER_30_DAYS = 2.2f;

    private static final double MULTIPLIER_PERMANENT = 3.0f;

    private CashShopPricing() {
    }

    public static long ceraPrice(CashShopCatalog.Entry entry, ItemScript item,
                                 boolean discountActive) {
        long price = basePrice(entry, item);
        if (price <= 0) {
            return 0L;
        }
        if (discountActive) {
            price = (long) ((double) price * DISCOUNT_RATE);
        }
        if (fromAvatarTypeTable(entry, item)) {
            return price;
        }
        return applyPeriodMultiplier(price, entry.periodCode());
    }

    public static long basePrice(CashShopCatalog.Entry entry, ItemScript item) {
        if (!entry.priceFromItemScript()) {
            return Math.max(0, entry.ceraPrice());
        }
        if (item == null) {
            return 0L;
        }
        var type = item.avatarTypeByPeriodCode(entry.periodCode());
        if (type.isPresent()) {
            return Math.max(0, type.get().cera());
        }
        return Math.max(0, item.cash());
    }

    private static boolean fromAvatarTypeTable(CashShopCatalog.Entry entry, ItemScript item) {
        return entry.priceFromItemScript() && item != null
                && item.avatarTypeByPeriodCode(entry.periodCode()).isPresent();
    }

    public static long applyPeriodMultiplier(long price, int periodCode) {
        return switch (periodCode) {
            case CashShopCatalog.PERIOD_30_DAYS -> (long) ((double) price * MULTIPLIER_30_DAYS);
            case CashShopCatalog.PERIOD_PERMANENT -> {
                long thirtyDays = (long) ((double) price * MULTIPLIER_30_DAYS);
                yield (long) ((double) thirtyDays * MULTIPLIER_PERMANENT);
            }
            default -> price;
        };
    }
}
