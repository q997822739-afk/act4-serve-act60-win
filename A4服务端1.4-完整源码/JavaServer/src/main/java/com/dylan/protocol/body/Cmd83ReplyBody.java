package com.dylan.protocol.body;

import java.util.List;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd83ReplyBody {

    private Cmd83ReplyBody() {
    }

    public static final int PACKET_ID = 83;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int UPGRADE_SUCCESS = 0;

    public static final int UPGRADE_FAILED = 1;

    public static final int UPGRADE_DEGRADED = 2;

    public static final int UPGRADE_DESTROYED = 3;

    public static final int ERROR_NO_SUCH_ITEM = 4;

    public static final int ERROR_NOT_FULL_ENDURANCE = 7;

    public static final int ERROR_NOT_ENOUGH_GOLD = 10;

    public static final int ERROR_MAX_LEVEL = 13;

    public static final int ERROR_DELETE_FAILED = 17;

    public static final int ERROR_NOT_UPGRADABLE = 19;

    public static final int ERROR_BAG_FULL = 21;

    public static final int ERROR_BAD_MATERIAL = 22;

    public static final int ERROR_AMPLIFY_MISMATCH = 23;

    public static final int ERROR_RARITY_MAX_LEVEL = 95;

    public record Fragment(int slot, int itemId, int count) {}

    public static byte[] kept(int amplify, int materialSlot, long materialLeft, int result,
                              int upgrade, int slot) {
        if (result != UPGRADE_SUCCESS && result != UPGRADE_FAILED && result != UPGRADE_DEGRADED) {
            throw new PacketFormatException("result " + result + " 不是「装备还在」的三种之一");
        }
        return header(amplify, materialSlot, materialLeft, result, upgrade, slot).toArray();
    }

    public static byte[] destroyed(int amplify, int materialSlot, long materialLeft, int slot,
                                   List<Fragment> fragments) {
        if (fragments.size() > 0xFF) {
            throw new PacketFormatException("碎片 " + fragments.size() + " 条，条数字段只有 u8");
        }
        PacketWriter w = header(amplify, materialSlot, materialLeft, UPGRADE_DESTROYED, 0, slot);
        w.u8(fragments.size());
        for (Fragment f : fragments) {
            if (f.slot() < 0 || f.slot() > 0xFFFF) {
                throw new PacketFormatException("碎片槽号放不进 u16：" + f.slot());
            }
            if (f.itemId() < 0) {
                throw new PacketFormatException("碎片编号是负数：" + f.itemId());
            }
            if (f.count() < 0) {
                throw new PacketFormatException("碎片个数是负数：" + f.count());
            }
            w.u16(f.slot()).u32(f.itemId()).u32(f.count());
        }
        return w.toArray();
    }

    public static byte[] rejected(int errorCode) {
        if (errorCode < 0 || errorCode > 0xFF) {
            throw new PacketFormatException("错误码放不进 u8：" + errorCode);
        }
        return new byte[]{(byte) RESULT_FAIL, (byte) errorCode};
    }

    private static PacketWriter header(int amplify, int materialSlot, long materialLeft,
                                       int result, int upgrade, int slot) {
        checkU8("amplify", amplify);
        checkU8("result", result);
        checkU8("upgrade", upgrade);
        if (materialSlot < 0 || materialSlot > 0xFFFF) {
            throw new PacketFormatException("材料槽放不进 u16：" + materialSlot);
        }
        if (materialLeft < 0 || materialLeft > 0xFFFF_FFFFL) {
            throw new PacketFormatException("材料余量放不进 u32：" + materialLeft);
        }
        if (slot < 0 || slot > 0xFFFF) {
            throw new PacketFormatException("装备槽放不进 u16：" + slot);
        }
        if (com.dylan.net.ServerConfig.A4) {
            // A4 客户端（DNF.exe 00419B1A..00419B47）读 u16 材料槽、u32 余量、u8 结果、u8 强化、u16 装备槽，
            // 没有 ACT5 开头的增幅 u8。破碎（结果 3）后的碎片表与 ACT5 相同（u8 条数 + u16,u32,u32）。
            return new PacketWriter(16)
                    .u8(RESULT_OK)
                    .u16(materialSlot)
                    .u32((int) materialLeft)
                    .u8(result)
                    .u8(upgrade)
                    .u16(slot);
        }
        return new PacketWriter(16)
                .u8(RESULT_OK)
                .u8(amplify)
                .u16(materialSlot)
                .u32((int) materialLeft)
                .u8(result)
                .u8(upgrade)
                .u16(slot);
    }

    private static void checkU8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(name + " 超出 u8：" + value);
        }
    }
}
