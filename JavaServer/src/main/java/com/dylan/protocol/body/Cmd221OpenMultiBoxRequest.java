package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd221OpenMultiBoxRequest(int boxSlot, int keySlot) {
    public static final int PACKET_ID = 221;

    public static final int PACKET_ID_SAMSUNG = 222;

    public static final int PACKET_ID_NATIONAL_DAY = 223;

    /**
     * A4 把同一批魔盒改了号（官方枚举 UseRandomBoxItem / Samsung / Goblin）。
     * 发包点 0090DEAB / 0090C56B / 0090AC2B 都是 u16 盒槽 + u16 钥匙槽，
     * 物品号仍是 200、202、2681522（push 0xC8 / 0xCA / 0x28EAB2 后找格子）。
     * 回包 0041C1CD、0041C71D、0041CC2D：结果字节已由分发器读走，成功接着读两个 u16 再按条
     * u16+u32+u32+u16+u8，和 {@link Cmd221ReplyBody#opened} 一样。结果为 0 跳到 0041D1DB，不再读字节
     * （固定提示 0x2EA）。失败包多出的错误码留在本帧里，不会读到下一包。
     */
    public static final int A4_PACKET_ID = 191;

    public static final int A4_PACKET_ID_SAMSUNG = 192;

    public static final int A4_PACKET_ID_GOBLIN = 193;

    public static final int BOX_AMAZING = 200;

    public static final int BOX_SAMSUNG = 202;

    public static final int BOX_NATIONAL_DAY = 2681522;

    private static final java.util.Map<Integer, Integer> BOX_BY_PACKET = java.util.Map.of(
            PACKET_ID, BOX_AMAZING,
            PACKET_ID_SAMSUNG, BOX_SAMSUNG,
            PACKET_ID_NATIONAL_DAY, BOX_NATIONAL_DAY,
            A4_PACKET_ID, BOX_AMAZING,
            A4_PACKET_ID_SAMSUNG, BOX_SAMSUNG,
            A4_PACKET_ID_GOBLIN, BOX_NATIONAL_DAY);

    public static java.util.Set<Integer> packetIds() {
        return BOX_BY_PACKET.keySet();
    }

    public static int boxItemIdOf(int packetId) {
        Integer boxItemId = BOX_BY_PACKET.get(packetId);
        if (boxItemId == null) {
            throw new PacketFormatException("CMD " + packetId + " 不是魔盒命令");
        }
        return boxItemId;
    }

    public static final int LENGTH = 4;

    public Cmd221OpenMultiBoxRequest {
        if (boxSlot < 0 || boxSlot > 0xFFFF) {
            throw new PacketFormatException("boxSlot 放不进 u16：" + boxSlot);
        }
        if (keySlot < 0 || keySlot > 0xFFFF) {
            throw new PacketFormatException("keySlot 放不进 u16：" + keySlot);
        }
    }

    public static Cmd221OpenMultiBoxRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException(
                    "开魔盒的包体至少 " + LENGTH + " 字节，实际 "
                            + (plain == null ? "null" : plain.length));
        }
        PacketReader r = new PacketReader(plain);
        return new Cmd221OpenMultiBoxRequest(r.u16(), r.u16());
    }
}
