package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;

import java.util.List;
import java.util.Map;

public final class ServerParameter {
    public static final String PATH = "etc/serverparameter.etc";

    public static final int NOTICE_LEGACY_ALL = 0;

    public record BurningFatigueStep(int threshold, int expBonus, int refill) {
    }

    public record LegacyUpgradeStep(int min, int max, int rate) {
        public int range() {
            return 1000 * (max - min) + 999;
        }
    }

    private final double monsterExpRate;
    private final double clearExpRate;
    private final List<Double> partySizeExpRates;
    private final List<Double> difficultyExpRates;
    private final List<Double> clearRankExpRates;
    private final List<Double> monsterKindExpRates;
    private final double pcRoomPartyExpRate;
    private final List<BurningFatigueStep> burningFatigueSteps;
    private final int weekendBonusFatigue;
    private final int weekendBonusExp;
    private final List<LegacyUpgradeStep> legacyUpgradeSteps;
    private final List<LegacyUpgradeStep> compoundUpgradeSteps;
    private final int noticeLegacyRarity;
    private final boolean[] noticeLegacySlots;
    private final int noticeLegacyUpgrade;
    private final List<Integer> staminaRecoveryCosts;

    private ServerParameter(double monsterExpRate, double clearExpRate,
                            List<Double> partySizeExpRates, List<Double> difficultyExpRates,
                            List<Double> clearRankExpRates, List<Double> monsterKindExpRates,
                            double pcRoomPartyExpRate,
                            List<BurningFatigueStep> burningFatigueSteps,
                            int weekendBonusFatigue, int weekendBonusExp,
                            List<LegacyUpgradeStep> legacyUpgradeSteps,
                            List<LegacyUpgradeStep> compoundUpgradeSteps,
                            int noticeLegacyRarity, boolean[] noticeLegacySlots,
                            int noticeLegacyUpgrade, List<Integer> staminaRecoveryCosts) {
        this.monsterExpRate = monsterExpRate;
        this.clearExpRate = clearExpRate;
        this.partySizeExpRates = List.copyOf(partySizeExpRates);
        this.difficultyExpRates = List.copyOf(difficultyExpRates);
        this.clearRankExpRates = List.copyOf(clearRankExpRates);
        this.monsterKindExpRates = List.copyOf(monsterKindExpRates);
        this.pcRoomPartyExpRate = pcRoomPartyExpRate;
        this.burningFatigueSteps = List.copyOf(burningFatigueSteps);
        this.weekendBonusFatigue = weekendBonusFatigue;
        this.weekendBonusExp = weekendBonusExp;
        this.legacyUpgradeSteps = List.copyOf(legacyUpgradeSteps);
        this.compoundUpgradeSteps = List.copyOf(compoundUpgradeSteps);
        this.noticeLegacyRarity = noticeLegacyRarity;
        this.noticeLegacySlots = noticeLegacySlots.clone();
        this.noticeLegacyUpgrade = noticeLegacyUpgrade;
        this.staminaRecoveryCosts = List.copyOf(staminaRecoveryCosts);
    }

    public static ServerParameter load(PvfArchive archive) {
        return parse(archive.readText(PATH));
    }

    public static ServerParameter parse(String text) {
        Map<String, List<String>> sections = EtcSections.split(text);
        double monster = 1.0;
        double clear = 1.0;
        List<Double> party = List.of(1.0);
        List<Double> difficulty = List.of(1.0);
        List<Double> rank = List.of();
        List<Double> monsterKind = List.of();
        double pcRoomParty = 0.0;
        List<BurningFatigueStep> burning = List.of();
        int weekendFatigue = 0;
        int weekendExp = 0;
        List<LegacyUpgradeStep> legacyUpgrade = List.of();
        List<LegacyUpgradeStep> compoundUpgrade = List.of();
        int noticeRarity = NOTICE_LEGACY_ALL;
        boolean[] noticeSlots = new boolean[com.dylan.protocol.body.EquipmentSlot.COUNT];
        int noticeUpgrade = NOTICE_LEGACY_ALL;
        List<Integer> staminaCosts = List.of();
        for (var entry : sections.entrySet()) {
            if ("burning fatigue event param".equals(entry.getKey())) {
                burning = burningSteps(entry.getValue());
                continue;
            }
            if ("notice legacy with type".equals(entry.getKey())) {
                noticeSlots = noticeSlots(entry.getValue());
                continue;
            }
            List<Double> values = numbers(entry.getValue());
            if (values.isEmpty()) {
                continue;
            }
            switch (entry.getKey()) {
                case "monster exp bonusrate" -> monster = values.get(0);
                case "clear exp bonusrate" -> clear = values.get(0);
                case "party user number exp bonusrate" -> party = values;
                case "dungeon difficulty exp bonusrate" -> difficulty = values;
                case "clear rank exp bonusrate" -> rank = values;
                case "drop bonusrate of monster kind" -> monsterKind = values;
                case "pc room party exp bonus rate" -> pcRoomParty = values.get(0);
                case "weekend bonus fatigue" -> weekendFatigue = values.get(0).intValue();
                case "weekend bonus exp" -> weekendExp = values.get(0).intValue();
                case "upgrade with legacy" -> legacyUpgrade = legacyUpgradeSteps(values);
                case "upgrade with compound" -> compoundUpgrade = compoundUpgradeSteps(values);
                case "notice legacy with rarity" -> noticeRarity = values.get(0).intValue();
                case "notice legacy with upgrade" -> noticeUpgrade = values.get(0).intValue();
                case "stamina recovery cost" -> staminaCosts = values.stream()
                        .map(Double::intValue).toList();
                default -> {                        }
            }
        }
        return new ServerParameter(monster, clear, party, difficulty, rank, monsterKind,
                pcRoomParty, burning, weekendFatigue, weekendExp,
                legacyUpgrade, compoundUpgrade, noticeRarity, noticeSlots, noticeUpgrade,
                staminaCosts);
    }

