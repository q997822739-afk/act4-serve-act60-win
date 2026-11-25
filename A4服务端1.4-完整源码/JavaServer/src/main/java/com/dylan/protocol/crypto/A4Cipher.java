package com.dylan.protocol.crypto;

/**
 * DNF.exe 1.0.44.1 频道包加密。双向同一把 32 字节循环 XOR，取代 A5 的 ROR/XOR 下行与 AES 上行。
 * 下行：6 字节头后整段载荷，从密钥第 0 字节开始。
 * 上行：载荷前 2 字节（u16 序号）保持明文，其余从密钥第 0 字节开始；CRC32 对含序号的明文计算。
 */
public final class A4Cipher {
    public static final byte[] KEY = {
            0x21, 0x0B, (byte) 0x85, (byte) 0xF0, (byte) 0xC0, 0x51, 0x56, (byte) 0xD9,
            (byte) 0x81, 0x02, 0x43, 0x3B, (byte) 0x80, 0x11, 0x3A, (byte) 0xA1,
            0x75, 0x77, 0x6A, (byte) 0x8D, (byte) 0x9E, (byte) 0x9C, 0x32, (byte) 0x93,
            0x3D, 0x7A, 0x56, 0x6D, 0x2D, 0x5E, 0x6C, (byte) 0xCB,
    };

    public static final int SEQUENCE_SIZE = 2;

    private A4Cipher() {
    }

    /** XOR 自逆，加密解密同一个函数。 */
    public static void apply(byte[] bytes, int offset, int count) {
        for (int i = 0; i < count; i++) {
            bytes[offset + i] ^= KEY[i % KEY.length];
        }
    }

    /** 上行载荷 → 明文（含前 2 字节序号）。不改入参。 */
    public static byte[] decryptUpstream(byte[] body) {
        byte[] plain = body.clone();
        if (plain.length > SEQUENCE_SIZE) {
            apply(plain, SEQUENCE_SIZE, plain.length - SEQUENCE_SIZE);
        }
        return plain;
    }
}
