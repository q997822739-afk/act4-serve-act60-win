package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;
import com.dylan.protocol.io.PacketReader;

public record Cmd146QueryCharacInfoRequest(String name) {

    public static final int PACKET_ID = 146;

    public static final int NAME_CAPACITY = 30;

    public static final int CLIENT_REPLY_CAPACITY = 20;

    public Cmd146QueryCharacInfoRequest {
        if (name == null || name.isEmpty()) {
            throw new PacketFormatException("QUERY_CHARAC_INFO 的名字是空的");
        }
    }

    public static Cmd146QueryCharacInfoRequest parse(byte[] plain) {
        PacketReader r = new PacketReader(plain);
        int declared = r.u32();
        if (declared <= 0 || declared >= NAME_CAPACITY) {
            throw new PacketFormatException("要查的角色名声明了 " + declared
                    + " 字节，而官方那一格的容量是 " + NAME_CAPACITY + "（判据 len < "
                    + NAME_CAPACITY + "）");
        }
        if (declared >= CLIENT_REPLY_CAPACITY) {
            throw new PacketFormatException("要查的角色名有 " + declared
                    + " 字节，而 ACT5 客户端读应答的缓冲只有 " + CLIENT_REPLY_CAPACITY
                    + " 字节（判据 len < " + CLIENT_REPLY_CAPACITY
                    + "）—— 原版客户端发不出这么长的名字");
        }
        return new Cmd146QueryCharacInfoRequest(
                new String(r.bytes(declared), PacketWriter.DEFAULT_CHARSET));
    }

    @Override
    public String toString() {
        return "QUERY_CHARAC_INFO[名字=" + name + "]";
    }
}
