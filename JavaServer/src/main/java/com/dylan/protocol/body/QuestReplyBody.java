package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;
import com.dylan.pvf.script.QuestRewardType;
import com.dylan.pvf.script.QuestType;

import java.util.List;

public final class QuestReplyBody {

    private QuestReplyBody() {
    }

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static byte[] rejected(int error) {
        if (error < 0 || error > 0xFF) {
            throw new PacketFormatException("任务错误码超出 u8：" + error);
        }
        return new PacketWriter(2).u8(RESULT_FAIL).u8(error).toArray();
    }

    public record GivenItem(int slot, int itemId, int count) {
        public GivenItem {
            if (slot < 0 || slot > 0xFFFF) {
                throw new PacketFormatException("槽号超出 u16：" + slot);
            }
            if (itemId < 0) {
                throw new PacketFormatException("itemId 不能是负数：" + itemId);
            }
            if (count < 0) {
                throw new PacketFormatException("数量不能是负数：" + count);
            }
        }
    }

    public static byte[] accepted(int questId, long trigger, List<GivenItem> items) {
        checkU16("questId", questId);
        checkU32("trigger", trigger);
        if (items.size() > 0xFF) {
            throw new PacketFormatException("接任务发的道具条数是 u8，装不下 " + items.size());
        }
        PacketWriter w = new PacketWriter(8 + items.size() * 10);
        w.u8(RESULT_OK).u16(questId).u32((int) trigger).u8(items.size());
        for (GivenItem item : items) {
            w.u16(item.slot()).u32(item.itemId()).u32(item.count());
        }
        return w.toArray();
    }

    public static byte[] givenUp(int questId) {
        checkU16("questId", questId);
        return new PacketWriter(3).u8(RESULT_OK).u16(questId).toArray();
    }

    public static byte[] triggerSet(int questId, long trigger) {
        checkU16("questId", questId);
        checkU32("trigger", trigger);
        return new PacketWriter(7).u8(RESULT_OK).u16(questId).u32((int) trigger).toArray();
    }

    public record Consumed(int kind, int slot, int count) {

        public static final int FROM_BAG = 0;

        public Consumed {
            if (kind < 0 || kind > 0xFF) {
                throw new PacketFormatException("kind 超出 u8：" + kind);
            }
            if (slot < 0 || slot > 0xFFFF) {
                throw new PacketFormatException("槽号超出 u16：" + slot);
            }
            if (count < 0) {
                throw new PacketFormatException("数量不能是负数：" + count);
            }
        }

        public static Consumed fromBag(int slot, int count) {
            return new Consumed(FROM_BAG, slot, count);
        }
    }

    public record Reward(int slot, int itemId, int count, int abilityType, int abilityValue) {

        public Reward(int slot, int itemId, int count) {
            this(slot, itemId, count, 0, 0);
        }

        public Reward {
            if (slot < 0 || slot > 0xFFFF) {
                throw new PacketFormatException("槽号超出 u16：" + slot);
            }
            if (itemId < 0) {
                throw new PacketFormatException("itemId 不能是负数：" + itemId);
            }
            if (count < 0) {
                throw new PacketFormatException("数量不能是负数：" + count);
            }
            if (abilityType < 0 || abilityType > 0xFF) {
                throw new PacketFormatException("abilityType 超出 u8：" + abilityType);
            }
            if (abilityValue < 0 || abilityValue > 0xFFFF) {
                throw new PacketFormatException("abilityValue 超出 u16：" + abilityValue);
            }
        }
    }

    public record GrantedSkill(int flag, int skillId, int level) {
        public GrantedSkill {
            if (flag < 0 || flag > 0xFF || skillId < 0 || skillId > 0xFF
                    || level < 0 || level > 0xFF) {
                throw new PacketFormatException("转职技能三元组都是 u8："
                        + flag + "/" + skillId + "/" + level);
            }
        }
    }

