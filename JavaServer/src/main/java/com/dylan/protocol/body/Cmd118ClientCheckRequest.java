package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketReader;

public record Cmd118ClientCheckRequest(int code, Integer detail) {

    public static final int PACKET_ID = 118;

    public static final int CODE_INIT = 256;

    public static final int CODE_2 = 2;

    public static final int CODE_16 = 16;

    public static final int CODE_128 = 128;

    public static Cmd118ClientCheckRequest parse(byte[] plain) {
        PacketReader r = new PacketReader(plain);
        int code = r.u16();
        Integer detail = code == CODE_16 ? r.u8() : null;
        return new Cmd118ClientCheckRequest(code, detail);
    }

    public boolean known() {
        return code == CODE_INIT || code == CODE_2 || code == CODE_16 || code == CODE_128;
    }

    @Override
    public String toString() {
        return "CMD 118 客户端自检上报[码=" + code + (detail == null ? "" : " 附加=" + detail)
                + (known() ? "" : " ★ 这个码不在客户端认的那四支里") + "]";
    }
}