    private static List<LegacyUpgradeStep> compoundUpgradeSteps(List<Double> values) {
        if (values.size() < 6) {
            return List.of();
        }
        var out = new java.util.ArrayList<LegacyUpgradeStep>(2);
        for (int i = 0; i < 2; i++) {
            out.add(new LegacyUpgradeStep(
                    values.get(i * 3).intValue(),
                    values.get(i * 3 + 1).intValue(),
                    values.get(i * 3 + 2).intValue()));
        }
        return out;
    }

    private static List<LegacyUpgradeStep> legacyUpgradeSteps(List<Double> values) {
        if (values.size() < 9) {
            return List.of();
        }
        var out = new java.util.ArrayList<LegacyUpgradeStep>(3);
        for (int i = 0; i < 3; i++) {
            out.add(new LegacyUpgradeStep(
                    values.get(i * 3).intValue(),
                    values.get(i * 3 + 1).intValue(),
                    values.get(i * 3 + 2).intValue()));
        }
        return out;
    }

    private static boolean[] noticeSlots(List<String> lines) {
        boolean[] out = new boolean[com.dylan.protocol.body.EquipmentSlot.COUNT];
        for (String line : lines) {
            var matcher = BACKQUOTED.matcher(line);
            while (matcher.find()) {
                String label = matcher.group(1).trim();
                int slot = com.dylan.protocol.body.EquipmentSlot.of(label);
                if (slot < 0 || slot >= out.length) {
                    continue;
                }
                out[slot] = true;
            }
        }
        return out;
    }

    private static final java.util.regex.Pattern BACKQUOTED =
            java.util.regex.Pattern.compile("`([^`]+)`");

