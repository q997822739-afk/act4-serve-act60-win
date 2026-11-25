package com.dylan.tools;

import com.dylan.game.BagLayout;
import com.dylan.game.Cargo;
import com.dylan.game.CashShopService;
import com.dylan.game.InventoryService;
import com.dylan.net.ServerConfig;
import com.dylan.pvf.GameScripts;
import com.dylan.protocol.body.ItemListType;
import com.dylan.pvf.script.ItemScript;
import com.dylan.store.CharacterCargo;
import com.dylan.store.CharacterInventory;
import com.dylan.store.GameStore;
import com.dylan.store.blob.InvenItem;
import com.dylan.store.blob.ItemArray;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class BagTool {

    private BagTool() {
    }

    private static final int MAX_ITEM_ID = 200_000;

    private static final String USAGE = """
            命令行往角色背包里放东西。新角色的背包是空的（官方也一样），所以在掉落、
            商店、任务奖励之外，这是第二条显式入口 —— 它调的是 InventoryService.grant，
            和那几条路同一个入口，不是绕过服务层直接写库。

              java -cp target\\s1a5-0.1.0-SNAPSHOT-all.jar com.dylan.tools.BagTool <子命令> [参数]

              list <charac_no>                   打这个角色的背包 / 装备栏 / 宠物栏 / 金库和四个钱数
              give <charac_no> <itemId> [数量]    放一件东西（可重复给多组 itemId 数量）
              money <charac_no> <金币>            直接设金币数
              coin <charac_no> <复活币> [点券 [活动点券]]   inventory 的三列
              winpoint <charac_no> <胜点>         pvp_result.win_point
              cera <charac_no> <点券>             member_info.cera，账号级
              cargo <charac_no> [容量]            打金库；给了容量就设 cargo_capacity（0..152）
              store <charac_no> <itemId> [数量]   直接往金库里放一件
              clear <charac_no>                  把主背包清空（虚拟货币格不动）
              find <关键字> [上限]                按名字找 itemId，给 give 用
              mail <charac_no> <itemId> <数量> <金币> [留言]   投一封系统邮件（itemId 填 0 = 只带钱）
              mailbox <charac_no>                打这个角色邮箱里的邮件和信件
              pet <charac_no> <itemId> [数量]     发一件东西进宠物栏
              pets <charac_no>                   打这个角色的 creature_items（含穿在身上的那只）
              fixpet <charac_no>                 把主背包里的宠物用品搬进宠物栏
              fixtab <charac_no>                 把主背包里放错页签的东西归位
              fixcoin <charac_no>                清掉主背包和金库里的假货币物品（物品号 0/1/2）

            pet 认三种东西，都落宠物栏：宠物本体（equipment/creature/，能带上身）、
            宠物蛋（要先孵）、改名卡和饲料（stackable/cash/creature/）。宠物物品编号
            63000 往上，用 find 按名字查。

            pet 和 mail 各是宠物栏和邮箱唯一的入口 —— 这一版没有能自动产生它们的系统。
            """;

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        if (args.length == 0) {
            out.print(USAGE);
            return;
        }
        try (GameScripts scripts = GameScripts.load(ServerConfig.PVF_PATH);
             GameStore store = GameStore.open(ServerConfig.DB_URL)) {
            switch (args[0]) {
                case "list" -> list(out, store, scripts, characNo(args, 1));
                case "give" -> give(out, store, scripts, characNo(args, 1), args);
                case "money" -> money(out, store, scripts, characNo(args, 1),
                        Long.parseLong(args[2]));
                case "coin" -> coin(out, store, characNo(args, 1), args);
                case "winpoint" -> winPoint(out, store, characNo(args, 1),
                        Long.parseLong(args[2]));
                case "cera" -> cera(out, store, characNo(args, 1), Long.parseLong(args[2]));
                case "cargo" -> cargo(out, store, scripts, characNo(args, 1),
                        args.length > 2 ? Integer.parseInt(args[2]) : -1);
                case "store" -> storeIntoCargo(out, store, scripts, characNo(args, 1), args);
                case "clear" -> clear(out, store, scripts, characNo(args, 1));
                case "find" -> find(out, scripts, args[1],
                        args.length > 2 ? Integer.parseInt(args[2]) : 30);
                case "mail" -> mail(out, store, scripts, characNo(args, 1), args);
                case "mailbox" -> mailbox(out, store, scripts, characNo(args, 1));
                case "pet" -> pet(out, store, scripts, characNo(args, 1),
                        Integer.parseInt(args[2]),
                        args.length > 3 ? Integer.parseInt(args[3]) : 1);
                case "pets" -> pets(out, store, scripts, characNo(args, 1));
                case "fixpet" -> fixPet(out, store, scripts, characNo(args, 1));
                case "fixtab" -> fixTab(out, store, scripts, characNo(args, 1));
                case "fixcoin" -> fixCoin(out, store, scripts, characNo(args, 1));
                default -> out.println("不认识的子命令：" + args[0]);
            }
        }
    }

    private static void mail(PrintStream out, GameStore store, GameScripts scripts, int characNo,
                             String[] args) {
        long itemId = args.length > 2 ? Long.parseLong(args[2]) : 0;
        long count = args.length > 3 ? Long.parseLong(args[3]) : 1;
        long gold = args.length > 4 ? Long.parseLong(args[4]) : 0;
        String text = args.length > 5 ? String.join(" ", java.util.Arrays.copyOfRange(args, 5,
                args.length)) : null;
        if (itemId != 0 && scripts.item((int) itemId).isEmpty()) {
            out.printf("物品 %d 在 pvf 里查不到，客户端会把这一条丢掉，不投%n", itemId);
            return;
        }
        long postalId = com.dylan.game.MailService.deliver(store, characNo, SYSTEM_SENDER,
                itemId, count, gold, text, true);
        out.printf("投给角色 %d：postal_id=%d，%s，金币 %d%s%n", characNo, postalId,
                itemId == 0 ? "不带物品" : "物品 " + itemId + " × " + count,
                gold, text == null ? "" : "，留言「" + text + "」");
        out.println("★ 这封是不过期的系统邮件（unlimit_flag=1）。玩家下次选角进世界就会收到 NOTI 97。");
    }

    private static final String SYSTEM_SENDER = "系统";

    private static void mailbox(PrintStream out, GameStore store, GameScripts scripts,
                                int characNo) {
        var now = java.time.Instant.now();
        var mails = store.mail().loadMails(characNo,
                0, com.dylan.store.MailRow.PAGE_SIZE);
        out.printf("角色 %d 的邮件（%d 封，一次最多拉 %d 封）%n", characNo, mails.size(),
                com.dylan.store.MailRow.PAGE_SIZE);
        for (var m : mails) {
            ItemScript s = m.itemId() == 0
                    ? null : scripts.item((int) m.itemId()).orElse(null);
            out.printf("  [%d] 来自「%s」金币 %d%s%s，%s%n", m.postalId(), m.senderName(), m.gold(),
                    m.itemId() == 0 ? "" : "，物品 " + m.itemId()
                            + (s == null ? "(pvf 里查不到)" : "「" + s.name() + "」")
                            + (m.avatar() ? "（时装，add_info 是 ui_id " + m.addInfo() + "）"
                                    : " × " + Math.max(1L, m.addInfo())),
                    m.letterId() == 0 ? "" : "，附信 " + m.letterId(),
                    m.unlimited() ? "不过期" : "还剩 " + m.remainingSeconds(now) + " 秒");
        }
        var letters = store.mail().loadLetters(characNo);
        out.printf("信件（%d 封，其中保管中 %d 封，上限 %d）%n", letters.size(),
                letters.stream().filter(l ->
                        l.stat() == com.dylan.protocol.body.LetterStat.KEPT).count(),
                com.dylan.protocol.body.LetterStat.MAX_KEPT);
        for (var l : letters) {
            out.printf("  [%d] 来自「%s」stat=%d：%s%n", l.letterId(), l.senderName(), l.stat(),
                    l.text());
        }
    }

    private static int characNo(String[] args, int i) {
        if (args.length <= i) {
            throw new IllegalArgumentException("缺 charac_no");
        }
        return Integer.parseInt(args[i]);
    }

    private static CharacterInventory load(GameStore store, int characNo) {
        return store.inventory().loadInventory(characNo).orElseThrow(
                () -> new IllegalStateException("charac_no=" + characNo + " 在 inventory 表里没有行"));
    }

    private static void list(PrintStream out, GameStore store, GameScripts scripts, int characNo) {
        CharacterInventory inv = load(store, characNo);
        out.printf("角色 %d：金币 %d，复活币 %d，点券 %d，活动点券 %d%n",
                characNo, inv.money(), inv.coin(), inv.payCoin(), inv.eventCoin());
        dump(out, scripts, "主背包", inv.inventory(), BagLayout.FIRST_ITEM_SLOT);
        dump(out, scripts, "装备栏", inv.equipslot(), 0);
        dump(out, scripts, "宠物栏", inv.creature(), 0);
    }

    private static void dump(PrintStream out, GameScripts scripts, String label,
                            ItemArray array, int firstSlot) {
        out.printf("  %s（%d 格，用了 %d）%n", label, array.slots(), array.usedSlots());
        for (int slot = firstSlot; slot < array.slots(); slot++) {
            InvenItem item = array.get(slot);
            if (item.isEmpty()) {
                continue;
            }
            ItemScript s = scripts.item((int) item.itemId()).orElse(null);
            BagLayout.Section sec = BagLayout.tabAt(slot);
            out.printf("    [%3d] %-8d %-24s %s%s%n", slot, item.itemId(),
                    s == null ? "(pvf 里查不到)" : s.name(), item,
                    sec == null ? (BagLayout.QUICK.contains(slot) ? "  ←快捷栏" : "")
                            : "  ←" + sec.label());
        }
    }

    private static void give(PrintStream out, GameStore store, GameScripts scripts, int characNo,
                             String[] args) {
        CharacterInventory inv = load(store, characNo);
        for (int i = 2; i < args.length; i += 2) {
            int itemId = Integer.parseInt(args[i]);
            int count = i + 1 < args.length ? Integer.parseInt(args[i + 1]) : 1;
            ItemScript script = scripts.item(itemId).orElse(null);
            if (script == null) {
                out.printf("  ！物品 %d 在 pvf 里查不到，不放 —— 客户端也认不出它%n", itemId);
                continue;
            }
            InventoryService.Landing landing =
                    InventoryService.addItem(store, characNo, inv, script, count);
            if (!landing.ok()) {
                out.printf("  ！背包满了，%s x%d 没放进去%n", script.name(), count);
                continue;
            }
            int slot = landing.slot();
            if (landing.itemSpace() == ItemListType.LIST_CREATURE) {
                out.printf("  宠物栏第 %d 格 ← %s x%d%n", slot, script.name(), count);
            } else {
                out.printf("  第 %d 格 ← %s x%d（%s）%n", slot, script.name(), count,
                        BagLayout.QUICK.contains(slot) ? "快捷栏" : BagLayout.tabAt(slot).label());
            }
        }
        store.inventory().saveInventory(inv);
        out.println("已落库。重新选一次角色就能在背包里看到。");
    }

    private static void pet(PrintStream out, GameStore store, GameScripts scripts, int characNo,
                            int itemId, int count) {
        ItemScript script = scripts.item(itemId).orElse(null);
        if (script == null) {
            out.printf("！物品 %d 在 pvf 里查不到，不发 —— 客户端也认不出它%n", itemId);
            return;
        }
        if (script.clientContainer() != ItemListType.LIST_CREATURE) {
            out.printf("！%s（%d）不落宠物栏（clientContainer=%d），换 give 试试%n",
                    script.name(), itemId, script.clientContainer());
            return;
        }
        CharacterInventory inv = load(store, characNo);
        if (!script.isCreature()) {
            InventoryService.Landing landing =
                    InventoryService.addItem(store, characNo, inv, script, count);
            if (!landing.ok()) {
                out.println("！宠物栏满了");
                return;
            }
            store.inventory().saveCreatureItems(characNo, inv.creature());
            out.printf("宠物栏第 %d 格 ← %s（%d）×%d —— 不是宠物本体，没有 creature_items 行%n",
                    landing.slot(), script.name(), itemId, count);
            out.println("已落库。重新选一次角色就能在宠物栏里看到。");
            return;
        }
        var row = InventoryService.grantCreature(store, characNo, inv, script);
        if (row == null) {
            out.println("！宠物栏满了，没放进去（库里也没多出一行）");
            return;
        }
        int slot = row.slot();
        store.inventory().saveCreatureItems(characNo, inv.creature());
        out.printf("宠物栏第 %d 格 ← %s（%d），creature_type=%d%s%n", slot, script.name(), itemId,
                script.creatureType(),
                script.creatureType() == ItemScript.CREATURE_TYPE_BODY
                        ? "（宠物本体，能带在身上）"
                        : "（[sub type]=" + script.subType() + "，是蛋/胶囊，带不上身）");
        out.println("已落库。重新选一次角色，拖到装备栏第 "
                + com.dylan.protocol.body.EquipmentSlot.CREATURE + " 格就能带上。");
    }

    private static void fixPet(PrintStream out, GameStore store, GameScripts scripts,
                               int characNo) {
        CharacterInventory inv = load(store, characNo);
        ItemArray bag = inv.inventory();
        int moved = 0;
        for (int slot = BagLayout.FIRST_ITEM_SLOT; slot < bag.slots(); slot++) {
            InvenItem item = bag.get(slot);
            if (item.isEmpty()) {
                continue;
            }
            ItemScript script = scripts.item((int) item.itemId()).orElse(null);
            if (script == null
                    || !InventoryService.isCreatureItemType(
                            InventoryService.itemTypeOf(script))) {
                continue;
            }
            var landing = InventoryService.addItem(store, characNo, inv, script,
                    (int) item.stackCount());
            if (!landing.ok()) {
                out.printf("！宠物栏满了，第 %d 格的 %s 留在主背包%n", slot, script.name());
                break;
            }
            bag.set(slot, InvenItem.EMPTY);
            moved++;
            out.printf("  主背包第 %d 格 → 宠物栏第 %d 格：%s ×%d%n",
                    slot, landing.slot(), script.name(), item.stackCount());
        }
        if (moved == 0) {
            out.println("主背包里没有该进宠物栏的东西，什么都没动。");
            return;
        }
        store.inventory().saveInventory(inv);
        out.printf("搬了 %d 格，已落库。重新选一次角色就能在宠物栏里看到。%n", moved);
    }

    private static void fixTab(PrintStream out, GameStore store, GameScripts scripts,
                               int characNo) {
        CharacterInventory inv = load(store, characNo);
        ItemArray bag = inv.inventory();
        int moved = 0;
        int stuck = 0;

        for (int slot = 0; slot < BagLayout.FIRST_ITEM_SLOT && slot < bag.slots(); slot++) {
            InvenItem item = bag.get(slot);
            if (item.isEmpty()) {
                continue;
            }
            ItemScript script = scripts.item((int) item.itemId()).orElse(null);
            BagLayout.Section home = BagLayout.of(script);
            int free = firstFreeSlot(bag, home);
            if (free < 0) {
                out.printf("！%s页满了，虚拟货币格第 %d 格上的 %s 还留在那儿%n",
                        home.label(), slot,
                        script == null ? "物品 " + item.itemId() : script.name());
                stuck++;
                continue;
            }
            bag.set(free, item);
            bag.set(slot, InvenItem.EMPTY);
            moved++;
            out.printf("  ★ 虚拟货币格第 %d 格 → 第 %d 格（%s页）：%s ×%d"
                            + "  ← 这一格的东西客户端从来没收到过%n",
                    slot, free, home.label(),
                    script == null ? "物品 " + item.itemId() : script.name(),
                    item.stackCount());
        }

        for (int slot = BagLayout.FIRST_ITEM_SLOT;
             slot <= BagLayout.LAST_TAB_SLOT && slot < bag.slots(); slot++) {
            BagLayout.Section here = BagLayout.tabAt(slot);
            if (here == null) {
                continue;
            }
            InvenItem item = bag.get(slot);
            if (item.isEmpty()) {
                continue;
            }
            ItemScript script = scripts.item((int) item.itemId()).orElse(null);
            if (script == null) {
                continue;
            }
            BagLayout.Section home = BagLayout.of(script);
            if (home == here) {
                continue;
            }
            int free = firstFreeSlot(bag, home);
            if (free < 0) {
                out.printf("！%s页满了，第 %d 格的 %s 留在%s页%n",
                        home.label(), slot, script.name(), here.label());
                stuck++;
                continue;
            }
            bag.set(free, item);
            bag.set(slot, InvenItem.EMPTY);
            moved++;
            out.printf("  第 %d 格（%s页）→ 第 %d 格（%s页）：%s ×%d%n",
                    slot, here.label(), free, home.label(), script.name(), item.stackCount());
        }
        if (moved == 0) {
            out.println(stuck == 0
                    ? "主背包里没有放错页的东西，什么都没动。"
                    : "有 " + stuck + " 格该搬但目标页满了，什么都没动。");
            return;
        }
        store.inventory().saveInventory(inv);
        out.printf("归位 %d 格%s，已落库。重新选一次角色就能在对的页签里看到。%n",
                moved, stuck == 0 ? "" : "（还有 " + stuck + " 格因为目标页满没搬）");
    }

    private static int firstFreeSlot(ItemArray bag, BagLayout.Section section) {
        for (int slot = section.firstSlot();
             slot <= section.lastSlot() && slot < bag.slots(); slot++) {
            if (bag.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static void pets(PrintStream out, GameStore store, GameScripts scripts,
                             int characNo) {
        var rows = store.creature().loadCreatureItems(characNo);
        out.printf("角色 %d 的宠物（creature_items，%d 只）%n", characNo, rows.size());
        for (var row : rows) {
            ItemScript s = scripts.item(row.itemId()).orElse(null);
            out.printf("  ui_id=%-6d %-8d %-20s %s  饱食度 %d  经验 %d  type=%d  名字「%s」%n",
                    row.uiId(), row.itemId(), s == null ? "(pvf 里查不到)" : s.name(),
                    row.equipped()
                            ? "穿在装备栏第 " + row.equipSlot() + " 格"
                            : "宠物栏第 " + row.bagSlot() + " 格",
                    row.stomach(), row.exp(), row.creatureType(),
                    row.name() == null ? "" : row.name());
        }
    }

    private static void money(PrintStream out, GameStore store, GameScripts scripts, int characNo,
                              long amount) {
        CharacterInventory inv = load(store, characNo);
        store.inventory().saveInventory(new CharacterInventory(characNo, amount, inv.coin(), inv.payCoin(),
                inv.eventCoin(), inv.equipslot(), inv.inventory(), inv.creature()));
        out.printf("角色 %d 的金币设成 %d，已落库。%n", characNo, amount);
    }

    private static void coin(PrintStream out, GameStore store, int characNo, String[] args) {
        CharacterInventory inv = load(store, characNo);
        long coin = Long.parseLong(args[2]);
        long pay = args.length > 3 ? Long.parseLong(args[3]) : inv.payCoin();
        long event = args.length > 4 ? Long.parseLong(args[4]) : inv.eventCoin();
        store.inventory().saveInventory(new CharacterInventory(characNo, inv.money(), coin, pay, event,
                inv.equipslot(), inv.inventory(), inv.creature()));
        out.printf("角色 %d：复活币 %d + 点券 %d + 活动点券 %d = 背包槽 1 显示 %d，已落库。%n",
                characNo, coin, pay, event, coin + pay + event);
    }

    private static void winPoint(PrintStream out, GameStore store, int characNo, long value) {
        if (!store.pvp().setWinPoint(characNo, value)) {
            out.printf("！角色 %d 在 pvp_result 里没有行（建角色时该插的）%n", characNo);
            return;
        }
        out.printf("角色 %d 的决斗胜点设成 %d，已落库。重新选一次角色就能在背包里看到。%n",
                characNo, value);
    }

    private static void cera(PrintStream out, GameStore store, int characNo, long value) {
        Long mId = store.character().accountIdOf(characNo).orElse(null);
        if (mId == null) {
            out.printf("！找不到角色 %d%n", characNo);
            return;
        }
        if (!store.cera().setCera(mId, value)) {
            out.printf("！账号 %d 不在 member_info 里%n", mId);
            return;
        }
        out.printf("账号 %d（角色 %d 所属）的点券设成 %d，已落库。%n", mId, characNo, value);
    }

    private static void cargo(PrintStream out, GameStore store, GameScripts scripts, int characNo,
                             int capacity) {
        CharacterCargo box = store.inventory().loadCargo(characNo)
                .orElseGet(() -> CharacterCargo.empty(characNo));
        if (capacity >= 0) {
            if (capacity > ItemArray.CARGO_SLOTS) {
                out.printf("！容量最多 %d 格（官方的上界）%n",
                        ItemArray.CARGO_SLOTS);
                return;
            }
            box = new CharacterCargo(characNo, capacity, box.items());
            store.inventory().saveCargo(box);
            out.printf("角色 %d 的金库容量设成 %d（实际可用 %d 格），已落库。%n",
                    characNo, capacity, box.usableSlots());
        }
        out.printf("角色 %d 的金库：%d 格可用（cargo_capacity=%d），放了 %d 件%n",
                characNo, box.usableSlots(), box.capacity(), box.items().usedSlots());
        dump(out, scripts, "金库", box.items(), 0);
    }

    private static void storeIntoCargo(PrintStream out, GameStore store, GameScripts scripts,
                                       int characNo, String[] args) {
        CharacterCargo box = store.inventory().loadCargo(characNo)
                .orElseGet(() -> CharacterCargo.empty(characNo));
        ItemArray items = box.items();
        for (int i = 2; i < args.length; i += 2) {
            int itemId = Integer.parseInt(args[i]);
            int count = i + 1 < args.length ? Integer.parseInt(args[i + 1]) : 1;
            ItemScript script = scripts.item(itemId).orElse(null);
            if (script == null) {
                out.printf("  ！物品 %d 在 pvf 里查不到，不放%n", itemId);
                continue;
            }
            InvenItem item = InventoryService.newItem(script, count);
            int slot = Cargo.getEmptySlot(items, box.usableSlots());
            if (slot < 0) {
                out.printf("  ！金库满了（%d 格），%s x%d 没放进去%n",
                        box.usableSlots(), script.name(), count);
                continue;
            }
            int landed = Cargo.insert(items, box.usableSlots(), item, slot,
                    id -> scripts.item(id).orElse(null));
            if (landed < 0) {
                out.printf("  ！%s x%d 放不下（%d）%n", script.name(), count, landed);
                continue;
            }
            out.printf("  金库第 %d 格 ← %s x%d%n", landed, script.name(), count);
        }
        store.inventory().saveCargo(box);
        out.println("已落库。重新选一次角色就能在金库里看到。");
    }

    private static void fixCoin(PrintStream out, GameStore store, GameScripts scripts,
                                int characNo) {
        CharacterInventory inv = load(store, characNo);
        CharacterCargo box = store.inventory().loadCargo(characNo)
                .orElseGet(() -> CharacterCargo.empty(characNo));
        long[] found = new long[3];
        int cleared = sweepCurrency(out, inv.inventory(), BagLayout.FIRST_ITEM_SLOT,
                "主背包", found);
        cleared += sweepCurrency(out, box.items(), 0, "金库", found);
        if (cleared == 0) {
            out.println("主背包和金库里没有假货币物品，什么都没动。");
            return;
        }

        long money = Math.min(CashShopService.CURRENCY_MAX,
                inv.money() + found[BagLayout.SLOT_GOLD]);
        long coin = Math.min(CashShopService.CURRENCY_MAX,
                inv.coin() + found[BagLayout.SLOT_COIN]);
        store.inventory().saveInventory(new CharacterInventory(characNo, money, coin,
                inv.payCoin(), inv.eventCoin(), inv.equipslot(), inv.inventory(), inv.creature()));
        store.inventory().saveCargo(box);
        out.printf("清掉 %d 格。金币 %d → %d，复活币（coin 列）%d → %d。%n",
                cleared, inv.money(), money, inv.coin(), coin);
        if (found[BagLayout.SLOT_WIN_POINT] > 0) {
            out.printf("！还清掉了 %d 个假决斗胜点 —— 它在 pvp_result 那张表上，"
                            + "要补回去用：winpoint %d <胜点>%n",
                    found[BagLayout.SLOT_WIN_POINT], characNo);
        }
        out.println("已落库。重新选一次角色就能看到。");
    }

    private static int sweepCurrency(PrintStream out, ItemArray array, int firstSlot,
                                     String label, long[] found) {
        int cleared = 0;
        for (int slot = firstSlot; slot < array.slots(); slot++) {
            InvenItem item = array.get(slot);
            if (item.isEmpty() || !InventoryService.isCurrencyPlaceholder(item.itemId())) {
                continue;
            }
            long count = item.stackCount();
            found[(int) item.itemId()] += count;
            array.set(slot, InvenItem.EMPTY);
            cleared++;
            out.printf("  %s第 %d 格：假货币物品 %d ×%d，清掉%n",
                    label, slot, item.itemId(), count);
        }
        return cleared;
    }

    private static void clear(PrintStream out, GameStore store, GameScripts scripts, int characNo) {
        CharacterInventory inv = load(store, characNo);
        ItemArray bag = inv.inventory();
        int removed = 0;
        for (int slot = BagLayout.FIRST_ITEM_SLOT; slot < bag.slots(); slot++) {
            if (!bag.get(slot).isEmpty()) {
                bag.set(slot, InvenItem.EMPTY);
                removed++;
            }
        }
        store.inventory().saveInventoryItems(characNo, bag);
        out.printf("清掉 %d 件，已落库（0/1/2 号虚拟货币格没动）。%n", removed);
    }

    private static void find(PrintStream out, GameScripts scripts, String keyword, int limit) {
        List<ItemScript> hits = new ArrayList<>();
        for (int id = 0; id < MAX_ITEM_ID && hits.size() < limit; id++) {
            ItemScript s = scripts.item(id).orElse(null);
            if (s == null) {
                continue;
            }
            if ((s.name() != null && s.name().contains(keyword))
                    || (s.path() != null && s.path().contains(keyword))) {
                hits.add(s);
            }
        }
        for (ItemScript s : hits) {
            out.printf("  %-8d %-28s 耐久 %-5d 堆叠上限 %-5d %s%n",
                    s.id(), s.name(), s.durability(), s.stackLimit(), s.path());
        }
        out.printf("共 %d 条（上限 %d）。%n", hits.size(), limit);
    }
}
