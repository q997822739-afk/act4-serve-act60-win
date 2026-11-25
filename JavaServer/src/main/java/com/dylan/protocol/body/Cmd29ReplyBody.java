package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketWriter;

public final class Cmd29ReplyBody {
    private Cmd29ReplyBody() {
    }

    public static final int PACKET_ID = 29;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int SUCCESS_LENGTH = 19;

    public static final int AVATAR_TAIL_LENGTH = Noti13ItemListBody.AVATAR_TAIL_LENGTH;

    public static final int ERROR_NO_SUCH_ITEM = 21;

    public static final int ERROR_LEVEL_TOO_LOW = 14;

    public static final int ERROR_NOT_ENOUGH_MONEY = 10;

    public static final int ERROR_INVENTORY_FULL = 4;

    public static final int ERROR_CANNOT_OPEN = 17;

    public static byte[] opened(int slot, int insertedSlot, int itemId, long addInfo,
                                int endurance, int itemAttr, int abilityType, int abilityValue,
                                boolean avatarTail) {
        if (com.dylan.net.ServerConfig.A4) {
            // A4 客户端（DNF.exe 00414067..00414097）只读 u16 u16 u32 u32 u16 u8 = 15 字节：槽、插入槽、
            // 物品、附加信息、耐久、属性。没有 ACT5 的增幅 u8+u16，也没有时装 u32 30 + 30 字节尾巴
            // （A4 装扮栏同样没有，见 Noti13ItemListBody）。
            return new PacketWriter(16)
                    .u8(RESULT_OK)
                    .u16(slot)
                    .u16(insertedSlot)
                    .u32(itemId)
                    .u32((int) addInfo)
                    .u16(endurance)
                    .u8(itemAttr)
                    .toArray();
        }
        PacketWriter w = new PacketWriter(
                avatarTail ? SUCCESS_LENGTH + 4 + AVATAR_TAIL_LENGTH : SUCCESS_LENGTH)
                .u8(RESULT_OK)
                .u16(slot)
                .u16(insertedSlot)
                .u32((int) itemId)
                .u32((int) addInfo)
                .u16(endurance)
                .u8(itemAttr)
                .u8(abilityType)
                .u16(abilityValue);
        if (avatarTail) {
            w.u32(AVATAR_TAIL_LENGTH).zeros(AVATAR_TAIL_LENGTH);
        }
        return w.toArray();
    }

    public static byte[] gold(int slot, long gained) {
        return opened(slot, 0, 0, gained, 0, 0, 0, 0, false);
    }

    public static byte[] rejected(int errorCode) {
        return new PacketWriter(2).u8(RESULT_FAIL).u8(errorCode).toArray();
    }
}
