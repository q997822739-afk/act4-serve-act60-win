package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketReader;

public record Cmd65DebugCommandRequest(GmSubCommand sub, int rawId,
                                       int a, int b, int c, int d, String text) {

    public static final int PACKET_ID = 65;

    public static final int NAME_CAPACITY = 32;

    public static Cmd65DebugCommandRequest parse(byte[] plain) {
        return parse(new PacketReader(plain));
    }

    public static Cmd65DebugCommandRequest parse(PacketReader r) {
        int rawId = r.u8();
        GmSubCommand sub = GmSubCommand.byId(rawId);
        if (sub == null) {
            return new Cmd65DebugCommandRequest(null, rawId, 0, 0, 0, 0, "");
        }
        return switch (sub.args()) {
            case NONE -> of(sub, rawId, 0, 0, 0, 0);
            case U8 -> of(sub, rawId, r.u8(), 0, 0, 0);
            case TWO_U8 -> {
                int first = r.u8();
                yield of(sub, rawId, first, r.u8(), 0, 0);
            }
            case U32, I32 -> of(sub, rawId, r.u32(), 0, 0, 0);
            case U32_OPT2 -> {
                int first = r.u32();
                int second = r.u32();
                yield of(sub, rawId, first, second, r.u32(), 0);
            }
            case TWO_U32 -> {
                int first = r.u32();
                yield of(sub, rawId, first, r.u32(), 0, 0);
            }
            case THREE_U32 -> {
                int first = r.u32();
                int second = r.u32();
                yield of(sub, rawId, first, second, r.u32(), 0);
            }
            case AVATAR -> {
                int itemId = r.u32();
                int slot = r.u8();
                int period = r.u8();
                yield of(sub, rawId, itemId, slot, period, r.u16());
            }
            case STRING, STRING_OPT -> new Cmd65DebugCommandRequest(sub, rawId, 0, 0, 0, 0,
                    r.string(NAME_CAPACITY, sub.display() + " 的角色名"));
        };
    }

    private static Cmd65DebugCommandRequest of(GmSubCommand sub, int rawId,
                                               int a, int b, int c, int d) {
        return new Cmd65DebugCommandRequest(sub, rawId, a, b, c, d, "");
    }

    public long unsignedA() {
        return Integer.toUnsignedLong(a);
    }

    public String detail() {
        if (sub == null) {
            return "未知子命令 " + rawId;
        }
        return switch (sub.args()) {
            case NONE -> "";
            case U8, U32 -> Long.toString(unsignedA());
            case I32 -> Integer.toString(a);
            case TWO_U8, TWO_U32 -> a + " " + b;
            case THREE_U32, U32_OPT2 -> a + " " + b + " " + c;
            case AVATAR -> a + " 部位 " + b + " 期限 " + c + " 颜色 " + d;
            case STRING, STRING_OPT -> text;
        };
    }

    @Override
    public String toString() {
        String name = sub == null ? "GM 子命令 " + rawId : sub.display();
        String detail = detail();
        return "CMD 65 " + name + (detail.isEmpty() ? "" : " " + detail);
    }
}
