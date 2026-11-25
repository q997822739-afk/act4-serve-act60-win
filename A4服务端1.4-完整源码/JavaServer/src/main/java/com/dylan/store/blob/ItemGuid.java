package com.dylan.store.blob;

public record ItemGuid(long serverId, long datetime, int sequenceNo) {

    public static final int BYTES = 10;

    public static final ItemGuid EMPTY = new ItemGuid(0, 0, 0);

    public ItemGuid {
        if (serverId < 0 || serverId > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("serverId 超出 u32：" + serverId);
        }
        if (datetime < 0 || datetime > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("datetime 超出 u32：" + datetime);
        }
        if (sequenceNo < 0 || sequenceNo > 0xFFFF) {
            throw new IllegalArgumentException("sequenceNo 超出 u16：" + sequenceNo);
        }
    }

    public boolean isEmpty() {
        return serverId == 0 && datetime == 0 && sequenceNo == 0;
    }

    static ItemGuid read(byte[] src, int offset) {
        return new ItemGuid(
                LittleEndian.u32(src, offset),
                LittleEndian.u32(src, offset + 4),
                LittleEndian.u16(src, offset + 8));
    }

    void write(byte[] dst, int offset) {
        LittleEndian.putU32(dst, offset, serverId);
        LittleEndian.putU32(dst, offset + 4, datetime);
        LittleEndian.putU16(dst, offset + 8, sequenceNo);
    }
}
