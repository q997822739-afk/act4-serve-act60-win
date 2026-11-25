package com.dylan.store.blob;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ContinuousItemBlob {

    private ContinuousItemBlob() {
    }

    public static final int BYTES = 404;

    public static final int MAX_ENTRIES = (BYTES - 4) / 8;

    private static final int COUNT_OFFSET = 0;

    private static final int FIRST_ENTRY_OFFSET = 4;

    public static Map<Integer, Long> decode(byte[] blob) {
        Map<Integer, Long> out = new LinkedHashMap<>();
        if (blob == null || blob.length < FIRST_ENTRY_OFFSET) {
            return out;
        }
        int count = blob[COUNT_OFFSET];
        for (int i = 0; i < count && i < MAX_ENTRIES; i++) {
            int at = FIRST_ENTRY_OFFSET + 8 * i;
            if (at + 8 > blob.length) {
                break;
            }
            long itemId = LittleEndian.u32(blob, at);
            long expire = LittleEndian.u32(blob, at + 4);
            out.put((int) itemId, expire);
        }
        return out;
    }

    public static byte[] encode(Map<Integer, Long> entries) {
        byte[] blob = new byte[BYTES];
        int n = 0;
        for (var e : entries.entrySet()) {
            if (n >= MAX_ENTRIES) {
                break;
            }
            int at = FIRST_ENTRY_OFFSET + 8 * n;
            LittleEndian.putU32(blob, at, e.getKey() & 0xFFFF_FFFFL);
            LittleEndian.putU32(blob, at + 4, e.getValue());
            n++;
        }
        blob[COUNT_OFFSET] = (byte) n;
        return blob;
    }
}
