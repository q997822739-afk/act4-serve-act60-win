package com.dylan.tools;

import com.dylan.game.InventoryService;
import com.dylan.game.TutorialFlags;
import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Cmd4ReplyBody;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.ItemScript;
import com.dylan.store.CharacterInventory;
import com.dylan.store.CharacterQuests;
import com.dylan.store.CharacterSkills;
import com.dylan.store.FatigueRow;
import com.dylan.store.GameStore;
import com.dylan.store.QuestClearFlags;
import com.dylan.store.blob.InvenItem;
import com.dylan.store.blob.ItemArray;
import com.dylan.store.blob.SkillSlots;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 把官方成品服 DF60ACT4 的存档（dnf2008.sqlite）里一个账号的全部角色导进 A5 库，给两边逐包对照用。
 *
 * <pre>
 *   java -Ds1a5.dbUrl=jdbc:sqlite:目标.db -Ds1a5.clientDir=客户端目录 -cp s1a5-all.jar \
 *        com.dylan.tools.OfficialSaveImport &lt;官方存档.sqlite&gt; &lt;官方账号名&gt; &lt;A5 账号&gt; [密码]
 * </pre>
 *
 * 只导数据，不碰服务逻辑：建角走 {@code CharacterStore.createCharacter}（和客户端建角同一条写库路径），
 * 之后各表都用 A5 自己的 store / blob 编码写回。A5 账号必须是新的（已有角色就拒绝，防止覆盖用户存档）。
 *
 * <p>官方 → A5 的对应关系（都是对官方服实抓核对过的：官方存档里的槽号就是线上的槽号）：
 * <ul>
 *   <li>character_items.slot_index = NOTI 13/0 槽号 = A5 inventory 数组下标；槽 0/1/2 是金币 / 复活币 / 胜点的
 *       虚拟格（官方另有 characters.gold / pvp_points 列，item_count 只是镜像），不进数组。</li>
 *   <li>character_equipment.slot_index 9..18 = A5 {@code EquipmentSlot}（武器 9 … 戒指 18）；
 *       item_state = 强化等级（NOTI 2/0 装备项第 7 字节）。</li>
 *   <li>character_avatars 0..7 = 穿着的时装：装备栏同槽 + user_items 一行（slot = 装备槽）；
 *       durability 是 ability_no（NOTI 2/1 时装项里的那个字节）。</li>
 *   <li>character_creature_items.slot_index = NOTI 13/7 槽号 = A5 creature 数组下标。</li>
 *   <li>character_skills.slot_index 0..221 与 A5 skill_slot 下标同一布局（快捷栏 0..5、各页 6..215）。</li>
 *   <li>characters.exp 是本级内经验（满级号全是 0），A5 存总经验 → 加上 exptable 的本级下限。</li>
 * </ul>
 * 未导：仓库（存档里 0 条）、邮件（0 条）、账号会员（类型编号未对过）。
 */
public final class OfficialSaveImport {

    private OfficialSaveImport() {
    }

    /** 官方 character_items 槽 0..2 是虚拟货币格。 */
    private static final int FIRST_REAL_ITEM_SLOT = 3;

    private static final int CHARACTER_SEX = 1;

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        if (args.length < 3) {
            out.println("用法：OfficialSaveImport <官方存档.sqlite> <官方账号名> <A5 账号> [密码]");
            System.exit(2);
        }
        String src = args[0];
        String officialAccount = args[1];
        String userId = args[2];
        String password = args.length > 3 ? args[3] : "1";

        try (Connection off = DriverManager.getConnection("jdbc:sqlite:file:" + src + "?mode=ro");
             GameScripts scripts = GameScripts.load(ServerConfig.PVF_PATH);
             GameStore store = GameStore.open(ServerConfig.DB_URL)) {
            long officialId = officialAccountId(off, officialAccount);
            var account = store.account().findOrCreateAccount(userId, password);
            if (store.character().characterCount(account.mId()) > 0) {
                throw new IllegalStateException("A5 账号 " + userId + " 已经有角色了，换一个新账号名（不覆盖已有存档）");
            }
            List<Long> characters = new ArrayList<>();
            try (PreparedStatement ps = off.prepareStatement(
                    "SELECT character_id FROM characters WHERE account_id = ? ORDER BY slot_index")) {
                ps.setLong(1, officialId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        characters.add(rs.getLong(1));
                    }
                }
            }
            setSlotLimit(account.mId(), Math.max(characters.size(),
                    com.dylan.store.AccountStore.DEFAULT_CHARACTER_SLOTS));

