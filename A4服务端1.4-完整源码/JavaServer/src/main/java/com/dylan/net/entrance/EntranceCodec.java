package com.dylan.net.entrance;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.Deflater;

/**
 * 网关协议的两段编码 —— 移植自 A12 参考实现
 * {@code server/src/crypto/aes.cpp} + {@code crypto/zstream.cpp}。
 *
 * <h2>密钥：用 32 字节里的前 16 字节</h2>
 * 协议 11 发出去的是一把 **32 字节**密钥（{@code 0x10,0x11,…,0x2F}），但
 * {@code Aes128Ecb::EncryptPadded(plain, key)} 的形参是 {@code const uint8_t key[16]} ——
 * C++ 里数组形参会退化成指针，所以**实际只用前 16 个字节** = {@code 0x10..0x1F}。
 * 这就是"32 字节密钥却做 AES-128"的原因。
 *
 * <h2>填充：是补零，不是 PKCS#7</h2>
 * <pre>
 *   padded = plain.empty() ? 16 : (plain.size() + 15) / 16 * 16
 *   out(padded, 0);  memcpy(out, plain, plain.size());
 * </pre>
 * 也就是**零填充到 16 的整数倍**；刚好整除时**不再补一整块**（与 PKCS#7 不同，
 * 这点搞错会导致所有应答都多 16 字节）。
 *
 * <h2>deflate：是 zlib 流（带 2 字节头 + Adler-32 尾），不是裸 deflate</h2>
 * A12 用的是 zlib 的 {@code compress2(..., Z_BEST_COMPRESSION)}。Java 的
 * {@link Deflater} 默认就带 zlib 包装（{@code nowrap=false}），与之对应。
 *
 * <p>{@link #wrap} = 先 AES 再 deflate（A12 的 {@code EntranceHandler::Wrap}，
 * 只在 {@code WireMode::Encrypted} 下做；我们的客户端就是要加密这一档）。
 */
public final class EntranceCodec {

    /** 协议 11 发给客户端的 32 字节密钥：0x10,0x11,…,0x2F。 */
    public static final byte[] KEY32 = buildKey();

    /** AES 真正用的 16 字节：上面那把密钥的前半段。 */
    public static final byte[] AES_KEY = Arrays.copyOf(KEY32, 16);

    private EntranceCodec() {
    }

    private static byte[] buildKey() {
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            key[i] = (byte) (0x10 + i);
        }
        return key;
    }

    /**
     * AES-128-ECB + **零填充**（不是 PKCS#7）。
     *
     * @param key 16 字节
     */
    public static byte[] aesEncryptZeroPadded(byte[] plain, byte[] key) {
        byte[] data = plain == null ? new byte[0] : plain;
        int padded = data.length == 0 ? 16 : (data.length + 15) / 16 * 16;
        byte[] block = new byte[padded];
        System.arraycopy(data, 0, block, 0, data.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(block);
        } catch (Exception e) {
            throw new IllegalStateException("AES-128-ECB 加密失败", e);
        }
    }

    /** {@link #aesEncryptZeroPadded} 用默认密钥。 */
    public static byte[] aesEncryptZeroPadded(byte[] plain) {
        return aesEncryptZeroPadded(plain, AES_KEY);
    }

    /** zlib deflate，{@code Z_BEST_COMPRESSION} 档（A12 的 {@code Deflate}）。 */
    public static byte[] deflate(byte[] data) {
        if (data == null || data.length == 0) {
            return new byte[0];
        }
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length / 2));
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int n = deflater.deflate(buffer);
                if (n <= 0) {
                    break;
                }
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /**
     * 网关应答的"包裹"：**先 AES（零填充）再 deflate**。
     *
     * <p>A12 在两步失败时都有降级（AES 失败发原文、deflate 失败只发 AES），
     * 这里照抄降级语义，并且把降级写进日志 —— 否则客户端报错时无从判断。
     */
    public static byte[] wrap(byte[] plain) {
        byte[] encrypted;
        try {
            encrypted = aesEncryptZeroPadded(plain, AES_KEY);
        } catch (RuntimeException e) {
            System.out.println("[entrance] AES 阶段失败，改发原文：" + e);
            return plain == null ? new byte[0] : plain;
        }
        byte[] compressed = deflate(encrypted);
        if (compressed.length == 0) {
            System.out.println("[entrance] deflate 阶段失败，只发 AES 结果");
            return encrypted;
        }
        return compressed;
    }
}
