package com.dylan.game;

import com.dylan.game.drop.DropContext;
import com.dylan.game.drop.DungeonDropGenerator;
import com.dylan.game.drop.GeneratedItem;
import com.dylan.game.drop.MonsterDropGenerator;
import com.dylan.game.drop.WorldDropGenerator;
import com.dylan.protocol.body.Noti38Body;
import com.dylan.pvf.GameScripts;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public final class DropService {

    private final GameScripts scripts;
    private final DungeonRoom room;
    private final DungeonRun run;

    public DropService(GameScripts scripts, DungeonRoom room, DungeonRun run) {
        this.scripts = scripts;
        this.room = room;
        this.run = run;
    }

    public List<Noti38Body.Drop> monsterDrops(RoomMonster monster, boolean lastOfHellGroup,
                                              RandomGenerator random) {
        MonsterDropGenerator generator = scripts == null ? null : scripts.monsterDrop();
        if (generator == null) {
            return List.of();
        }
        DropContext ref = contextFor(monster, lastOfHellGroup);
        List<GeneratedItem> generated = new ArrayList<>();
        generator.generateRandomItem(ref, generated, random);
        generator.generateSpecificItem(ref, generated, random);
        generateDungeonDrop(generated, random);
        generateWorldDrop(ref, generated, random);
        if (generated.isEmpty()) {
            return List.of();
        }
        List<Noti38Body.Drop> wire = new ArrayList<>(generated.size());
        for (GeneratedItem item : generated) {
            int itemNo = room.drop(item, monster.seqId());
            wire.add(toWire(itemNo, item));
        }
        return wire;
    }

    private void generateDungeonDrop(List<GeneratedItem> out, RandomGenerator random) {
        DungeonDropGenerator generator = scripts.dungeonDrop();
        if (generator == null) {
            return;
        }
        var dungeon = scripts.dungeon(run.dungeonIndex()).orElse(null);
        if (dungeon == null) {
            return;
        }
        generator.generateSpecificItem(dungeon.dungeonDrop(), out, random);
    }

    private void generateWorldDrop(DropContext ref, List<GeneratedItem> out,
                                   RandomGenerator random) {
        WorldDropGenerator generator = scripts.worldDrop();
        if (generator == null) {
            return;
        }
        generator.generateRandomItem(ref, out, random);
    }

    private DropContext contextFor(RoomMonster monster, boolean lastOfHellGroup) {
        return new DropContext(
                itemDropRate(),
                monster.level(),
                monster.type(),
                run.basisLevel(),
                run.difficulty(),
                1,
                0,
                monster.randomDropCount(),
                monster.specifyDropCount(),
                monster.monsterId(),
                monster.isHellParty(),
                monster.hellDifficulty(),
                lastOfHellGroup);
    }

    private static int itemDropRate() {
        int multiplier = com.dylan.game.event.EventService.isRunning(
                com.dylan.game.event.EventNo.ITEM_DROP_RATIO)
                ? com.dylan.game.event.EventService.param1(
                        com.dylan.game.event.EventNo.ITEM_DROP_RATIO)
                : 1;
        return DropContext.NO_PREMIUM * Math.max(1, multiplier);
    }

    private static Noti38Body.Drop toWire(int itemNo, GeneratedItem item) {
        return Noti38Body.Drop.of(itemNo,
                item.itemId(),
                (int) Math.min(item.addInfo(), Integer.MAX_VALUE),
                item.durability());
    }
}
