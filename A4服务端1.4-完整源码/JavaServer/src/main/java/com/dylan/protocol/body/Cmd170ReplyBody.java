package com.dylan.protocol.body;

import java.util.List;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd170ReplyBody {

    private Cmd170ReplyBody() {
    }

    public static final int PACKET_ID = 170;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int HEADER_LENGTH = 9;

    public static final int ENTRY_LENGTH = 8;

    public static final int ERROR_CANNOT_USE = 19;

    public static final int ERROR_WRONG_JOB = 23;

    public static final int ERROR_NOT_ENOUGH_SPACE = 203;

    public record Entry(int itemId, long count) {

        public Entry {
            if (count < 0 || count > 0xFFFFFFFFL) {
                throw new PacketFormatException("count 放不进 u32：" + count);
            }
        }
    }

    public static byte[] opened(int slot, long remain, List<Entry> entries) {
        if (slot < 0 || slot > 0xFFFF) {
            throw new PacketFormatException("slot 放不进 u16：" + slot);
        }
        if (remain < 0 || remain > 0xFFFFFFFFL) {
            throw new PacketFormatException("remain 放不进 u32：" + remain);
        }
        if (entries.size() > 0xFFFF) {
            throw new PacketFormatException("条目数放不进 u16：" + entries.size());
        }
        if (com.dylan.net.ServerConfig.A4) {
            // A4 客户端（DNF.exe 0041B59E）成功分支直接跳出 switch，一个字节都不读；只有失败分支读 u8 错误码。
            // 官方成品服 CMD 170 空包不回包，日志里也没出现过，拿不到官方成功包长度，按客户端只回结果字节。
            return new byte[]{(byte) RESULT_OK};
        }
        PacketWriter w = new PacketWriter(HEADER_LENGTH + entries.size() * ENTRY_LENGTH)
                .u8(RESULT_OK)
                .u16(slot)
                .u32((int) remain)
                .u16(entries.size());
        for (Entry e : entries) {
            w.u32(e.itemId()).u32((int) e.count());
        }
        return w.toArray();
    }

    public static byte[] rejected(int errorCode) {
        if (errorCode < 0 || errorCode > 0xFF) {
            throw new PacketFormatException("errorCode 放不进 u8：" + errorCode);
        }
        return new PacketWriter(2).u8(RESULT_FAIL).u8(errorCode).toArray();
    }
}
