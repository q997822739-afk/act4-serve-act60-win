package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd35SetQuestTriggerRequest(int questId, int triggerType) {

    public static final int PACKET_ID = 35;

    public static final int LENGTH = 3;

    public static final int TRIGGER_TYPE_WHOLE_DECREMENT = 0;

    public static final int TRIGGER_TYPE_WHOLE_INCREMENT = 1;

    public static final int TRIGGER_TYPE_CHANNEL_0 = 0x10;

    public static final int TRIGGER_TYPE_CHANNEL_1 = 0x20;

    public static final int TRIGGER_TYPE_CHANNEL_2 = 0x40;

    public static final int[] CHANNEL_BITS = {
            TRIGGER_TYPE_CHANNEL_0, TRIGGER_TYPE_CHANNEL_1, TRIGGER_TYPE_CHANNEL_2,
    };

    public Cmd35SetQuestTriggerRequest {
        if (questId < 0 || questId > 0xFFFF) {
            throw new PacketFormatException("questId 超出 u16：" + questId);
        }
        if (triggerType < 0 || triggerType > 0xFF) {
            throw new PacketFormatException("triggerType 超出 u8：" + triggerType);
        }
    }

    public static Cmd35SetQuestTriggerRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException("SET_QUEST_TRIGGER 包体至少要 " + LENGTH
                    + " 字节，实际 " + (plain == null ? "null" : plain.length));
        }
        PacketReader r = new PacketReader(plain);
        return new Cmd35SetQuestTriggerRequest(r.u16(), r.u8());
    }
}
