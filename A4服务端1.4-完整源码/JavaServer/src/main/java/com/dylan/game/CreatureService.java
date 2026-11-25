package com.dylan.game;

import com.dylan.protocol.body.EquipmentSlot;
import com.dylan.protocol.body.ItemListType;
import com.dylan.protocol.body.Noti100DiedCreatureBody;
import com.dylan.protocol.body.Noti102GainExpCreatureBody;
import com.dylan.protocol.body.Noti103CreatureStateBody;
import com.dylan.protocol.body.Noti104ResponseCreatureBody;
import com.dylan.protocol.body.Noti106EvoluteCreatureBody;
import com.dylan.protocol.body.Noti107RevivalCreatureBody;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.CreatureScript;
import com.dylan.pvf.script.ExpTable;
import com.dylan.pvf.script.ItemScript;
import com.dylan.store.CreatureItemRow;
import com.dylan.store.GameStore;

public final class CreatureService {
    public static final int FEED_ITEM_ID = 24;

    public static final int ERROR_FEED_FAILED = 133;

    private CreatureService() {
    }

    public static long nowSecs() {
        return System.currentTimeMillis() / 1000L;
    }

    public static Creature load(GameSession session, CreatureItemRow row) {
        return Creature.load(row, scriptOf(session, row.itemId()), expTable(session), nowSecs());
    }

    public static Creature reload(GameSession session, Creature old, CreatureItemRow updated) {
        Creature rebuilt = Creature.load(updated, scriptOf(session, updated.itemId()),
                expTable(session), old.stomach().lastSecs());
        rebuilt.stomach().set(old.stomach().raw());
        rebuilt.setStateRaw(old.state());
        return rebuilt;
    }

    public static Creature hatched(GameSession session, Creature old, CreatureItemRow updated) {
        Creature rebuilt = reload(session, old, updated);
        rebuilt.stomach().set(CreatureItemRow.FULL_STOMACH);
        rebuilt.stomach().resume(nowSecs());
        return rebuilt;
    }

    public static void setState(GameSession session, Creature creature, int newState) {
        long now = nowSecs();
        int oldState = creature.state();
        double stomach = creature.stomach().valueAt(oldState, now);

        if (oldState != newState && newState == CreatureStomach.STATE_FIGHTING) {
            scheduleStarvationCheck(session, stomach);
        } else if (oldState != newState && newState == CreatureStomach.STATE_TOWN
                && stomach < CreatureStomach.DEAD_BELOW) {
            revive(session, creature);
        }
        creature.setStateRaw(newState);
    }

    public static void equip(GameSession session, Creature creature) {
        setState(session, creature, fighting(session)
                ? CreatureStomach.STATE_FIGHTING : CreatureStomach.STATE_TOWN);
    }

    public static void dismantle(GameSession session, Creature creature) {
        setState(session, creature, CreatureStomach.STATE_UNEQUIPPED);
    }

    public static void turnStomach(GameSession session, boolean fighting) {
        Creature worn = wornCreature(session);
        if (worn == null) {
            return;
        }
        if (fighting) {
            if (worn.state() == CreatureStomach.STATE_TOWN) {
                setState(session, worn, CreatureStomach.STATE_FIGHTING);
            }
        } else if (worn.state() == CreatureStomach.STATE_FIGHTING) {
            setState(session, worn, CreatureStomach.STATE_TOWN);
        }
    }

    private static void scheduleStarvationCheck(GameSession session, double stomach) {
        if (session == null) {
            return;
        }
        long delayMs = (long) (stomach * CreatureStomach.DRAIN_SECONDS_PER_POINT * 1000L);
        int uniqueId = session.uniqueId();
        int characNo = session.characNo();
        session.schedule(() -> {
            if (session.uniqueId() != uniqueId || session.characNo() != characNo) {
                return;
            }
            checkDiedCreature(session);
        }, delayMs);
    }

    public static void checkDiedCreature(GameSession session) {
        Creature worn = wornCreature(session);
        if (worn == null || !worn.dead(nowSecs())) {
            return;
        }
        byte[] body = new Noti100DiedCreatureBody(session.uniqueId()).toBytes();
        broadcastWorld(session, Noti100DiedCreatureBody.PACKET_ID, body);
    }

    private static void revive(GameSession session, Creature creature) {
        byte[] revival = new Noti107RevivalCreatureBody(session.uniqueId()).toBytes();
        broadcastWorld(session, Noti107RevivalCreatureBody.PACKET_ID, revival);
        creature.stomach().set(CreatureStomach.REVIVE_VALUE);
        sendStomach(session, creature, (int) CreatureStomach.REVIVE_VALUE);
    }

