package com.dylan.protocol.body;

import java.util.List;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;
import com.dylan.pvf.script.ExpertJobScript;

public final class Noti221ExpertJobInfoBody {

    public static final int PACKET_ID = 221;

    public static final int MAX_RECIPES = 0xFF;

    private Noti221ExpertJobInfoBody() {
    }

    public static byte[] withRecipes(int giveupCount, int expertJobType,
                                     List<Integer> recipeItemIds) {
        checkU8("giveupCount", giveupCount);
        checkU8("expertJobType", expertJobType);
        if (recipeItemIds.size() > MAX_RECIPES) {
            throw new PacketFormatException("配方条数是 u8，装不下 " + recipeItemIds.size());
        }
        PacketWriter w = new PacketWriter(3 + recipeItemIds.size() * 4);
        w.u8(giveupCount).u8(expertJobType).u8(recipeItemIds.size());
        for (int id : recipeItemIds) {
            w.u32(id);
        }
        return w.toArray();
    }

    public static byte[] disjointer(int giveupCount, int level, int endurance) {
        checkU8("giveupCount", giveupCount);
        PacketWriter w = new PacketWriter(10);
        w.u8(giveupCount).u8(ExpertJobScript.TYPE_DISJOINTER).u32(level + 1).u32(endurance);
        return w.toArray();
    }

    public static byte[] none(int giveupCount) {
        checkU8("giveupCount", giveupCount);
        return new PacketWriter(2).u8(giveupCount).u8(ExpertJobScript.TYPE_NONE).toArray();
    }

    private static void checkU8(String what, int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(what + " 超出 u8：" + value);
        }
    }
}
