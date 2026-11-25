package com.dylan.protocol.io;

import java.nio.charset.Charset;
import java.util.Arrays;

public final class PacketWriter {
    private static final int DEFAULT_CAPACITY = 256;

    public static final Charset DEFAULT_CHARSET = Charset.forName("GBK");

    public static final int MAX_BODY_LENGTH = 0xAF000;

    private byte[] buf;
    private int pos;
    private final Charset charset;

    public PacketWriter() {
        this(DEFAULT_CAPACITY, DEFAULT_CHARSET);
    }

    public PacketWriter(int initialCapacity) {
        this(initialCapacity, DEFAULT_CHARSET);
    }

    public PacketWriter(int initialCapacity, Charset charset) {
        this.buf = new byte[Math.max(16, initialCapacity)];
        this.charset = charset;
    }

    private void ensure(int extra) {
        int needed = pos + extra;
        if (needed > MAX_BODY_LENGTH) {
            throw new PacketFormatException("body would exceed the client's "
                    + MAX_BODY_LENGTH + "-byte limit");
        }
        if (needed > buf.length) {
            int cap = buf.length;
            while (cap < needed) {
                cap = Math.min(MAX_BODY_LENGTH, cap * 2);
            }
            buf = Arrays.copyOf(buf, cap);
        }
    }

    public PacketWriter u8(int value) {
        ensure(1);
        buf[pos++] = (byte) value;
        return this;
    }

    public PacketWriter u16(int value) {
        ensure(2);
        buf[pos++] = (byte) value;
        buf[pos++] = (byte) (value >>> 8);
        return this;
    }

    public PacketWriter u32(int value) {
        ensure(4);
        buf[pos++] = (byte) value;
        buf[pos++] = (byte) (value >>> 8);
        buf[pos++] = (byte) (value >>> 16);
        buf[pos++] = (byte) (value >>> 24);
        return this;
    }

    public PacketWriter bytes(byte[] data) {
        ensure(data.length);
        System.arraycopy(data, 0, buf, pos, data.length);
        pos += data.length;
        return this;
    }

    public PacketWriter zeros(int count) {
        ensure(count);
        pos += count;
        return this;
    }

    public PacketWriter string(String value) {
        byte[] raw = value.getBytes(charset);
        u32(raw.length);
        return bytes(raw);
    }

    public int length() {
        return pos;
    }

    public byte[] toArray() {
        return Arrays.copyOf(buf, pos);
    }
}
