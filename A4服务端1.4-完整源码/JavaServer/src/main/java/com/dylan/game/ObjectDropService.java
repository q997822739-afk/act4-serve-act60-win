package com.dylan.game;

import com.dylan.game.drop.GeneratedItem;
import com.dylan.game.drop.ObjectDropGenerator;
import com.dylan.protocol.body.Noti29Body;
import com.dylan.pvf.GameScripts;
import com.dylan.pvf.script.DungeonScript;
import com.dylan.pvf.script.MapScript;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public final class ObjectDropService {

    private static final int MIN_LEVEL = 1;
    private static final int MAX_LEVEL = 200;

    private final GameScripts scripts;

    public ObjectDropService(GameScripts scripts) {
        this.scripts = scripts;
    }

    public record ObjectDrop(GeneratedItem item, int objectIndex) {
    }

    public List<ObjectDrop> fill(MapScript map, DungeonScript dungeon,
                                 int standardLevel, int dungeonDiff, RandomGenerator random) {
        if (map == null || dungeon == null || map.mapItems().isEmpty()) {
            return List.of();
        }
        if (standardLevel < MIN_LEVEL || standardLevel > MAX_LEVEL) {
            return List.of();
        }

        List<DungeonScript.AssignItem> assigns = dungeon.assignItems();
        List<ObjectDrop> locked = new ArrayList<>();
        for (MapScript.MapItem mapItem : map.mapItems()) {
            int index = mapItem.assignIndex();
            if (index < 0 || index >= assigns.size()) {
                break;
            }
            DungeonScript.AssignItem assign = assigns.get(index);
            int level = assign.levelOr(standardLevel);

            List<GeneratedItem> generated = new ArrayList<>();
            generateRandom(level, dungeonDiff, mapItem, generated, random);
            generateSpecific(assign, mapItem, generated, random);
            generateDungeonObjectDrop(dungeon, generated, random);

            for (GeneratedItem item : generated) {
                locked.add(new ObjectDrop(item, mapItem.objectIndex()));
            }
        }
        return List.copyOf(locked);
    }

    public static List<Noti29Body.MapObject> wireOf(DungeonRoom room) {
        List<DungeonRoom.GroundItem> ground = room.groundItems();
        List<Noti29Body.MapObject> wire = new ArrayList<>(ground.size());
        for (DungeonRoom.GroundItem g : ground) {
            wire.add(toWire(g.itemNo(), g.objectIndex(), g.item()));
        }
        return wire;
    }

    private void generateRandom(int level, int dungeonDiff, MapScript.MapItem mapItem,
                                List<GeneratedItem> out, RandomGenerator random) {
        ObjectDropGenerator generator = scripts.objectDrop();
        if (generator == null) {
            return;
        }
        generator.generateRandomItem(level, dungeonDiff, mapItem.randomDropCount(), out, random);
    }

    private void generateSpecific(DungeonScript.AssignItem assign, MapScript.MapItem mapItem,
                                  List<GeneratedItem> out, RandomGenerator random) {
        ObjectDropGenerator generator = scripts.objectDrop();
        if (generator == null) {
            return;
        }
        generator.generateSpecificItem(assign, mapItem.specifyDropCount(), out, random);
    }

    private void generateDungeonObjectDrop(DungeonScript dungeon, List<GeneratedItem> out,
                                           RandomGenerator random) {
        var generator = scripts.dungeonDrop();
        if (generator == null) {
            return;
        }
        generator.generateSpecificItem(dungeon.dungeonDrop(), out, random);
    }

    private static Noti29Body.MapObject toWire(int itemNo, int objectIndex, GeneratedItem item) {
        return new Noti29Body.MapObject(objectIndex, itemNo, item.itemId(),
                (int) Math.min(item.addInfo(), Integer.MAX_VALUE), item.durability(),
                0, 0);
    }
}
