package com.dylan.net.entrance;

import java.nio.charset.StandardCharsets;

/**
 * 网关（entrance）帧的 11 字节头 —— 移植自 A12 参考实现
 * {@code server/src/proto/wire.cpp} 的 {@code WireCodec::EncodeEntrance}：
 *
 * <pre>
 *   [0]      u8   type = 0（网关帧恒为 0；游戏帧这里是 0=通知 / 1=指令应答）
 *   [1]      u8   协议号
 *   [2..5]   u32 LE  total = 11 + payload.size()      ← 含头自身
 *   [6..9]   u32 LE  0
 *   [10]     u8   1
 *   [11..]   payload
 * </pre>
 *
 * <p>实测客户端的第一发包（43 字节）与此完全吻合：
 * <pre>
 *   00 0B 2B 00 00 00 00 00 00 00 01 | F2 F7 …（32 字节 payload）
 *   ↑  ↑  └─ 0x2B = 43 = 11 + 32     ↑        ↑
 *   │  └─ 协议号 11（密钥交换）        │        └─ payload 起点 = 偏移 11
 *   └─ type 0                        └─ 偏移 10 的常量 1
 * </pre>
 *
 * <p><b>一个容易踩的不对称</b>：A12 的 {@code EncodeEntrance} 把 payload 放在偏移
 * <b>11</b>，而 {@code DecodeRaw} 取 body 的起点是 {@code kHeaderLength} = <b>10</b>。
 * 也就是说它自己解析请求时会把偏移 10 那个常量 {@code 1} 也算进 body 里。
 * 因为网关的五个协议**全都不读请求体**，这个不对称一直没有暴露。
 * 本实现按 EncodeEntrance 的口径（payload 在 11），请求体一律不解析。
 */
public final class EntranceFrame {

    /** payload 之前的字节数。 */
    public static final int HEADER = 11;

    /** 只够读出 total 的最小长度（total 在偏移 2..5，读全需要 6 字节）。 */
    public static final int MIN_HEADER = 6;

    /** A12 的 {@code WireCodec::kMaxFrame}。 */
    public static final int MAX_FRAME = 4 * 1024 * 1024;

    public static final int TYPE_ENTRANCE = 0;

    private EntranceFrame() {
    }

    /** 组一个网关应答帧。 */
    public static byte[] encode(int protocolId, byte[] payload) {
        byte[] body = payload == null ? new byte[0] : payload;
        byte[] out = new byte[HEADER + body.length];
        // A12 wire.cpp and the original capture both use classification 0.
        out[0] = TYPE_ENTRANCE;
        out[1] = (byte) protocolId;
        writeU32(out, 2, HEADER + body.length);
        writeU32(out, 6, 0);
        out[10] = 1;
        System.arraycopy(body, 0, out, HEADER, body.length);
        return out;
    }

    public static int totalOf(byte[] buffer) {
        return (int) readU32(buffer, 2);
    }

    public static int protocolIdOf(byte[] buffer) {
        return buffer[1] & 0xFF;
    }

    public static int typeOf(byte[] buffer) {
        return buffer[0] & 0xFF;
    }

    public static void writeU32(byte[] out, int offset, long value) {
        out[offset] = (byte) value;
        out[offset + 1] = (byte) (value >>> 8);
        out[offset + 2] = (byte) (value >>> 16);
        out[offset + 3] = (byte) (value >>> 24);
    }

    public static long readU32(byte[] in, int offset) {
        return (in[offset] & 0xFFL)
                | ((in[offset + 1] & 0xFFL) << 8)
                | ((in[offset + 2] & 0xFFL) << 16)
                | ((in[offset + 3] & 0xFFL) << 24);
    }

    /**
     * A12 的 {@code ByteBuffer::AsciiFixed}：
     * <pre>
     *   n = min(text.length, width - 1)     ← 留一个字节，最多写 width-1 个字符
     *   写 n 个字节，再补 (width - n) 个 0
     * </pre>
     * 注意是**截断到 width-1**（不是 width），并且用 0 填充。
     */
    public static byte[] asciiFixed(String text, int width) {
        byte[] raw = text == null ? new byte[0] : text.getBytes(StandardCharsets.US_ASCII);
        int n = Math.min(raw.length, width - 1);
        byte[] out = new byte[width];
        System.arraycopy(raw, 0, out, 0, n);
        return out;
    }

    public static String hex(byte[] data) {
        if (data == null) {
            return "(null)";
        }
        StringBuilder sb = new StringBuilder(data.length * 3);
        for (byte b : data) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    public static String hexPrefix(byte[] data, int limit) {
        if (data == null) {
            return "(null)";
        }
        if (data.length <= limit) {
            return hex(data);
        }
        byte[] head = new byte[limit];
        System.arraycopy(data, 0, head, 0, limit);
        return hex(head) + " …(共 " + data.length + " 字节)";
    }
}

