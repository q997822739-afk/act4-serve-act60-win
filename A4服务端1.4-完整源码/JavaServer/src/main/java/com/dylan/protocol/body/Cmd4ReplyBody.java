package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Cmd4ReplyBody(
        long regTime,
        long createTime,
        int uniqueId,
        int totalFatigue,
        int totalMaxFatigue,
        int usedFatigue,
        List<Premium> premiums,
        long cera,
        List<QuestSlot> activeQuests,
        List<CompletedQuestSet> completedQuestSets,
        int townId,
        int tutorialSkipable,
        List<Integer> completedTutorials,
        int fatigueBatteryCharging,
        int fatigueGrownUpBuff) {

    public static final int PACKET_ID = 4;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAILED = 0;

    public static final int MIN_LENGTH = 90;

    public static final int TUTORIAL_ENTER_TOWN = 30;

    public static final int QUEST_SLOTS = 10;

    public static final int EMPTY_QUEST_ID = 0xFFFF;

    public static final long EMPTY_QUEST_TRIGGER = 0xFFFFL;

    public record QuestSlot(int questId, long trigger) {
        public QuestSlot {
            if (questId < 0 || questId > 0xFFFF) {
                throw new PacketFormatException("任务号超出 u16：" + questId);
            }
            if (trigger < 0 || trigger > 0xFFFF_FFFFL) {
                throw new PacketFormatException("任务进度超出 u32：" + trigger);
            }
        }
    }

    public record CompletedQuestSet(int category, byte[] bitmap) {

        public CompletedQuestSet {
            if (category < 0 || category > MAX_CATEGORY) {
                throw new PacketFormatException("已完成任务的档号 " + category + " 超出 0.."
                        + MAX_CATEGORY + "，客户端会拿它当 表+(档号<<6) 的下标往表外写");
            }
            if (bitmap == null || bitmap.length != BITMAP_LENGTH) {
                throw new PacketFormatException("已完成任务的位图必须正好 " + BITMAP_LENGTH
                        + " 字节（客户端无条件按这个长度整块拷），给了 "
                        + (bitmap == null ? "null" : bitmap.length + " 字节"));
            }
            bitmap = bitmap.clone();
        }

        @Override
        public byte[] bitmap() {
            return bitmap.clone();
        }

        public int completedCount() {
            int bits = 0;
            for (byte b : bitmap) {
                bits += Integer.bitCount(b & 0xFF);
            }
            return bits;
        }

        public static CompletedQuestSet all(int category) {
            byte[] bitmap = new byte[BITMAP_LENGTH];
            java.util.Arrays.fill(bitmap, (byte) 0xFF);
            return new CompletedQuestSet(category, bitmap);
        }

        public static List<CompletedQuestSet> allCategories() {
            List<CompletedQuestSet> sets = new java.util.ArrayList<>(MAX_CATEGORY + 1);
            for (int c = 0; c <= MAX_CATEGORY; c++) {
                sets.add(all(c));
            }
            return List.copyOf(sets);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof CompletedQuestSet other
                    && other.category == category
                    && java.util.Arrays.equals(other.bitmap, bitmap);
        }

        @Override
        public int hashCode() {
            return 31 * category + java.util.Arrays.hashCode(bitmap);
        }

        @Override
        public String toString() {
            return "第 " + category + " 档 " + completedCount() + " 位";
        }
    }

    public static final int MAX_CATEGORY = 9;

    public static final int BITMAP_LENGTH = 64;

    public static final int BITS_PER_CATEGORY = 512;

    public record Premium(int type, long remainSecs) {
        public Premium {
            if (type < 0 || type > 0xFF) {
                throw new PacketFormatException("特权类型超出 u8：" + type);
            }
            if (remainSecs < 0 || remainSecs > 0xFFFF_FFFFL) {
                throw new PacketFormatException("特权剩余秒数超出 u32：" + remainSecs);
            }
        }
    }

    public Cmd4ReplyBody {
        if (premiums == null) {
            throw new PacketFormatException("premiums 不能为 null，没有特权就传空表");
        }
        premiums = List.copyOf(premiums);
        if (premiums.size() > 0xFF) {
            throw new PacketFormatException("特权条数超出 u8：" + premiums.size());
        }
        if (activeQuests == null) {
            throw new PacketFormatException("activeQuests 不能为 null，没有任务就传空表");
        }
        activeQuests = List.copyOf(activeQuests);
        if (activeQuests.size() > QUEST_SLOTS) {
            throw new PacketFormatException("客户端只读 " + QUEST_SLOTS + " 个任务槽，给了 "
                    + activeQuests.size() + " 个");
        }
        checkU16("uniqueId", uniqueId);
        checkU16("totalFatigue", totalFatigue);
        checkU16("totalMaxFatigue", totalMaxFatigue);
        checkU16("usedFatigue", usedFatigue);
        checkU32("regTime", regTime);
        checkU32("createTime", createTime);
        checkU32("cera", cera);
        if (completedTutorials == null) {
            throw new PacketFormatException("completedTutorials 不能为 null，没跑过教程就传空表");
        }
        completedTutorials = List.copyOf(completedTutorials);
        if (completedTutorials.size() > 0xFF) {
            throw new PacketFormatException("教程条数超出 u8：" + completedTutorials.size());
        }
        for (int t : completedTutorials) {
            checkU8("教程号", t);
        }
        if (completedQuestSets == null) {
            throw new PacketFormatException(
                    "completedQuestSets 不能为 null，一个任务都没完成就传空表");
        }
        completedQuestSets = List.copyOf(completedQuestSets);
        if (completedQuestSets.size() > 0xFF) {
            throw new PacketFormatException(
                    "已完成任务的档数超出 u8：" + completedQuestSets.size());
        }
        checkU8("townId", townId);
        checkU8("tutorialSkipable", tutorialSkipable);
        checkU16("fatigueBatteryCharging", fatigueBatteryCharging);
        checkU16("fatigueGrownUpBuff", fatigueGrownUpBuff);
    }

    public Cmd4ReplyBody(long regTime, long createTime, int uniqueId,
                         int totalFatigue, int totalMaxFatigue, int usedFatigue,
                         List<Premium> premiums, long cera) {
        this(regTime, createTime, uniqueId, totalFatigue, totalMaxFatigue, usedFatigue,
                premiums, cera, List.of(), List.of(), 0, 0, List.of(), 0, 0);
    }

    private static void checkU8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new PacketFormatException(name + " 超出 u8：" + value);
        }
    }

    private static void checkU16(String name, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new PacketFormatException(name + " 超出 u16：" + value);
        }
    }

    private static void checkU32(String name, long value) {
        if (value < 0 || value > 0xFFFF_FFFFL) {
            throw new PacketFormatException(name + " 超出 u32：" + value);
        }
    }

    public Cmd4ReplyBody withFatigue(int totalFatigue, int totalMaxFatigue, int usedFatigue) {
        return new Cmd4ReplyBody(regTime, createTime, uniqueId,
                totalFatigue, totalMaxFatigue, usedFatigue,
                premiums, cera, activeQuests, completedQuestSets, townId, tutorialSkipable, completedTutorials,
                fatigueBatteryCharging, fatigueGrownUpBuff);
    }

    public Cmd4ReplyBody withCompletedQuestSets(List<CompletedQuestSet> sets) {
        return new Cmd4ReplyBody(regTime, createTime, uniqueId,
                totalFatigue, totalMaxFatigue, usedFatigue,
                premiums, cera, activeQuests, sets, townId, tutorialSkipable, completedTutorials,
                fatigueBatteryCharging, fatigueGrownUpBuff);
    }

    public Cmd4ReplyBody withTutorialSkipable(boolean skipable) {
        return new Cmd4ReplyBody(regTime, createTime, uniqueId,
                totalFatigue, totalMaxFatigue, usedFatigue,
                premiums, cera, activeQuests, completedQuestSets, townId, skipable ? 1 : 0,
                completedTutorials, fatigueBatteryCharging, fatigueGrownUpBuff);
    }

    public Cmd4ReplyBody withFatigueExtras(int batteryCharging, int grownUpBuff) {
        return new Cmd4ReplyBody(regTime, createTime, uniqueId,
                totalFatigue, totalMaxFatigue, usedFatigue,
                premiums, cera, activeQuests, completedQuestSets, townId, tutorialSkipable,
                completedTutorials, batteryCharging, grownUpBuff);
    }

    public Cmd4ReplyBody withActiveQuests(List<QuestSlot> slots) {
        return new Cmd4ReplyBody(regTime, createTime, uniqueId,
                totalFatigue, totalMaxFatigue, usedFatigue,
                premiums, cera, slots, completedQuestSets, townId, tutorialSkipable, completedTutorials,
                fatigueBatteryCharging, fatigueGrownUpBuff);
    }

    public Cmd4ReplyBody withCera(long value) {
        return new Cmd4ReplyBody(regTime, createTime, uniqueId,
                totalFatigue, totalMaxFatigue, usedFatigue,
                premiums, value, activeQuests, completedQuestSets, townId, tutorialSkipable, completedTutorials,
                fatigueBatteryCharging, fatigueGrownUpBuff);
    }

    public Cmd4ReplyBody withPremiums(List<Premium> value) {
        return new Cmd4ReplyBody(regTime, createTime, uniqueId,
                totalFatigue, totalMaxFatigue, usedFatigue,
                value, cera, activeQuests, completedQuestSets, townId, tutorialSkipable,
                completedTutorials, fatigueBatteryCharging, fatigueGrownUpBuff);
    }

    public static final int MAX_PREMIUM_TYPE = 43;

    public static Cmd4ReplyBody minimal(long regTime, long createTime, int uniqueId) {
        return new Cmd4ReplyBody(regTime, createTime, uniqueId, 0, 0, 0, List.of(), 0,
                List.of(), List.of(), 0, 0, List.of(), 0, 0);
    }

    public static Cmd4ReplyBody enteringTown(long regTime, long createTime, int uniqueId,
                                             int townId) {
        return new Cmd4ReplyBody(regTime, createTime, uniqueId, 0, 0, 0, List.of(), 0,
                List.of(), List.of(), townId, 0, List.of(TUTORIAL_ENTER_TOWN), 0, 0);
    }

    public static Cmd4ReplyBody pristineInTown(long regTime, long createTime, int uniqueId,
                                               int townId) {
        return new Cmd4ReplyBody(regTime, createTime, uniqueId, 0, 0, 0, List.of(), 0,
                List.of(), List.of(), townId, 0, List.of(), 0, 0);
    }

    public static Cmd4ReplyBody withTutorials(long regTime, long createTime, int uniqueId,
                                              int townId, List<Integer> completedTutorials) {
        return new Cmd4ReplyBody(regTime, createTime, uniqueId, 0, 0, 0, List.of(), 0,
                List.of(), List.of(), townId, 0, completedTutorials, 0, 0);
    }

    public boolean entersTown() {
        return completedTutorials.contains(TUTORIAL_ENTER_TOWN);
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(
                MIN_LENGTH + premiums.size() * 5 + completedTutorials.size()
                        + completedQuestSets.size() * (1 + 4 + BITMAP_LENGTH));
        w.u8(RESULT_OK);
        w.u32((int) regTime);
        w.u32((int) createTime);
        w.u16(uniqueId);
        w.u16(totalFatigue);
        w.u16(totalMaxFatigue);
        w.u16(usedFatigue);
        w.u8(premiums.size());
        for (Premium p : premiums) {
            w.u8(p.type());
            w.u32((int) p.remainSecs());
        }
        w.u32((int) cera);
        for (int i = 0; i < QUEST_SLOTS; i++) {
            if (i < activeQuests.size()) {
                QuestSlot q = activeQuests.get(i);
                w.u16(q.questId());
                w.u32((int) q.trigger());
            } else {
                w.u16(EMPTY_QUEST_ID);
                w.u32((int) EMPTY_QUEST_TRIGGER);
            }
        }
        w.u8(completedQuestSets.size());
        for (CompletedQuestSet set : completedQuestSets) {
            w.u8(set.category());
            byte[] bitmap = set.bitmap();
            w.u32(bitmap.length);
            w.bytes(bitmap);
        }
        w.u8(townId);
        w.u8(tutorialSkipable);
        w.u8(completedTutorials.size());
        for (int t : completedTutorials) {
            w.u8(t);
        }
        w.u16(fatigueBatteryCharging);
        w.u16(fatigueGrownUpBuff);
        return w.toArray();
    }

    /** A4 选角回包只有 3 个任务槽（ACT5 是 10 个）。 */
    public static final int A4_QUEST_SLOTS = 3;

    /** 官方日志"命令 153：教程最终模块 31 已确认"。 */
    public static final int A4_TUTORIAL_FINAL = 31;

    /**
     * A4 选角回包（官方成品服新角色 09:16:48.708 逐字节）。字段与 {@link #toBytes()} 同序，区别只有三处：
     * 开头一个 u32 是角色号（ACT5 是 regTime + createTime 两个 u32）；任务槽 3 个；末尾只有一个 u16。
     * <pre>01 | u32 characNo | u16 uid | u16×3 疲劳 | u8 特权数 {u8,u32} | u32 点券
     *    | 3×(u16 任务, u32 进度) | u8 档数 {u8,u32,64B} | u8 城镇 | u8 可跳过 | u8 教程数 {u8} | u16</pre>
     */
    public byte[] toA4Bytes(int characNo) {
        PacketWriter w = new PacketWriter(
                64 + premiums.size() * 5 + completedTutorials.size()
                        + completedQuestSets.size() * (1 + 4 + BITMAP_LENGTH));
        w.u8(RESULT_OK);
        w.u32(characNo);
        w.u16(uniqueId);
        w.u16(totalFatigue);
        w.u16(totalMaxFatigue);
        w.u16(usedFatigue);
        w.u8(premiums.size());
        for (Premium p : premiums) {
            w.u8(p.type());
            w.u32((int) p.remainSecs());
        }
        w.u32((int) cera);
        for (int i = 0; i < A4_QUEST_SLOTS; i++) {
            if (i < activeQuests.size()) {
                QuestSlot q = activeQuests.get(i);
                w.u16(q.questId());
                w.u32((int) q.trigger());
            } else {
                w.u16(EMPTY_QUEST_ID);
                w.u32((int) EMPTY_QUEST_TRIGGER);
            }
        }
        w.u8(completedQuestSets.size());
        for (CompletedQuestSet set : completedQuestSets) {
            w.u8(set.category());
            byte[] bitmap = set.bitmap();
            w.u32(bitmap.length);
            w.bytes(bitmap);
        }
        w.u8(townId);
        // A4 尾部只有 u32 已完成教程位图（官方：新角色 00000000，教程打完 FFFFFFFF），
        // 没有 ACT5 的 tutorialSkipable / 条数+编号表 / u16 充电。按 ACT5 写，客户端把编号表当位图读，
        // 判成教程没完成，每次选角都重进教程。
        int mask = 0;
        for (int t : completedTutorials) {
            if (t == A4_TUTORIAL_FINAL) {
                mask = -1;   // 官方：最终模块确认后整块 FFFFFFFF
                break;
            }
            if (t >= 0 && t < Integer.SIZE) {
                mask |= 1 << t;
            }
        }
        w.u32(mask);
        return w.toArray();
    }

    public static byte[] failure(int errorCode) {
        if (errorCode < 0 || errorCode > 0xFF) {
            throw new PacketFormatException("错误码超出 u8：" + errorCode);
        }
        return new byte[]{RESULT_FAILED, (byte) errorCode};
    }

    public static byte[] failure() {
        return failure(ERROR_GENERIC);
    }

    public static final int ERROR_GENERIC = 1;
}
