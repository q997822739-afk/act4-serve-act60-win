package com.dylan.pvf.script;

import java.util.List;

public record RecipeInfo(List<Material> materials, List<Material> outputs,
                         List<Material> skills, int flag,
                         int successRate, int broadcastType, int maintainAbility,
                         List<Material> failOutputs, List<Material> beadMaterials) {
    public static final RecipeInfo EMPTY =
            new RecipeInfo(List.of(), List.of(), List.of(), 0, 0, 0, 0, List.of(), List.of());

    public static final int MONSTER_CARD_SUB_TYPE = 1;

    public static final int FLAG_NEEDS_PREMIUM = 1;

    public static final int FLAG_EXPERT_JOB = 2;

    public RecipeInfo {
        materials = List.copyOf(materials);
        outputs = List.copyOf(outputs);
        skills = List.copyOf(skills);
        failOutputs = List.copyOf(failOutputs);
        beadMaterials = List.copyOf(beadMaterials);
    }

    public List<Material> allMaterials() {
        if (beadMaterials.isEmpty()) {
            return materials;
        }
        var out = new java.util.ArrayList<Material>(materials.size() + beadMaterials.size());
        out.addAll(materials);
        out.addAll(beadMaterials);
        return out;
    }

    public RecipeInfo withAdditional(int successRate, int broadcastType, int maintainAbility,
                                     List<Integer> failOutput, List<Integer> beadItem) {
        return new RecipeInfo(materials, outputs, skills, flag,
                successRate, broadcastType, maintainAbility,
                pairs(failOutput), pairs(beadItem));
    }

    private static List<Material> pairs(List<Integer> flat) {
        if (flat == null || flat.size() < 2) {
            return List.of();
        }
        var out = new java.util.ArrayList<Material>(flat.size() / 2);
        for (int i = 0; i + 1 < flat.size(); i += 2) {
            out.add(new Material(flat.get(i), flat.get(i + 1)));
        }
        return out;
    }

    public record Material(int itemId, int count) {
    }

    public boolean isEmpty() {
        return materials.isEmpty() && outputs.isEmpty() && skills.isEmpty();
    }

    public boolean needsPremium() {
        return flag == FLAG_NEEDS_PREMIUM;
    }

    public boolean expertJobRecipe() {
        return flag == FLAG_EXPERT_JOB;
    }

    public static RecipeInfo of(List<Integer> intData) {
        if (intData == null || intData.isEmpty()) {
            return EMPTY;
        }
        var cursor = new int[]{0};
        var materials = group(intData, cursor);
        var outputs = group(intData, cursor);
        var skills = group(intData, cursor);
        int flag = cursor[0] < intData.size() ? intData.get(cursor[0]) : 0;
        if (materials.isEmpty() && outputs.isEmpty() && skills.isEmpty()) {
            return EMPTY;
        }
        return new RecipeInfo(materials, outputs, skills, flag, 0, 0, 0,
                List.of(), List.of());
    }

    private static List<Material> group(List<Integer> data, int[] cursor) {
        if (cursor[0] >= data.size()) {
            return List.of();
        }
        int count = data.get(cursor[0]++);
        var out = new java.util.ArrayList<Material>(Math.max(0, count));
        for (int i = 0; i < count && cursor[0] + 1 < data.size(); i++) {
            int itemId = data.get(cursor[0]++);
            int amount = data.get(cursor[0]++);
            out.add(new Material(itemId, amount));
        }
        return out;
    }

    public int monsterCardItemId(java.util.function.IntUnaryOperator subTypeOf) {
        for (var m : materials) {
            if (subTypeOf.applyAsInt(m.itemId()) == MONSTER_CARD_SUB_TYPE) {
                return m.itemId();
            }
        }
        return 0;
    }

    public List<Material> inputMaterials(java.util.function.IntUnaryOperator subTypeOf) {
        var out = new java.util.ArrayList<Material>(materials.size());
        for (var m : materials) {
            if (subTypeOf.applyAsInt(m.itemId()) != MONSTER_CARD_SUB_TYPE) {
                out.add(m);
            }
        }
        return out;
    }
}
