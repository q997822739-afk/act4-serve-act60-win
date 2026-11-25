package com.dylan.game;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Noti37ExpBody;
import com.dylan.pvf.script.ExpTable;
import com.dylan.store.GameStore;

public final class ExperienceService {

    private ExperienceService() {
    }

    public static void load(GameSession s, int characNo) {
        GameStore store = s.store();
        if (store == null) {
            return;
        }
        var row = store.character().characterLevelAndExp(characNo).orElse(null);
        if (row == null) {
            return;
        }
        s.levelAndExp(Math.max(1, row.level()), row.exp());
        var table = s.scripts() == null ? null : s.scripts().expTable();
        if (table != null) {
            checkInvalidExpPerLevel(s, table);
        }
    }

    private static void checkInvalidExpPerLevel(GameSession s, ExpTable table) {
        if (isExpConsistent(table, s.level(), s.exp())) {
            return;
        }
        table.levelBottom(s.level());
        table.levelBottom(s.level() + 1);
    }

    static boolean isExpConsistent(ExpTable table, int level, long exp) {
        return exp >= table.levelBottom(level) && exp <= table.levelBottom(level + 1);
    }

    public static int monsterExperience(GameSession s, RoomMonster monster) {
        int base = lookupBase(s, monster.level(), monster.toString());
        if (base <= 0) {
            return 0;
        }
        var scripts = s.scripts();
        var params = scripts == null ? null : scripts.serverParameter();
        DungeonRun run = s.dungeon();
        int members = partyMemberCount(s);
        double rate = monsterExpRate(params,
                run == null ? 0 : run.difficulty(),
                run == null ? DEFAULT_EXP_WEIGHT : run.expWeight(),
                members, partyExpBonusRate(s, members),
                s.level(), monster.level(),
                monster.expKind(), monster.isNamedMonster());
        return (int) Math.min(Integer.MAX_VALUE, Math.round(base * rate));
    }

    static final double DEFAULT_EXP_WEIGHT =
            com.dylan.pvf.script.DungeonScript.DEFAULT_EXP_WEIGHT;

    static final double NAMED_MONSTER_RATE = 3.0;

    static double monsterExpRate(com.dylan.pvf.script.ServerParameter params,
                                 int difficulty, double expWeight,
                                 int members, double partyEventBonus,
                                 int characterLevel, int monsterLevel,
                                 int monsterKind, boolean named) {
        int n = Math.max(1, members);
        double rate = ServerConfig.EXP_RATE_PERCENT / 100.0 / 2.0;
        double kindRate = params == null ? 1.0 : params.monsterKindExpRate(monsterKind);
        if (named) {
            kindRate *= NAMED_MONSTER_RATE;
        }
        rate *= kindRate * expWeight;
        if (params != null) {
            rate *= params.monsterExpRate()
                    * params.difficultyExpRate(difficulty)
                    * (params.partySizeExpRate(n) + partyEventBonus)
                    / n;
        }
        return rate * ExpLevelPenalty.rateOf(characterLevel, monsterLevel);
    }

    static int towerRewardExp(GameSession s, String what) {
        int base = lookupBase(s, s.level(), what);
        if (base <= 0) {
            return 0;
        }
        double rate = ServerConfig.EXP_RATE_PERCENT / 100.0 / 2.0;
        var scripts = s.scripts();
        if (scripts != null && scripts.serverParameter() != null) {
            rate *= scripts.serverParameter().monsterExpRate();
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.round(base * rate));
    }

    private static int lookupBase(GameSession s, int level, String what) {
        GameStore store = s.store();
        if (store == null) {
            return 0;
        }
        int at = Math.max(1, level);
        int base = store.growthTable().monsterRewardExp(at);
        return base;
    }

    private static int partyMemberCount(GameSession s) {
        Party party = PartyRegistry.partyOf(s);
        return party == null ? 1 : Math.max(1, party.memberCount());
    }

    private static double partyExpBonusRate(GameSession s, int memberCount) {
        if (memberCount <= 1) {
            return 0.0;
        }
        return com.dylan.game.event.EventService.bonusFactor(
                com.dylan.game.event.EventNo.PARTY_EXP_BONUS, s);
    }

