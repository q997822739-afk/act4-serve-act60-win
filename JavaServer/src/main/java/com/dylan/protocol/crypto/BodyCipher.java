package com.dylan.protocol.crypto;

public final class BodyCipher {
    public static final int DEFAULT_SEED_VALUE = 16053;

    public static final KeyParams DOWNSTREAM = KeyParams.fromSeedValue(DEFAULT_SEED_VALUE);

    private BodyCipher() {
    }

    public static void decrypt(byte[] buf, int offset, int length, KeyParams key) {
        int k = key.xorKey();
        int r = key.rolCount();
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            buf[i] = (byte) (k ^ Bits.rol8(buf[i], r));
        }
    }

    public static void encrypt(byte[] buf, int offset, int length, KeyParams key) {
        int k = key.xorKey();
        int r = key.rolCount();
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            buf[i] = (byte) Bits.ror8((buf[i] ^ k), r);
        }
    }

    public static byte[] decryptToCopy(byte[] buf, int offset, int length, KeyParams key) {
        byte[] out = new byte[length];
        System.arraycopy(buf, offset, out, 0, length);
        decrypt(out, 0, length, key);
        return out;
    }
}