            Warnings warn = new Warnings(out);
            long cera = 0;
            int index = 0;
            for (long officialChar : characters) {
                cera = Math.max(cera, importCharacter(off, store, scripts, account.mId(), officialChar,
                        index++, out, warn));
            }
            store.cera().setCera(account.mId(), cera);
            importPremium(off, store, officialId, account.mId(), out);
            store.dungeonClear().saveDungeonClear(account.mId(), dungeonProgress(off, officialId));
            store.account().markTutorialSkipable(account.mId());
            out.println("完成：" + characters.size() + " 个角色 → A5 账号 " + userId
                    + "（m_id=" + account.mId() + "），点券 " + cera + "，警告 " + warn.count + " 条");
        }
    }

    private static final class Warnings {
        final PrintStream out;
        int count;

        Warnings(PrintStream out) {
            this.out = out;
        }

        void add(String msg) {
            count++;
            out.println("  ⚠ " + msg);
        }
    }

    private static long officialAccountId(Connection off, String name) throws SQLException {
        try (PreparedStatement ps = off.prepareStatement(
                "SELECT account_id FROM accounts WHERE account_name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("官方存档里没有账号 " + name);
                }
                return rs.getLong(1);
            }
        }
    }

    /** member_info.slot 没有 store 方法（A5 只在建号时写 16）；官方号 20 个角色要放得下。 */
    private static void setSlotLimit(long mId, int slots) throws SQLException {
        try (Connection c = DriverManager.getConnection(ServerConfig.DB_URL);
             PreparedStatement ps = c.prepareStatement("UPDATE member_info SET slot = ? WHERE m_id = ?")) {
            ps.setInt(1, slots);
            ps.setLong(2, mId);
            ps.executeUpdate();
        }
    }

    private static long importCharacter(Connection off, GameStore store, GameScripts scripts, long mId,
                                        long officialChar, int index, PrintStream out, Warnings warn)
            throws SQLException {
        String name;
        int job, growType, level, skillPoints, townId, stamina, storageCapacity;
        long exp, gold, cash, pvpPoints;
        try (PreparedStatement ps = off.prepareStatement("""
                SELECT name, job, grow_type, level, exp, skill_points, gold, cash, pvp_points, town_id, stamina,
                       storage_capacity
                  FROM characters WHERE character_id = ?""")) {
            ps.setLong(1, officialChar);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                name = rs.getString(1);
                job = rs.getInt(2);
                growType = rs.getInt(3);
                level = rs.getInt(4);
                exp = rs.getLong(5);
                skillPoints = rs.getInt(6);
                gold = rs.getLong(7);
                cash = rs.getLong(8);
                pvpPoints = rs.getLong(9);
                townId = rs.getInt(10);
                stamina = rs.getInt(11);
                storageCapacity = rs.getInt(12);
            }
        }

        SkillSlots skills = SkillSlots.empty();
        forEach(off, "SELECT slot_index, skill_id, skill_level FROM character_skills WHERE character_id = ?",
                officialChar, rs -> {
                    int slot = rs.getInt(1);
                    if (slot < 0 || slot >= SkillSlots.SLOTS) {
                        warn.add(name + " 技能槽 " + slot + " 超出 A5 的 " + SkillSlots.SLOTS + " 格，跳过");
                        return;
                    }
                    skills.set(slot, rs.getInt(2), rs.getInt(3));
                });

        int characNo = store.character().createCharacter(mId, name, job, CHARACTER_SEX, skills.encode(), 0);
        store.character().saveGrowType(characNo, growType);
        var table = scripts.expTable();
        long totalExp = (table == null ? 0 : table.levelBottom(level)) + exp;
        store.character().saveExperience(characNo, level, totalExp);
        store.skill().saveSkills(new CharacterSkills(characNo, skillPoints, skills, SkillSlots.empty()));
        store.character().saveCharacterVillage(characNo, townId);
        store.stamina().saveStamina(characNo, stamina);
        store.pvp().setWinPoint(characNo, pvpPoints);

        // 官方教程打完 = REPLY 4 尾部 FFFFFFFF（满级号全部 tutorial_started=1）
        var tutorials = TutorialFlags.of(0, new byte[TutorialFlags.HIGH_BYTES]);
        for (int bit = 0; bit <= Cmd4ReplyBody.A4_TUTORIAL_FINAL; bit++) {
            tutorials.set(bit);
        }
        store.character().saveTutorialFlag(characNo, tutorials.lowFlag(), tutorials.highBytes());

        forEach(off, "SELECT consumed FROM character_fatigue WHERE character_id = ?", officialChar,
                rs -> store.character().saveFatigue(characNo,
                        new FatigueRow(0, rs.getInt(1), 0, 0, Instant.EPOCH)));

        ItemArray equipslot = ItemArray.emptyEquipslot();
        ItemArray inventory = ItemArray.emptyInventory();
        ItemArray creature = ItemArray.emptyCreature();
        long[] revive = {0};

        // 官方 NOTI 2/1 装备项：addInfo = item_count（新角色武器也是 1）、耐久 = durability 列（存档里是 0，
        // 新角色是脚本耐久）→ 两列照搬，不用 newItem 的随机品级码和脚本耐久
        forEach(off, """
                SELECT slot_index, item_id, item_state, enchant_card_id, item_count, durability
                  FROM character_equipment WHERE character_id = ?""", officialChar, rs -> {
            int slot = rs.getInt(1);
            InvenItem item = item(scripts, rs.getInt(2), 1, name, warn);
            if (item == null) {
                return;
            }
            if (slot >= equipslot.slots()) {
                warn.add(name + " 装备槽 " + slot + " 超出 A5 装备栏，跳过 " + rs.getInt(2));
                return;
            }
            equipslot.set(slot, item.withUpgrade(rs.getInt(3)).withEnchant(rs.getLong(4))
                    .withAddInfo(rs.getLong(5)).withEndurance(rs.getInt(6)));
        });

        forEach(off, """
                SELECT slot_index, item_id, durability, expires_at
                  FROM character_avatars WHERE character_id = ?""", officialChar, rs -> {
            int slot = rs.getInt(1);
            int itemId = rs.getInt(2);
            int abilityNo = rs.getInt(3);
            long expires = rs.getLong(4);
            if (slot >= InventoryService.AVATAR_BAG_SLOT_BASE) {
                warn.add(name + " 时装槽 " + slot + "（装扮栏里的，不是穿着的）未导：" + itemId);
                return;
            }
            InvenItem scripted = item(scripts, itemId, 1, name, warn);
            if (scripted == null) {
                return;
            }
            var row = store.avatar().insertAvatarItemAt(characNo, slot, itemId,
                    expires == 0 ? null : Instant.ofEpochSecond(expires), abilityNo, "0", 0);
            // 与 InventoryService.avatarInvenItem 同形：装备栏那格 addInfo = user_items.ui_id、耐久位 = ability_no
            equipslot.set(slot, InvenItem.of(itemId, scripted.itemType(), row.uiId())
                    .withEndurance(abilityNo));
        });

        // 背包封印：官方 NOTI 13/0 封印字节 = seal_state 列；A5 的 attachType 留着脚本的值（交易属性），
        // 只在官方已解封（seal_state 0）时把 [sealing] 清成 0（同 InventoryService.unsealWorn）
        // 背包里的装备同装备栏：NOTI 13/0 数量 = item_count、属性 = item_state（强化）、耐久 = durability 列
        forEach(off, """
                SELECT slot_index, item_id, item_count, seal_state, item_state, durability
                  FROM character_items WHERE character_id = ?""",
                officialChar, rs -> {
                    int slot = rs.getInt(1);
                    int count = rs.getInt(3);
                    int seal = rs.getInt(4);
                    int state = rs.getInt(5);
                    int durability = rs.getInt(6);
                    if (slot == 1) {
                        revive[0] = count;
                    }
                    if (slot < FIRST_REAL_ITEM_SLOT) {
                        return;
                    }
                    if (slot >= inventory.slots()) {
                        warn.add(name + " 背包槽 " + slot + " 超出 A5 的 " + inventory.slots() + " 格，跳过");
                        return;
                    }
                    InvenItem item = item(scripts, rs.getInt(2), count, name, warn);
                    if (item == null) {
                        return;
                    }
                    item = withSeal(item, seal);
                    if (item.addInfo() != count) {
                        // 不可叠加（装备）：newItem 没写数量 → 照搬三列
                        item = item.withAddInfo(count).withItemAttr(state).withEndurance(durability);
                    }
                    inventory.set(slot, item);
                });

        forEach(off, "SELECT slot_index, item_id, item_count, seal_state FROM character_creature_items WHERE character_id = ?",
                officialChar, rs -> {
                    int slot = rs.getInt(1);
                    if (slot >= creature.slots()) {
                        warn.add(name + " 宠物栏槽 " + slot + " 超出 A5 的 " + creature.slots() + " 格，跳过");
                        return;
                    }
                    InvenItem item = item(scripts, rs.getInt(2), rs.getInt(3), name, warn);
                    if (item != null) {
                        // 官方 NOTI 13/7 数量 = item_count（宠物装备 1）；宠物本体那格官方发的是 34，来源未明，照 item_count 填
                        creature.set(slot, withSeal(item, rs.getInt(4)).withAddInfo(rs.getInt(3)));
                    }
                });

        store.inventory().saveInventory(new CharacterInventory(characNo, gold, revive[0], 0, 0,
                equipslot, inventory, creature));
        // 官方 NOTI 13/2 首个 u16 = characters.storage_capacity（假人 120 / 8）
        store.inventory().saveCargo(new com.dylan.store.CharacterCargo(characNo, storageCapacity,
                ItemArray.emptyCargo()));

        QuestClearFlags cleared = QuestClearFlags.empty();
        forEach(off, "SELECT quest_id FROM character_completed_quests WHERE character_id = ?", officialChar,
                rs -> {
                    if (!cleared.setCleared(rs.getInt(1)) && rs.getInt(1) > QuestClearFlags.MAX_QUEST_ID) {
                        warn.add(name + " 已完成任务 " + rs.getInt(1) + " 超出 A5 位图");
                    }
                });
        List<int[]> running = new ArrayList<>();
        forEach(off, "SELECT quest_id, trigger_value FROM character_quests WHERE character_id = ?",
                officialChar, rs -> running.add(new int[]{rs.getInt(1), rs.getInt(2)}));
        int[] ids = new int[running.size()];
        long[] triggers = new long[running.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = running.get(i)[0];
            triggers[i] = running.get(i)[1];
        }
        store.quest().saveQuests(CharacterQuests.of(characNo, cleared, ids, triggers, -1));

        out.println(String.format("  [%2d] %s → charac_no %d：Lv%d job %d grow %d，装备 %d 背包 %d 宠物 %d 任务 %d/%d",
                index, name, characNo, level, job, growType, equipslot.usedSlots(), inventory.usedSlots(),
                creature.usedSlots(), countCleared(off, officialChar), ids.length));
        return cash;
    }

    private static InvenItem item(GameScripts scripts, int itemId, int count, String who, Warnings warn) {
        ItemScript script = scripts.item(itemId).orElse(null);
        if (script == null) {
            warn.add(who + " 物品 " + itemId + " 在 A4 PVF 里没有，跳过");
            return null;
        }
        InvenItem item = InventoryService.newItem(script, count);
        // newItem 给有品级的装备随机一个品级码；导存档要可复现，统一 0（官方存档里也没有这一列）
        return script.isStackableItem() ? item : item.withAddInfo(0);
    }

    private static InvenItem withSeal(InvenItem item, int officialSealState) {
        boolean sealing = item.attachType() == ItemScript.ATTACH_SEALING
                || item.attachType() == ItemScript.ATTACH_SEALING_TRADE;
        if (officialSealState == 0 && sealing) {
            return item.withAttachType(ItemScript.ATTACH_FREE);
        }
        if (officialSealState != 0 && !sealing) {
            return item.withAttachType(ItemScript.ATTACH_SEALING);
        }
        return item;
    }

    private static int countCleared(Connection off, long officialChar) throws SQLException {
        try (PreparedStatement ps = off.prepareStatement(
                "SELECT count(*) FROM character_completed_quests WHERE character_id = ?")) {
            ps.setLong(1, officialChar);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * 官方 account_premium(premium_type, expires_at 秒) → member_premium，写法同 PremiumService.grant。
     * 官方 REPLY 4 特权项 = {u8 类型, u32 剩余秒}（假人类型 27 剩约 64 天，过期的 22 不发），两边同一套类型号。
     */
    private static void importPremium(Connection off, GameStore store, long officialId, long mId, PrintStream out)
            throws SQLException {
        long now = Instant.now().getEpochSecond();
        try (PreparedStatement ps = off.prepareStatement(
                "SELECT premium_type, expires_at FROM account_premium WHERE account_id = ?")) {
            ps.setLong(1, officialId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int type = rs.getInt(1);
                    long end = rs.getLong(2);
                    if (end <= now) {
                        continue;
                    }
                    store.premium().savePremium(mId, type, now, end,
                            com.dylan.game.PremiumService.EVENT_ID, com.dylan.game.PremiumService.SERVER_ID);
                    out.println("  会员 " + type + " 到 " + Instant.ofEpochSecond(end));
                }
            }
        }
    }

    /** A5 member_dungeon 是「副本号|已通关难度」逗号串（DungeonClearService.serialize）。 */
    private static String dungeonProgress(Connection off, long officialId) throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement ps = off.prepareStatement(
                "SELECT dungeon_id, max_difficulty FROM account_dungeon_progress WHERE account_id = ? ORDER BY dungeon_id")) {
            ps.setLong(1, officialId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (sb.length() > 0) {
                        sb.append(',');
                    }
                    sb.append(rs.getInt(1)).append('|').append(rs.getInt(2));
                }
            }
        }
        return sb.toString();
    }

    @FunctionalInterface
    private interface RowHandler {
        void row(ResultSet rs) throws SQLException;
    }

    private static void forEach(Connection off, String sql, long characterId, RowHandler h) throws SQLException {
        try (PreparedStatement ps = off.prepareStatement(sql)) {
            ps.setLong(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    h.row(rs);
                }
            }
        }
    }
}
