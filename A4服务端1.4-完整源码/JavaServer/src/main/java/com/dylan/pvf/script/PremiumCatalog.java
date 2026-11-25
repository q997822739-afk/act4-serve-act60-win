package com.dylan.pvf.script;

import com.dylan.protocol.body.EquipmentSlot;
import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class PremiumCatalog {

    public static final String PATH = "etc/premiumlist.etc";

    public static final int MAX_TYPE = 105;

    private static final int APPLY_DATE_END = -1;

    private static final int MAX_OVER_EQUIP = 5;

    private static final int MAX_RETURN_ITEM = 3;

    private static final int MAX_DAILY_ITEMS = 5;

    public enum Attr {
        BONUS,
        TERM
    }

    public record ItemGrant(int itemId, int count) {
    }

    public record OverEquip(int slot, int level) {
    }

    public record ReturnItem(int itemId, int rate) {
    }

    public record Entry(int type, String name, Attr attr, int termDays, int expireTime,
                        List<ItemGrant> dailyItems, int fatigueRate, int coin, int expRate,
                        boolean unlimitFatigue, int overSkillLevel, int cerashopDiscountRate,
                        int bonusExp, int questItemDropRate, List<Integer> independentDropRates,
                        boolean applyTargetServerSelf, boolean goldBonus,
                        List<OverEquip> overEquip, List<ReturnItem> returnItems,
                        Set<Integer> applyDays, int inventoryLimit,
                        Map<String, Integer> statBonus) {

        public boolean bonus() {
            return attr == Attr.BONUS;
        }

        public boolean appliesOn(int dayOfWeek) {
            return applyDays.isEmpty() || applyDays.contains(dayOfWeek);
        }

        public boolean hasEffect() {
            return !overEquip.isEmpty() || overSkillLevel > 0 || !returnItems.isEmpty()
                    || !dailyItems.isEmpty() || fatigueRate > 0 || coin > 0 || expRate > 0
                    || goldBonus || unlimitFatigue || cerashopDiscountRate > 0
                    || inventoryLimit > 0 || bonusExp > 0 || questItemDropRate > 0
                    || !independentDropRates.isEmpty() || !statBonus.isEmpty();
        }

        Entry withEffectsOf(Entry donor) {
            return new Entry(type, name, attr, termDays, expireTime,
                    donor.dailyItems(), donor.fatigueRate(), donor.coin(), donor.expRate(),
                    donor.unlimitFatigue(), donor.overSkillLevel(), donor.cerashopDiscountRate(),
                    donor.bonusExp(), donor.questItemDropRate(), donor.independentDropRates(),
                    applyTargetServerSelf, donor.goldBonus(),
                    donor.overEquip(), donor.returnItems(),
                    applyDays, donor.inventoryLimit(), donor.statBonus());
        }
    }

    private final Map<Integer, Entry> byType;

    private final Map<Integer, Integer> effectSourceByType;

    private PremiumCatalog(Map<Integer, Entry> byType, Map<Integer, Integer> effectSourceByType) {
        this.byType = Map.copyOf(byType);
        this.effectSourceByType = Map.copyOf(effectSourceByType);
    }

    public static PremiumCatalog load(PvfArchive archive) {
        return parse(archive.readText(PATH));
    }

    public static PremiumCatalog parse(String text) {
        List<ScriptReader.Token> tokens = ScriptReader.tokenize(text);

        List<List<Integer>> overEquipGroups = new ArrayList<>();
        overEquipGroups.add(List.of());
        List<List<Integer>> returnItemGroups = new ArrayList<>();
        returnItemGroups.add(List.of());

        Map<Integer, Builder> builders = new LinkedHashMap<>();
        Builder current = null;

        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            String tag = token.text().toLowerCase();
            List<ScriptReader.Token> body = bodyOf(tokens, i);

            switch (tag) {
                case "overequip list" -> readSlotGroups(body, overEquipGroups);
                case "returnitem list" -> readIntGroups(body, returnItemGroups);
                case "type" -> {
                    int type = intAt(body, 0, tag);
                    if (type < 0 || type > MAX_TYPE) {
                        throw new PvfFormatException(PATH + " 里的会员类型 " + type
                                + " 超出 0.." + MAX_TYPE);
                    }
                    current = builders.computeIfAbsent(type, Builder::new);
                }
                default -> {
                    if (current == null) {
                        continue;
                    }
                    current.apply(tag, body, overEquipGroups, returnItemGroups);
                }
            }
        }

        Map<Integer, Entry> out = new LinkedHashMap<>();
        for (var builder : builders.values()) {
            out.put(builder.type, builder.build());
        }
        if (out.isEmpty()) {
            throw new PvfFormatException(PATH + " 里没有解析出任何会员类型");
        }
        Map<Integer, Integer> effectSource = new LinkedHashMap<>();
        return new PremiumCatalog(inheritWithinFamily(out, effectSource), effectSource);
    }

    private static Map<Integer, Entry> inheritWithinFamily(Map<Integer, Entry> entries,
                                                           Map<Integer, Integer> effectSourceOut) {
        Map<String, Entry> donors = new LinkedHashMap<>();
        for (Entry entry : entries.values()) {
            if (!entry.name().isBlank() && entry.hasEffect()) {
                donors.putIfAbsent(entry.name(), entry);
            }
        }
        if (donors.isEmpty()) {
            return entries;
        }
        Map<Integer, Entry> out = new LinkedHashMap<>();
        for (var e : entries.entrySet()) {
            Entry entry = e.getValue();
            Entry donor = entry.name().isBlank() ? null : donors.get(entry.name());
            if (donor == null || donor == entry || entry.hasEffect()) {
                out.put(e.getKey(), entry);
                continue;
            }
            out.put(e.getKey(), entry.withEffectsOf(donor));
            effectSourceOut.put(e.getKey(), donor.type());
        }
        return out;
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

    private static void readSlotGroups(List<ScriptReader.Token> body, List<List<Integer>> out) {
        int i = 0;
        while (i < body.size()) {
            int count = body.get(i++).asInt();
            List<Integer> group = new ArrayList<>();
            for (int k = 0; k < count && i < body.size(); k++) {
                String label = body.get(i++).text();
                int slot = EquipmentSlot.of(label);
                if (slot == EquipmentSlot.NONE) {
                    continue;
                }
                group.add(slot);
            }
            out.add(List.copyOf(group));
        }
    }

    private static void readIntGroups(List<ScriptReader.Token> body, List<List<Integer>> out) {
        int i = 0;
        while (i < body.size()) {
            int count = body.get(i++).asInt();
            List<Integer> group = new ArrayList<>();
            for (int k = 0; k < count && i < body.size(); k++) {
                group.add(body.get(i++).asInt());
            }
            out.add(List.copyOf(group));
        }
    }

    private static int intAt(List<ScriptReader.Token> body, int index, String tag) {
        if (index >= body.size()) {
            throw new PvfFormatException(PATH + " 的 [" + tag + "] 段少了第 " + (index + 1) + " 个值");
        }
        return body.get(index).asInt();
    }

    private static final class Builder {

        private static final Set<String> STAT_TAGS = Set.of(
                "hp max", "mp max", "physical attack", "physical defense",
                "magical attack", "magical defense", "fire resistance", "water resistance",
                "dark resistance", "light resistance", "hp regen speed", "mp regen speed",
                "move speed", "attack speed", "cast speed", "hit recovery", "jump power");

        private final int type;
        private String name = "";
        private Attr attr = Attr.TERM;
        private int termDays;
        private int expireTime;
        private final List<ItemGrant> dailyItems = new ArrayList<>();
        private int fatigueRate;
        private int coin;
        private int expRate;
        private boolean unlimitFatigue;
        private int overSkillLevel;
        private int cerashopDiscountRate;
        private int bonusExp;
        private int questItemDropRate;
        private final List<Integer> independentDropRates = new ArrayList<>();
        private boolean applyTargetServerSelf;
        private boolean goldBonus;
        private final List<OverEquip> overEquip = new ArrayList<>();
        private final List<ReturnItem> returnItems = new ArrayList<>();
        private final Set<Integer> applyDays = new TreeSet<>();
        private int inventoryLimit;
        private final Map<String, Integer> statBonus = new LinkedHashMap<>();

        Builder(int type) {
            this.type = type;
        }

        private static String unwrap(String text) {
            String trimmed = text.trim();
            return trimmed.length() >= 2 && trimmed.startsWith("[") && trimmed.endsWith("]")
                    ? trimmed.substring(1, trimmed.length() - 1).trim()
                    : trimmed;
        }

        void apply(String tag, List<ScriptReader.Token> body,
                   List<List<Integer>> overEquipGroups, List<List<Integer>> returnItemGroups) {
            if (body.isEmpty() && !tag.equals("apply date")) {
                return;
            }
            switch (tag) {
                case "name" -> name = body.get(0).text();
                case "attr" -> attr = unwrap(body.get(0).text()).equalsIgnoreCase("bonus")
                        ? Attr.BONUS : Attr.TERM;
                case "term" -> termDays = body.get(0).asInt();
                case "expire time" -> expireTime = body.get(0).asInt();
                case "items" -> {
                    for (int i = 0; i + 1 < body.size() && dailyItems.size() < MAX_DAILY_ITEMS;
                            i += 2) {
                        dailyItems.add(new ItemGrant(body.get(i).asInt(), body.get(i + 1).asInt()));
                    }
                }
                case "fatigue" -> fatigueRate = body.get(0).asInt();
                case "coin" -> coin = body.get(0).asInt();
                case "exp" -> expRate = body.get(0).asInt();
                case "unlimit fatigue" -> unlimitFatigue = body.get(0).asInt() == 1;
                case "over skill" -> overSkillLevel = body.get(0).asInt();
                case "cerashop discount rate" -> cerashopDiscountRate = body.get(0).asInt();
                case "bonus exp" -> bonusExp = body.get(0).asInt();
                case "quest item drop rate" -> questItemDropRate = body.get(0).asInt();
                case "independent drop rate" -> body.forEach(t -> independentDropRates.add(t.asInt()));
                case "apply target server" -> applyTargetServerSelf =
                        unwrap(body.get(0).text()).equalsIgnoreCase("self");
                case "gold bonus" -> goldBonus = unwrap(body.get(0).text()).equalsIgnoreCase("true");
                case "inventory limit" -> inventoryLimit = body.get(0).asInt();
                case "over equipinfo" -> readOverEquip(body, overEquipGroups);
                case "return iteminfo" -> readReturnItem(body, returnItemGroups);
                case "apply date" -> {
                    for (var token : body) {
                        int day = token.asInt();
                        if (day == APPLY_DATE_END) {
                            break;
                        }
                        applyDays.add(day);
                    }
                }
                default -> {
                    if (STAT_TAGS.contains(tag)) {
                        statBonus.put(tag, body.get(0).asInt());
                    }
                }
            }
        }

        private void readOverEquip(List<ScriptReader.Token> body, List<List<Integer>> groups) {
            int count = Math.min(body.get(0).asInt(), MAX_OVER_EQUIP);
            for (int k = 0; k < count && 2 + 2 * k < body.size(); k++) {
                int group = body.get(1 + 2 * k).asInt();
                int level = body.get(2 + 2 * k).asInt();
                if (group < 0 || group >= groups.size()) {
                    continue;
                }
                for (int slot : groups.get(group)) {
                    overEquip.add(new OverEquip(slot, level));
                }
            }
        }

        private void readReturnItem(List<ScriptReader.Token> body, List<List<Integer>> groups) {
            int count = Math.min(body.get(0).asInt(), MAX_RETURN_ITEM);
            for (int k = 0; k < count && 2 + 2 * k < body.size(); k++) {
                int group = body.get(1 + 2 * k).asInt();
                int rate = body.get(2 + 2 * k).asInt();
                if (group < 0 || group >= groups.size()) {
                    continue;
                }
                for (int itemId : groups.get(group)) {
                    returnItems.add(new ReturnItem(itemId, rate));
                }
            }
        }

        Entry build() {
            return new Entry(type, name, attr, termDays, expireTime,
                    List.copyOf(dailyItems), fatigueRate, coin, expRate,
                    unlimitFatigue, overSkillLevel, cerashopDiscountRate,
                    bonusExp, questItemDropRate, List.copyOf(independentDropRates),
                    applyTargetServerSelf, goldBonus,
                    List.copyOf(overEquip), List.copyOf(returnItems),
                    Set.copyOf(applyDays), inventoryLimit, Map.copyOf(statBonus));
        }
    }

    public Optional<Entry> byType(int type) {
        return Optional.ofNullable(byType.get(type));
    }

    public int effectSourceType(int type) {
        return effectSourceByType.getOrDefault(type, type);
    }

    public int size() {
        return byType.size();
    }

    public java.util.Collection<Entry> entries() {
        return byType.values();
    }

    @Override
    public String toString() {
        long bonus = byType.values().stream().filter(Entry::bonus).count();
        return "PremiumCatalog[" + byType.size() + " 种会员，带加成 " + bonus
                + "，只有期限 " + (byType.size() - bonus) + "]";
    }
}
