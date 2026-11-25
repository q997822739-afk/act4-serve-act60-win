package com.dylan.net;

public final class Hex {

    private Hex() {
    }

    public static String dumpInline(byte[] data, int limit) {
        if (data == null || data.length == 0) {
            return "(空)";
        }
        int n = Math.min(data.length, limit);
        StringBuilder sb = new StringBuilder(n * 3 + 24);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", data[i]));
        }
        if (n < data.length) {
            sb.append(" … （共 ").append(data.length).append(" 字节，省略 ")
                    .append(data.length - n).append("）");
        }
        return sb.toString();
    }

    public static String dumpBlock(byte[] data, int limit) {
        if (data == null || data.length == 0) {
            return "(空)";
        }
        int n = Math.min(data.length, limit);
        StringBuilder sb = new StringBuilder();
        for (int off = 0; off < n; off += 16) {
            int end = Math.min(off + 16, n);
            sb.append(String.format("%n  %04X  ", off));
            for (int i = off; i < off + 16; i++) {
                sb.append(i < end ? String.format("%02X ", data[i]) : "   ");
            }
            sb.append(' ');
            for (int i = off; i < end; i++) {
                int c = data[i] & 0xFF;
                sb.append(c >= 0x20 && c < 0x7F ? (char) c : '.');
            }
        }
        if (n < data.length) {
            sb.append(String.format("%n  … 共 %d 字节，省略 %d", data.length, data.length - n));
        }
        return sb.toString();
    }
}
