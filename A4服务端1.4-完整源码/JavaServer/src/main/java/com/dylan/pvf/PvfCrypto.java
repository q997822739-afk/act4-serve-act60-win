package com.dylan.pvf;

import com.dylan.protocol.crypto.Crc32;

public final class PvfCrypto {

    private static final int ROTATION = 6;

    private PvfCrypto() {
    }

    public static boolean decrypt(byte[] data, int seed, int keyAndCrc) {
        if ((data.length & 3) != 0) {
            throw new PvfFormatException("加密块长度 " + data.length
                    + " 不是 4 的倍数；客户端遇到这种长度直接判失败");
        }
        for (int i = 0; i < data.length; i += 4) {
            int cipher = (data[i] & 0xFF)
                    | ((data[i + 1] & 0xFF) << 8)
                    | ((data[i + 2] & 0xFF) << 16)
                    | ((data[i + 3] & 0xFF) << 24);
            int plain = Integer.rotateRight(cipher ^ keyAndCrc, ROTATION);
            data[i] = (byte) plain;
            data[i + 1] = (byte) (plain >>> 8);
            data[i + 2] = (byte) (plain >>> 16);
            data[i + 3] = (byte) (plain >>> 24);
        }
        return Crc32.update(seed, data) == keyAndCrc;
    }
}
