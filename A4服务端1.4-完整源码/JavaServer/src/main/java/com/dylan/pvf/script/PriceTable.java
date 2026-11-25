package com.dylan.pvf.script;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

public final class PriceTable {
    public static final String PATH = "equipment/pricetable.tbl";

    private static final String RATE_SECTION = "비율";

    public static final int DENOMINATOR = 1000;

    public static final PriceTable DEFAULT = new PriceTable(200, 150, 225);

    private final int townSellRate;
    private final int dungeonSellRate;
    private final int repairRate;

    private PriceTable(int townSellRate, int dungeonSellRate, int repairRate) {
        this.townSellRate = townSellRate;
        this.dungeonSellRate = dungeonSellRate;
        this.repairRate = repairRate;
    }

    public int sellRate(boolean inDungeon) {
        return inDungeon ? dungeonSellRate : townSellRate;
    }

    public long sellPrice(long basePrice, boolean inDungeon, int durability, int maxDurability) {
        long full = basePrice * sellRate(inDungeon) / DENOMINATOR;
        if (maxDurability <= 0) {
            return full;
        }
        return (long) durability * full / maxDurability;
    }

    public static PriceTable load(PvfArchive archive) {
        return parse(archive.readText(PATH));
    }

    public static PriceTable parse(String text) {
        var tokens = ScriptReader.tokenize(text);
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.kind() != ScriptReader.Kind.SECTION || isNumeric(token.text())) {
                continue;
            }
            if (i + 3 >= tokens.size()) {
                break;
            }
            return new PriceTable(tokens.get(i + 1).asInt(), tokens.get(i + 2).asInt(),
                    tokens.get(i + 3).asInt());
        }
        throw new PvfFormatException(PATH + " 里没有 [" + RATE_SECTION + "] 那一行的三个比率");
    }

    private static boolean isNumeric(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "PriceTable[城镇收购 " + townSellRate + "‰, 副本收购 " + dungeonSellRate
                + "‰, 修理 " + repairRate + "‰]";
    }
}