    static double expDoubleRate(GameSession s) {
        return com.dylan.game.event.EventService.bonusFactor(
                com.dylan.game.event.EventNo.EXP_DOUBLE, s);
    }

    static int burningExpBonusPercent(GameSession s) {
        if (!com.dylan.game.event.EventService.isRunning(
                com.dylan.game.event.EventNo.BURNING_FATIGUE, s)) {
            return 0;
        }
        var scripts = s.scripts();
        if (scripts == null || scripts.serverParameter() == null) {
            return 0;
        }
        return scripts.serverParameter().burningFatigueExpBonus(s.fatigue().usedFatigue());
    }

    static double expItemBonusRate(GameSession s) {
        var consumables = s == null ? null : s.consumables();
        double rate = consumables != null && consumables.isExpItemAffected()
                && consumables.enablePeriodExpItem() ? consumables.expAffectRate() : 0.0;
        if (rate != 0.0 && com.dylan.game.event.EventService.isRunning(
                com.dylan.game.event.EventNo.STOP_OVERLAP_EXP, s)) {
            return 0.0;
        }
        return rate;
    }

    public static boolean grant(GameSession s, int amount, int bonus) {
        Outcome outcome = gainExpSp(s, amount, bonus);
        if (outcome.leveledUp()) {
            if (s.inDungeon()) {
                CharacterService.sendDetailToParty(s, "升到 " + s.level() + " 级");
            } else {
                CharacterService.resendOwnCharacterInfo(s, "升到 " + s.level() + " 级");
            }
        }
        return outcome.notiSent();
    }

    private record Outcome(boolean leveledUp, boolean notiSent) {

        static final Outcome NONE = new Outcome(false, false);
    }

    private static Outcome gainExpSp(GameSession s, int amount, int bonus) {
        if (amount <= 0) {
            return Outcome.NONE;
        }
        var scripts = s.scripts();
        ExpTable table = scripts == null ? null : scripts.expTable();
        if (table == null) {
            return Outcome.NONE;
        }
        int before = s.level();
        int max = maxCharacterLevel(table);
        if (before >= max) {
            return Outcome.NONE;
        }
        int guildBonus = guildSkillBonus(s, amount);
        if (guildBonus > 0) {
            amount += guildBonus;
            bonus += guildBonus;
        }
        s.runSummary().addExperience(amount);

        LevelUpResult result = checkLevelUp(table, max, s.level(), s.exp(), amount,
                grade -> spAtLevelUp(s, grade), s.characNo());
        s.levelAndExp(result.level(), result.exp());
        SessionRegistry.updateExp(s, result.exp());
        if (result.gainedSp() > 0) {
            s.skills().gainSp(result.gainedSp());
        }
        int level = result.level();
        long exp = result.exp();
        result.gainedSp();
        boolean leveledUp = level > before;

        if (leveledUp) {
            CharacterService.recalcCombatStats(s, "升到 " + level + " 级");
            TutorialService.markSkipableOnLevelUp(s, level);
        }

        byte[] body = new Noti37ExpBody(level, (int) exp, bonus, 0,
                s.skills().remainSp(), Noti37ExpBody.clampWinPoint(s.winPoint()), 0).toBytes();
        s.sendNow(Noti37ExpBody.PACKET_ID, body);

        if (s.hasCharacter() && s.store() != null) {
            s.store().character().saveExperience(s.characNo(), level, exp);
        }
        if (leveledUp) {
            onLevelUp(s, before, level, table);
        }
        return new Outcome(leveledUp, true);
    }

    public static boolean grant(GameSession s, int amount) {
        return grant(s, amount, 0);
    }

    public static void syncWinPoint(GameSession s, String why) {
        byte[] body = new Noti37ExpBody(s.level(), (int) s.exp(), 0, 0,
                s.skills().remainSp(), Noti37ExpBody.clampWinPoint(s.winPoint()), 0).toBytes();
        s.sendNow(Noti37ExpBody.PACKET_ID, body);
    }

