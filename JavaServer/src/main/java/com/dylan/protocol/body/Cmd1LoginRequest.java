package com.dylan.protocol.body;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public record Cmd1LoginRequest(String account, String password, int flag,
                               byte[] secCode, int version, int clientIp, byte[] trailing) {
    public static final int PACKET_ID = 1;

    public static final int EXPECTED_VERSION = 20080418;

    private static final int MIN_LENGTH = 4 + 4 + 1 + 4 + 4;

    private static final int TRAILING_LENGTH = 12;

    private static final int TRAILING_IP_OFFSET = 6;

    public static Cmd1LoginRequest parse(byte[] body) {
        if (body == null || body.length < MIN_LENGTH) {
            throw new IllegalArgumentException("CMD 1 包体太短："
                    + (body == null ? "null" : body.length + " 字节，至少要 " + MIN_LENGTH));
        }
        Cursor c = new Cursor(body);

        String account = c.readString("账号");
        String password = c.readString("密码");
        int flag = c.readU8("flag");
        byte[] secCode = c.readBytes(c.readLength("secCode"), "secCode");
        int version = c.readU32("version");

        byte[] trailing = c.readBytes(Math.min(TRAILING_LENGTH, c.remaining()), "尾部");

        int clientIp = trailing.length >= TRAILING_IP_OFFSET + 4
                ? ((trailing[TRAILING_IP_OFFSET] & 0xFF) << 24)
                        | ((trailing[TRAILING_IP_OFFSET + 1] & 0xFF) << 16)
                        | ((trailing[TRAILING_IP_OFFSET + 2] & 0xFF) << 8)
                        | (trailing[TRAILING_IP_OFFSET + 3] & 0xFF)
                : 0;

        return new Cmd1LoginRequest(account, password, flag, secCode, version,
                clientIp, trailing);
    }

    public boolean hasExpectedVersion() {
        return version == EXPECTED_VERSION;
    }

    public String clientIpText() {
        return ((clientIp >>> 24) & 0xFF) + "." + ((clientIp >>> 16) & 0xFF) + "."
                + ((clientIp >>> 8) & 0xFF) + "." + (clientIp & 0xFF);
    }

    @Override
    public String toString() {
        return "Cmd1LoginRequest[账号=" + account
                + ", 密码=***(" + password.length() + " 位)"
                + ", flag=" + flag
                + ", secCode=" + secCode.length + "B/" + HexFormat.of().formatHex(secCode)
                + ", version=" + version + (hasExpectedVersion() ? "" : " ★对不上")
                + ", clientIp=" + clientIpText()
                + ", 尾部=" + HexFormat.of().formatHex(trailing) + "]";
    }

    private static final class Cursor {
        private final byte[] data;
        private int pos;

        Cursor(byte[] data) {
            this.data = data;
        }

        int remaining() {
            return data.length - pos;
        }

        private void need(int n, String field) {
            if (remaining() < n) {
                throw new IllegalArgumentException("CMD 1 读 " + field + " 要 " + n
                        + " 字节，只剩 " + remaining() + "（偏移 " + pos
                        + "，包体 " + data.length + "）");
            }
        }

        int readU8(String field) {
            need(1, field);
            return data[pos++] & 0xFF;
        }

        int readU32(String field) {
            need(4, field);
            int v = (data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8)
                    | ((data[pos + 2] & 0xFF) << 16) | ((data[pos + 3] & 0xFF) << 24);
            pos += 4;
            return v;
        }

        int readLength(String field) {
            int n = readU32(field + " 的长度");
            if (n < 0 || n > remaining()) {
                throw new IllegalArgumentException("CMD 1 里 " + field + " 的长度 "
                        + Integer.toUnsignedLong(n) + " 不合理，只剩 " + remaining() + " 字节");
            }
            return n;
        }

        byte[] readBytes(int n, String field) {
            need(n, field);
            byte[] out = new byte[n];
            System.arraycopy(data, pos, out, 0, n);
            pos += n;
            return out;
        }

        String readString(String field) {
            int n = readLength(field);
            return new String(readBytes(n, field), StandardCharsets.UTF_8);
        }
    }
}
