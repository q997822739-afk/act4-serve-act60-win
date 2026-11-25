package com.dylan.pvf;

public final class PvfPath {
    private PvfPath() {
    }

    public static String normalize(String path) {
        StringBuilder sb = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '\\') {
                c = '/';
            } else if (c >= 'A' && c <= 'Z') {
                c += 32;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static int hash(String normalized) {
        int h = 5381;
        if (normalized.isEmpty()) {
            return h;
        }
        for (int i = 0; i < normalized.length(); i++) {
            h = h * 33 + (byte) normalized.charAt(i);
        }
        return h * 33;
    }
}
