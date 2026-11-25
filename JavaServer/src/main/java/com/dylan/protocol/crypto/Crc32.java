package com.dylan.protocol.crypto;

public final class Crc32 {

    public static final int INIT = 0;

    private static final int POLYNOMIAL = 0xEDB88320;
    private static final int[] TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int c = i;
            for (int bit = 0; bit < 8; bit++) {
                c = ((c & 1) != 0) ? (POLYNOMIAL ^ (c >>> 1)) : (c >>> 1);
            }
            TABLE[i] = c;
        }
    }

    private Crc32() {
    }

    public static int update(int crc, byte[] data, int offset, int length) {
        int c = ~crc;
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            c = TABLE[(c ^ data[i]) & 0xFF] ^ (c >>> 8);
        }
        return ~c;
    }

    public static int update(int crc, byte[] data) {
        return update(crc, data, 0, data.length);
    }

    public static int of(byte[] data, int offset, int length) {
        return update(INIT, data, offset, length);
    }

    public static int of(byte[] data) {
        return update(INIT, data, 0, data.length);
    }
}
