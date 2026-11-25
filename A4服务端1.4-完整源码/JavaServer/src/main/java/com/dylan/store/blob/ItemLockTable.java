package com.dylan.store.blob;

import java.util.Arrays;

public final class ItemLockTable {

    public static final int LOCKS = 100;

    public static final int ENTRY_BYTES = 8;

    public static final int BYTES = LOCKS * ENTRY_BYTES;

    public static final int FIRST_KEY = 1;

    public static final int LAST_KEY = LOCKS;

    public static final int STATE_FREE = 0;

    public static final int STATE_LOCKED = 1;

    public static final int STATE_UNLOCK_WAITING = 2;

    private final int[] state = new int[LOCKS];
    private final long[] time = new long[LOCKS];

    private ItemLockTable() {
    }

    public static ItemLockTable empty() {
        return new ItemLockTable();
    }

    public static ItemLockTable decode(byte[] blob) {
        ItemLockTable table = new ItemLockTable();
        if (blob == null) {
            return table;
        }
        for (int i = 0; i < LOCKS; i++) {
            int off = i * ENTRY_BYTES;
            if (off + ENTRY_BYTES > blob.length) {
                break;
            }
            table.time[i] = LittleEndian.u32(blob, off);
            table.state[i] = blob[off + 4] & 0xFF;
        }
        return table;
    }

    public byte[] encode() {
        byte[] blob = new byte[BYTES];
        for (int i = 0; i < LOCKS; i++) {
            int off = i * ENTRY_BYTES;
            LittleEndian.putU32(blob, off, time[i]);
            blob[off + 4] = (byte) state[i];
        }
        return blob;
    }

    private static int index(int key) {
        if (key < FIRST_KEY || key > LAST_KEY) {
            throw new BlobFormatException("锁编号只有 " + FIRST_KEY + ".." + LAST_KEY
                    + "，收到 " + key);
        }
        return key - FIRST_KEY;
    }

    public int state(int key) {
        return state[index(key)];
    }

    public long time(int key) {
        return time[index(key)];
    }

    public boolean free(int key) {
        return state(key) == STATE_FREE;
    }

    public void set(int key, int newState, long newTime) {
        int i = index(key);
        if (newState < STATE_FREE || newState > STATE_UNLOCK_WAITING) {
            throw new BlobFormatException("锁状态只有 0 / 1 / 2，收到 " + newState);
        }
        if (newTime < 0 || newTime > 0xFFFF_FFFFL) {
            throw new BlobFormatException("时刻超出 u32：" + newTime);
        }
        state[i] = newState;
        time[i] = newTime;
    }

    public void release(int key) {
        set(key, STATE_FREE, 0);
    }

    public int firstFreeKey() {
        for (int key = FIRST_KEY; key <= LAST_KEY; key++) {
            if (free(key)) {
                return key;
            }
        }
        return 0;
    }

    public int usedLocks() {
        int used = 0;
        for (int s : state) {
            if (s != STATE_FREE) {
                used++;
            }
        }
        return used;
    }

    @Override
    public String toString() {
        return "ItemLockTable[用掉 " + usedLocks() + " / " + LOCKS + " 把]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemLockTable other)) {
            return false;
        }
        return Arrays.equals(state, other.state) && Arrays.equals(time, other.time);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(state) + Arrays.hashCode(time);
    }
}