    public static int useItem(GameSession session, long itemId) {
        if (itemId != FEED_ITEM_ID) {
            return 0;
        }
        return useFeed(session) ? 0 : ERROR_FEED_FAILED;
    }

    public static boolean useFeed(GameSession session) {
        Creature worn = wornCreature(session);
        if (worn == null) {
            return false;
        }
        if (worn.dead(nowSecs())) {
            byte[] revival = new Noti107RevivalCreatureBody(session.uniqueId()).toBytes();
            broadcastWorld(session, Noti107RevivalCreatureBody.PACKET_ID, revival);
        }
        int fed = (int) worn.stomach().feed();
        worn.setRow(worn.row().withStomach(fed));
        sendStomach(session, worn, fed);
        persist(session, worn);
        return true;
    }

    public static boolean setStomach(GameSession session, int value) {
        Creature worn = wornCreature(session);
        if (worn == null) {
            return false;
        }
        worn.stomach().set(value);
        worn.stomach().resume(nowSecs());
        int now = worn.stomachNow(nowSecs());
        worn.setRow(worn.row().withStomach(now));
        sendStomach(session, worn, now);
        persist(session, worn);
        return true;
    }

    public static void gainExp(GameSession session, int base) {
        Creature worn = wornCreature(session);
        if (worn == null || worn.script() == null || base <= 0) {
            return;
        }
        if (worn.maxedOut()) {
            return;
        }
        if (worn.stomachNow(nowSecs()) <= 0) {
            return;
        }
        boolean levelledUp = worn.gainExp(base, artifactExpRate(session, worn), expTable(session));
        if (!worn.maxedOut() || levelledUp) {
            byte[] body = new Noti102GainExpCreatureBody(worn.level(), worn.exp()).toBytes();
            session.send(Noti102GainExpCreatureBody.PACKET_ID, body);
        }
        persist(session, worn);
        if (levelledUp && ableToEvolute(session, worn)) {
            evolute(session, worn);
        }
    }

    public static void gainExpOnDungeonClear(GameSession session, int fatigueSpent) {
        if (fatigueSpent <= 0) {
            return;
        }
        gainExp(session, fatigueSpent);
    }

    private static boolean ableToEvolute(GameSession session, Creature creature) {
        CreatureScript script = creature.script();
        if (script == null || !script.evolves()) {
            return false;
        }
        if (script.evolutionLevel() > creature.level()) {
            return false;
        }
        if (script.evolutionQuest()) {
            return false;
        }
        return true;
    }

    public static void evolute(GameSession session, Creature creature) {
        CreatureScript script = creature.script();
        int newCreatureId = script.evolutionCreatureId();
        GameScripts scripts = session == null ? null : session.scripts();
        if (scripts == null) {
            return;
        }
        var table = scripts.creatureScripts();
        if (table.byCreatureId(newCreatureId).isEmpty()) {
            return;
        }
        int newItemId = table.itemIdOf(newCreatureId);
        if (newItemId <= 0) {
            return;
        }
        InventoryService inventory = session.inventory();
        var equipslot = inventory.containerOf(ItemListType.LIST_EQUIPPED);
        if (equipslot == null || EquipmentSlot.CREATURE >= equipslot.slots()) {
            return;
        }
        var slotItem = equipslot.get(EquipmentSlot.CREATURE);
        if (slotItem.isEmpty()) {
            return;
        }
        equipslot.set(EquipmentSlot.CREATURE, slotItem.withItemId(newItemId));
        inventory.markItemsDirty();
        Creature evolved = reload(session, creature, creature.row().withEvolved(newItemId));
        inventory.replaceCreature(creature, evolved);
        GameStore store = session.store();
        if (store != null && evolved.uiId() != CreatureItemRow.NOT_PERSISTED) {
            store.creature().evolveCreatureItem(evolved.uiId(), newItemId);
        }

        byte[] body = new Noti106EvoluteCreatureBody(newCreatureId, session.uniqueId()).toBytes();
        sendAccordingToPlace(session, Noti106EvoluteCreatureBody.PACKET_ID, body);
        CharacterService.sendEquipChanged(session, EquipmentSlot.CREATURE, "宠物进化");
        session.flush();
    }

