package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketReader;

public record Cmd8GetUserInfoRequest(int targetUserId, int kind) {

    public static final int PACKET_ID = 8;

    public static final int LENGTH = 3;

    public static final int NO_TARGET = 0xFFFF;

    public static final int KIND_CHARACTER_LIST = Noti2Body.SUBTYPE_CHARACTER_LIST;

    public static Cmd8GetUserInfoRequest parse(byte[] body) {
        return parse(new PacketReader(body));
    }

    public static Cmd8GetUserInfoRequest parse(PacketReader r) {
        return new Cmd8GetUserInfoRequest(r.u16(), r.u8());
    }

    public boolean wantsCharacterList() {
        return kind == KIND_CHARACTER_LIST;
    }

    public boolean wantsOtherUser() {
        return !wantsCharacterList() && targetUserId != NO_TARGET;
    }

    @Override
    public String toString() {
        return "CMD 8 GET_USERINFO[目标连接编号="
                + (targetUserId == NO_TARGET ? "无（0xFFFF）" : targetUserId)
                + " kind=" + kind + "（应答走 NOTI 2 子类型 " + kind + "）]";
    }
}
