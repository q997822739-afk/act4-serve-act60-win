package com.dylan.store.blob;

import java.util.Arrays;

public record InvenItem(
        int attachType,
        int itemType,
        long itemId,
        int itemAttr,
        long addInfo,
        int endurance,
        long enchant,
        AmplifyOption amplify,
        ItemGuid guid,
        ChronicleRandomOption chronicle,
        int upgradeSeparateInfo,
        byte[] reserved) {

    public static final int BYTES = 61;

    public static final int RESERVED_BYTES = 9;

    private static final int OFF_ATTACH_TYPE = 0;
    private static final int OFF_ITEM_TYPE = 1;
    private static final int OFF_ITEM_ID = 2;
    private static final int OFF_ITEM_ATTR = 6;
    private static final int OFF_ADD_INFO = 7;
    private static final int OFF_ENDURANCE = 11;
    private static final int OFF_ENCHANT = 13;
    private static final int OFF_AMPLIFY = 17;
    private static final int OFF_GUID = 21;
    private static final int OFF_CHRONICLE = 31;
    private static final int OFF_UPGRADE_SEPARATE = 51;
    private static final int OFF_RESERVED = 52;

    private static final int UPGRADE_MASK = 0x1F;
    private static final int TRADE_LIMIT_SHIFT = 5;

    private static final int UPGRADE_SEPARATE_MASK = 0x1F;
    private static final int TRADE_RESTRICTION_BIT = 0x20;

    public static final InvenItem EMPTY = new InvenItem(
            0, 0, 0, 0, 0, 0, 0,
            AmplifyOption.EMPTY, ItemGuid.EMPTY, ChronicleRandomOption.EMPTY,
            0, new byte[RESERVED_BYTES]);

    public InvenItem {
        checkU8("attachType", attachType);
        checkU8("itemType", itemType);
        checkU32("itemId", itemId);
        checkU8("itemAttr", itemAttr);
        checkU32("addInfo", addInfo);
        if (endurance < 0 || endurance > 0xFFFF) {
            throw new IllegalArgumentException("endurance 超出 u16：" + endurance);
        }
        checkU32("enchant", enchant);
        checkU8("upgradeSeparateInfo", upgradeSeparateInfo);
        if (amplify == null || guid == null || chronicle == null) {
            throw new IllegalArgumentException("InvenItem 的子结构不能为 null");
        }
        if (reserved == null || reserved.length != RESERVED_BYTES) {
            throw new IllegalArgumentException("reserved 必须正好 " + RESERVED_BYTES + " 字节");
        }
        reserved = reserved.clone();
    }

    private static void checkU8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException(name + " 超出 u8：" + value);
        }
    }

    private static void checkU32(String name, long value) {
        if (value < 0 || value > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException(name + " 超出 u32：" + value);
        }
    }

    public boolean isEmpty() {
        return itemId == 0;
    }

    public int upgrade() {
        return itemAttr & UPGRADE_MASK;
    }

    public boolean isAmplified() {
        return amplify.hasAbility();
    }

    public int tradeLimitCount() {
        return (itemAttr & 0xFF) >>> TRADE_LIMIT_SHIFT;
    }

    public int reSealCount() {
        return tradeLimitCount();
    }

    public int upgradeSeparate() {
        return upgradeSeparateInfo & UPGRADE_SEPARATE_MASK;
    }

    public boolean tradeRestricted() {
        return (upgradeSeparateInfo & TRADE_RESTRICTION_BIT) != 0;
    }

    public ItemType type() {
        return ItemType.of(itemType);
    }

    public boolean equipable() {
        return itemType == 1 || itemType == 5 || itemType == 6 || itemType == 8;
    }

    public boolean avatar() {
        return itemType == 8;
    }

    public boolean creature() {
        return itemType == 5 || itemType == 6 || itemType == 7;
    }

    public long stackCount() {
        return equipable() ? 1L : Math.max(1L, addInfo);
    }

    public static InvenItem of(long itemId, int itemType, long addInfo) {
        return new InvenItem(0, itemType, itemId, 0, addInfo, 0, 0,
                AmplifyOption.EMPTY, ItemGuid.EMPTY, ChronicleRandomOption.EMPTY,
                0, new byte[RESERVED_BYTES]);
    }

    public InvenItem withUpgrade(int level) {
        if (level < 0 || level > UPGRADE_MASK) {
            throw new IllegalArgumentException("强化等级超出 5 位：" + level);
        }
        int attr = (itemAttr & ~UPGRADE_MASK) | level;
        return new InvenItem(attachType, itemType, itemId, attr, addInfo, endurance, enchant,
                amplify, guid, chronicle, upgradeSeparateInfo, reserved);
    }

    public InvenItem withItemAttr(int value) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException("itemAttr 超出 u8：" + value);
        }
        return new InvenItem(attachType, itemType, itemId, value, addInfo, endurance, enchant,
                amplify, guid, chronicle, upgradeSeparateInfo, reserved);
    }

    public InvenItem withReSealCount(int value) {
        int attr = (itemAttr & UPGRADE_MASK) | ((value & 0x7) << TRADE_LIMIT_SHIFT);
        return new InvenItem(attachType, itemType, itemId, attr, addInfo, endurance, enchant,
                amplify, guid, chronicle, upgradeSeparateInfo, reserved);
    }

    public InvenItem withEnchant(long value) {
        if (value < 0 || value > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("enchant 超出 u32：" + value);
        }
        return new InvenItem(attachType, itemType, itemId, itemAttr, addInfo, endurance, value,
                amplify, guid, chronicle, upgradeSeparateInfo, reserved);
    }

    public InvenItem withEndurance(int value) {
        return new InvenItem(attachType, itemType, itemId, itemAttr, addInfo, value, enchant,
                amplify, guid, chronicle, upgradeSeparateInfo, reserved);
    }

    public InvenItem withAttachType(int value) {
        return new InvenItem(value, itemType, itemId, itemAttr, addInfo, endurance, enchant,
                amplify, guid, chronicle, upgradeSeparateInfo, reserved);
    }

    public InvenItem withAmplify(AmplifyOption value) {
        return new InvenItem(attachType, itemType, itemId, itemAttr, addInfo, endurance, enchant,
                value, guid, chronicle, upgradeSeparateInfo, reserved);
    }

    public InvenItem withItemId(long value) {
        return new InvenItem(attachType, itemType, value, itemAttr, addInfo, endurance, enchant,
                amplify, guid, chronicle, upgradeSeparateInfo, reserved);
    }

    public InvenItem withAddInfo(long value) {
        return new InvenItem(attachType, itemType, itemId, itemAttr, value, endurance, enchant,
                amplify, guid, chronicle, upgradeSeparateInfo, reserved);
    }

    public static InvenItem read(byte[] src, int offset) {
        if (offset < 0 || offset + BYTES > src.length) {
            throw new BlobFormatException("读 Inven_Item 越界：offset=" + offset
                    + "，可用 " + src.length + " 字节");
        }
        return new InvenItem(
                src[offset + OFF_ATTACH_TYPE] & 0xFF,
                src[offset + OFF_ITEM_TYPE] & 0xFF,
                LittleEndian.u32(src, offset + OFF_ITEM_ID),
                src[offset + OFF_ITEM_ATTR] & 0xFF,
                LittleEndian.u32(src, offset + OFF_ADD_INFO),
                LittleEndian.u16(src, offset + OFF_ENDURANCE),
                LittleEndian.u32(src, offset + OFF_ENCHANT),
                AmplifyOption.read(src, offset + OFF_AMPLIFY),
                ItemGuid.read(src, offset + OFF_GUID),
                ChronicleRandomOption.read(src, offset + OFF_CHRONICLE),
                src[offset + OFF_UPGRADE_SEPARATE] & 0xFF,
                Arrays.copyOfRange(src, offset + OFF_RESERVED, offset + OFF_RESERVED + RESERVED_BYTES));
    }

    public void write(byte[] dst, int offset) {
        if (offset < 0 || offset + BYTES > dst.length) {
            throw new BlobFormatException("写 Inven_Item 越界：offset=" + offset
                    + "，可用 " + dst.length + " 字节");
        }
        dst[offset + OFF_ATTACH_TYPE] = (byte) attachType;
        dst[offset + OFF_ITEM_TYPE] = (byte) itemType;
        LittleEndian.putU32(dst, offset + OFF_ITEM_ID, itemId);
        dst[offset + OFF_ITEM_ATTR] = (byte) itemAttr;
        LittleEndian.putU32(dst, offset + OFF_ADD_INFO, addInfo);
        LittleEndian.putU16(dst, offset + OFF_ENDURANCE, endurance);
        LittleEndian.putU32(dst, offset + OFF_ENCHANT, enchant);
        amplify.write(dst, offset + OFF_AMPLIFY);
        guid.write(dst, offset + OFF_GUID);
        chronicle.write(dst, offset + OFF_CHRONICLE);
        dst[offset + OFF_UPGRADE_SEPARATE] = (byte) upgradeSeparateInfo;
        System.arraycopy(reserved, 0, dst, offset + OFF_RESERVED, RESERVED_BYTES);
    }

    @Override
    public byte[] reserved() {
        return reserved.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InvenItem other)) {
            return false;
        }
        return attachType == other.attachType
                && itemType == other.itemType
                && itemId == other.itemId
                && itemAttr == other.itemAttr
                && addInfo == other.addInfo
                && endurance == other.endurance
                && enchant == other.enchant
                && upgradeSeparateInfo == other.upgradeSeparateInfo
                && amplify.equals(other.amplify)
                && guid.equals(other.guid)
                && chronicle.equals(other.chronicle)
                && Arrays.equals(reserved, other.reserved);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(itemId);
        result = 31 * result + attachType;
        result = 31 * result + itemType;
        result = 31 * result + itemAttr;
        result = 31 * result + Long.hashCode(addInfo);
        result = 31 * result + endurance;
        result = 31 * result + Long.hashCode(enchant);
        result = 31 * result + upgradeSeparateInfo;
        result = 31 * result + amplify.hashCode();
        result = 31 * result + guid.hashCode();
        result = 31 * result + chronicle.hashCode();
        result = 31 * result + Arrays.hashCode(reserved);
        return result;
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "InvenItem[空]";
        }
        StringBuilder sb = new StringBuilder("InvenItem[id=").append(itemId)
                .append(", type=").append(itemType);
        if (upgrade() != 0) {
            sb.append(isAmplified() ? ", 增幅+" : ", 强化+").append(upgrade());
        }
        if (upgradeSeparate() != 0) {
            sb.append(", 锻造+").append(upgradeSeparate());
        }
        if (addInfo != 0) {
            sb.append(", addInfo=").append(addInfo);
        }
        if (endurance != 0) {
            sb.append(", 耐久=").append(endurance);
        }
        return sb.append(']').toString();
    }
}
