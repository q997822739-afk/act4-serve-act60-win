package com.dylan.store.blob;

final class LittleEndian {

    private LittleEndian() {
    }

    static int u16(byte[] src, int offset) {
        return (src[offset] & 0xFF) | ((src[offset + 1] & 0xFF) << 8);
    }

    static void putU16(byte[] dst, int offset, int value) {
        dst[offset] = (byte) value;
        dst[offset + 1] = (byte) (value >>> 8);
    }

    static long u32(byte[] src, int offset) {
        return ((long) (src[offset] & 0xFF)
                | ((long) (src[offset + 1] & 0xFF) << 8)
                | ((long) (src[offset + 2] & 0xFF) << 16)
                | ((long) (src[offset + 3] & 0xFF) << 24)) & 0xFFFF_FFFFL;
    }

    static void putU32(byte[] dst, int offset, long value) {
        dst[offset] = (byte) value;
        dst[offset + 1] = (byte) (value >>> 8);
        dst[offset + 2] = (byte) (value >>> 16);
        dst[offset + 3] = (byte) (value >>> 24);
    }
}
