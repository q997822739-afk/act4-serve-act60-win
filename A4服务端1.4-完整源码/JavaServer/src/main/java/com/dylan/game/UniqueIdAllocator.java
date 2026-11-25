package com.dylan.game;

import java.util.BitSet;

public final class UniqueIdAllocator {

    public static final int FIRST = 1;

    public static final int LAST = 0xFFFE;

    public static final int NONE = 0xFFFF;

    public static final int UNASSIGNED = -1;

    private final BitSet used = new BitSet(LAST + 1);

    private int cursor = FIRST;

    public synchronized int allocate() {
        int id = find();
        if (id < 0) {
            cursor = FIRST;
            id = find();
        }
        if (id < 0) {
            throw new IllegalStateException(
                    "连接编号用光了：" + FIRST + ".." + LAST + " 全被占着（在线 " + inUse() + " 条）");
        }
        cursor = id + 1;
        return id;
    }

    private int find() {
        for (int i = cursor; i <= LAST; i++) {
            if (!used.get(i)) {
                used.set(i);
                return i;
            }
        }
        return -1;
    }

    public synchronized void release(int id) {
        if (id == UNASSIGNED) {
            return;
        }
        if (id < FIRST || id > LAST) {
            return;
        }
        used.clear(id);
    }

    public synchronized int inUse() {
        return used.cardinality();
    }

    synchronized void reset() {
        used.clear();
        cursor = FIRST;
    }
}
