package com.dylan.pvf.script;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public record EnchantEffect(List<Stat> stats, String elementalProperty,
                            List<SkillLevelUp> skillLevelUps,
                            List<Integer> slots, List<String> unknownSlotTags,
                            List<String> unknownStatTags) {

    public static final EnchantEffect NONE =
            new EnchantEffect(List.of(), "", List.of(), List.of(), List.of(), List.of());

    public EnchantEffect {
        stats = List.copyOf(stats);
        elementalProperty = elementalProperty == null ? "" : elementalProperty;
        skillLevelUps = List.copyOf(skillLevelUps);
        slots = List.copyOf(slots);
        unknownSlotTags = List.copyOf(unknownSlotTags);
        unknownStatTags = List.copyOf(unknownStatTags);
    }

    public boolean isEmpty() {
        return stats.isEmpty() && elementalProperty.isEmpty() && skillLevelUps.isEmpty();
    }

    public boolean canAttachTo(int equipmentSlot) {
        return slots.contains(equipmentSlot);
    }

    public record Stat(StatTag tag, List<Double> values) {

        public Stat {
            values = List.copyOf(values);
        }

        public double value() {
            return values.isEmpty() ? 0.0 : values.get(0);
        }
    }

    public record SkillLevelUp(int job, String jobTag, int skillIndex, int levelUp) {
    }

    public record Parsed(List<Stat> stats, String elementalProperty,
                         List<SkillLevelUp> skillLevelUps, List<String> unknownStatTags) {

        public static final Parsed EMPTY = new Parsed(List.of(), "", List.of(), List.of());

        public Parsed {
            stats = List.copyOf(stats);
            elementalProperty = elementalProperty == null ? "" : elementalProperty;
            skillLevelUps = List.copyOf(skillLevelUps);
            unknownStatTags = List.copyOf(unknownStatTags);
        }

        public EnchantEffect toEffect(List<String> stringDataTags) {
            if (this == EMPTY && (stringDataTags == null || stringDataTags.isEmpty())) {
                return NONE;
            }
            var slots = new java.util.ArrayList<Integer>();
            var unknownSlots = new java.util.ArrayList<String>();
            if (stringDataTags != null) {
                for (String tag : stringDataTags) {
                    int slot = com.dylan.protocol.body.EquipmentSlot.of(tag);
                    if (slot == com.dylan.protocol.body.EquipmentSlot.NONE) {
                        unknownSlots.add(tag);
                    } else if (!slots.contains(slot)) {
                        slots.add(slot);
                    }
                }
            }
            if (stats.isEmpty() && elementalProperty.isEmpty() && skillLevelUps.isEmpty()
                    && slots.isEmpty() && unknownSlots.isEmpty() && unknownStatTags.isEmpty()) {
                return NONE;
            }
            return new EnchantEffect(stats, elementalProperty, skillLevelUps,
                    slots, unknownSlots, unknownStatTags);
        }
    }

    public static Parsed parseSection(List<ScriptReader.Token> tokens, int at) {
        var stats = new java.util.ArrayList<Stat>();
        var skills = new java.util.ArrayList<SkillLevelUp>();
        var unknown = new java.util.ArrayList<String>();
        String elemental = "";

        int i = at + 1;
        while (i < tokens.size()) {
            var t = tokens.get(i);
            if (t.kind() == ScriptReader.Kind.SECTION_END) {
                if ("enchant".equalsIgnoreCase(t.text())) {
                    break;
                }
                i++;
                continue;
            }
            if (t.kind() != ScriptReader.Kind.SECTION) {
                i++;
                continue;
            }
            String name = t.text().trim().toLowerCase(Locale.ROOT);
            var payload = new java.util.ArrayList<ScriptReader.Token>();
            int j = i + 1;
            while (j < tokens.size()
                    && tokens.get(j).kind() != ScriptReader.Kind.SECTION
                    && tokens.get(j).kind() != ScriptReader.Kind.SECTION_END) {
                payload.add(tokens.get(j));
                j++;
            }
            i = j;

            switch (name) {
                case "elemental property" -> {
                    if (!payload.isEmpty()) {
                        elemental = payload.get(0).text().trim().toLowerCase(Locale.ROOT);
                    }
                }
                case "skill levelup" -> {
                    for (int k = 0; k + 2 < payload.size(); k += 3) {
                        String jobTag = payload.get(k).text().trim().toLowerCase(Locale.ROOT);
                        Integer job = ItemScript.JOB_NAMES.get(jobTag);
                        skills.add(new SkillLevelUp(job == null ? -1 : job, jobTag,
                                payload.get(k + 1).asInt(), payload.get(k + 2).asInt()));
                    }
                }
                default -> {
                    var tag = StatTag.byTag(name);
                    if (tag == null) {
                        unknown.add(name);
                        break;
                    }
                    var values = new java.util.ArrayList<Double>(payload.size());
                    for (var p : payload) {
                        values.add(p.asDouble());
                    }
                    stats.add(new Stat(tag, values));
                }
            }
        }
        if (stats.isEmpty() && elemental.isEmpty() && skills.isEmpty() && unknown.isEmpty()) {
            return Parsed.EMPTY;
        }
        return new Parsed(stats, elemental, skills, unknown);
    }

    public enum StatTag {
        HP_MAX("[hp max]"),
        MP_MAX("[mp max]"),
        HP_REGEN("[hp regen speed]"),
        MP_REGEN("[mp regen speed]"),
        PHYSICAL_ATTACK("[physical attack]"),
        MAGICAL_ATTACK("[magical attack]"),
        PHYSICAL_DEFENSE("[physical defense]"),
        MAGICAL_DEFENSE("[magical defense]"),
        EQUIPMENT_PHYSICAL_ATTACK("[equipment physical attack]"),
        EQUIPMENT_MAGICAL_ATTACK("[equipment magical attack]"),
        PHYSICAL_CRITICAL("[physical critical hit]"),
        MAGICAL_CRITICAL("[magical critical hit]"),
        FIRE_ATTACK("[fire attack]"),
        WATER_ATTACK("[water attack]"),
        LIGHT_ATTACK("[light attack]"),
        DARK_ATTACK("[dark attack]"),
        FIRE_RESISTANCE("[fire resistance]"),
        WATER_RESISTANCE("[water resistance]"),
        LIGHT_RESISTANCE("[light resistance]"),
        DARK_RESISTANCE("[dark resistance]"),
        ALL_ELEMENTAL_RESISTANCE("[all elemental resistance]"),
        STONE_RESISTANCE("[stone resistance]"),
        SLOW_RESISTANCE("[slow resistance]"),
        STUCK_RESISTANCE("[stuck resistance]"),
        HOLD_RESISTANCE("[hold resistance]"),
        STUCK("[stuck]"),
        MOVE_SPEED("[move speed]"),
        ATTACK_SPEED("[attack speed]"),
        CAST_SPEED("[cast speed]"),
        HIT_RECOVERY("[hit recovery]"),
        JUMP_POWER("[jump power]"),
        RIGIDITY("[rigidity]");

        private final String tag;

        StatTag(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }

        private static final Map<String, StatTag> BY_TAG = buildIndex();

        private static Map<String, StatTag> buildIndex() {
            var m = new java.util.HashMap<String, StatTag>();
            for (var t : values()) {
                m.put(t.tag, t);
            }
            return Map.copyOf(m);
        }

        public static StatTag byTag(String raw) {
            if (raw == null) {
                return null;
            }
            String s = raw.trim().toLowerCase(Locale.ROOT);
            if (!s.startsWith("[")) {
                s = "[" + s + "]";
            }
            return BY_TAG.get(s);
        }
    }
}
