package com.dylan.protocol.body;

import java.util.List;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Cmd179TryEnchantReplyBody(int result, int type, int success, long expertJobExp,
                                        long money, List<MaterialChange> materials, int error) {

    public static final int PACKET_ID = Cmd179TryEnchantRequest.PACKET_ID;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public record MaterialChange(int itemSpace, int slot, long count) {

        public static final int BYTES = 7;

        public MaterialChange {
            if (itemSpace < 0 || itemSpace > 0xFF) {
                throw new PacketFormatException("itemSpace 是 u8：" + itemSpace);
            }
            if (slot < 0 || slot > 0xFFFF) {
                throw new PacketFormatException("slot 是 u16：" + slot);
            }
            if (count < 0 || count > 0xFFFF_FFFFL) {
                throw new PacketFormatException("count 是 u32：" + count);
            }
        }
    }

    public Cmd179TryEnchantReplyBody {
        materials = List.copyOf(materials);
    }

    public static Cmd179TryEnchantReplyBody done(int type, boolean success, long exp, long money,
                                                 List<MaterialChange> materials) {
        if (exp < 0 || exp > 0xFFFF_FFFFL) {
            throw new PacketFormatException("熟练度是 u32，超出 0..4294967295：" + exp);
        }
        if (money < 0 || money > 0xFFFF_FFFFL) {
            throw new PacketFormatException("金币是 u32，超出 0..4294967295：" + money);
        }
        if (materials.size() > 0xFF) {
            throw new PacketFormatException("材料条数是 u8，装不下 " + materials.size() + " 条");
        }
        return new Cmd179TryEnchantReplyBody(RESULT_OK, type, success ? 1 : 0, exp, money,
                materials, EnchantError.NONE);
    }

    public static Cmd179TryEnchantReplyBody refused() {
        return new Cmd179TryEnchantReplyBody(RESULT_OK, Cmd179TryEnchantRequest.TYPE_REFUSED,
                0, 0, 0, List.of(), EnchantError.NONE);
    }

    public static Cmd179TryEnchantReplyBody rejected(int error) {
        if (error < 0 || error > 0xFF) {
            throw new PacketFormatException("错误码是 u8：" + error);
        }
        return new Cmd179TryEnchantReplyBody(RESULT_FAIL, 0, 0, 0, 0, List.of(), error);
    }

    public boolean ok() {
        return result != RESULT_FAIL;
    }

    public boolean isRefusal() {
        return ok() && type == Cmd179TryEnchantRequest.TYPE_REFUSED;
    }

    public byte[] toBytes() {
        if (!ok()) {
            return new PacketWriter(2).u8(RESULT_FAIL).u8(error).toArray();
        }
        if (isRefusal()) {
            return new PacketWriter(2).u8(RESULT_OK)
                    .u8(Cmd179TryEnchantRequest.TYPE_REFUSED).toArray();
        }
        if (com.dylan.net.ServerConfig.A4) {
            // A4 客户端（DNF.exe 0041BF6D）成功分支只读 1 个 u8，交给 0091C530（非 0 → 全局 1，0 → -1，含义未知），
            // 不是 ACT5 的附魔结果；失败分支也只读 u8 错误码。官方成品服 CMD 179 空包回 `00 01`（cmd_sweep）。
            // 成功时第 2 字节填 success（未逐一核对官方含义）。
            return new PacketWriter(2).u8(RESULT_OK).u8(success).toArray();
        }
        PacketWriter w = new PacketWriter(
                12 + materials.size() * MaterialChange.BYTES);
        w.u8(RESULT_OK);
        w.u8(type);
        w.u8(success);
        w.u32((int) expertJobExp);
        w.u32((int) money);
        w.u8(materials.size());
        for (MaterialChange m : materials) {
            w.u8(m.itemSpace()).u16(m.slot()).u32((int) m.count());
        }
        return w.toArray();
    }
}
