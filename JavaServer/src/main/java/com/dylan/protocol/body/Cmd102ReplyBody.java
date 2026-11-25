package com.dylan.protocol.body;

import java.util.List;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd102ReplyBody {

    private Cmd102ReplyBody() {
    }

    public static final int PACKET_ID = 102;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int AVATAR_TAIL_LENGTH = Noti13ItemListBody.AVATAR_TAIL_LENGTH;

    public static final int ERROR_FAILED = 1;

    public static final int ERROR_NOT_AVATAR = 17;

    public static final int ERROR_PERIOD_MISMATCH = 19;

    public static final int ERROR_NOT_ENOUGH_MATERIAL = 22;

    public static final int ERROR_BAD_PAIR = 23;

    public static final int ERROR_ALMOST_EXPIRED = 24;

    public record Change(int container, int slot, int count) {}

    public static byte[] ok(List<Change> changes, int slot, int itemId, long remainSeconds,
                            int abilityNo) {
        if (changes.size() > 0xFF) {
            throw new PacketFormatException("变动 " + changes.size() + " 条，条数字段只有 u8");
        }
        if (slot < 0 || slot > 0xFFFF) {
            throw new PacketFormatException("装扮栏槽号放不进 u16：" + slot);
        }
        if (itemId < 0) {
            throw new PacketFormatException("物品号是负数：" + itemId);
        }
        if (remainSeconds < 0 || remainSeconds > 0xFFFFFFFFL) {
            throw new PacketFormatException("剩余秒数放不进 u32：" + remainSeconds);
        }
        if (abilityNo < 0 || abilityNo > 0xFFFF) {
            throw new PacketFormatException("属性下标放不进 u16：" + abilityNo);
        }
        PacketWriter w = new PacketWriter();
        w.u8(RESULT_OK);
        w.u8(changes.size());
        for (Change c : changes) {
            if (c.container() < 0 || c.container() > 0xFF) {
                throw new PacketFormatException("容器号放不进 u8：" + c.container());
            }
            if (c.slot() < 0 || c.slot() > 0xFFFF) {
                throw new PacketFormatException("槽号放不进 u16：" + c.slot());
            }
            if (c.count() < 0) {
                throw new PacketFormatException("件数是负数：" + c.count());
            }
            w.u8(c.container()).u16(c.slot()).u32(c.count());
        }
        w.u16(slot).u32(itemId).u32((int) remainSeconds).u16(abilityNo);
        // A4 客户端读完 u16 u32 u32 u16 就结束（DNF.exe 0041A8C9..0041A8ED），没有 ACT5 的时装
        // u32 30 + 30 字节尾巴；官方合成时装 8 次全是 35 字节（3 条变动 + 12 字节）。
        if (!com.dylan.net.ServerConfig.A4) {
            w.u32(AVATAR_TAIL_LENGTH).zeros(AVATAR_TAIL_LENGTH);
        }
        return w.toArray();
    }

    public static byte[] rejected(int errorCode) {
        if (errorCode < 0 || errorCode > 0xFF) {
            throw new PacketFormatException("错误码放不进 u8：" + errorCode);
        }
        return new PacketWriter(2).u8(RESULT_FAIL).u8(errorCode).toArray();
    }
}
