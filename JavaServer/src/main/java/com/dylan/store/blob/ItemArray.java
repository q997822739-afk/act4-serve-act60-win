package com.dylan.store.blob;

import java.util.Arrays;

public final class ItemArray {
    public static final int EQUIPSLOT_SLOTS =
            com.dylan.protocol.body.EquipmentSlot.COUNT;

    public static final int INVENTORY_SLOTS = 249;

    public static final int CREATURE_SLOTS = com.dylan.game.CreatureBagLayout.SLOTS;

    public static final int CARGO_SLOTS = 152;

    private final InvenItem[] items;

    private ItemArray(InvenItem[] items) {
        this.items = items;
    }

    public static ItemArray empty(int slots) {
        if (slots <= 0) {
            throw new IllegalArgumentException("槽位数必须为正：" + slots);
        }
        InvenItem[] items = new InvenItem[slots];
        Arrays.fill(items, InvenItem.EMPTY);
        return new ItemArray(items);
    }

    public static ItemArray emptyEquipslot() {
        return empty(EQUIPSLOT_SLOTS);
    }

    public static ItemArray emptyInventory() {
        return empty(INVENTORY_SLOTS);
    }

    public static ItemArray emptyCreature() {
        return empty(CREATURE_SLOTS);
    }

    public static ItemArray emptyCargo() {
        return empty(CARGO_SLOTS);
    }

    public static ItemArray decode(byte[] blob, int slots) {
        byte[] raw = BlobCodec.decompress(blob, blob == null || blob.length == 0 ? -1 : slots * InvenItem.BYTES);
        if (raw.length == 0) {
            return empty(slots);
        }
        if (raw.length != slots * InvenItem.BYTES) {
            throw new BlobFormatException("blob 解出 " + raw.length + " 字节，期望 "
                    + (slots * InvenItem.BYTES) + "（" + slots + " × " + InvenItem.BYTES + "）");
        }
        InvenItem[] items = new InvenItem[slots];
        for (int i = 0; i < slots; i++) {
            items[i] = InvenItem.read(raw, i * InvenItem.BYTES);
        }
        return new ItemArray(items);
    }

    public static ItemArray decodeTolerant(byte[] blob, int slots) {
        byte[] raw = BlobCodec.decompress(blob, -1);
        if (raw.length == 0) {
            return empty(slots);
        }
        if (raw.length % InvenItem.BYTES != 0) {
            throw new BlobFormatException("blob 解出 " + raw.length + " 字节，不是 "
                    + InvenItem.BYTES + " 的整数倍");
        }
        int stored = raw.length / InvenItem.BYTES;
        InvenItem[] items = new InvenItem[slots];
        Arrays.fill(items, InvenItem.EMPTY);
        for (int i = 0; i < Math.min(stored, slots); i++) {
            items[i] = InvenItem.read(raw, i * InvenItem.BYTES);
        }
        if (stored > slots) {
            for (int i = slots; i < stored; i++) {
                InvenItem dropped = InvenItem.read(raw, i * InvenItem.BYTES);
                if (!dropped.isEmpty()) {
                    throw new BlobFormatException("blob 存了 " + stored + " 格，现在只认 "
                            + slots + " 格，而第 " + i + " 格上有物品 " + dropped.itemId()
                            + " —— 截断会丢东西，先把容量改回去");
                }
            }
        }
        return new ItemArray(items);
    }

    public byte[] encode() {
        byte[] raw = new byte[items.length * InvenItem.BYTES];
        for (int i = 0; i < items.length; i++) {
            items[i].write(raw, i * InvenItem.BYTES);
        }
        return BlobCodec.compress(raw);
    }

    public int slots() {
        return items.length;
    }

    public InvenItem get(int slot) {
        checkSlot(slot);
        return items[slot];
    }

    public void set(int slot, InvenItem item) {
        checkSlot(slot);
        if (item == null) {
            throw new IllegalArgumentException("item 不能为 null，空格子用 InvenItem.EMPTY");
        }
        items[slot] = item;
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= items.length) {
            throw new IndexOutOfBoundsException("槽号 " + slot + " 越界，共 " + items.length + " 格");
        }
    }

    public int usedSlots() {
        int n = 0;
        for (InvenItem item : items) {
            if (!item.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    @Override
    public String toString() {
        return "ItemArray[" + usedSlots() + "/" + items.length + " 格有物品]";
    }
}
