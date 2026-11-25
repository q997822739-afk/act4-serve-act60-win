package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd178AgreeEnchantRequest(int type) {

    public static final int PACKET_ID = 178;

    public static final int TYPE_REFUSE = 0;

    public static final int TYPE_AGREE = 1;

    public static final int TYPE_CANCEL = 2;

    public static final int LENGTH = 1;

    public Cmd178AgreeEnchantRequest {
        if (type < 0 || type > 0xFF) {
            throw new PacketFormatException("type 是 u8：" + type);
        }
    }

    public String describe() {
        return switch (type) {
            case TYPE_REFUSE -> "拒绝(0)";
            case TYPE_AGREE -> "同意(1)";
            case TYPE_CANCEL -> "取消(2)";
            default -> "未知(" + type + ")";
        };
    }

    public static Cmd178AgreeEnchantRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException("CMD " + PACKET_ID + " 至少 " + LENGTH
                    + " 字节，实际 " + (plain == null ? "null" : plain.length));
        }
        return new Cmd178AgreeEnchantRequest(new PacketReader(plain).u8());
    }
}
