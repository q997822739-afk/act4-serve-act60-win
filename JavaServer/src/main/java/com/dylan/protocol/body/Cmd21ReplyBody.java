package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd21ReplyBody {
    private Cmd21ReplyBody() {
    }

    public static final int PACKET_ID = 21;

    public static final int SUCCESS_LENGTH = 38;

    public static final int ERROR_BAG_FULL = 4;

    public static final int ERROR_NOT_ENOUGH_MONEY = 10;

    public static final int ERROR_INVALID = 17;

    public static final int ERROR_BUSY = 19;

    public static final int ERROR_NOT_ON_SHELF = 21;

    public static byte[] bought(long gold, long winPoint, long finishPoint, long cera,
                                int slot, long itemId, long count,
                                int materialItemId, long materialLeft) {
        if (com.dylan.net.ServerConfig.A4) {
            return boughtA4(gold, winPoint, finishPoint, cera, slot, itemId, count);
        }
        return new PacketWriter(SUCCESS_LENGTH)
                .u8(1)
                .u32((int) requireU32("金币", gold))
                .u32((int) requireU32("决斗胜点", winPoint))
                .u32((int) requireU32("通关点", finishPoint))
                .u32((int) requireU32("点券", cera))
                .u16(requireU16("槽号", slot))
                .u32((int) requireU32("物品编号", itemId))
                .u32((int) requireU32("数量", count))
                .u8(0)
                .u16(0)
                .u32(materialItemId)
                .u32((int) requireU32("材料余量", materialLeft))
                .toArray();
    }

    /**
     * A4 客户端成功分支（DNF.exe 0041274B..00412793）只读 4×u32 + u16 + 2×u32 = 26 字节：
     * 金币、胜点、通关点、点券、槽号、物品、数量。没有 ACT5 尾部的 u8+u16 和材料两个 u32。
     * 官方日志 27 字节：{@code 01 B0A9FB05 47300F00 FFFFFFFF 00000000 0600 A8160000 01000000}。
     */
    public static final int A4_SUCCESS_LENGTH = 27;

    private static byte[] boughtA4(long gold, long winPoint, long finishPoint, long cera,
                                   int slot, long itemId, long count) {
        return new PacketWriter(A4_SUCCESS_LENGTH)
                .u8(1)
                .u32((int) requireU32("金币", gold))
                .u32((int) requireU32("决斗胜点", winPoint))
                .u32((int) requireU32("通关点", finishPoint))
                .u32((int) requireU32("点券", cera))
                .u16(requireU16("槽号", slot))
                .u32((int) requireU32("物品编号", itemId))
                .u32((int) requireU32("数量", count))
                .toArray();
    }

    public static final int NO_MATERIAL = -1;

    public static byte[] rejected(int errorCode) {
        if (errorCode < 0 || errorCode > 0xFF) {
            throw new PacketFormatException("错误码 " + errorCode + " 放不进 u8");
        }
        return new byte[]{0, (byte) errorCode};
    }

    private static long requireU32(String field, long value) {
        if (value < 0 || value > 0xFFFFFFFFL) {
            throw new PacketFormatException(field + " " + value + " 放不进 u32");
        }
        return value;
    }

    private static int requireU16(String field, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new PacketFormatException(field + " " + value + " 放不进 u16");
        }
        return value;
    }
}
