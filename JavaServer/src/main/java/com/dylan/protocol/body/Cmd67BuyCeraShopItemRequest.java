package com.dylan.protocol.body;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public record Cmd67BuyCeraShopItemRequest(int mode, String receiver, List<Line> lines) {
    public static final int PACKET_ID = 67;

    public static final int MODE_BUY = 0;

    public static final int MODE_GIFT = 1;

    public static final int MAX_LINES = 0x23;

    public static final int MAX_RECEIVER_BYTES = 30;

    public static final int MAX_EXTRA = 0x23;

    private static final Charset NAME_CHARSET = Charset.forName("GBK");

    public record Line(int avatarOption, int periodCode, long commodityNo,
                       List<AvatarOption> avatarOptions,
                       List<PackageSelection> packageSelections) {
        public record AvatarOption(long itemId, int abilityNo) {
        }

        public record PackageSelection(long itemId, int groupIndex, int itemIndex) {
        }

        public static final int NO_AVATAR_OPTION = 0xFF;

        public static final int MAX_AVATAR_OPTION = 99;

        public static final int MIN_LENGTH = 8;

        public boolean hasAvatarOption() {
            return avatarOption != NO_AVATAR_OPTION;
        }

        public static int takeAbilityNo(List<AvatarOption> pool, long itemId) {
            for (var it = pool.iterator(); it.hasNext(); ) {
                AvatarOption one = it.next();
                if (one.itemId() == itemId) {
                    it.remove();
                    return one.abilityNo();
                }
            }
            return 0;
        }
    }

    public boolean gift() {
        return mode == MODE_GIFT;
    }

    public static Cmd67BuyCeraShopItemRequest parse(byte[] body) {
        var r = new PacketReader(body);
        int mode = r.u8();
        if (mode != MODE_BUY && mode != MODE_GIFT) {
            throw new PacketFormatException("CMD 67 的 mode 只能是 0 或 1，读到 " + mode);
        }
        String receiver = "";
        if (mode == MODE_GIFT) {
            int len = r.u32();
            if (len < 0 || len > MAX_RECEIVER_BYTES) {
                throw new PacketFormatException("CMD 67 的收礼人名长度 " + len
                        + " 超出官方缓冲区 " + MAX_RECEIVER_BYTES + " 字节");
            }
            receiver = new String(r.bytes(len), NAME_CHARSET).trim();
        }

        int count = r.u8();
        if (count <= 0 || count > MAX_LINES) {
            throw new PacketFormatException("CMD 67 的商品项数 " + count
                    + " 不在 1.." + MAX_LINES + " 之内");
        }

        List<Line> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int avatarOption = r.u8();
            int periodCode = r.u8();
            boolean a4 = ServerConfig.A4;
            if (a4) {
                // A4 每项：选项、期限、1 字节含义未知、u32 商品号、u8 条数 + 条数×(u32 物品, u8)。
                // 没有 ACT5 的子列表 B。官方日志：礼包 FF FF FF + 9 条；普通 4 项每项 8 字节
                // FF 00 00 + 商品号 + 00；永久时装 00 03 00 + 商品号 + 00。
                r.u8();
            }
            long commodityNo = Integer.toUnsignedLong(r.u32());

            int extraA = r.u8();
            if (extraA > MAX_EXTRA) {
                throw new PacketFormatException("CMD 67 第 " + (i + 1) + " 项的子列表 A 有 "
                        + extraA + " 条，官方上限 " + MAX_EXTRA);
            }
            List<Line.AvatarOption> options = new ArrayList<>(extraA);
            for (int j = 0; j < extraA; j++) {
                long itemId = Integer.toUnsignedLong(r.u32());
                options.add(new Line.AvatarOption(itemId, r.u8()));
            }
            int extraB = a4 ? 0 : r.u8();
            if (extraB > MAX_EXTRA) {
                throw new PacketFormatException("CMD 67 第 " + (i + 1) + " 项的子列表 B 有 "
                        + extraB + " 条，官方上限 " + MAX_EXTRA);
            }
            List<Line.PackageSelection> selections = new ArrayList<>(extraB);
            for (int j = 0; j < extraB; j++) {
                long itemId = Integer.toUnsignedLong(r.u32());
                selections.add(new Line.PackageSelection(itemId, r.u16(), r.u16()));
            }
            lines.add(new Line(avatarOption, periodCode, commodityNo,
                    List.copyOf(options), List.copyOf(selections)));
        }
        return new Cmd67BuyCeraShopItemRequest(mode, receiver, List.copyOf(lines));
    }
}