    public static byte[] finished(int questId, QuestType questType, long exp,
                                  List<Consumed> consumed, QuestRewardType rewardType,
                                  List<Reward> rewards, int rewardParam,
                                  List<GrantedSkill> skills) {
        checkU16("questId", questId);
        checkU32("exp", exp);
        int type = questType == null ? QuestType.SEEKING.code() : questType.code();
        QuestRewardType reward = rewardType == null ? QuestRewardType.ITEM : rewardType;

        if (com.dylan.net.ServerConfig.A4) {
            // A4 交任务回包布局不同，见 finishedA4 注释
            return finishedA4(questId, consumed, reward, rewards, rewardParam, skills);
        }
        PacketWriter w = new PacketWriter(32 + consumed.size() * 7 + rewards.size() * 13);
        w.u8(RESULT_OK).u16(questId).u8(type).u32((int) exp);

        if (questType != null && questType.consumesItems()) {
            if (consumed.size() > 0xFF) {
                throw new PacketFormatException("扣道具条数是 u8，装不下 " + consumed.size());
            }
            w.u8(consumed.size());
            for (Consumed c : consumed) {
                w.u8(c.kind()).u16(c.slot()).u32(c.count());
            }
        }

        w.u8(reward.code());
        if (reward.isItem()) {
            if (rewards.size() > 0xFF) {
                throw new PacketFormatException("奖励条数是 u8，装不下 " + rewards.size());
            }
            w.u8(rewards.size());
            for (Reward r : rewards) {
                w.u16(r.slot()).u32(r.itemId()).u32(r.count())
                        .u8(r.abilityType()).u16(r.abilityValue());
            }
        } else if (reward.carriesSkillPayload()) {
            if (rewardParam < 0 || rewardParam > 0xFF) {
                throw new PacketFormatException("rewardParam 超出 u8：" + rewardParam);
            }
            if (skills.size() > 0xFF) {
                throw new PacketFormatException("技能条数是 u8，装不下 " + skills.size());
            }
            w.u8(rewardParam).u8(skills.size());
            for (GrantedSkill s : skills) {
                w.u8(s.flag()).u8(s.skillId()).u8(s.level());
            }
        }
        return w.toArray();
    }

    /**
     * A4 客户端读法（DNF.exe 0041581B）：u16 任务号、u8 0、u8 扣道具条数 + 条数×(u8, u16, u32)、
     * u8 奖励类型；物品奖励 u8 条数 + 条数×(u16 槽, u32 物品, u32 数量)，技能奖励 u8 参数 + u8 条数 + 条数×3 字节。
     * 没有 ACT5 的任务类型、u32 经验（A4 经验走 NOTI 37）和每条奖励的增幅 u8+u16。
     * 官方任务 1016 实抓 37 字节：{@code 01 f803 00 03 [03 1f04 03000000]×3 00 01 0900 c1460000 01000000}。
     */
    private static byte[] finishedA4(int questId, List<Consumed> consumed, QuestRewardType reward,
                                     List<Reward> rewards, int rewardParam, List<GrantedSkill> skills) {
        if (consumed.size() > 0xFF || rewards.size() > 0xFF || skills.size() > 0xFF) {
            throw new PacketFormatException("条数是 u8：扣 " + consumed.size() + "，奖 " + rewards.size()
                    + "，技能 " + skills.size());
        }
        PacketWriter w = new PacketWriter(8 + consumed.size() * 7 + rewards.size() * 10 + skills.size() * 3);
        w.u8(RESULT_OK).u16(questId).u8(0);
        w.u8(consumed.size());
        for (Consumed c : consumed) {
            w.u8(c.kind()).u16(c.slot()).u32(c.count());
        }
        w.u8(reward.code());
        if (reward.isItem()) {
            w.u8(rewards.size());
            for (Reward r : rewards) {
                w.u16(r.slot()).u32(r.itemId()).u32(r.count());
            }
        } else if (reward.carriesSkillPayload()) {
            w.u8(rewardParam).u8(skills.size());
            for (GrantedSkill s : skills) {
                w.u8(s.flag()).u8(s.skillId()).u8(s.level());
            }
        }
        return w.toArray();
    }

    private static void checkU16(String what, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new PacketFormatException(what + " 超出 u16：" + value);
        }
    }

    private static void checkU32(String what, long value) {
        if (value < 0 || value > 0xFFFF_FFFFL) {
            throw new PacketFormatException(what + " 超出 u32：" + value);
        }
    }
}
