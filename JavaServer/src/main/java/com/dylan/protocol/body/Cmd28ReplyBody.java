package com.dylan.protocol.body;

import java.util.List;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd28ReplyBody {
    private Cmd28ReplyBody() {
    }

    public static final int PACKET_ID = 28;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int ERROR_BAG_FULL = 4;

    public static final int ERROR_NO_SUCH_ITEM = 17;

    public static final int ERROR_ITEM_LOCKED = 205;

    public record Product(int slot, int itemId, int count) {}

    public static byte[] done(int slot, int itemSpace, List<Product> products) {
        if (slot < 0 || slot > 0xFFFF) {
            throw new PacketFormatException("装备槽放不进 u16：" + slot);
        }
        if (itemSpace < 0 || itemSpace > 0xFF) {
            throw new PacketFormatException("容器号放不进 u8：" + itemSpace);
        }
        if (products.size() > 0xFF) {
            throw new PacketFormatException("产物 " + products.size() + " 条，条数字段只有 u8");
        }
        PacketWriter w = new PacketWriter(8 + 10 * products.size())
                .u8(RESULT_OK)
                .u16(slot)
                .u8(itemSpace)
                .u8(products.size());
        for (Product p : products) {
            if (p.slot() < 0 || p.slot() > 0xFFFF) {
                throw new PacketFormatException("产物槽号放不进 u16：" + p.slot());
            }
            if (p.itemId() < 0) {
                throw new PacketFormatException("产物编号是负数：" + p.itemId());
            }
            if (p.count() < 0) {
                throw new PacketFormatException("产物个数是负数：" + p.count());
            }
            w.u16(p.slot()).u32(p.itemId()).u32(p.count());
        }
        return w.toArray();
    }

    public static byte[] rejected(int errorCode) {
        if (errorCode < 0 || errorCode > 0xFF) {
            throw new PacketFormatException("错误码放不进 u8：" + errorCode);
        }
        return new byte[]{(byte) RESULT_FAIL, (byte) errorCode};
    }
}
