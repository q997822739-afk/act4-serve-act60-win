package com.dylan.protocol.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

public final class UpstreamCipher {

    public static final int BLOCK_SIZE = 16;

    public static final byte[] DEFAULT_KEY = {
            0x37, 0x3A, 0x3B, 0x3A, 0x37, 0x38, 0x36, 0x37,
            0x35, 0x3B, 0x35, 0x31, 0x3B, 0x33, 0x38, 0x35,
    };

    private static final byte[] EMPTY = new byte[0];

    private final byte[] key;

    public UpstreamCipher(byte[] key) {
        if (key.length != BLOCK_SIZE) {
            throw new IllegalArgumentException("上行密钥必须正好 " + BLOCK_SIZE
                    + " 字节（AES-128），实际 " + key.length);
        }
        this.key = key.clone();
    }

    public static UpstreamCipher withDefaultKey() {
        return new UpstreamCipher(DEFAULT_KEY);
    }

    public byte[] decrypt(byte[] body) {
        if (body.length == 0) {
            return EMPTY;
        }
        if (body.length % BLOCK_SIZE != 0) {
            return null;
        }
        try {
            Cipher c = Cipher.getInstance("AES/ECB/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            return c.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-128/ECB 在这个 JVM 上不可用", e);
        }
    }

    public String describe() {
        StringBuilder hex = new StringBuilder();
        for (byte b : key) {
            hex.append(String.format("%02X ", b));
        }
        return "AES-128/ECB，密钥 " + hex.toString().trim()
                + "（ASCII \"" + new String(key, StandardCharsets.US_ASCII) + "\"）";
    }
}
