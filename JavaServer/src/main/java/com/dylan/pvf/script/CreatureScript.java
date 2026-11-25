package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;

import java.util.List;

public record CreatureScript(int creatureId, String path, int maxLevel, int startLevel,
                             List<Boolean> artifactSlots, int evolutionCreatureId,
                             int evolutionLevel, boolean evolutionQuest,
                             int skillRecoveryTimeMs, int overSkillRecoveryTimeMs,
                             int learnOverSkillLevel) {

    public static final String LIST_PATH = "creature/creature.lst";

    public static final int ARTIFACT_SLOTS = 3;

    public CreatureScript {
        artifactSlots = List.copyOf(artifactSlots);
    }

    public static CreatureScript load(PvfArchive archive, int creatureId, String path) {
        String normalized = com.dylan.pvf.PvfPath.normalize(path);
        return parse(creatureId, normalized, archive.readText(normalized));
    }

    public static CreatureScript parse(int creatureId, String normalized, String text) {
        var tokens = ScriptReader.tokenize(text);

        int maxLevel = 0;
        int startLevel = 1;
        int evolutionCreatureId = 0;
        int evolutionLevel = 0;
        boolean evolutionQuest = false;
        int skillRecoveryTimeMs = 0;
        int overSkillRecoveryTimeMs = 0;
        int learnOverSkillLevel = 0;
        boolean[] artifacts = new boolean[ARTIFACT_SLOTS];

        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION) {
                continue;
            }
            switch (token.text().toLowerCase()) {
                case "max level" -> maxLevel = nextInt(tokens, i, maxLevel);
                case "start level" -> startLevel = nextInt(tokens, i, startLevel);
                case "evolution creature id" ->
                        evolutionCreatureId = nextInt(tokens, i, evolutionCreatureId);
                case "evolution level" -> evolutionLevel = nextInt(tokens, i, evolutionLevel);
                case "evolution quest" -> evolutionQuest = nextInt(tokens, i, 0) != 0;
                case "skill recovery time" ->
                        skillRecoveryTimeMs = nextInt(tokens, i, skillRecoveryTimeMs);
                case "over skill recovery time" ->
                        overSkillRecoveryTimeMs = nextInt(tokens, i, overSkillRecoveryTimeMs);
                case "learn overskill level" ->
                        learnOverSkillLevel = nextInt(tokens, i, learnOverSkillLevel);
                case "artifact slot" -> {
                    for (int j = i + 1; j < tokens.size(); j++) {
                        var t = tokens.get(j);
                        if (t.kind() == ScriptReader.Kind.SECTION
                                || t.kind() == ScriptReader.Kind.SECTION_END) {
                            break;
                        }
                        if (t.kind() != ScriptReader.Kind.STRING) {
                            continue;
                        }
                        int colour = colourOf(t.text());
                        if (colour >= 0) {
                            artifacts[colour] = true;
                        }
                    }
                }
                default -> {
                }
            }
        }
        return new CreatureScript(creatureId, normalized, maxLevel, startLevel,
                List.of(artifacts[0], artifacts[1], artifacts[2]),
                evolutionCreatureId, evolutionLevel, evolutionQuest,
                skillRecoveryTimeMs, overSkillRecoveryTimeMs, learnOverSkillLevel);
    }

    public boolean artifactAvailable(int equipSlot) {
        int index = equipSlot - com.dylan.protocol.body.EquipmentSlot.FIRST_ARTIFACT;
        return index >= 0 && index < ARTIFACT_SLOTS && artifactSlots.get(index);
    }

    public boolean evolves() {
        return evolutionLevel > 0 && evolutionCreatureId > 0;
    }

    public int levelFor(ExpTable table, long exp) {
        if (table == null) {
            return Math.max(1, startLevel);
        }
        int level = table.levelFor(1, exp);
        return maxLevel > 0 ? Math.min(level, maxLevel) : level;
    }

    public boolean maxedOut(int level) {
        return maxLevel > 0 && level >= maxLevel;
    }

    private static int colourOf(String label) {
        return switch (label.trim().toLowerCase()) {
            case "red" -> 0;
            case "blue" -> 1;
            case "green" -> 2;
            default -> -1;
        };
    }

    private static int nextInt(List<ScriptReader.Token> tokens, int at, int fallback) {
        if (at + 1 < tokens.size() && tokens.get(at + 1).kind() == ScriptReader.Kind.WORD) {
            try {
                return tokens.get(at + 1).asInt();
            } catch (RuntimeException e) {
                return fallback;
            }
        }
        return fallback;
    }
}
