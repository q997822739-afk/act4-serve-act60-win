package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd50ReplyBody {

    public static final int PACKET_ID = 50;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int ERROR_INTERNAL = 17;

    public static final int ERROR_WRONG_STATE = 19;

    public static final int ERROR_GROUND_FULL = 22;

    public static final int ERROR_CANNOT_DROP = 23;

    public static final int ERROR_TOO_RARE = 13;

    public static final int ERROR_MONEY_LIMIT = 70;

    public static final int ERROR_ITEM_LOCKED = 213;

    public static final int MONEY_DROP_LIMIT = 1000;

    private Cmd50ReplyBody() {
    }

    public static byte[] success(int itemSpace, int slot, long count) {
        checkU8("itemSpace", itemSpace);
        if (slot < 0 || slot > 0xFFFF) {
            throw new PacketFormatException("slot 超出 u16：" + slot);
        }
        if (count < 0 || count > 0xFFFFFFFFL) {
            throw new PacketFormatException("count 超出 u32：" + count);
        }
        PacketWriter w = new PacketWriter(8);
        w.u8(RESULT_OK);
        w.u8(itemSpace);
        w.u16(slot);
        w.u32((int) count);
        return w.toArray();
    }

    public static byte[] rejected(int errorCode, int itemSpace) {
        checkU8("errorCode", errorCode);
        checkU8("itemSpace", itemSpace);
        PacketWriter w = new PacketWriter(3);
        w.u8(RESULT_FAIL);
        w.u8(errorCode);
        w.u8(itemSpace);
        return w.toArray();
    }

    public static String describe(int errorCode) {
        return switch (errorCode) {
            case ERROR_INTERNAL -> "内部错误（空格子 / 扣不掉）";
            case ERROR_WRONG_STATE -> "状态不对（不在副本里 / 交易中）";
            case ERROR_GROUND_FULL -> "地上放不下";
            case ERROR_CANNOT_DROP -> "这件东西不能丢";
            case ERROR_TOO_RARE -> "稀有度太高，不让丢";
            case ERROR_MONEY_LIMIT -> "一次丢的金币超过 " + MONEY_DROP_LIMIT;
            case ERROR_ITEM_LOCKED -> "这一格上着锁";
            default -> "错误码 " + errorCode;
        };
    }

    private static void checkU8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(name + " 超出 u8：" + value);
        }
    }
}
