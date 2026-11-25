package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Cmd48MoveMapRequest(int roomX, int roomY, int x, int y, byte[] tail) {

    public static final int PACKET_ID = 48;

    public static final int LENGTH = 10;

    public Cmd48MoveMapRequest {
        if (roomX < 0 || roomX > 0xFF || roomY < 0 || roomY > 0xFF) {
            throw new PacketFormatException("roomX 和 roomY 都是 u8：" + roomX + "," + roomY);
        }
        tail = tail == null ? new byte[0] : tail.clone();
    }

    public static Cmd48MoveMapRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException("MOVE_MAP 包体至少要 " + LENGTH + " 字节，实际 "
                    + (plain == null ? "null" : plain.length));
        }
        int roomX = plain[0] & 0xFF;
        int roomY = plain[1] & 0xFF;
        int x = u32(plain, 2);
        int y = u32(plain, 6);
        byte[] tail = new byte[plain.length - LENGTH];
        System.arraycopy(plain, LENGTH, tail, 0, tail.length);
        return new Cmd48MoveMapRequest(roomX, roomY, x, y, tail);
    }

    @Override
    public byte[] tail() {
        return tail.clone();
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(LENGTH + tail.length);
        w.u8(roomX);
        w.u8(roomY);
        w.u32(x);
        w.u32(y);
        for (byte b : tail) {
            w.u8(b & 0xFF);
        }
        return w.toArray();
    }

    private static int u32(byte[] b, int at) {
        return (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8)
                | ((b[at + 2] & 0xFF) << 16) | ((b[at + 3] & 0xFF) << 24);
    }
}