    private static List<BurningFatigueStep> burningSteps(List<String> lines) {
        var out = new java.util.ArrayList<BurningFatigueStep>();
        for (String rawLine : lines) {
            String line = rawLine.split("//", 2)[0].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] tokens = line.split("\\s+");
            if (tokens.length < 3) {
                continue;
            }
            try {
                out.add(new BurningFatigueStep(
                        Integer.parseInt(tokens[0]),
                        Integer.parseInt(tokens[1]),
                        Integer.parseInt(tokens[2])));
            } catch (NumberFormatException e) {
            }
        }
        return out;
    }

    private static List<Double> numbers(List<String> lines) {
        var out = new java.util.ArrayList<Double>();
        for (String rawLine : lines) {
            String line = rawLine.split("//", 2)[0].trim();
            if (line.isEmpty()) {
                continue;
            }
            for (String token : line.split("\\s+")) {
                try {
                    out.add(Double.parseDouble(token));
                } catch (NumberFormatException e) {
                }
            }
        }
        return out;
    }

    public double monsterExpRate() {
        return monsterExpRate;
    }

    public double clearExpRate() {
        return clearExpRate;
    }

    public double partySizeExpRate(int partySize) {
        return at(partySizeExpRates, partySize - 1, 1.0);
    }

    public double monsterKindExpRate(int kind) {
        if (kind < 0 || kind > 3) {
            return 1.0;
        }
        return at(monsterKindExpRates, kind, 1.0);
    }

    public double difficultyExpRate(int difficulty) {
        return at(difficultyExpRates, difficulty, 1.0);
    }

    public double pcRoomPartyExpRate() {
        return pcRoomPartyExpRate;
    }

    public double clearRankExpRate(com.dylan.game.ClearRank rank) {
        int index = switch (rank) {
            case B -> 0;
            case A -> 1;
            case S -> 2;
            case SS -> 3;
            case SSS -> 4;
            default -> -1;
        };
        return index < 0 ? 0.0 : at(clearRankExpRates, index, 0.0);
    }

    public int burningFatigueExpBonus(int usedFatigue) {
        for (int i = 1; i < burningFatigueSteps.size(); i++) {
            if (burningFatigueSteps.get(i).threshold() > usedFatigue) {
                return burningFatigueSteps.get(i - 1).expBonus();
            }
        }
        return 0;
    }

    public int burningFatigueRefill(int usedFatigue, int spend) {
        for (int i = 1; i < burningFatigueSteps.size(); i++) {
            int threshold = burningFatigueSteps.get(i).threshold();
            if (threshold <= usedFatigue && threshold > usedFatigue - spend) {
                return burningFatigueSteps.get(i).refill();
            }
        }
        return 0;
    }

    public int weekendBonusFatigue() {
        return weekendBonusFatigue;
    }

    public int recoverStaminaPrice(int level, int stamina) {
        if (staminaRecoveryCosts.isEmpty()) {
            return 0;
        }
        int index = Math.min(staminaRecoveryCosts.size(), level) - 1;
        if (index < 0) {
            index = 0;
        }
        return staminaRecoveryCosts.get(index) * (100 - stamina) / 90;
    }

    public List<LegacyUpgradeStep> legacyUpgradeSteps() {
        return legacyUpgradeSteps;
    }

    public List<LegacyUpgradeStep> compoundUpgradeSteps() {
        return compoundUpgradeSteps;
    }

    public int rollCompoundUpgrade(java.util.Random random) {
        if (compoundUpgradeSteps.size() < 2) {
            return 0;
        }
        LegacyUpgradeStep normal = compoundUpgradeSteps.get(0);
        LegacyUpgradeStep special = compoundUpgradeSteps.get(1);
        int rand = random.nextInt(LEGACY_UPGRADE_RANGE + 1);
        LegacyUpgradeStep step = rand >= normal.rate() ? special : normal;
        int level = step.min() + random.nextInt(step.range() + 1) / 1000;
        return level >= 0x20 ? 0 : level;
    }

    public int rollLegacyUpgrade(java.util.Random random) {
        if (legacyUpgradeSteps.size() < 3) {
            return 0;
        }
        LegacyUpgradeStep first = legacyUpgradeSteps.get(0);
        LegacyUpgradeStep second = legacyUpgradeSteps.get(1);
        LegacyUpgradeStep third = legacyUpgradeSteps.get(2);
        int rand = random.nextInt(LEGACY_UPGRADE_RANGE + 1);
        LegacyUpgradeStep step;
        if (first.rate() <= rand) {
            step = second.rate() + first.rate() <= rand ? third : second;
        } else {
            step = first;
        }
        int level = step.min() + random.nextInt(step.range() + 1) / 1000;
        return level >= 0x20 ? 0 : level;
    }

    public static final int LEGACY_UPGRADE_RANGE = 10000;

    public int noticeLegacyRarity() {
        return noticeLegacyRarity;
    }

    public boolean noticeLegacySlot(int slot) {
        return slot >= 0 && slot < noticeLegacySlots.length && noticeLegacySlots[slot];
    }

    public int noticeLegacyUpgrade() {
        return noticeLegacyUpgrade;
    }

    private static double at(List<Double> values, int index, double fallback) {
        if (values.isEmpty()) {
            return fallback;
        }
        int i = Math.max(0, Math.min(values.size() - 1, index));
        return values.get(i);
    }

    @Override
    public String toString() {
        return "打怪 ×" + monsterExpRate + "、通关 ×" + clearExpRate
                + "、人数 " + partySizeExpRates + "（每人拿 weight[n]÷n）"
                + "、难度 " + difficultyExpRates
                + "、怪物类型 " + monsterKindExpRates + "（命名怪再 ×3）"
                + "、评级加成 " + clearRankExpRates
                + "、网吧队友 ×" + pcRoomPartyExpRate
                + "、燃烧疲劳 " + burningFatigueSteps.size() + " 档"
                + "、周末奖励疲劳 +" + weekendBonusFatigue
                + "、罐子强化 " + legacyUpgradeSteps
                + "、配方强化 " + compoundUpgradeSteps
                + "、罐子播报门槛 rarity≥" + noticeLegacyRarity
                + " / upgrade≥" + noticeLegacyUpgrade;
    }
}
