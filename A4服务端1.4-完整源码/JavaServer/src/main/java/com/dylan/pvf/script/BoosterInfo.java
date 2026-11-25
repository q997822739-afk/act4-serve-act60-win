package com.dylan.pvf.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public record BoosterInfo(List<Element> elements) {
    public static final BoosterInfo EMPTY = new BoosterInfo(List.of());
    public static final int TYPE_CREATURE = 3;
    public static final int TYPE_ETC = 6;

    public static final int TYPE_UNKNOWN = 9;

    public static final int TYPE_WITH_AVATAR_COLUMNS = 1;

    private static final String[] TAGS = {
            "avatar", "special avatar", "cera", "creature", "equipment",
            "stackable", "etc", "special creature", "emblem",
    };

    public BoosterInfo {
        elements = List.copyOf(elements);
    }

    public record Entry(int itemId, int weightAccum, int count, int periodIndex, int abilityNo) {
        public Entry(int itemId, int weightAccum, int count) {
            this(itemId, weightAccum, count, 0, 0);
        }
    }

    public record Element(int type, String tag, int drawCount, int totalWeight,
                          List<Entry> entries) {
        public Element {
            entries = List.copyOf(entries);
        }

        public Entry roll(Random random) {
            int rand = random.nextInt(totalWeight + 1);
            for (Entry e : entries) {
                if (rand < e.weightAccum()) {
                    return e;
                }
            }
            return null;
        }
    }

    public static int typeOf(String tag) {
        String want = tag == null ? "" : tag.trim().toLowerCase();
        for (int i = 0; i < TAGS.length; i++) {
            if (TAGS[i].equals(want)) {
                return i;
            }
        }
        return TYPE_UNKNOWN;
    }

    public List<Entry> draw(Random random) {
        var out = new ArrayList<Entry>();
        for (Element e : elements) {
            for (int i = 0; i < e.drawCount(); i++) {
                Entry got = e.roll(random);
                if (got != null) {
                    out.add(got);
                }
            }
        }
        return out;
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public static BoosterInfo parse(List<ScriptReader.Token> tokens, int start, String path) {
        var elements = new ArrayList<Element>();
        int i = start + 1;
        while (i < tokens.size()) {
            var token = tokens.get(i);
            if (token.kind() == ScriptReader.Kind.SECTION_END
                    && "booster info".equalsIgnoreCase(token.text())) {
                return elements.isEmpty() ? EMPTY : new BoosterInfo(elements);
            }
            if (token.kind() != ScriptReader.Kind.SECTION) {
                break;
            }
            int type = typeOf(token.text());
            if (type == TYPE_UNKNOWN) {
                break;
            }
            String tag = token.text();
            i++;
            if (i >= tokens.size() || tokens.get(i).kind() != ScriptReader.Kind.WORD) {
                break;
            }
            int drawCount = tokens.get(i++).asInt();

            var entries = new ArrayList<Entry>();
            int acc = 0;
            boolean closed = false;
            while (i < tokens.size()) {
                var t = tokens.get(i);
                if (t.kind() != ScriptReader.Kind.WORD) {
                    closed = t.kind() == ScriptReader.Kind.SECTION_END
                            && typeOf(t.text()) == type;
                    i++;
                    break;
                }
                int need = type <= TYPE_WITH_AVATAR_COLUMNS ? 5 : 3;
                if (i + need > tokens.size()) {
                    return EMPTY;
                }
                int itemId = tokens.get(i).asInt();
                int weight = tokens.get(i + 1).asInt();
                int count = tokens.get(i + 2).asInt();
                acc += weight;
                if (need == 5) {
                    entries.add(new Entry(itemId, acc, count,
                            tokens.get(i + 3).asInt(), tokens.get(i + 4).asInt()));
                } else {
                    entries.add(new Entry(itemId, acc, count));
                }
                i += need;
            }
            if (!closed) {
                break;
            }
            elements.add(new Element(type, tag, drawCount, acc, entries));
        }
        return elements.isEmpty() ? EMPTY : new BoosterInfo(elements);
    }

    @Override
    public String toString() {
        if (elements.isEmpty()) {
            return "无池";
        }
        var sb = new StringBuilder(elements.size() + " 段");
        for (int i = 0; i < elements.size(); i++) {
            Element e = elements.get(i);
            sb.append(i == 0 ? "（" : "，")
              .append('[').append(e.tag()).append("] ×").append(e.drawCount())
              .append('/').append(e.entries().size()).append(" 条/权重 ")
              .append(e.totalWeight());
        }
        return sb.append('）').toString();
    }
}
