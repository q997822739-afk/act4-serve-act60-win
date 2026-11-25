package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;

public record Cmd4SelectCharacterRequest(int listIndex) {

    public static final int PACKET_ID = 4;

    public static final int MAX_LIST_INDEX = 0x24;

    public Cmd4SelectCharacterRequest {
        if (listIndex < 0 || listIndex >= MAX_LIST_INDEX) {
            throw new PacketFormatException("SELECT_CHARACTER 的列表序号越界：" + listIndex
                    + "（官方上限 " + MAX_LIST_INDEX + "）");
        }
    }

    public static Cmd4SelectCharacterRequest parse(byte[] plain) {
        if (plain == null || plain.length < 1) {
            throw new PacketFormatException("SELECT_CHARACTER 包体至少要 1 字节，实际 "
                    + (plain == null ? "null" : plain.length));
        }
        return new Cmd4SelectCharacterRequest(plain[0] & 0xFF);
    }
}
