package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketReader;

public record Cmd132CreatureScriptMessageRequest(int msgType, int targetUserId,
                                                 int creatureParam, String text,
                                                 String targetName) {

    public static final int PACKET_ID = 132;

    public static final int TEXT_CAPACITY = 256;

    public static final int MAX_TEXT_BYTES = TEXT_CAPACITY - 1;

    public static final int NAME_CAPACITY = 30;

    public static Cmd132CreatureScriptMessageRequest parse(byte[] plain) {
        return parse(new PacketReader(plain));
    }

    public static Cmd132CreatureScriptMessageRequest parse(PacketReader r) {
        int msgType = r.u8();
        int targetUserId = r.u16();
        int creatureParam = r.u32();
        String text = r.string(TEXT_CAPACITY, "宠物台词");
        String targetName = ChatType.creatureScriptHasTargetName(msgType)
                ? r.string(NAME_CAPACITY, "密语目标的名字")
                : "";
        return new Cmd132CreatureScriptMessageRequest(msgType, targetUserId, creatureParam,
                text, targetName);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CMD 132 CREATURE_SCRIPT_MESSAGE ")
                .append(ChatType.name(msgType));
        if (targetUserId != 0) {
            sb.append(" → userId=").append(targetUserId);
        }
        if (!targetName.isEmpty()) {
            sb.append(" → \"").append(targetName).append('"');
        }
        if (creatureParam != 0) {
            sb.append(" param=").append(creatureParam);
        }
        return sb.append(" 「").append(text).append("」").toString();
    }
}
