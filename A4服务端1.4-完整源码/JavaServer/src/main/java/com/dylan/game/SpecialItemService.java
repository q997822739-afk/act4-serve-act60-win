package com.dylan.game;

import java.util.Optional;

import com.dylan.protocol.body.Noti66SpecialItemBody;
import com.dylan.pvf.script.ItemScript;
import com.dylan.pvf.script.PremiumCatalog;

public final class SpecialItemService {

    private SpecialItemService() {
    }

    public static Optional<CeraShopSpecialItems.Special> deliverable(ItemScript item) {
        var special = CeraShopSpecialItems.classify(item).orElse(null);
        if (special == null) {
            return Optional.empty();
        }
        if (!special.kind().supported()) {
            return Optional.empty();
        }
        return Optional.of(special);
    }

    public static String cannotApply(GameSession session, CeraShopSpecialItems.Special special) {
        if (special.kind().premium()) {
            var scripts = session.scripts();
            var catalog = scripts == null ? null : scripts.premium().orElse(null);
            if (catalog == null || catalog.byType(special.value()).isEmpty()) {
                return "会员 " + special.value() + " 不在 " + PremiumCatalog.PATH + " 里";
            }
            return null;
        }
        return session.inventory().cargo() == null ? "没有金库数据源" : null;
    }

    public record Applied(boolean ok, String detail) {

        static Applied failed(String why) {
            return new Applied(false, why);
        }
    }

    public static Applied apply(GameSession session, ItemScript item,
                                CeraShopSpecialItems.Special special, int count) {
        var kind = special.kind();
        if (kind.premium()) {
            PremiumService premium = session.premium();
            PremiumService.Grant grant = premium.grant(special.value(), count);
            if (!grant.ok()) {
                return Applied.failed(item.name() + "（会员 " + special.value() + " 开通失败）");
            }
            premium.notifyGranted(kind.id(), grant);
            return new Applied(true, item.name() + " → 会员 " + special.value());
        }

        InventoryService inventory = session.inventory();
        int before = inventory.cargoCapacity();
        int target = kind == CeraShopSpecialItems.Kind.CARGO
                ? special.value() : before + special.value();
        if (!inventory.upgradeCargo(target)) {
            return Applied.failed(item.name() + "（金库扩不动：现在 " + before
                    + " 格，想扩到 " + target + "）");
        }
        int now = inventory.cargoCapacity();
        session.send(Noti66SpecialItemBody.PACKET_ID,
                Noti66SpecialItemBody.cargo(kind.id(), now).toBytes());
        return new Applied(true, item.name() + " → 金库 " + now + " 格");
    }
}
