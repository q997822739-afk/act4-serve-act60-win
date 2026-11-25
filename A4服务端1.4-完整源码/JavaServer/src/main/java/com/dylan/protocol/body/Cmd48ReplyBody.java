package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd48ReplyBody {

    public static final int PACKET_ID = 48;

    public static final int RESULT_FAIL = 0;

    public static final int ERROR_BAD_REQUEST = 1;

    public static final int ERROR_BOSS_CONDITION = 7;

    public static final int ERROR_SAME_ROOM = 18;

    public static final int ERROR_NOT_NOW = 19;

    public static final int ERROR_NOT_CLEARED = 20;

    public static final int ERROR_NO_SUCH_ROOM = 21;

    public static final int ERROR_NO_SUCH_MAP = 23;

    private Cmd48ReplyBody() {
    }

    /**
     * 成功：只有 1 字节 {@code 01}。
     *
     * <p>依据：官方实抓 scen_off / replay_off 里每次 CMD 48 走通都回 {@code 01}（客户端读包表里
     * 48 只读 result 字节，{@code client_check.layout('REPLY', 48, 01)} = ok/1）。A5 原来走通不回，
     * 客户端换房后只能靠 NOTI 29 自己推进。
     */
    public static byte[] accepted() {
        return new byte[]{1};
    }

    public static byte[] rejected(int errorCode) {
        if (errorCode < 0 || errorCode > 0xFF) {
            throw new PacketFormatException("errorCode 超出 u8：" + errorCode);
        }
        PacketWriter w = new PacketWriter(2);
        w.u8(RESULT_FAIL);
        w.u8(errorCode);
        return w.toArray();
    }

    public static String describe(int errorCode) {
        return switch (errorCode) {
            case ERROR_BAD_REQUEST -> "坐标越界或副本查不到";
            case ERROR_BOSS_CONDITION -> "进 BOSS 房的条件没满足";
            case ERROR_SAME_ROOM -> "目标就是当前这一格";
            case ERROR_NOT_NOW -> "不在副本里，或者这一局已经通关";
            case ERROR_NOT_CLEARED -> "本房间还有活怪";
            case ERROR_NO_SUCH_ROOM -> "这一格走不过去（不相邻 / 空格 / 没有能从这一侧进的图）";
            case ERROR_NO_SUCH_MAP -> "map.lst 里查不到候选地图";
            default -> "错误码 " + errorCode;
        };
    }
}
