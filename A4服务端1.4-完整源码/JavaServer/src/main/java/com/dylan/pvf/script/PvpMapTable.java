package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code etc/pvpmapbasicdata.etc}：决斗地图表。客户端 {@code 007EBFFF} 按出现顺序读进
 * {@code 0xD00000}（地图号）、{@code 0xE02E10}（死斗图）、{@code 0xE02E24}（大会）、{@code 0xE02E38}（一般决斗场），最多 0x14 条。
 * NOTI 45 的地图字节、CMD 62 和 NOTI 41/42 的地图都是这张表的下标（{@code 00961870} 取 +0x84 → 第 n 条）。
 * 第 0 条是「50000 随机」，map.lst 里没有这张图，开打时不能发 0。
 */
public final class PvpMapTable {
    public static final String PATH = "etc/pvpmapbasicdata.etc";

    /** 客户端 {@code 007EC07E} 读满 0x14 条就停。 */
    public static final int MAX = 0x14;

    public record Entry(int mapId, boolean deathMatch, boolean tournament, boolean normal) {
    }

    private final List<Entry> entries;

    private PvpMapTable(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static PvpMapTable empty() {
        return new PvpMapTable(List.of());
    }

    public static PvpMapTable load(PvfArchive archive) {
        return parse(archive.readText(PATH));
    }

    /** 每个 {@code [pvp map basic data]} 段六个数：地图号、大图、小图、死斗、大会、一般。 */
    public static PvpMapTable parse(String text) {
        var tokens = ScriptReader.tokenize(text);
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < tokens.size() && out.size() < MAX; i++) {
            if (!tokens.get(i).isSection("pvp map basic data")) {
                continue;
            }
            List<Integer> v = new ArrayList<>(6);
            for (int k = i + 1; k < tokens.size() && v.size() < 6; k++) {
                if (tokens.get(k).kind() != ScriptReader.Kind.WORD) {
                    break;
                }
                v.add(tokens.get(k).asInt());
            }
            if (v.size() == 6) {
                out.add(new Entry(v.get(0), v.get(3) != 0, v.get(4) != 0, v.get(5) != 0));
            }
        }
        return new PvpMapTable(out);
    }

    public int size() {
        return entries.size();
    }

    public Entry get(int index) {
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    public boolean isDeathMatch(int index) {
        Entry e = get(index);
        return e != null && e.deathMatch();
    }
}
