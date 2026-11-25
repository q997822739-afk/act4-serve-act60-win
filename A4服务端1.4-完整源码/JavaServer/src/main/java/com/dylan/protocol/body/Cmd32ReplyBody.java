package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketWriter;

public final class Cmd32ReplyBody {
    private Cmd32ReplyBody() {
    }

    public static final int PACKET_ID = 32;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int SUCCESS_LENGTH = 10;

    public static final int TYPE_SKILL_POINT = 0;

    public static final int TYPE_EXPERIENCE = 1;

    public static final int TYPE_MAX_HP = 2;

    public static final int TYPE_MAX_MP = 3;

    public static final int TYPE_STRENGTH = 4;

    public static final int TYPE_VITALITY = 5;

    public static final int TYPE_INTELLIGENCE = 6;

    public static final int TYPE_SPIRIT = 7;

    public static final int TYPE_MOVE_SPEED = 8;

    public static final int TYPE_ALL_RESISTANCE = 9;

    public static final int TYPE_RESET_SKILL = 10;

    public static final int TYPE_GUILD_EXP = 11;

    public static final int TYPE_EVENT_COIN = 13;

    public static final int TYPE_RECOVER_FATIGUE = 14;

    public static final int TYPE_GUILD_EXP_BOOK = 16;

    public static final int TYPE_COIN = 21;

    public static final int ERROR_CANNOT_USE = 17;

    /**
     * 空槽 / 越界槽的拒绝码。
     *
     * <p>依据：A4 官方实抓（sweep_town CMD 32，包体 {@code 05000000…} = 用第 5 格，那格是空的）回 {@code 00 02}；
     * 官方同样拿越界槽号也是这一条（07_协议检修/待修清单.md「CMD 32 城镇道具用空槽 / 越界槽」）。
     * 含义按 A4 错误码表未知（A5 的 ACT5 错误码表里没有 2），照官方字节抄；客户端 00415086 只读 u8 错误码。
     */
    public static final int ERROR_UNUSABLE = 2;

    public static final int ERROR_BAD_STATE = 19;

    public static final int ERROR_PVP_SKILLTREE_CHANNEL_ONLY = 23;

    public static final int ERROR_NO_GUILD = 64;

    public static final int ERROR_FATIGUE_ALREADY_FULL = 67;

    public static final int ERROR_ITEM_LOCKED = 213;

    public static final int ERROR_NO_LETHE_PREMIUM = 216;

    public static byte[] applied(int slot, int type, long value, int extra) {
        return new PacketWriter(SUCCESS_LENGTH)
                .u8(RESULT_OK)
                .u16(slot)
                .u8(type)
                .u32((int) value)
                .u16(extra)
                .toArray();
    }

    public static byte[] applied(int slot, int type, long value) {
        return applied(slot, type, value, 0);
    }

    public static byte[] rejected(int errorCode) {
        return new PacketWriter(2).u8(RESULT_FAIL).u8(errorCode).toArray();
    }
}
