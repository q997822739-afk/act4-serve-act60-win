package com.dylan.game;

import com.dylan.protocol.body.Cmd16ReplyBody;
import com.dylan.protocol.body.ItemListType;
import com.dylan.pvf.script.DungeonScript;

public final class AncientDungeonService {
    private AncientDungeonService() {
    }

    public static final int PASSED = -1;

    public static final int ERROR_NO_TICKET = Cmd16ReplyBody.ERROR_ALREADY_INSIDE;

    public static final int ERROR_INTERNAL = 1;

    private static final String MSG_NO_TICKET = "没有进入的必需品";

    public static int checkEnter(Party party, DungeonScript dungeon) {
        if (dungeon == null) {
            return PASSED;
        }
        for (DungeonScript.RequiredItem required : dungeon.requiredItems()) {
            if (!required.consume()) {
                continue;
            }
            for (int slot = 0; slot < Party.MAX_MEMBERS; slot++) {
                GameSession member = party.view().at(slot);
                if (member == null) {
                    continue;
                }
                long have = ticketCount(member, required.itemId());
                if (have < required.count()) {
                    return slot;
                }
            }
        }
        return PASSED;
    }

    public static long ticketCount(GameSession member, int itemId) {
        var inv = member.inventory();
        if (inv == null || inv.inventory() == null) {
            return 0;
        }
        return InventoryService.countOf(inv.inventory().inventory(), itemId);
    }

    public static boolean useItems(Party party, DungeonScript dungeon) {
        if (dungeon == null) {
            return true;
        }
        boolean ok = true;
        for (DungeonScript.RequiredItem required : dungeon.requiredItems()) {
            if (!required.consume()) {
                continue;
            }
            for (GameSession member : party.view().members()) {
                if (!consumeOne(member, dungeon, required)) {
                    ok = false;
                }
            }
        }
        return ok;
    }

    private static boolean consumeOne(GameSession member, DungeonScript dungeon,
                                      DungeonScript.RequiredItem required) {
        boolean[] ok = {true};
        member.run(() -> {
            var inv = member.inventory();
            var taken = InventoryService.consume(inv.inventory().inventory(),
                    required.itemId(), required.count());
            if (taken.isEmpty()) {
                ok[0] = false;
                return;
            }
            inv.markItemsDirty();
            inv.save();
            int[] slots = taken.stream().mapToInt(InventoryService.Taken::slot).toArray();
            inv.sendItemUpdate(ItemListType.LIST_MAIN,
                    "进副本 " + dungeon.id() + " 扣掉 " + required.itemId(), slots);
            member.flush();
        });
        return ok[0];
    }

    public static boolean everyoneHasTicket(Party party, DungeonScript dungeon) {
        if (dungeon == null) {
            return true;
        }
        for (DungeonScript.RequiredItem required : dungeon.requiredItems()) {
            if (!required.consume()) {
                continue;
            }
            for (GameSession member : party.view().members()) {
                if (ticketCount(member, required.itemId()) < required.count()) {
                    return false;
                }
            }
        }
        return true;
    }
}
