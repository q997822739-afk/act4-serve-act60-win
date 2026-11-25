package com.dylan.game;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.dylan.pvf.script.ItemScript;

public final class CeraShopSpecialItems {
    private CeraShopSpecialItems() {
    }

    public enum Kind {
        CARGO(1, "金库容量设为"),

        PREMIUM(2, "会员"),

        TRADER(3, "大商人使用券"),

        PRIVATE_STORE(4, "个人商店"),

        PACKAGE(5, "礼包"),

        BOOSTER(6, "成长胶囊"),

        CARGO_ADD(7, "金库容量增加"),

        CHARAC_SLOT_EXTEND(8, "角色栏扩展"),

        SKILL_CONFIRM(9, "技能确定券"),

        SKILL_TREE_STYLE(10, "技能树样式"),

        INVENTORY_UPGRADE(11, "背包扩展"),

        BOOSTER_SELECTION(12, "成长胶囊选择券"),

        CERAPOINT_GIFTCARD(13, "点券礼品卡");

        private final int id;

        private final String label;

        Kind(int id, String label) {
            this.id = id;
            this.label = label;
        }

        public int id() {
            return id;
        }

        public String label() {
            return label;
        }

        public boolean supported() {
            return this == CARGO || this == PREMIUM || this == PRIVATE_STORE || this == CARGO_ADD;
        }

        public boolean premium() {
            return this == PREMIUM || this == TRADER || this == PRIVATE_STORE;
        }

        public boolean cargo() {
            return this == CARGO || this == CARGO_ADD;
        }
    }

    public record Special(Kind kind, int value) {
    }

    private static final Map<Integer, Special> TABLE = buildTable();

