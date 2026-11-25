package com.dylan.store.blob;

public final class SkillSlots {

    public static final int ENTRY_BYTES = 2;

    public static final int SLOTS = 222;

    public static final int RAW_BYTES = SLOTS * ENTRY_BYTES;

    public static final int LEGACY_RAW_BYTES = 204 * ENTRY_BYTES;

    public static final int PAGE_SLOTS = 42;

    public static final int QUICK_SLOT_START = 0;
    public static final int QUICK_SLOT_END = 6;
    public static final int LOCKED_START = 204;
    public static final int LOCKED_END = 216;
    public static final int QUICK_SLOT_2ND_START = 216;
    public static final int QUICK_SLOT_2ND_END = SLOTS;

    private static final int[][] GROUP_BOUNDS = {
            {6, 48}, {48, 90}, {90, 132}, {132, 174}, {174, 216},
    };

    public static final int GROUP_COUNT = GROUP_BOUNDS.length;

    private final byte[] raw;

    private SkillSlots(byte[] raw) {
        this.raw = raw;
    }

    public static SkillSlots empty() {
        return new SkillSlots(new byte[RAW_BYTES]);
    }

    public static SkillSlots decode(byte[] blob) {
        byte[] decoded = BlobCodec.decompress(blob, -1);
        if (decoded.length == 0) {
            return empty();
        }
        if (decoded.length == LEGACY_RAW_BYTES) {
            byte[] widened = new byte[RAW_BYTES];
            System.arraycopy(decoded, 0, widened, 0, LEGACY_RAW_BYTES);
            return new SkillSlots(widened);
        }
        if (decoded.length != RAW_BYTES) {
            throw new BlobFormatException("skill_slot 解出 " + decoded.length
                    + " 字节，期望 " + RAW_BYTES + "（或 " + LEGACY_RAW_BYTES + " 的老行）");
        }
        return new SkillSlots(decoded);
    }

    public byte[] encode() {
        return BlobCodec.compress(raw.clone());
    }

    public int skillIndexAt(int slot) {
        checkSlot(slot);
        return raw[slot * ENTRY_BYTES] & 0xFF;
    }

    public int levelAt(int slot) {
        checkSlot(slot);
        return raw[slot * ENTRY_BYTES + 1];
    }

    public boolean isEmptyAt(int slot) {
        return skillIndexAt(slot) == 0;
    }

    public void set(int slot, int skillIndex, int level) {
        checkSlot(slot);
        if (skillIndex < 0 || skillIndex > 0xFF) {
            throw new IllegalArgumentException("skillIndex 超出 u8：" + skillIndex);
        }
        if (level < Byte.MIN_VALUE || level > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("level 超出 i8：" + level);
        }
        raw[slot * ENTRY_BYTES] = (byte) skillIndex;
        raw[slot * ENTRY_BYTES + 1] = (byte) level;
    }

    public void clear(int slot) {
        set(slot, 0, 0);
    }

    public int learn(int group, int skillIndex, int level) {
        if (group < 0 || group >= GROUP_COUNT) {
            throw new IllegalArgumentException("group 越界：" + group + "，合法 0.." + (GROUP_COUNT - 1));
        }
        return fill(GROUP_BOUNDS[group][0], GROUP_BOUNDS[group][1], skillIndex, level);
    }

    public int learnInQuickSlot(int skillIndex, int level) {
        return fill(QUICK_SLOT_START, QUICK_SLOT_END, skillIndex, level);
    }

    private int fill(int start, int end, int skillIndex, int level) {
        for (int i = start; i < end; i++) {
            if (isEmptyAt(i)) {
                set(i, skillIndex, level);
                return i;
            }
        }
        return -1;
    }

    public int learnedCount() {
        int n = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (!isEmptyAt(i)) {
                n++;
            }
        }
        return n;
    }

    public void swap(int slotA, int slotB) {
        checkSlot(slotA);
        checkSlot(slotB);
        int a = slotA * ENTRY_BYTES;
        int b = slotB * ENTRY_BYTES;
        byte index = raw[a];
        byte level = raw[a + 1];
        raw[a] = raw[b];
        raw[a + 1] = raw[b + 1];
        raw[b] = index;
        raw[b + 1] = level;
    }

    public static int slotGroup(int slot) {
        if (slot < 0 || slot >= SLOTS) {
            return GROUP_OUT_OF_RANGE;
        }
        if (slot < QUICK_SLOT_END) {
            return GROUP_QUICK;
        }
        if (slot >= QUICK_SLOT_2ND_START) {
            return GROUP_QUICK;
        }
        if (slot >= LOCKED_START) {
            return GROUP_LOCKED;
        }
        return (slot - QUICK_SLOT_END) / PAGE_SLOTS;
    }

    public static final int GROUP_QUICK = -1;

    public static final int GROUP_LOCKED = 5;

    public static final int GROUP_OUT_OF_RANGE = 6;

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= SLOTS) {
            throw new IndexOutOfBoundsException("技能槽号 " + slot + " 越界，共 " + SLOTS + " 槽");
        }
    }

    @Override
    public String toString() {
        return "SkillSlots[" + learnedCount() + "/" + SLOTS + " 槽已学]";
    }
}
