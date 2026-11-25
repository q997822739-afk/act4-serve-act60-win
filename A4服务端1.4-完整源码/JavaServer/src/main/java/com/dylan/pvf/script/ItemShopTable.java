package com.dylan.pvf.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

public final class ItemShopTable {
    public static final String LIST_PATH = "itemshop/itemshop.lst";

    public static final int TYPE_ETC = 0;

    public static final int TYPE_WEAPON = 1;

    public static final int TYPE_DISJOINT = 2;

    public static final int TYPE_UNKNOWN = -1;

    public record Shop(int shopNo, String path, int npc, int type, List<Integer> sellItems) {
        public Shop {
            sellItems = List.copyOf(sellItems);
        }

        public boolean sells(long itemId) {
            for (int id : sellItems) {
                if (id == itemId) {
                    return true;
                }
            }
            return false;
        }

        public int itemCount() {
            return (int) sellItems.stream().filter(id -> id >= 0).count();
        }

        @Override
        public String toString() {
            return "店" + shopNo + "[NPC " + npc + "，" + itemCount() + " 件]";
        }
    }

    private final Map<Integer, Shop> byShopNo;

    private ItemShopTable(Map<Integer, Shop> byShopNo) {
        this.byShopNo = Collections.unmodifiableMap(byShopNo);
    }

    public static ItemShopTable empty() {
        return new ItemShopTable(Map.of());
    }

    public static ItemShopTable of(Map<Integer, Shop> shops) {
        return new ItemShopTable(new LinkedHashMap<>(shops));
    }

    public int size() {
        return byShopNo.size();
    }

    public boolean sells(int shopNo, long itemId) {
        Shop shop = byShopNo.get(shopNo);
        return shop != null && shop.sells(itemId);
    }

    public Map<Integer, Shop> shops() {
        return byShopNo;
    }

    public static ItemShopTable load(PvfArchive archive) {
        ScriptList list = ScriptList.load(archive, LIST_PATH);
        Map<Integer, Shop> shops = new LinkedHashMap<>();
        int failed = 0;
        for (var entry : list.entries().entrySet()) {
            int shopNo = entry.getKey();
            String path = entry.getValue();
            try {
                shops.put(shopNo, parse(shopNo, path, archive.readText(path)));
            } catch (RuntimeException ex) {
                failed++;
            }
        }
        return new ItemShopTable(shops);
    }

    public static Shop parse(int shopNo, String path, String text) {
        var tokens = ScriptReader.tokenize(text);
        int npc = 0;
        int type = TYPE_UNKNOWN;
        List<Integer> sellItems = new ArrayList<>();
        boolean sawSellItem = false;
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            switch (token.text().toLowerCase(java.util.Locale.ROOT)) {
                case "npc" -> {
                    if (i + 1 < tokens.size()) {
                        npc = tokens.get(i + 1).asInt();
                    }
                }
                case "type" -> {
                    if (i + 1 < tokens.size()) {
                        type = typeOf(tokens.get(i + 1).text());
                    }
                }
                case "sell item" -> {
                    sawSellItem = true;
                    for (int j = i + 1; j < tokens.size(); j++) {
                        var word = tokens.get(j);
                        if (word.kind() != ScriptReader.Kind.WORD) {
                            break;
                        }
                        sellItems.add(word.asInt());
                    }
                }
                default -> {
                }
            }
        }
        if (!sawSellItem) {
            throw new PvfFormatException(path + " 里没有 [sell item]，这不是一份商店脚本");
        }
        return new Shop(shopNo, path, npc, type, sellItems);
    }

    private static int typeOf(String label) {
        return switch (label.trim()) {
            case "[etc shop]" -> TYPE_ETC;
            case "[weapon shop]" -> TYPE_WEAPON;
            case "[disjoint shop]" -> TYPE_DISJOINT;
            default -> TYPE_UNKNOWN;
        };
    }

    @Override
    public String toString() {
        int items = byShopNo.values().stream().mapToInt(Shop::itemCount).sum();
        return "ItemShopTable[" + byShopNo.size() + " 家店，共 " + items + " 件商品]";
    }
}
