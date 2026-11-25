package com.dylan.store;

import com.dylan.store.blob.BlobCodec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class QuestClearFlags {

    public static final int SIZE = 30000;

    public static final int MAX_QUEST_ID = 29999;

    private final byte[] flags;
    private boolean dirty;

    private QuestClearFlags(byte[] flags) {
        this.flags = flags;
    }

    public static QuestClearFlags empty() {
        return new QuestClearFlags(new byte[SIZE]);
    }

    public static QuestClearFlags decode(byte[] blob) {
        if (blob == null || blob.length == 0) {
            return empty();
        }
        byte[] raw = BlobCodec.decompress(blob, SIZE);
        if (raw.length != SIZE) {
            byte[] padded = new byte[SIZE];
            System.arraycopy(raw, 0, padded, 0, Math.min(raw.length, SIZE));
            return new QuestClearFlags(padded);
        }
        return new QuestClearFlags(raw);
    }

    public byte[] encode() {
        return BlobCodec.compress(flags);
    }

    public boolean isCleared(int questId) {
        if (questId <= 0 || questId > MAX_QUEST_ID) {
            return false;
        }
        return flags[questId] != 0;
    }

    public boolean setCleared(int questId) {
        if (questId <= 0 || questId > MAX_QUEST_ID) {
            return false;
        }
        if (flags[questId] == 0) {
            flags[questId] = 1;
            dirty = true;
        }
        return true;
    }

    public boolean reset(int questId) {
        if (questId <= 0 || questId > MAX_QUEST_ID) {
            return false;
        }
        if (flags[questId] != 0) {
            flags[questId] = 0;
            dirty = true;
        }
        return true;
    }

    public int count() {
        int n = 0;
        for (int i = 1; i <= MAX_QUEST_ID; i++) {
            if (flags[i] != 0) {
                n++;
            }
        }
        return n;
    }

    public List<Integer> clearedIds() {
        List<Integer> out = new ArrayList<>();
        for (int i = 1; i <= MAX_QUEST_ID; i++) {
            if (flags[i] != 0) {
                out.add(i);
            }
        }
        return out;
    }

    public boolean dirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof QuestClearFlags other && Arrays.equals(flags, other.flags);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(flags);
    }

    @Override
    public String toString() {
        return "已完成 " + count() + " 个任务";
    }
}
