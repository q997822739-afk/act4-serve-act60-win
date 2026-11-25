package com.dylan.protocol.body;

public final class EquipmentSlot {

    private EquipmentSlot() {
    }

    private static final String[] NAMES = {
            "[hat avatar]", "[hair avatar]", "[face avatar]", "[coat avatar]",
            "[pants avatar]", "[shoes avatar]", "[breast avatar]", "[waist avatar]",
            "[skin avatar]", "[weapon]", "[title name]", "[coat]",
            "[shoulder]", "[pants]", "[shoes]", "[waist]",
            "[amulet]", "[wrist]", "[ring]", "[creature]",
            "[artifact red]", "[artifact blue]", "[artifact green]",
    };

    public static final int COUNT = NAMES.length;

    public static final int SKIN_AVATAR = 8;

    public static final int WEAPON = 9;

    public static final int TITLE = 10;

    public static final int BASIC_LIST_COUNT = TITLE + 1;

    public static final int CREATURE = 19;

    public static final int FIRST_ARTIFACT = 20;

    public static final int NONE = -1;

    public static boolean affectsModel(int slot) {
        return slot >= 0 && slot <= TITLE;
    }

    public static boolean refreshesOwnInfo(int slot) {
        return affectsModel(slot) || slot == CREATURE;
    }

    public static boolean broadcastSuppressedInDungeon(int slot) {
        return slot != CREATURE;
    }

    public static String name(int slot) {
        return slot >= 0 && slot < NAMES.length ? NAMES[slot] : "[?" + slot + "]";
    }

    public static int of(String label) {
        if (label == null) {
            return NONE;
        }
        String trimmed = label.trim();
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equalsIgnoreCase(trimmed)) {
                return i;
            }
        }
        return NONE;
    }
}
