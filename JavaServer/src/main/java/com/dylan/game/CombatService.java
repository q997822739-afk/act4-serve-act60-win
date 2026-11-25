package com.dylan.game;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Cmd42DieMonsterRequest;
import com.dylan.protocol.body.Cmd46GetItemRequest;
import com.dylan.protocol.body.Cmd46ReplyBody;
import com.dylan.protocol.body.Cmd127BossDieCheckRequest;
import com.dylan.protocol.body.Noti127Body;
import com.dylan.protocol.body.ItemListType;
import com.dylan.protocol.body.Noti38Body;
import com.dylan.protocol.body.Noti39Body;
import com.dylan.pvf.script.DeathTowerTable;
import com.dylan.pvf.script.GoldDropTable;
import com.dylan.pvf.script.ItemScript;
import com.dylan.store.CharacterInventory;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class CombatService {

    private CombatService() {
    }

    public static void dieMonster(GameSession s, byte[] plain) {
        Cmd42DieMonsterRequest req = Cmd42DieMonsterRequest.parse(plain);
        RoomMonster monster = s.room().kill(req.monsterSeqId());
        if (monster == null) {
            reportUnknownMonster(s, req);
            return;
        }
        checkKillReport(s, req);
        BattleField hellField = s.battleField();
        DungeonRoom.HellKill hellKill = hellField == null
                ? DungeonRoom.HellKill.NONE
                : hellField.killHellPartyMonster(monster);
        if (hellField != null) {
            hellField.reportBossRoomConditionKill(monster);
        }
        if (!ServerConfig.DIE_MONSTER_REPLY) {
            return;
        }

        List<GameSession> members = partyMembers(s);
        List<Noti38Body.Drop> drops = rollDrops(s, monster, hellKill);
        byte[] body = dieMonsterBody(s, monster, drops);
        for (GameSession member : members) {
            member.sendNow(Noti38Body.PACKET_ID, body);
        }
        Party inTower = s.party().current();
        DeathTower tower = s.deathTower();
        grantKillReward(members, monster, tower);
        questItemsFor(s, members, monster);
        if (tower != null) {
            DeathTowerService.afterKillMonster(s, inTower, tower);
            s.flush();
            return;
        }
        DungeonService.prepareDungeonClear(s);
        DungeonService.reportKillClearCondition(s, monster);
        s.flush();
    }

    private static void checkKillReport(GameSession s, Cmd42DieMonsterRequest req) {
        MonsterKillWindow window = s.killWindow();
        window.resetUnknownIdStreak();

        if (window.report(System.currentTimeMillis(), req.dieX(), req.dieY())) {
            s.hack().count(HackType.KILL_MONSTER_POSITION_SPREAD, 0, window.killsInWindow(),
                    "一秒内跨越 x=" + window.spreadX() + " y=" + window.spreadY()
                            + "（阈值 " + MonsterKillWindow.SPREAD_LIMIT + "）各杀一只，"
                            + "这一秒已经杀了 " + window.killsInWindow() + " 只，"
                            + "这一只倒在 (" + req.dieX() + "," + req.dieY() + ")");
        }

        int hitCount = req.hitCount();
        if (hitCount > HIT_COUNT_LIMIT) {
            s.hack().count(HackType.KILL_MONSTER_HIT_OVERFLOW, 0, hitCount,
                    "这一只挨了 " + hitCount + " 下，超过官方上限 " + HIT_COUNT_LIMIT);
            hitCount = 1;
        }
        if (hitCount == ZERO_HIT_MARK) {
            s.hack().count(HackType.KILL_MONSTER_ZERO_HIT, 0, req.hitCount(),
                    "这一只只挨了 1 下就死了（上报的原值是 " + req.hitCount() + "）");
        }

        for (Cmd42DieMonsterRequest.Attacker attacker : req.attackers()) {
            if (!attacker.isCharacter()) {
                continue;
            }
            GameSession who = memberByUniqueId(s, attacker.objectId());
            if (who == null) {
                continue;
            }
            checkAttacker(who, attacker, s.uniqueId());
        }
    }

    private static void checkAttacker(GameSession who, Cmd42DieMonsterRequest.Attacker attacker,
                                      int reporterId) {
        String from = who.uniqueId() == reporterId ? "" : "（由 " + reporterId + " 上报）";
        int perHit = attacker.damagePerHit();
        int limit = ServerConfig.KILL_MONSTER_DAMAGE_LIMIT;
        boolean zeroHit = attacker.hitCount() == 0;
        Runnable record = () -> {
            if (zeroHit) {
                who.hack().count(HackType.KILL_MONSTER_ZERO_HIT_COUNT, 0, attacker.damage(),
                        "打了 " + attacker.damage() + " 点伤害却报 0 次命中" + from);
            }
            if (perHit > limit) {
                who.hack().count(HackType.KILL_MONSTER_OVER_DAMAGE, 0, perHit,
                        "每击 " + perHit + " 点（共 " + attacker.damage() + " 点 / "
                                + attacker.hitCount() + " 下），超过上限 " + limit + from);
            }
        };
        if (!zeroHit && perHit <= limit) {
            return;
        }
        if (who.uniqueId() == reporterId) {
            record.run();
        } else {
            who.run(record);
        }
    }

    private static GameSession memberByUniqueId(GameSession s, int uniqueId) {
        for (GameSession member : partyMembers(s)) {
            if (member.uniqueId() == uniqueId) {
                return member;
            }
        }
        return null;
    }

    private static void reportUnknownMonster(GameSession s, Cmd42DieMonsterRequest req) {
        if (!s.killWindow().reportUnknownId()) {
            return;
        }
        s.hack().count(HackType.KILL_MONSTER_UNKNOWN_ID, 0, req.monsterSeqId(),
                "连续 " + MonsterKillWindow.UNKNOWN_ID_STREAK
                        + " 次上报房间里没有的怪，最后一只报的是 #" + req.monsterSeqId());
    }

    private static final int HIT_COUNT_LIMIT = 1500;

    private static final int ZERO_HIT_MARK = 1;

    public static void bossDying(GameSession s, byte[] plain) {
        Cmd127BossDieCheckRequest req = Cmd127BossDieCheckRequest.parse(plain);

        BattleField field = s.battleField();
        if (!s.inDungeon() || field == null) {
            return;
        }
        BattleField.BossDieCheck check = field.reportBossDie(req.monsterSeqId());
        Noti127Body noti = !check.known()
                ? Noti127Body.denied(BattleField.BOSS_NOT_IN_LIST)
                : check.sweep()
                        ? Noti127Body.confirmAndClear(req.monsterSeqId())
                        : Noti127Body.confirmOnly(req.monsterSeqId());

        if (!check.sweep()) {
            sendBossDieCheck(s, noti);
            return;
        }

        killRemaining(s, req.monsterSeqId(), RoomMonster::sweptOnBossDie);
        sendBossDieCheck(s, noti);
    }

    private static void sendBossDieCheck(GameSession s, Noti127Body noti) {
        byte[] body = noti.toBytes();
        List<GameSession> members = partyMembers(s);
        for (GameSession member : members) {
            member.sendNow(Noti127Body.PACKET_ID, body);
        }
    }

    private static void killRemaining(GameSession s, int keepSeqId,
                                      java.util.function.Predicate<RoomMonster> doomedBy) {
        List<GameSession> members = partyMembers(s);
        List<RoomMonster> doomed = new java.util.ArrayList<>(s.room().living());
        int killed = 0;
        for (RoomMonster m : doomed) {
            if (m.seqId() == keepSeqId) {
                continue;
            }
            if (!doomedBy.test(m)) {
                continue;
            }
            RoomMonster dead = s.room().kill(m.seqId());
            if (dead == null) {
                continue;
            }
            BattleField field = s.battleField();
            DungeonRoom.HellKill hellKill = field == null
                    ? DungeonRoom.HellKill.NONE
                    : field.killHellPartyMonster(dead);
            if (field != null) {
                field.reportBossRoomConditionKill(dead);
            }
            List<Noti38Body.Drop> drops = rollDrops(s, dead, hellKill);
            byte[] body = dieMonsterBody(s, dead, drops);
            for (GameSession member : members) {
                member.send(Noti38Body.PACKET_ID, body);
            }
            grantKillReward(members, dead, null);
            questItemsFor(s, members, dead);
            killed++;
        }
        for (GameSession member : members) {
            member.flush();
        }
    }

    private static void grantKillReward(List<GameSession> members, RoomMonster monster,
                                        DeathTower tower) {
        int winPoint = tower == null ? 0 : rollTowerWinPoint(members, monster);
        for (GameSession member : members) {
            member.run(() -> {
                member.runSummary().countKill(monster.type());
                int exp = tower != null
                        ? towerExp(member, monster)
                        : hellPartyExp(member, monster,
                                ExperienceService.monsterExperience(member, monster));
                boolean gotWinPoint = winPoint > 0 && grantTowerWinPoint(member, winPoint);
                boolean expPacketSent = ExperienceService.grant(member, exp);
                if (gotWinPoint && !expPacketSent) {
                    ExperienceService.syncWinPoint(member, "塔里打死 APC");
                }
            });
        }
    }

    private static int rollTowerWinPoint(List<GameSession> members, RoomMonster monster) {
        if (monster.type() != RoomMonster.TYPE_APC) {
            return 0;
        }
        if (monster.camp() != com.dylan.pvf.script.MapScript.CAMP_MONSTER
                && monster.camp() != com.dylan.pvf.script.MapScript.CAMP_NEUTRAL) {
            return 0;
        }
        for (GameSession member : members) {
            var scripts = member.scripts();
            if (scripts == null) {
                continue;
            }
            var apc = scripts.aiCharacter(monster.monsterId()).orElse(null);
            if (apc == null) {
                return 0;
            }
            return apc.rollPvpWinPoint(ThreadLocalRandom.current());
        }
        return 0;
    }

    private static boolean grantTowerWinPoint(GameSession member, int granted) {
        double rate = com.dylan.game.event.EventService.bonusFactor(
                com.dylan.game.event.EventNo.DEATH_TOWER_WIN_POINT, member);
        int actual = rate == 0.0 ? granted : (int) (granted * rate);
        if (actual <= 0) {
            return false;
        }
        long before = member.winPoint();
        member.winPoint(before + actual);
        if (member.store() != null && member.characNo() > 0
                && !member.store().pvp().setWinPoint(member.characNo(), member.winPoint())) {
            throw new IllegalStateException("charac_no=" + member.characNo()
                    + " 在 pvp_result 里没有行，胜点写不进去（建角色流程漏了 insertPvp）");
        }
        return true;
    }

    private static int towerExp(GameSession member, RoomMonster monster) {
        int base = ExperienceService.towerRewardExp(member, monster.toString());
        if (base <= 0) {
            return 0;
        }
        var scripts = member.scripts();
        DeathTowerTable table = scripts == null ? DeathTowerTable.empty() : scripts.deathTower();
        double weight = table.monsterExpWeight();
        if (monster.type() == RoomMonster.TYPE_APC) {
            weight *= table.apcExpWeight();
        }
        int exp = (int) Math.min(Integer.MAX_VALUE, Math.round(base * weight));
        return exp;
    }

    private static int hellPartyExp(GameSession member, RoomMonster monster, int baseExp) {
        if (!monster.isHellParty() || baseExp <= 0) {
            return baseExp;
        }
        var difficulty = HellPartyService.tableOf(member).difficulty(monster.hellDifficulty());
        if (difficulty == null || difficulty.expRate() <= 0) {
            return baseExp;
        }
        long scaled = (long) baseExp * difficulty.expRate();
        int exp = (int) Math.min(scaled, Integer.MAX_VALUE);
        return exp;
    }

    static List<GameSession> partyMembers(GameSession s) {
        Party party = s.party().current();
        return party == null ? List.of(s) : party.view().members();
    }

    public static void allMonstersDead(GameSession s) {
        if (!s.inDungeon()) {
            return;
        }
        int boss = -1;
        for (RoomMonster m : s.room().living()) {
            if (m.isBoss()) {
                boss = m.seqId();
                break;
            }
        }
        int alive = s.room().clearableLivingCount();
        if (alive <= (boss < 0 ? 0 : 1)) {
            return;
        }
        killRemaining(s, boss, RoomMonster::countsForRoomClear);
    }

    public static final int CMD_ALLDIE_MONSTER = 148;

    private static byte[] dieMonsterBody(GameSession s, RoomMonster monster,
                                         List<Noti38Body.Drop> drops) {
        BattleField field = s.battleField();
        if (!monster.fakeBoss() || field == null) {
            return Noti38Body.withDrops(monster.seqId(), drops).toBytes();
        }
        return Noti38Body.fakeBossDrops(monster.seqId(), drops, field.bossX(), field.bossY())
                .toBytes();
    }

    private static List<Noti38Body.Drop> rollDrops(GameSession s, RoomMonster monster,
                                                   DungeonRoom.HellKill hellKill) {
        if (!ServerConfig.DROPS) {
            return List.of();
        }
        Party inTower = s.party().current();
        DeathTower tower = s.deathTower();
        if (tower != null) {
            return applyDropRate(s,
                    DeathTowerService.rollTowerDrops(s, inTower, tower, monster));
        }
        return applyDropRate(s, new java.util.ArrayList<>(
                new DropService(s.scripts(), s.room(), s.dungeon())
                        .monsterDrops(monster, hellKill.lastOfGroup(),
                                ThreadLocalRandom.current())));
    }

    private static List<Noti38Body.Drop> applyDropRate(GameSession s,
                                                       List<Noti38Body.Drop> drops) {
        int rate = dropRateOf(s);
        if (rate >= 100 || drops.isEmpty()) {
            return drops;
        }
        List<Noti38Body.Drop> kept = new java.util.ArrayList<>(drops.size());
        for (Noti38Body.Drop drop : drops) {
            if (ThreadLocalRandom.current().nextInt(100) < rate) {
                kept.add(drop);
            }
        }
        return kept;
    }

    static int dropRateOf(GameSession s) {
        if (!ServerConfig.HACK_DROP_RATE_PENALTY) {
            return 100;
        }
        if (partyMembers(s).size() > 1) {
            return 100;
        }
        return s.hack().dropRatePercent();
    }

    private static void questItemsFor(GameSession killer, List<GameSession> members,
                                      RoomMonster monster) {
        if (!ServerConfig.DROPS) {
            return;
        }
        DungeonRun run = killer.dungeon();
        int inDungeon = run == null
                ? com.dylan.pvf.script.QuestScript.ANY_DUNGEON : run.dungeonIndex();
        int difficulty = run == null ? -1 : run.difficulty();
        int enemyType = QuestDropService.enemyTypeOf(monster);
        var random = ThreadLocalRandom.current();
        for (GameSession member : members) {
            QuestDropService.onMonsterKill(member, monster, enemyType, inDungeon, difficulty,
                    random);
            member.flush();
        }
    }

    public static void getItem(GameSession s, byte[] plain) {
        Cmd46GetItemRequest req = Cmd46GetItemRequest.parse(plain);
        if (!req.check7Passes()) {
            s.hack().count(HackType.GET_ITEM_BAD_SEED, 0, req.check7(),
                    "check7=" + req.check7() + " 不是 " + Cmd46GetItemRequest.CHECK_DIVISOR
                            + " 的倍数");
        }

        DungeonRoom.GroundItem ground = s.room().pickUp(req.itemNo());
        if (ground == null) {
            reject(s, req.itemNo(), Cmd46ReplyBody.ERROR_SILENT);
            return;
        }

        if (ground.item().isMoney()) {
            pickUpMoney(s, req, ground);
            return;
        }
        if (ground.item().itemId() == Noti39Body.GOLD_BAG_ITEM_ID) {
            pickUpGoldBag(s, req, ground);
            return;
        }

        ItemScript script = s.scripts() == null ? null
                : s.scripts().item(ground.item().itemId()).orElse(null);
        if (script == null) {
            s.fail("捡起来的 " + ground.item() + " 查不到物品脚本 —— 掉落池和物品库不同步");
            return;
        }
        int count = script.isStackableItem() ? (int) ground.item().addInfo() : 1;
        Party party = s.party().current();
        ItemRoutingService.Roll roll = party == null
                ? ItemRoutingService.Roll.toPicker(s)
                : ItemRoutingService.decide(party, s, ground, script, count,
                        req.routingFlag() == ROUTING_FLAG_ON);
        GameSession winner = roll.winner();

        if (winner == null) {
            s.room().restore(ground);
            for (GameSession member : partyMembers(s)) {
                reject(member, req.itemNo(), Cmd46ReplyBody.ERROR_BAG_FULL_ALT);
            }
            return;
        }

        winner.run(() -> {
            int slot = storePickedItem(winner, ground, script, count);
            s.run(() -> finishPickup(s, req, ground, roll, winner, slot));
        });
    }

    private static void finishPickup(GameSession s, Cmd46GetItemRequest req,
                                     DungeonRoom.GroundItem ground,
                                     ItemRoutingService.Roll roll, GameSession winner,
                                     int slot) {
        if (slot == SLOT_DISCONNECTED) {
            if (winner != s) {
                s.room().restore(ground);
                reject(s, req.itemNo(), Cmd46ReplyBody.ERROR_SILENT);
            }
            return;
        }
        if (slot < 0) {
            s.room().restore(ground);
            reject(s, req.itemNo(), slot == SLOT_BAG_FULL
                    ? Cmd46ReplyBody.ERROR_BAG_FULL : Cmd46ReplyBody.ERROR_SILENT);
            return;
        }

        byte[] body = new Noti39Body(req.itemNo(), s.uniqueId(), winner.uniqueId(), slot,
                roll.dice()).toBytes();
        List<GameSession> members = partyMembers(s);
        for (GameSession member : members) {
            member.sendNow(Noti39Body.PACKET_ID, body);
        }
    }

    private static void pickUpGoldBag(GameSession s, Cmd46GetItemRequest req,
                                      DungeonRoom.GroundItem ground) {
        GoldDropTable goldTable = s.scripts() == null ? null
                : s.scripts().monsterDrop().goldTable();
        if (goldTable == null) {
            s.fail("捡到金币袋 #" + req.itemNo() + " 但金钱表没就绪");
            return;
        }
        Party party = s.party().current();
        Noti39Body.MoneyShare[] shares = new Noti39Body.MoneyShare[Noti39Body.MONEY_SLOTS];
        java.util.Arrays.fill(shares, Noti39Body.MoneyShare.NONE);
        List<Runnable> credits = new java.util.ArrayList<>(Noti39Body.MONEY_SLOTS);
        long picked = 0;
        for (int slot = 0; slot < Noti39Body.MONEY_SLOTS; slot++) {
            GameSession member = party == null
                    ? (slot == 0 ? s : null)
                    : party.view().at(slot);
            if (member == null) {
                continue;
            }
            long amount = GoldDropTable.roll(goldTable.forLevel(member.level()),
                    ThreadLocalRandom.current());
            if (party != null && !party.enteredMapAt(slot)) {
                amount = 0;
            }
            amount += CreatureService.extraGold(member, amount);
            if (member == s) {
                picked = amount;
                shares[slot] = Noti39Body.MoneyShare.of(creditMoney(s, amount));
            } else {
                shares[slot] = Noti39Body.MoneyShare.of(amount);
                long share = amount;
                credits.add(() -> member.run(() -> {
                    creditMoney(member, share);
                    sendGoldUpdate(member);
                }));
            }
        }

        byte[] body = Noti39Body.money(req.itemNo(), s.uniqueId(), shares);
        List<GameSession> members = partyMembers(s);
        for (GameSession member : members) {
            member.sendNow(Noti39Body.PACKET_ID, body);
        }
        sendGoldUpdate(s);
        for (Runnable credit : credits) {
            credit.run();
        }
    }

    private static void pickUpMoney(GameSession s, Cmd46GetItemRequest req,
                                    DungeonRoom.GroundItem ground) {
        long total = ground.item().addInfo();
        Party party = s.party().current();
        int live = party == null ? 1 : party.liveCountEnterMap();
        if (live <= 0) {
            s.room().restore(ground);
            reject(s, req.itemNo(), Cmd46ReplyBody.ERROR_SILENT);
            return;
        }
        long each = total / live;
        long remainder = total % live;

        Noti39Body.MoneyShare[] shares = new Noti39Body.MoneyShare[Noti39Body.MONEY_SLOTS];
        java.util.Arrays.fill(shares, Noti39Body.MoneyShare.NONE);
        List<Runnable> credits = new java.util.ArrayList<>(Noti39Body.MONEY_SLOTS);
        for (int slot = 0; slot < Noti39Body.MONEY_SLOTS; slot++) {
            GameSession member = party == null
                    ? (slot == 0 ? s : null)
                    : party.view().at(slot);
            if (member == null) {
                continue;
            }
            long amount = member == s ? each + remainder : each;
            if (party != null && !party.enteredMapAt(slot)) {
                amount = 0;
            }
            amount += CreatureService.extraGold(member, amount);
            if (member == s) {
                shares[slot] = Noti39Body.MoneyShare.of(creditMoney(s, amount));
            } else {
                shares[slot] = Noti39Body.MoneyShare.of(amount);
                long share = amount;
                credits.add(() -> member.run(() -> {
                    creditMoney(member, share);
                    sendGoldUpdate(member);
                }));
            }
        }

        byte[] body = Noti39Body.money(req.itemNo(), s.uniqueId(), shares);
        List<GameSession> members = partyMembers(s);
        for (GameSession member : members) {
            member.sendNow(Noti39Body.PACKET_ID, body);
        }
        sendGoldUpdate(s);
        for (Runnable credit : credits) {
            credit.run();
        }
    }

    private static long creditMoney(GameSession s, long amount) {
        if (amount <= 0) {
            return 0;
        }
        InventoryService inv = s.inventory();
        if (inv.inventory() == null) {
            return amount;
        }
        long before = inv.money();
        inv.setMoney(before + amount);
        long got = inv.money() - before;
        inv.save();
        return got;
    }

    private static void sendGoldUpdate(GameSession s) {
        if (!ServerConfig.GOLD_PICKUP_ITEM_UPDATE) {
            return;
        }
        s.inventory().sendItemUpdate(ItemListType.LIST_MAIN, "拾取金币（对照开关）",
                BagLayout.SLOT_GOLD);
    }

    private static void reject(GameSession s, int itemNo, int errorCode) {
        byte[] body = Cmd46ReplyBody.rejected(errorCode);
        s.replyNow(Cmd46ReplyBody.PACKET_ID, body);
    }

    private static final int ROUTING_FLAG_ON = 1;

    private static final int SLOT_BAG_FULL = -1;

    private static final int SLOT_DISCONNECTED = -3;

    private static int storePickedItem(GameSession s, DungeonRoom.GroundItem ground,
                                       ItemScript script, int count) {
        if (s.store() == null || s.characNo() < 0) {
            return BagLayout.FIRST_ITEM_SLOT;
        }
        CharacterInventory inv = s.inventory().inventory();
        if (inv == null) {
            s.fail("角色 " + s.characNo() + " 在 inventory 表里没有行，捡不了东西");
            return SLOT_DISCONNECTED;
        }
        if (ground.fromPlayer()) {
            return storeDroppedBack(s, ground, script, inv);
        }
        InventoryService.Landing landing = s.inventory().addItem(script, count);
        if (!landing.ok()) {
            return SLOT_BAG_FULL;
        }
        int slot = landing.slot();
        boolean intoCreature = landing.itemSpace()
                == com.dylan.protocol.body.ItemListType.LIST_CREATURE;
        if (intoCreature) {
            s.store().inventory().saveCreatureItems(s.characNo(), inv.creature());
        } else {
            s.store().inventory().saveInventoryItems(s.characNo(), inv.inventory());
        }
        return slot;
    }

    private static int storeDroppedBack(GameSession s, DungeonRoom.GroundItem ground,
                                        ItemScript script, CharacterInventory inv) {
        boolean intoCreature = InventoryService.isCreatureItemType(
                InventoryService.itemTypeOf(script));
        com.dylan.store.blob.ItemArray bag =
                intoCreature ? inv.creature() : inv.inventory();
        int slot = s.inventory().insertExisting(bag, ground.original());
        if (slot < 0) {
            return SLOT_BAG_FULL;
        }
        s.inventory().markItemsDirty();
        if (intoCreature) {
            s.store().inventory().saveCreatureItems(s.characNo(), inv.creature());
        } else {
            s.store().inventory().saveInventoryItems(s.characNo(), inv.inventory());
        }
        return slot;
    }
}
