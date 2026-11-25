package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketReader;

public record Cmd17SendMessageRequest(int msgType, int targetUserId, int itemSlot,
                                      String text, String targetName) {

    public static final int PACKET_ID = 17;

    public static final int TEXT_CAPACITY = 256;

    public static final int MAX_TEXT_BYTES = TEXT_CAPACITY - 1;

    public static final int NAME_CAPACITY = 30;

    public static Cmd17SendMessageRequest parse(byte[] plain) {
        return parse(new PacketReader(plain));
    }

    public static Cmd17SendMessageRequest parse(PacketReader r) {
        int msgType = r.u8();
        int targetUserId = r.u16();
        int itemSlot = r.u32();
        String text = r.string(TEXT_CAPACITY, "聊天正文");
        String targetName = ChatType.hasTargetName(msgType)
                ? r.string(NAME_CAPACITY, "密语目标的名字")
                : "";
        return new Cmd17SendMessageRequest(msgType, targetUserId, itemSlot, text, targetName);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CMD 17 ").append(ChatType.name(msgType));
        if (targetUserId != 0) {
            sb.append(" → userId=").append(targetUserId);
        }
        if (!targetName.isEmpty()) {
            sb.append(" → \"").append(targetName).append('"');
        }
        if (itemSlot != 0) {
            sb.append(" itemSlot=").append(itemSlot);
        }
        return sb.append(" 「").append(text).append("」").toString();
    }
}
