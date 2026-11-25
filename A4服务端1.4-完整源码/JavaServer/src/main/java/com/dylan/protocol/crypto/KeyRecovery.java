package com.dylan.protocol.crypto;

import java.util.Optional;

public final class KeyRecovery {
    private KeyRecovery() {
    }

    public static final byte[] NO_PREFIX = new byte[0];

    public static Optional<KeyParams> recover(byte[] cipherBody, int offset, int length,
                                              byte[] crcPrefix, int expectedCrc) {
        if (length <= 0) {
            return Optional.empty();
        }

        int prefixCrc = Crc32.update(Crc32.INIT, crcPrefix);

        byte[] scratch = new byte[length];
        for (int k = 0; k <= 0xFF; k++) {
            for (int r = 0; r < 8; r++) {
                for (int i = 0; i < length; i++) {
                    scratch[i] = (byte) (k ^ Bits.rol8(cipherBody[offset + i], r));
                }
                if (Crc32.update(prefixCrc, scratch, 0, length) == expectedCrc) {
                    return Optional.of(new KeyParams(k, r));
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<byte[]> decrypt(byte[] cipherBody, int offset, int length,
                                           byte[] crcPrefix, int expectedCrc) {
        if (length <= 0) {
            return Optional.of(new byte[0]);
        }
        return recover(cipherBody, offset, length, crcPrefix, expectedCrc)
                .map(key -> BodyCipher.decryptToCopy(cipherBody, offset, length, key));
    }

    public static boolean verify(byte[] plainBody, int offset, int length,
                                 byte[] crcPrefix, int expectedCrc) {
        int crc = Crc32.update(Crc32.INIT, crcPrefix);
        return Crc32.update(crc, plainBody, offset, length) == expectedCrc;
    }
}
