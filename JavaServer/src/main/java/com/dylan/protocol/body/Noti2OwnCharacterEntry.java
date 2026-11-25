package com.dylan.protocol.body;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti2OwnCharacterEntry(
        int uniqueId,
        String name,
        int job,
        int growType,
        int level,
        int pvpGrade,
        int pvpResult,
        int userState,
        List<Noti2CharacterEntry.Equipment> equipment,
        int adminIcon,
        long guildId,
        int privateStore,
        long creatureItemId,
        String creatureName,
        int creatureAlive,
        int premiumFlag,
        long pcRoomNo,
        int nameBadge,
        long blackCount,
        int guildLevel,
        String guildName,
        long chaosPoint,
        int adult,
        int powerSide,
        int joinPowerWar,
        int expertJobType,
        long expertJobExp,
        int disguiseKind,
        int disguiseIndex,
        int visibleValues,
        long forbiddenChatMinutes,
        long forbiddenMoveMinutes,
        long helpAbuseRatio,
        int stamina,
        int usedFatigue,
        int serverGroup,
        int powerWarPoint,
        int powerRanking,
        int emotion) implements Noti2Entry {
    public static final int VISIBLE = 1;

    public static final int DEFAULT_VISIBLE = VISIBLE;

    public static final int NAME_MAX = 0x1F;

    public static final int ADMIN_ICON_NONE = 0;

    public static final int ADMIN_ICON = 1;

    public static final int CREATURE_NAME_MAX = 0x1F;

    public static final int GUILD_NAME_MAX = 0x40;

    public static final int FIXED_LENGTH = 80;

    public Noti2OwnCharacterEntry {
        equipment = List.copyOf(equipment);
    }

    @Override
    public int subType() {
        return Noti2Body.SUBTYPE_OWN_CHARACTER;
    }

    public static Noti2OwnCharacterEntry forCharacter(
            int uniqueId, String name, int job, int growType, int level,
            List<Noti2CharacterEntry.Equipment> equipment, int usedFatigue, long guildId) {
        return forCharacter(uniqueId, name, job, growType, level, equipment, usedFatigue,
                guildId, 0L);
    }

    public static Noti2OwnCharacterEntry forCharacter(
            int uniqueId, String name, int job, int growType, int level,
            List<Noti2CharacterEntry.Equipment> equipment, int usedFatigue, long guildId,
            long creatureItemId) {
        return forCharacter(uniqueId, name, job, growType, level, equipment, usedFatigue,
                guildId, creatureItemId, "", true);
    }

    public static Noti2OwnCharacterEntry forCharacter(
            int uniqueId, String name, int job, int growType, int level,
            List<Noti2CharacterEntry.Equipment> equipment, int usedFatigue, long guildId,
            long creatureItemId, String creatureName, boolean creatureAlive) {
        return forCharacter(uniqueId, name, job, growType, level, equipment, usedFatigue,
                guildId, creatureItemId, creatureName, creatureAlive, false, false);
    }

    public static Noti2OwnCharacterEntry forCharacter(
            int uniqueId, String name, int job, int growType, int level,
            List<Noti2CharacterEntry.Equipment> equipment, int usedFatigue, long guildId,
            long creatureItemId, String creatureName, boolean creatureAlive,
            boolean canPrivateStore, boolean premiumFlag) {
        return forCharacter(uniqueId, name, job, growType, level, equipment, usedFatigue,
                guildId, creatureItemId, creatureName, creatureAlive,
                canPrivateStore, premiumFlag, 0L);
    }

    public static Noti2OwnCharacterEntry forCharacter(
            int uniqueId, String name, int job, int growType, int level,
            List<Noti2CharacterEntry.Equipment> equipment, int usedFatigue, long guildId,
            long creatureItemId, String creatureName, boolean creatureAlive,
            boolean canPrivateStore, boolean premiumFlag, long pcRoomNo) {
        return forCharacter(uniqueId, name, job, growType, level, equipment, usedFatigue,
                guildId, creatureItemId, creatureName, creatureAlive,
                canPrivateStore, premiumFlag, pcRoomNo, 0);
    }

    public static Noti2OwnCharacterEntry forCharacter(
            int uniqueId, String name, int job, int growType, int level,
            List<Noti2CharacterEntry.Equipment> equipment, int usedFatigue, long guildId,
            long creatureItemId, String creatureName, boolean creatureAlive,
            boolean canPrivateStore, boolean premiumFlag, long pcRoomNo, int nameBadge) {
        return forCharacter(uniqueId, name, job, growType, level, equipment, usedFatigue,
                guildId, creatureItemId, creatureName, creatureAlive,
                canPrivateStore, premiumFlag, pcRoomNo, nameBadge, 0, "");
    }

    public static Noti2OwnCharacterEntry forCharacter(
            int uniqueId, String name, int job, int growType, int level,
            List<Noti2CharacterEntry.Equipment> equipment, int usedFatigue, long guildId,
            long creatureItemId, String creatureName, boolean creatureAlive,
            boolean canPrivateStore, boolean premiumFlag, long pcRoomNo, int nameBadge,
            int guildLevel, String guildName) {
        return new Noti2OwnCharacterEntry(
                uniqueId, name, job, growType, level,
                0,
                0,
                0,
                equipment,
                ADMIN_ICON_NONE,
                guildId,
                canPrivateStore ? 1 : 0,
                creatureItemId,
                creatureName,
                creatureAlive ? 1 : 0,
                premiumFlag ? 1 : 0,
                pcRoomNo,
                nameBadge,
                ServerConfig.BLACK_POINT_19 >= 0
                        ? ServerConfig.BLACK_POINT_19
                        : 0L,
                guildLevel,
                guildName,
                0L,
                1,
                0,
                0,
                0,
                0L,
                0,
                0,
                ServerConfig.VISIBLE_VALUES,
                0L,
                0L,
                0L,
                Stamina.FULL,
                usedFatigue,
                0,
                0,
                0,
                EMOTION_NONE);
    }

    public Noti2OwnCharacterEntry withAdminIcon(boolean admin) {
        int value = admin ? ADMIN_ICON : ADMIN_ICON_NONE;
        return value == this.adminIcon ? this : new Noti2OwnCharacterEntry(
                uniqueId, name, job, growType, level, pvpGrade, pvpResult, userState,
                equipment, value, guildId, privateStore, creatureItemId, creatureName,
                creatureAlive, premiumFlag, pcRoomNo, nameBadge, blackCount, guildLevel,
                guildName, chaosPoint, adult, powerSide, joinPowerWar, expertJobType,
                expertJobExp, disguiseKind, disguiseIndex, visibleValues,
                forbiddenChatMinutes, forbiddenMoveMinutes, helpAbuseRatio, stamina,
                usedFatigue, serverGroup, powerWarPoint, powerRanking, emotion);
    }

    /**
     * 公会：公会号（adminIcon 后 u32）+ A4 尾部公会等级 / 公会名。官方实抓（probe_guild.py S2）有公会时
     * 公会号 = 1、等级 01、名字 "GuildA"；没公会 = 0 / 01 / 空串。
     */
    public Noti2OwnCharacterEntry withGuild(long guildId, int guildLevel, String guildName) {
        return new Noti2OwnCharacterEntry(
                uniqueId, name, job, growType, level, pvpGrade, pvpResult, userState,
                equipment, adminIcon, guildId, privateStore, creatureItemId, creatureName,
                creatureAlive, premiumFlag, pcRoomNo, nameBadge, blackCount, guildLevel,
                guildName == null ? "" : guildName, chaosPoint, adult, powerSide, joinPowerWar, expertJobType,
                expertJobExp, disguiseKind, disguiseIndex, visibleValues,
                forbiddenChatMinutes, forbiddenMoveMinutes, helpAbuseRatio, stamina,
                usedFatigue, serverGroup, powerWarPoint, powerRanking, emotion);
    }

    public static final int EMOTION_NONE = 0;

    public Noti2OwnCharacterEntry withEmotion(int emotion) {
        return emotion == this.emotion ? this : new Noti2OwnCharacterEntry(
                uniqueId, name, job, growType, level, pvpGrade, pvpResult, userState,
                equipment, adminIcon, guildId, privateStore, creatureItemId, creatureName,
                creatureAlive, premiumFlag, pcRoomNo, nameBadge, blackCount, guildLevel,
                guildName, chaosPoint, adult, powerSide, joinPowerWar, expertJobType,
                expertJobExp, disguiseKind, disguiseIndex, visibleValues,
                forbiddenChatMinutes, forbiddenMoveMinutes, helpAbuseRatio, stamina,
                usedFatigue, serverGroup, powerWarPoint, powerRanking, emotion);
    }

    public Noti2OwnCharacterEntry withStamina(int stamina) {
        return stamina == this.stamina ? this : new Noti2OwnCharacterEntry(
                uniqueId, name, job, growType, level, pvpGrade, pvpResult, userState,
                equipment, adminIcon, guildId, privateStore, creatureItemId, creatureName,
                creatureAlive, premiumFlag, pcRoomNo, nameBadge, blackCount, guildLevel,
                guildName, chaosPoint, adult, powerSide, joinPowerWar, expertJobType,
                expertJobExp, disguiseKind, disguiseIndex, visibleValues,
                forbiddenChatMinutes, forbiddenMoveMinutes, helpAbuseRatio, stamina,
                usedFatigue, serverGroup, powerWarPoint, powerRanking, emotion);
    }

    public Noti2OwnCharacterEntry withExpertJob(int expertJobType, long expertJobExp) {
        return expertJobType == this.expertJobType && expertJobExp == this.expertJobExp
                ? this
                : new Noti2OwnCharacterEntry(
                uniqueId, name, job, growType, level, pvpGrade, pvpResult, userState,
                equipment, adminIcon, guildId, privateStore, creatureItemId, creatureName,
                creatureAlive, premiumFlag, pcRoomNo, nameBadge, blackCount, guildLevel,
                guildName, chaosPoint, adult, powerSide, joinPowerWar, expertJobType,
                expertJobExp, disguiseKind, disguiseIndex, visibleValues,
                forbiddenChatMinutes, forbiddenMoveMinutes, helpAbuseRatio, stamina,
                usedFatigue, serverGroup, powerWarPoint, powerRanking, emotion);
    }

    @Override
    public void writeTo(PacketWriter w, int subType) {
        validate();
        w.u16(uniqueId);
        w.string(name);
        w.u8(job);
        w.u8(growType & 0xFF);
        w.u8(level);
        w.u8(pvpGrade);
        w.u8(pvpResult);
        w.u8(userState);
        w.u8(equipment.size());
        for (Noti2CharacterEntry.Equipment e : equipment) {
            e.writeTo(w);
        }
        w.u8(adminIcon);
        w.u32((int) guildId);
        w.u8(privateStore);
        w.u32((int) creatureItemId);
        w.string(creatureName);
        w.u8(creatureAlive);
        w.u8(premiumFlag);
        w.u32((int) pcRoomNo);
        w.u8(nameBadge);
        if (ServerConfig.A4) {
            // A4 尾部布局完全不同（见 writeA4Tail 注释）
            writeA4Tail(w);
            return;
        }
        w.u32((int) blackCount);
        w.u8(guildLevel);
        w.string(guildName);
        w.u32((int) chaosPoint);
        w.u8(adult);
        w.u8(powerSide);
        w.u8(joinPowerWar);
        w.u8(expertJobType);
        w.u32((int) expertJobExp);
        w.u8(disguiseKind);
        w.u16(disguiseIndex);
        w.u8(visibleValues);
        w.u32((int) forbiddenChatMinutes);
        w.u32((int) forbiddenMoveMinutes);
        w.u32((int) helpAbuseRatio);
        w.u8(stamina);
        w.u16(usedFatigue);
        w.u8(serverGroup);
        w.u16(powerWarPoint);
        w.u8(powerRanking);
        w.u16(emotion);
    }

    /** 官方新角色这一字节恒为 06（四份样本都一样），含义未知。 */
    public static final int A4_UNKNOWN_10 = 6;

    /** A4 尾部首个 u32，官方恒 0x101，含义未知。 */
    public static final int A4_UNKNOWN_0 = 0x101;

    /**
     * A4 子类型 0 在 nameBadge 之后：没有 ACT5 的 blackCount（field_scan guild.add：公会号后紧跟
     * 公会等级 + 公会名），公会名之后是 29 字节（客户端 0041FBBE..0041FEA1），读取序列 u32 u8 u8 u32 u8 u32 u32 u32 u8 u16 u8 u16。
     * 已验证的只有 +6 起的 u32 = 强化熟练度（characters.enchant_expertness → +67）和末尾 u16 = 心情
     * （characters.mood → +88，与 ACT5 emotion 同义）。其余照官方新角色的字节填，含义未知：
     * +0 u32 恒为 0x101（官方 44 份样本：session_replay 23 份 + 满级假人 20 个角色 + 新角色，全是 01 01 00 00），
     * +10 恒为 06。
     */
    private void writeA4Tail(PacketWriter w) {
        w.u8(guildLevel);
        w.string(guildName);
        w.u32(A4_UNKNOWN_0);
        w.u8(0);
        w.u8(0);
        w.u32(0);
        w.u8(A4_UNKNOWN_10);
        w.u32(0);
        w.u32(0);
        w.u32(0);
        w.u8(0);
        w.u16(0);
        w.u8(0);
        w.u16(emotion);
    }

    public int length() {
        return FIXED_LENGTH
                + byteLength(name) + byteLength(creatureName) + byteLength(guildName)
                + equipment.size() * Noti2CharacterEntry.Equipment.LENGTH;
    }

    private static int byteLength(String s) {
        return s.getBytes(PacketWriter.DEFAULT_CHARSET).length;
    }

    private void validate() {
        checkName("角色名", name, NAME_MAX, true);
        checkName("宠物名", creatureName, CREATURE_NAME_MAX, false);
        checkName("公会名", guildName, GUILD_NAME_MAX, false);
        checkU16("uniqueId", uniqueId);
        checkU16("disguiseIndex", disguiseIndex);
        checkU16("usedFatigue", usedFatigue);
        checkU16("powerWarPoint", powerWarPoint);
        checkU16("emotion", emotion);
        if (equipment.size() > Noti2CharacterEntry.EQUIPMENT_MAX) {
            throw new PacketFormatException("装备条数是 u8，超出 0.."
                    + Noti2CharacterEntry.EQUIPMENT_MAX + "：" + equipment.size());
        }
    }

    private static void checkName(String what, String value, int max, boolean required) {
        if (value == null) {
            throw new PacketFormatException(what + "不能是 null，没有就传空串");
        }
        int len = byteLength(value);
        if (required && len == 0) {
            throw new PacketFormatException(what + "不能为空");
        }
        if (len >= max) {
            throw new PacketFormatException(what + "是 " + len + " 字节，必须小于 " + max
                    + " —— 客户端在长度 ≥ 上限时只吃 4 字节长度、不读内容，后面会整体错位");
        }
    }

    private static void checkU16(String what, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new PacketFormatException(what + " 超出 u16：" + value);
        }
    }
}
