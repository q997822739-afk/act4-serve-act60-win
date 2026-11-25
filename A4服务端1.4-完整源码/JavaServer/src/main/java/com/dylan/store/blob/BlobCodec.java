package com.dylan.store.blob;

import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class BlobCodec {

    private static final int HEADER_BYTES = 4;

    private static final int LENGTH_MASK = 0x3FFF_FFFF;

    private static final int MAX_RAW_BYTES = 1 << 20;

    private BlobCodec() {
    }

    public static byte[] compress(byte[] raw) {
        if (raw == null) {
            throw new IllegalArgumentException("raw 不能为 null");
        }
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try {
            deflater.setInput(raw);
            deflater.finish();
            byte[] buffer = new byte[HEADER_BYTES + raw.length + (raw.length >> 3) + 64];
            int compressed = deflater.deflate(buffer, HEADER_BYTES, buffer.length - HEADER_BYTES);
            if (!deflater.finished()) {
                throw new BlobFormatException("压缩缓冲区不够：raw=" + raw.length + " 字节");
            }
            int masked = raw.length & LENGTH_MASK;
            buffer[0] = (byte) masked;
            buffer[1] = (byte) (masked >>> 8);
            buffer[2] = (byte) (masked >>> 16);
            buffer[3] = (byte) (masked >>> 24);
            byte[] out = new byte[HEADER_BYTES + compressed];
            System.arraycopy(buffer, 0, out, 0, out.length);
            return out;
        } finally {
            deflater.end();
        }
    }

    public static byte[] decompress(byte[] blob, int expectedRawLength) {
        if (blob == null || blob.length == 0) {
            return new byte[0];
        }
        if (blob.length < HEADER_BYTES) {
            throw new BlobFormatException("blob 只有 " + blob.length + " 字节，连 4 字节长度头都不够");
        }
        int rawLength = ((blob[0] & 0xFF)
                | ((blob[1] & 0xFF) << 8)
                | ((blob[2] & 0xFF) << 16)
                | ((blob[3] & 0xFF) << 24)) & LENGTH_MASK;
        if (rawLength > MAX_RAW_BYTES) {
            throw new BlobFormatException("blob 头声称原始长度 " + rawLength
                    + " 字节，超出上限 " + MAX_RAW_BYTES + "，多半是数据坏了");
        }
        if (expectedRawLength >= 0 && rawLength != expectedRawLength) {
            throw new BlobFormatException("blob 头声称原始长度 " + rawLength
                    + " 字节，期望 " + expectedRawLength);
        }
        byte[] raw = new byte[rawLength];
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(blob, HEADER_BYTES, blob.length - HEADER_BYTES);
            int produced = inflater.inflate(raw);
            if (produced != rawLength) {
                throw new BlobFormatException("blob 头声称 " + rawLength
                        + " 字节，实际只解出 " + produced);
            }
            return raw;
        } catch (DataFormatException e) {
            throw new BlobFormatException("blob 的 zlib 流解不开：" + e.getMessage(), e);
        } finally {
            inflater.end();
        }
    }

    public static byte[] decompress(byte[] blob) {
        return decompress(blob, -1);
    }
}