    public static int maxCharacterLevel(ExpTable table) {
        return Math.min(ServerConfig.MAX_CHARACTER_LEVEL, table.maxLevel());
    }

    private static int guildSkillBonus(GameSession s, int amount) {
        return 0;
    }

    static LevelUpResult checkLevelUp(ExpTable table, int startLevel, long startExp, int amount,
                                      java.util.function.IntUnaryOperator spAtLevelUp,
                                      int characNo) {
        return checkLevelUp(table, maxCharacterLevel(table), startLevel, startExp, amount,
                spAtLevelUp, characNo);
    }

    static LevelUpResult checkLevelUp(ExpTable table, int maxLevel, int startLevel, long startExp,
                                      int amount, java.util.function.IntUnaryOperator spAtLevelUp,
                                      int characNo) {
        int level = startLevel;
        long exp = startExp;
        int next = level + 1;
        int sp = 0;
        int remaining = amount;
        boolean clamped = false;

        while (remaining > 0 && next <= maxLevel) {
            long bottom = table.levelBottom(level);
            long top = table.levelBottom(next);
            if (exp < bottom || exp > top) {
                exp = bottom;
                clamped = true;
            }
            if (top - bottom <= 0) {
                break;
            }
            if (remaining + exp < top) {
                exp += remaining;
                remaining = 0;
            } else {
                sp += spAtLevelUp.applyAsInt(next);
                remaining += (int) (exp - top);
                level = next;
                next++;
                exp = top;
            }
        }
        return new LevelUpResult(level, exp, sp, clamped);
    }

    record LevelUpResult(int level, long exp, int gainedSp, boolean clamped) {
    }

    private static int spAtLevelUp(GameSession s, int grade) {
        GameStore store = s.store();
        if (store == null) {
            return 0;
        }
        int sp = store.growthTable().spAtLevelUp(grade);
        return sp;
    }

    private static void onLevelUp(GameSession s, int before, int level, ExpTable table) {
        maxCharacterLevel(table);
        QuestService.sendAcceptableQuests(s, "升到 " + level + " 级");
    }

    public enum SetLevelStatus {

        DONE,

        UNCHANGED,

        OUT_OF_RANGE,

        NO_TABLE
    }

    public record SetLevelResult(SetLevelStatus status, int level, int spDelta, int maxLevel) {}

    public static SetLevelResult setLevel(GameSession s, int target, String why) {
        var scripts = s.scripts();
        ExpTable table = scripts == null ? null : scripts.expTable();
        if (table == null) {
            return new SetLevelResult(SetLevelStatus.NO_TABLE, s.level(), 0, 0);
        }
        int max = maxCharacterLevel(table);
        if (target < 1 || target > max) {
            return new SetLevelResult(SetLevelStatus.OUT_OF_RANGE, s.level(), 0, max);
        }
        int before = s.level();
        if (target == before) {
            return new SetLevelResult(SetLevelStatus.UNCHANGED, before, 0, max);
        }

        int spPerLevel = spAtLevelUp(s, target > before ? target : target + 1);
        int spDelta = spPerLevel * (target - before);
        s.skills().adjustSp(spDelta);

        long exp = table.levelBottom(target);
        s.levelAndExp(target, exp);
        if (s.hasCharacter() && s.store() != null) {
            s.store().character().saveExperience(s.characNo(), target, exp);
        }

        byte[] body = new Noti37ExpBody(target, (int) exp, 0, 0,
                s.skills().remainSp(), Noti37ExpBody.clampWinPoint(s.winPoint()), 0).toBytes();
        s.sendNow(Noti37ExpBody.PACKET_ID, body);

        CharacterService.recalcCombatStats(s, "GM 改等级");
        if (!s.inDungeon()) {
            CharacterService.resendOwnCharacterInfo(s, "GM 改等级");
        }
        CharacterService.sendDetailToParty(s, "GM 改等级");
        QuestService.sendAcceptableQuests(s, "GM 把等级改成 " + target);
        return new SetLevelResult(SetLevelStatus.DONE, target, spDelta, max);
    }
}