    private static Map<Integer, Special> buildTable() {
        var table = new java.util.HashMap<Integer, Special>();

        table.put(5, new Special(Kind.CARGO, 24));
        table.put(6, new Special(Kind.CARGO, 152));
        table.put(68, new Special(Kind.CARGO, 72));
        table.put(110, new Special(Kind.CARGO, 40));
        table.put(2670009, new Special(Kind.CARGO, 40));

        for (int itemId : new int[]{50, 57, 58, 59, 60, 61, 62, 2660235, 2660236}) {
            table.put(itemId, new Special(Kind.CARGO_ADD, 16));
        }

        table.put(17, new Special(Kind.TRADER, 7));
        table.put(16, new Special(Kind.PRIVATE_STORE, 8));
        table.put(2675057, new Special(Kind.PRIVATE_STORE, 42));

        table.put(9, new Special(Kind.PREMIUM, 1));
        table.put(11, new Special(Kind.PREMIUM, 3));
        table.put(18, new Special(Kind.PREMIUM, 9));
        table.put(19, new Special(Kind.PREMIUM, 10));
        table.put(20, new Special(Kind.PREMIUM, 11));
        table.put(22, new Special(Kind.PREMIUM, 13));
        table.put(23, new Special(Kind.PREMIUM, 14));
        table.put(29, new Special(Kind.PREMIUM, 16));
        table.put(30, new Special(Kind.PREMIUM, 17));
        table.put(31, new Special(Kind.PREMIUM, 18));
        table.put(32, new Special(Kind.PREMIUM, 19));
        table.put(33, new Special(Kind.PREMIUM, 20));
        table.put(34, new Special(Kind.PREMIUM, 21));
        table.put(35, new Special(Kind.PREMIUM, 22));
        table.put(43, new Special(Kind.PREMIUM, 23));
        table.put(44, new Special(Kind.PREMIUM, 24));
        table.put(45, new Special(Kind.PREMIUM, 25));
        table.put(46, new Special(Kind.PREMIUM, 26));
        table.put(47, new Special(Kind.PREMIUM, 27));
        table.put(137, new Special(Kind.PREMIUM, 29));
        table.put(193, new Special(Kind.PREMIUM, 30));
        table.put(194, new Special(Kind.PREMIUM, 31));
        table.put(196, new Special(Kind.PREMIUM, 32));
        table.put(197, new Special(Kind.PREMIUM, 33));
        table.put(198, new Special(Kind.PREMIUM, 33));
        table.put(200, new Special(Kind.PREMIUM, 40));
        table.put(720, new Special(Kind.PREMIUM, 35));
        table.put(741, new Special(Kind.PREMIUM, 36));
        table.put(742, new Special(Kind.PREMIUM, 37));
        table.put(743, new Special(Kind.PREMIUM, 38));
        table.put(744, new Special(Kind.PREMIUM, 39));
        table.put(820, new Special(Kind.PREMIUM, 41));
        table.put(920, new Special(Kind.PREMIUM, 44));
        table.put(2660012, new Special(Kind.PREMIUM, 45));
        table.put(2660013, new Special(Kind.PREMIUM, 46));
        table.put(2660050, new Special(Kind.PREMIUM, 47));
        table.put(2660051, new Special(Kind.PREMIUM, 48));
        table.put(2660290, new Special(Kind.PREMIUM, 33));
        table.put(2660358, new Special(Kind.PREMIUM, 33));
        table.put(2660401, new Special(Kind.PREMIUM, 35));
        table.put(2660402, new Special(Kind.PREMIUM, 38));
        table.put(2660408, new Special(Kind.PREMIUM, 79));
        table.put(2660409, new Special(Kind.PREMIUM, 76));
        table.put(2660410, new Special(Kind.PREMIUM, 77));
        table.put(2660411, new Special(Kind.PREMIUM, 78));
        table.put(2660543, new Special(Kind.PREMIUM, 80));
        table.put(2660544, new Special(Kind.PREMIUM, 83));
        table.put(2660545, new Special(Kind.PREMIUM, 84));
        table.put(2660546, new Special(Kind.PREMIUM, 87));
        table.put(2660703, new Special(Kind.PREMIUM, 79));
        table.put(2670266, new Special(Kind.PREMIUM, 70));
        table.put(2675025, new Special(Kind.PREMIUM, 17));
        table.put(2675026, new Special(Kind.PREMIUM, 23));
        table.put(2675028, new Special(Kind.PREMIUM, 31));
        table.put(2675058, new Special(Kind.PREMIUM, 43));
        table.put(10000387, new Special(Kind.PREMIUM, 92));
        table.put(10000388, new Special(Kind.PREMIUM, 89));
        table.put(10000389, new Special(Kind.PREMIUM, 90));
        table.put(10000390, new Special(Kind.PREMIUM, 91));
        table.put(10000391, new Special(Kind.PREMIUM, 93));

        table.put(195, new Special(Kind.SKILL_CONFIRM, 0));
        table.put(821, new Special(Kind.SKILL_TREE_STYLE, 0));
        table.put(2660239, new Special(Kind.CHARAC_SLOT_EXTEND, 1));
        table.put(2660296, new Special(Kind.INVENTORY_UPGRADE, 8));
        table.put(2660297, new Special(Kind.INVENTORY_UPGRADE, 8));
        table.put(690000221, new Special(Kind.CERAPOINT_GIFTCARD, 10));
        table.put(690000222, new Special(Kind.CERAPOINT_GIFTCARD, 50));
        table.put(690000223, new Special(Kind.CERAPOINT_GIFTCARD, 100));
        table.put(690000224, new Special(Kind.CERAPOINT_GIFTCARD, 500));
        table.put(690000225, new Special(Kind.CERAPOINT_GIFTCARD, 1000));

        return Map.copyOf(table);
    }

    public static Optional<Special> classify(int itemId) {
        return Optional.ofNullable(TABLE.get(itemId));
    }

    public static Optional<Special> classify(ItemScript item) {
        if (item == null) {
            return Optional.empty();
        }
        Special special = TABLE.get(item.id());
        if (special == null) {
            return Optional.empty();
        }
        if (!CONTRACT_TYPES.contains(item.stackableType())) {
            return Optional.empty();
        }
        return Optional.of(special);
    }

    private static final Set<Integer> CONTRACT_TYPES =
            Set.of(ItemScript.STACKABLE_ETC, ItemScript.STACKABLE_CONTRACT);

    private static final Set<Integer> BANNED_IN_PVP_CHANNEL = Set.of(
            194, 196, 197, 720, 743, 744, 820, 920,
            2660290, 2660358, 2660401, 2660402, 2670266, 2675028);

    public static boolean bannedInPvpChannel(int itemId) {
        return BANNED_IN_PVP_CHANNEL.contains(itemId);
    }
}
