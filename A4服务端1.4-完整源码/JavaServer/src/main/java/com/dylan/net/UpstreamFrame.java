package com.dylan.net;

import java.util.ArrayList;
import java.util.List;

public record UpstreamFrame(int category, int packetId, int totalLength,
                            int crc, int sequence, byte[] body) {
    /** A4：type u8, id u8, 总长 u32, crc u32。u16 序号是载荷前 2 字节（明文），不在头里。 */
    public static final int HEADER_SIZE = 10;

    public static final int MAX_TOTAL_LENGTH = 0xAF000 + HEADER_SIZE;

    public record Drained(List<UpstreamFrame> frames, int consumed, boolean corrupt) {
    }

    public static Drained drain(byte[] buffer, int length) {
        List<UpstreamFrame> out = new ArrayList<>();
        int off = 0;
        while (length - off >= HEADER_SIZE) {
            int total = u32(buffer, off + 2);
            if (total < HEADER_SIZE || total > MAX_TOTAL_LENGTH) {
                return new Drained(List.copyOf(out), off, true);
            }
            if (total > length - off) {
                break;
            }
            out.add(at(buffer, off, total));
            off += total;
        }
        return new Drained(List.copyOf(out), off, false);
    }

    public static List<UpstreamFrame> split(byte[] batch) {
        List<UpstreamFrame> out = new ArrayList<>();
        int off = 0;
        while (off < batch.length) {
            if (batch.length - off < HEADER_SIZE) {
                return List.of();
            }
            int total = u32(batch, off + 2);
            if (total < HEADER_SIZE || total > MAX_TOTAL_LENGTH || total > batch.length - off) {
                return List.of();
            }
            out.add(at(batch, off, total));
            off += total;
        }
        return out;
    }

    private static UpstreamFrame at(byte[] buffer, int off, int total) {
        byte[] body = new byte[total - HEADER_SIZE];
        System.arraycopy(buffer, off + HEADER_SIZE, body, 0, body.length);
        int sequence = body.length >= 2 ? (body[0] & 0xFF) | ((body[1] & 0xFF) << 8) : 0;
        return new UpstreamFrame(
                buffer[off] & 0xFF,
                buffer[off + 1] & 0xFF,
                total,
                u32(buffer, off + 6),
                sequence,
                body);
    }

    private static int u32(byte[] b, int off) {
        return (b[off] & 0xFF)
                | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16)
                | ((b[off + 3] & 0xFF) << 24);
    }

    @Override
    public String toString() {
        return String.format("上行 cat=%d id=%d 总长=%d seq=%d crc=0x%08X body=%d 字节",
                category, packetId, totalLength, sequence, crc, body.length);
    }
}