    public static double artifactExpRate(GameSession session, Creature creature) {
        InventoryService inventory = session == null ? null : session.inventory();
        GameScripts scripts = session == null ? null : session.scripts();
        if (inventory == null || scripts == null) {
            return 0.0;
        }
        var equipslot = inventory.containerOf(ItemListType.LIST_EQUIPPED);
        if (equipslot == null) {
            return 0.0;
        }
        double total = 0.0;
        for (int slot = EquipmentSlot.FIRST_ARTIFACT; slot < EquipmentSlot.COUNT; slot++) {
            if (!creature.artifactAvailable(slot) || slot >= equipslot.slots()) {
                continue;
            }
            var item = equipslot.get(slot);
            if (item.isEmpty()) {
                continue;
            }
            ItemScript artifact = scripts.item((int) item.itemId()).orElse(null);
            if (artifact == null) {
                continue;
            }
            int min = artifact.creature().minimumLevel();
            if (min == 0 || creature.level() >= min) {
                total += artifact.creature().expAmountRate();
            }
        }
        return total;
    }

    public static void respond(GameSession session) {
        byte[] body = new Noti104ResponseCreatureBody(session.uniqueId()).toBytes();
        broadcastArea(session, Noti104ResponseCreatureBody.PACKET_ID, body);
        session.flush();
    }

    public static long extraGold(GameSession session, long gold) {
        Creature worn = wornCreature(session);
        if (worn == null || gold <= 0 || worn.deadPeek(nowSecs())) {
            return 0;
        }
        return switch (speciesOf(worn)) {
            case 22, 23, 24, 64 -> gold * 5 / 100;
            case 42, 54, 55, 56, 63 -> gold * 3 / 100;
            default -> 0;
        };
    }

    public static void saveAll(GameSession session) {
        InventoryService inventory = session == null ? null : session.inventory();
        GameStore store = session == null ? null : session.store();
        if (inventory == null || store == null) {
            return;
        }
        long now = nowSecs();
        int saved = 0;
        for (Creature creature : inventory.creatures()) {
            if (creature.uiId() == CreatureItemRow.NOT_PERSISTED) {
                continue;
            }
            int stomach = creature.stomachNow(now);
            if (!creature.stomach().worthSaving(creature.state())) {
                continue;
            }
            creature.setRow(creature.row().withStomach(stomach));
            store.creature().updateCreatureItemStat(creature.uiId(), creature.exp(), stomach);
            saved++;
        }
    }

    private static void persist(GameSession session, Creature creature) {
        GameStore store = session == null ? null : session.store();
        if (store == null || creature.uiId() == CreatureItemRow.NOT_PERSISTED) {
            return;
        }
        store.creature().updateCreatureItemStat(creature.uiId(), creature.exp(),
                creature.stomachNow(nowSecs()));
    }

    private static Creature wornCreature(GameSession session) {
        InventoryService inventory = session == null ? null : session.inventory();
        return inventory == null ? null : inventory.equippedCreature();
    }

    private static boolean fighting(GameSession session) {
        return session != null && session.inDungeon();
    }

    private static int speciesOf(Creature creature) {
        return creature.script() == null ? 0 : creature.script().creatureId();
    }

    private static CreatureScript scriptOf(GameSession session, int itemId) {
        GameScripts scripts = session == null ? null : session.scripts();
        if (scripts == null) {
            return null;
        }
        ItemScript item = scripts.item(itemId).orElse(null);
        if (item == null || item.creature().species() == 0) {
            return null;
        }
        return scripts.creatureScripts().byCreatureId(item.creature().species()).orElse(null);
    }

    private static ExpTable expTable(GameSession session) {
        GameScripts scripts = session == null ? null : session.scripts();
        return scripts == null ? null : scripts.creatureScripts().expTable();
    }

    private static void sendStomach(GameSession session, Creature creature, int stomach) {
        byte[] body = new Noti103CreatureStateBody(creature.uiId(), stomach).toBytes();
        session.send(Noti103CreatureStateBody.PACKET_ID, body);
    }

    private static void broadcastWorld(GameSession session, int notiId, byte[] body) {
        session.send(notiId, body);
        session.flush();
    }

    private static void broadcastArea(GameSession session, int notiId, byte[] body) {
        session.send(notiId, body);
    }

    private static void sendAccordingToPlace(GameSession session, int notiId, byte[] body) {
        if (session.inDungeon()) {
            session.send(notiId, body);
            return;
        }
        broadcastArea(session, notiId, body);
    }
}
