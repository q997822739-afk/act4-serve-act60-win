package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketReader;

public record Cmd0CheckConnectionRequest(int inventoryChecksum, int skillChecksum) {
    public static final int PACKET_ID = 0;

    public static final int BODY_LENGTH = 8;

    public static Cmd0CheckConnectionRequest parse(byte[] body) {
        if (body == null || body.length < BODY_LENGTH) {
            return null;
        }
        PacketReader r = new PacketReader(body);
        return new Cmd0CheckConnectionRequest(r.u32(), r.u32());
    }

    @Override
    public String toString() {
        return String.format("CMD %d CHECK_CONNECTION 心跳应答：背包校验和 0x%08X，技能校验和 0x%08X",
                PACKET_ID, inventoryChecksum, skillChecksum);
    }
}
