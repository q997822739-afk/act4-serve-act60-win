package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CashShopCatalog {
    public static final String PATH = "etc/newcashshop.etc";
    public static final int PERIOD_30_DAYS = 2;
    public static final int PERIOD_PERMANENT = 3;

    public static final int AGE_UNLIMITED = -1;

    public static final int CATEGORY_UNKNOWN = -1;

    public enum Section {
        AVATAR("avatar", 0, 6),
        COIN("coin", 1, 9),
        ITEM("item", 2, 9),
        PREMIUM("premium", 5, 9),
        CREATURE("creature", 3, 9),
        PACKAGE("package", 4, 10);

        private final String tag;
        private final int category;
        private final int columns;

        Section(String tag, int category, int columns) {
            this.tag = tag;
            this.category = category;
            this.columns = columns;
        }

        public String tag() {
            return tag;
        }

        public int category() {
            return category;
        }

        public int columns() {
            return columns;
        }

        static Optional<Section> byTag(String name) {
            for (Section s : values()) {
                if (s.tag.equalsIgnoreCase(name)) {
                    return Optional.of(s);
                }
            }
            return Optional.empty();
        }
    }

    public record Entry(int commodityNo, int itemId, int count, int periodCode,
                        int goldPrice, int medalPrice, int ceraPrice,
                        int limitGroup, int minimumAge, int sticker,
                        String name, Section section,
                        boolean giftable, boolean purchasable) {
        public boolean priceFromItemScript() {
            return section == Section.AVATAR;
        }

        public boolean toAvatarBag() {
            return section == Section.AVATAR;
        }
    }

    private final Map<Integer, Entry> byCommodityNo;

    private final int maxGiftCount;

    private CashShopCatalog(Map<Integer, Entry> byCommodityNo, int maxGiftCount) {
        this.byCommodityNo = Map.copyOf(byCommodityNo);
        this.maxGiftCount = maxGiftCount;
    }

    public static CashShopCatalog load(PvfArchive archive) {
        return parse(archive.readText(PATH));
    }

    public static CashShopCatalog parse(String text) {
        var tokens = ScriptReader.tokenize(text);

        Map<Integer, Entry> entries = new LinkedHashMap<>();
        Set<Integer> notGiftable = new HashSet<>();
        Set<Integer> notPurchasable = new HashSet<>();
        int maxGiftCount = 0;

        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            String tag = token.text();
            List<ScriptReader.Token> body = bodyOf(tokens, i);

            var section = Section.byTag(tag);
            if (section.isPresent()) {
                parseProducts(section.get(), body, entries);
            } else if (tag.equalsIgnoreCase("gift disallowance")) {
                notGiftable.addAll(readInts(body));
            } else if (tag.equalsIgnoreCase("not stackable buy")) {
                notPurchasable.addAll(readInts(body));
            } else if (tag.equalsIgnoreCase("max gift count")) {
                maxGiftCount = body.isEmpty() ? 0 : body.get(0).asInt();
            }
        }

        if (entries.isEmpty()) {
            throw new PvfFormatException(PATH + " 里没有解析出任何商品");
        }

        Map<Integer, Entry> out = new LinkedHashMap<>();
        for (var e : entries.entrySet()) {
            Entry v = e.getValue();
            boolean giftable = !notGiftable.contains(v.commodityNo());
            boolean purchasable = !notPurchasable.contains(v.commodityNo());
            out.put(e.getKey(), giftable && purchasable ? v : new Entry(
                    v.commodityNo(), v.itemId(), v.count(), v.periodCode(),
                    v.goldPrice(), v.medalPrice(), v.ceraPrice(),
                    v.limitGroup(), v.minimumAge(), v.sticker(),
                    v.name(), v.section(), giftable, purchasable));
        }
        return new CashShopCatalog(out, maxGiftCount);
    }

    private static List<ScriptReader.Token> bodyOf(List<ScriptReader.Token> tokens, int at) {
        List<ScriptReader.Token> out = new ArrayList<>();
        for (int i = at + 1; i < tokens.size(); i++) {
            var kind = tokens.get(i).kind();
            if (kind == ScriptReader.Kind.SECTION || kind == ScriptReader.Kind.SECTION_END) {
                break;
            }
            out.add(tokens.get(i));
        }
        return out;
    }

    /**
     * 每行列数按相邻两个商品名字串的间距量出来。A4 的 [coin] 一行 8 列（没有末尾的购买限制组），
     * ACT5 是 9 列；只接受"规定列数"或"少最后一列"两种，其余仍按规定列数。
     */
    private static int strideOf(Section section, List<ScriptReader.Token> body) {
        int first = -1;
        for (int i = 0; i < body.size(); i++) {
            if (body.get(i).kind() != ScriptReader.Kind.STRING) {
                continue;
            }
            if (first < 0) {
                first = i;
                continue;
            }
            int gap = i - first;
            return gap == section.columns() - 1 ? gap : section.columns();
        }
        return section.columns();
    }

    private static void parseProducts(Section section, List<ScriptReader.Token> body,
                                      Map<Integer, Entry> out) {
        // 只有 COIN/ITEM/PREMIUM/CREATURE 的最后一列（购买限制组）可以缺，见 strideOf。
        boolean lastColumnOptional = section != Section.AVATAR && section != Section.PACKAGE;
        int stride = lastColumnOptional ? strideOf(section, body) : section.columns();
        for (int base = 0; base + stride <= body.size(); base += stride) {
            int commodityNo = body.get(base).asInt();
            if (commodityNo <= 0) {
                continue;
            }
            Entry entry = switch (section) {
                case AVATAR -> new Entry(commodityNo,
                        body.get(base + 1).asInt(),
                        1,
                        body.get(base + 2).asInt(),
                        0, 0, 0,
                        body.get(base + 4).asInt(),
                        body.get(base + 5).asInt(),
                        body.get(base + 3).asInt(),
                        "", section, true, true);
                case COIN, ITEM, PREMIUM, CREATURE -> new Entry(commodityNo,
                        body.get(base + 1).asInt(),
                        body.get(base + 2).asInt(),
                        0,
                        body.get(base + 3).asInt(),
                        body.get(base + 4).asInt(),
                        body.get(base + 5).asInt(),
                        stride > 8 ? body.get(base + 8).asInt() : 0,
                        AGE_UNLIMITED,
                        body.get(base + 7).asInt(),
                        body.get(base + 6).text(), section, true, true);
                case PACKAGE -> new Entry(commodityNo,
                        body.get(base + 1).asInt(),
                        1,
                        body.get(base + 8).asInt(),
                        body.get(base + 2).asInt(),
                        body.get(base + 3).asInt(),
                        body.get(base + 4).asInt(),
                        body.get(base + 7).asInt(),
                        body.get(base + 9).asInt(),
                        body.get(base + 6).asInt(),
                        body.get(base + 5).text(), section, true, true);
            };
            out.putIfAbsent(commodityNo, entry);
        }
    }

    private static List<Integer> readInts(List<ScriptReader.Token> body) {
        List<Integer> out = new ArrayList<>();
        for (var token : body) {
            if (token.kind() == ScriptReader.Kind.WORD) {
                out.add(token.asInt());
            }
        }
        return out;
    }

    public Optional<Entry> byCommodityNo(int commodityNo) {
        return Optional.ofNullable(byCommodityNo.get(commodityNo));
    }

    public Collection<Entry> entries() {
        return byCommodityNo.values();
    }

    public int size() {
        return byCommodityNo.size();
    }

    public long countOf(Section section) {
        return byCommodityNo.values().stream().filter(e -> e.section() == section).count();
    }

    @Override
    public String toString() {
        return "CashShopCatalog[" + byCommodityNo.size() + " 件商品，时装 "
                + countOf(Section.AVATAR) + "，复活币 " + countOf(Section.COIN)
                + "，道具 " + countOf(Section.ITEM) + "，会员 " + countOf(Section.PREMIUM)
                + "，宠物 " + countOf(Section.CREATURE)
                + "，礼包 " + countOf(Section.PACKAGE) + "]";
    }
}
