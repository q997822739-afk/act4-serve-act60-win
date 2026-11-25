package com.dylan.protocol.body;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti2CharacterEntry(
        int listIndex,
        String name,
        int nameFlagged,
        int job,
        int growType,
        int level,
        int reserved7,
        int statusFlag,
        List<Equipment> equipment,
        int reserved15,
        int guildId,
        int privateStore,
        int premiumFlag,
        int pcRoomNo,
        int unknown20,
        int unknown21,
        int banRemainDays) implements Noti2Entry {
    public record Equipment(int slot, int itemId, int flags,
                            int clearAvatarItemId, int enchant) {
        public static final int LENGTH = 14;

        public static final int SLOT_MAX = 34;

        public static final int MAX_UPGRADE = 0x7F;

        /**
         * ACT5：flags = 强化 &lt;&lt; 1 | 增幅位。A4 没有增幅，flags 就是强化等级本身：field_scan 把官方库
         * equip9.item_state 改成 7，NOTI 2/2 +26、2/0 +26 都是 07；满级假人 +30 武器两包都是 1E（A5 写成 3C）。
         */
        public static Equipment of(int slot, int itemId, int upgrade, boolean amplified,
                                   int clearAvatarItemId, int enchant) {
            if (upgrade < 0 || upgrade > MAX_UPGRADE) {
                throw new PacketFormatException("强化等级只有 7 位，超出 0.." + MAX_UPGRADE
                        + "：" + upgrade);
            }
            int flags = ServerConfig.A4 ? upgrade : (upgrade << 1) | (amplified ? 1 : 0);
            return new Equipment(slot, itemId, flags, clearAvatarItemId, enchant);
        }

        public int upgrade() {
            return ServerConfig.A4 ? flags & MAX_UPGRADE : (flags >> 1) & MAX_UPGRADE;
        }

        void writeTo(PacketWriter w) {
            if (slot < 0 || slot >= SLOT_MAX) {
                throw new PacketFormatException("装备槽位 " + slot + " 超出 0.."
                        + (SLOT_MAX - 1) + "：客户端拿它直接当数组下标，没有上界检查");
            }
            if (flags < 0 || flags > 0xFF) {
                throw new PacketFormatException("装备 flags 是 u8，超出 0..255：" + flags);
            }
            w.u8(slot);
            w.u32(itemId);
            w.u8(flags);
            w.u32(clearAvatarItemId);
            w.u32(enchant);
        }
    }

    public static final int NAME_MAX = 0x1F;

    public static final int EQUIPMENT_MAX = 0xFF;

    public static final int FIXED_LENGTH = 30;

    public static final int STATUS_NORMAL = 0;

    public Noti2CharacterEntry {
        equipment = List.copyOf(equipment);
    }

    public static Noti2CharacterEntry minimal(String name) {
        return new Noti2CharacterEntry(0, name, 0, 0, 0, 0, 0, 0,
                List.of(), 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static Noti2CharacterEntry of(int listIndex, String name, int job, int growType,
                                         int level, List<Equipment> equipment) {
        return new Noti2CharacterEntry(listIndex, name, 0, job, growType, level, 0,
                STATUS_NORMAL, equipment, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public int length() {
        return FIXED_LENGTH + name.getBytes(PacketWriter.DEFAULT_CHARSET).length
                + equipment.size() * Equipment.LENGTH;
    }

    @Override
    public int subType() {
        return Noti2Body.SUBTYPE_CHARACTER_LIST;
    }

    @Override
    public void writeTo(PacketWriter w, int subType) {
        validate();
        w.u16(listIndex);
        w.string(name);
        w.u8(nameFlagged);
        w.u8(job);
        w.u8(growType & 0xFF);
        w.u8(level);
        w.u8(reserved7);
        w.u8(statusFlag);
        w.u8(equipment.size());
        for (Equipment e : equipment) {
            e.writeTo(w);
        }
        w.u8(reserved15);
        w.u32(guildId);
        w.u8(privateStore);
        w.u8(premiumFlag);
        if (ServerConfig.A4) {
            // A4 装备后 13 字节（ACT5 17）。官方新角色恒为 00 00000000 00 00 01000000 00 00；
            // 后 6 字节含义未知，照官方长度和位置填。
            w.u32(A4_TAIL_UNKNOWN);
            w.u8(0);
            w.u8(0);
            return;
        }
        w.u32(pcRoomNo);
        w.u8(unknown20);
        w.u8(unknown21);
        w.u32(banRemainDays);
    }

    public static final int A4_TAIL_UNKNOWN = 1;

    private void validate() {
        if (name == null) {
            throw new PacketFormatException("角色名不能为 null");
        }
        int n = name.getBytes(PacketWriter.DEFAULT_CHARSET).length;
        if (n == 0 || n >= NAME_MAX) {
            throw new PacketFormatException("角色名的字节数必须落在 1.." + (NAME_MAX - 1)
                    + "，实际 " + n + "：长度为 0 或超宽时客户端只吃掉 4 字节长度、"
                    + "不读内容，后面的字段会整体错位");
        }
        if (equipment.size() > EQUIPMENT_MAX) {
            throw new PacketFormatException("装备条数是 u8，超出 0..255："
                    + equipment.size());
        }
        requireU8(nameFlagged, "#3 nameFlagged");
        requireU8(job, "#4 job");
        if (growType < -0x80 || growType > 0xFF) {
            throw new PacketFormatException("#5 growType 是 u8（客户端按有符号读），"
                    + "超出 -128..255：" + growType);
        }
        requireU8(level, "#6 level");
        requireU8(reserved7, "#7");
        requireU8(statusFlag, "#8 statusFlag");
        requireU8(reserved15, "#15");
        requireU8(privateStore, "#17 privateStore");
        requireU8(premiumFlag, "#18 premiumFlag");
        requireU8(unknown20, "#20");
        requireU8(unknown21, "#21");
    }

    private static void requireU8(int value, String what) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(what + " 是 u8，超出 0..255：" + value);
        }
    }
}
