package com.dylan.protocol.io;

public final class PacketReader {

    private final byte[] body;
    private int pos;

    public PacketReader(byte[] body) {
        if (body == null) {
            throw new PacketFormatException("包体是 null");
        }
        this.body = body;
    }

    public int position() {
        return pos;
    }

    public int remaining() {
        return body.length - pos;
    }

    public int u8() {
        need(1);
        return body[pos++] & 0xFF;
    }

    public int u16() {
        need(2);
        return (body[pos++] & 0xFF) | ((body[pos++] & 0xFF) << 8);
    }

    public int u32() {
        need(4);
        return (body[pos++] & 0xFF)
                | ((body[pos++] & 0xFF) << 8)
                | ((body[pos++] & 0xFF) << 16)
                | ((body[pos++] & 0xFF) << 24);
    }

    public byte[] bytes(int count) {
        need(count);
        byte[] out = new byte[count];
        System.arraycopy(body, pos, out, 0, count);
        pos += count;
        return out;
    }

    public String string(int capacity, String what) {
        int length = u32();
        if (length < 0 || length >= capacity) {
            throw new PacketFormatException(what + " 声明了 " + length
                    + " 字节，而这个字段的容量是 " + capacity + "（判据是 len < capacity）");
        }
        return new String(bytes(length), PacketWriter.DEFAULT_CHARSET);
    }

    public PacketReader skip(int count) {
        need(count);
        pos += count;
        return this;
    }

    /**
     * 只给 tools.RequestLengthCheck 用：开着时记下每次读的宽度（"2+4+2"），和客户端写包的宽度逐个比。
     * 服务端运行时恒为 null。
     */
    public static final ThreadLocal<StringBuilder> TRACE = new ThreadLocal<>();

    private void need(int n) {
        StringBuilder t = TRACE.get();
        if (t != null) {
            t.append(t.isEmpty() ? "" : "+").append(n);
        }
        if (n < 0 || pos + n > body.length) {
            throw new PacketFormatException("包体读越界：偏移 " + pos + " 处要 " + n
                    + " 字节，总长只有 " + body.length);
        }
    }
}
