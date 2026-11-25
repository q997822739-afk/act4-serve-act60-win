package com.dylan.pvf;

public record PvfEntry(String name, int nameHash, int offset, int length, int key) {

    public int paddedLength() {
        return (length + 3) & ~3;
    }
}
